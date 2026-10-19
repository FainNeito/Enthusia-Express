@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.DialogSessions
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.lang.reflect.Proxy
import java.time.Duration
import java.util.function.Consumer

private const val FLOODGATE_API = "org.geysermc.floodgate.api.FloodgateApi"
private const val BUILDER_SUFFIX = "\$Builder"
private const val BUILD_METHOD = "build"
private const val TEXT_INPUT_BUILDER = "input.TextDialogInput\$Builder"
private const val CALLBACK_OPTIONS_BUILDER = "net.kyori.adventure.text.event.ClickCallback\$Options\$Builder"
private const val CREATE_METHOD = "create"

data class MenuField(val key: String, val label: String, val value: String = "", val max: Int = 80)

data class MenuChoice(val label: String, val action: (Map<String, String>) -> Unit)

private data class MenuScreen(val title: String, val body: String, val fields: List<MenuField>, val choices: List<MenuChoice>)

/** Optional platform UI only: callbacks return to the server thread and never own deposited cargo. */
class DialogMenus(private val plugin: JavaPlugin) {
    private val sessions = DialogSessions()
    private var warned = false

    @Suppress("LabeledExpression") // Drop stale callback work without escaping or continuing the scheduler action.
    fun show(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        choices: List<MenuChoice>,
    ): Boolean {
        // The existing inventory owner must return cargo or retain pending compensation before a form opens.
        player.closeInventory()
        if (!plugin.config.getBoolean("gui.dialogs.enabled", true)) return false
        val screen = MenuScreen(title, body, fields, choices)
        val token = sessions.open(player.uniqueId, System.currentTimeMillis())
        val action: (Int, Map<String, String>) -> Unit = { index, values ->
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    if (connected(player) &&
                        sessions.accept(player.uniqueId, token, System.currentTimeMillis())
                    ) {
                        choices.getOrNull(index)?.action?.invoke(values)
                    }
                },
            )
        }
        return try {
            val shown = present(player, screen, action)
            if (!shown) sessions.close(player.uniqueId)
            shown
        } catch (
            error: ReflectiveOperationException,
        ) {
            unavailable(error)
            sessions.close(player.uniqueId)
            false
        } catch (
            error: LinkageError,
        ) {
            unavailable(error)
            sessions.close(player.uniqueId)
            false
        } catch (error: ClassCastException) {
            unavailable(error)
            sessions.close(player.uniqueId)
            false
        } catch (error: IllegalArgumentException) {
            unavailable(error)
            sessions.close(player.uniqueId)
            false
        }
    }

    private fun present(player: Player, screen: MenuScreen, action: (Int, Map<String, String>) -> Unit): Boolean {
        if (isBedrock(player)) bedrock(player, screen, action)
        else {
            if (!supportsDialog(player)) return false
            native(player, screen, action)
        }
        return true
    }

    fun close() {
        sessions.clear()
    }

    private fun unavailable(error: Throwable) {
        if (!warned) {
            warned = true
            plugin.logger.info("Express dialogs unavailable; retaining chest/command fallback: ${error.message}")
        }
    }

    private fun connected(player: Player): Boolean =
        plugin.isEnabled && player.isOnline && Bukkit.getPlayer(player.uniqueId) === player

    private fun isBedrock(player: Player): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false
        val api = dialogStatic(FLOODGATE_API, "getInstance")
        return dialogCall(FLOODGATE_API, api, "isFloodgatePlayer", player.uniqueId) == true
    }

    private fun supportsDialog(player: Player): Boolean {
        val protocol =
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                val api = dialogStatic("com.viaversion.viaversion.api.Via", "getAPI")
                (dialogCall("com.viaversion.viaversion.api.ViaAPI", api, "getPlayerVersion", player.uniqueId) as Number).toInt()
            } else {
                player.protocolVersion
            }
        // ViaVersion ProtocolVersion.v1_21_6 is the first dialog-capable Java wire protocol.
        return protocol >= 771
    }

    @Suppress("LabeledExpression") // Object proxy methods must return their own JVM result, not dispatch a menu action.
    private fun native(
        player: Player,
        screen: MenuScreen,
        action: (Int, Map<String, String>) -> Unit,
    ) {
        val (title, body, fields, choices) = screen
        val prefix = "io.papermc.paper.registry.data.dialog."
        val inputs =
            fields.map { field ->
                val builder = dialogStatic(prefix + "input.DialogInput", "text", field.key, Component.text(field.label))
                dialogCall(prefix + TEXT_INPUT_BUILDER, builder, "initial", field.value)
                dialogCall(prefix + TEXT_INPUT_BUILDER, builder, "maxLength", field.max)
                dialogCall(prefix + TEXT_INPUT_BUILDER, builder, BUILD_METHOD)
            }
        val after = Class.forName(prefix + "DialogBase\$DialogAfterAction").getField("CLOSE").get(null)
        val base =
            dialogStatic(
                prefix + "DialogBase", CREATE_METHOD, Component.text(title), null, true, false, after,
                listOf(dialogStatic(prefix + "body.DialogBody", "plainMessage", Component.text(body))), inputs,
            )
        val optionsBuilder = dialogStatic("net.kyori.adventure.text.event.ClickCallback\$Options", "builder")
        dialogCall(CALLBACK_OPTIONS_BUILDER, optionsBuilder, "uses", 1)
        dialogCall(CALLBACK_OPTIONS_BUILDER, optionsBuilder, "lifetime", Duration.ofMinutes(3))
        val options = dialogCall(CALLBACK_OPTIONS_BUILDER, optionsBuilder, BUILD_METHOD)
        val callbackType = Class.forName(prefix + "action.DialogActionCallback")
        val buttons =
            choices.mapIndexed { index, choice ->
                val callback =
                    Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { proxy, method, args ->
                        if (method.declaringClass == Any::class.java) {
                            return@newProxyInstance when (method.name) {
                                "hashCode" -> System.identityHashCode(proxy)
                                "equals" -> proxy === args?.getOrNull(0)
                                else -> "EnthusiaExpress dialog callback"
                            }
                        }
                        if (method.name == "accept" && args?.size == 2 && args[1] === player) {
                            val response = args[0]
                            action(
                                index,
                                fields.associate {
                                    it.key to (dialogCall("io.papermc.paper.dialog.DialogResponseView", response, "getText", it.key) as? String).orEmpty()
                                },
                            )
                        }
                        null
                    }
                val click = dialogStatic(prefix + "action.DialogAction", "customClick", callback, options)
                dialogStatic(prefix + "ActionButton", CREATE_METHOD, Component.text(choice.label), null, 300, click)
            }
        val type = dialogStatic(prefix + "type.DialogType", "multiAction", buttons, null, 1)
        val build =
            Consumer<Any> { factory ->
                val builder = dialogCall("io.papermc.paper.registry.RegistryBuilderFactory", factory, "empty")
                dialogCall(prefix + "DialogRegistryEntry\$Builder", builder, "base", base)
                dialogCall(prefix + "DialogRegistryEntry\$Builder", builder, "type", type)
            }
        val dialog = dialogStatic("io.papermc.paper.dialog.Dialog", CREATE_METHOD, build)
        dialogCall("net.kyori.adventure.audience.Audience", player, "showDialog", dialog)
    }

    private fun bedrock(
        player: Player,
        screen: MenuScreen,
        action: (Int, Map<String, String>) -> Unit,
    ) {
        val (title, body, fields, choices) = screen
        val formName = "org.geysermc.cumulus.form." + if (fields.isEmpty()) "SimpleForm" else "CustomForm"
        val builder = dialogStatic(formName, "builder")
        dialogCall(formName + BUILDER_SUFFIX, builder, "title", title)
        if (fields.isEmpty()) {
            dialogCall(formName + BUILDER_SUFFIX, builder, "content", body)
            choices.forEach { dialogCall(formName + BUILDER_SUFFIX, builder, "button", it.label) }
        } else {
            dialogCall(formName + BUILDER_SUFFIX, builder, "label", body)
            fields.forEach { dialogCall(formName + BUILDER_SUFFIX, builder, "input", it.label, "", it.value) }
        }
        val response =
            Consumer<Any> { result ->
                if (fields.isEmpty()) {
                    action(
                        (dialogCall("org.geysermc.cumulus.response.SimpleFormResponse", result, "clickedButtonId") as Number).toInt(),
                        emptyMap(),
                    )
                } else {
                    action(
                        0,
                        fields.mapIndexed {
                                index,
                                field,
                            ->
                            field.key to (dialogCall("org.geysermc.cumulus.response.CustomFormResponse", result, "asInput", index + 1) as? String).orEmpty()
                        }.toMap(),
                    )
                }
            }
        dialogCall(formName + BUILDER_SUFFIX, builder, "validResultHandler", response)
        val form = dialogCall(formName + BUILDER_SUFFIX, builder, BUILD_METHOD)
        val api = dialogStatic(FLOODGATE_API, "getInstance")
        if (dialogCall(FLOODGATE_API, api, "sendForm", player.uniqueId, form) == false) {
            throw ReflectiveOperationException("Floodgate did not accept the form")
        }
    }

}
