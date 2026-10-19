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

data class MenuField(val key: String, val label: String, val value: String = "", val max: Int = 80)

data class MenuChoice(val label: String, val action: (Map<String, String>) -> Unit)

/** Optional platform UI only: callbacks return to the server thread and never own deposited cargo. */
class DialogMenus(private val plugin: JavaPlugin) {
    private val sessions = DialogSessions()
    private var warned = false

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
        val token = sessions.open(player.uniqueId, System.currentTimeMillis())
        val action: (Int, Map<String, String>) -> Unit = { index, values ->
            Bukkit.getScheduler().runTask(
                plugin,
                Runnable {
                    if (plugin.isEnabled && player.isOnline && Bukkit.getPlayer(player.uniqueId) === player &&
                        sessions.accept(player.uniqueId, token, System.currentTimeMillis())
                    ) {
                        choices.getOrNull(index)?.action?.invoke(values)
                    }
                },
            )
        }
        return try {
            if (isBedrock(player)) {
                bedrock(player, title, body, fields, choices, action)
            } else {
                if (!supportsDialog(player)) {
                    sessions.close(player.uniqueId)
                    return false
                }
                native(player, title, body, fields, choices, action)
            }
            true
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

    fun close() {
        sessions.clear()
    }

    private fun unavailable(error: Throwable) {
        if (!warned) {
            warned = true
            plugin.logger.info("Express dialogs unavailable; retaining chest/command fallback: ${error.message}")
        }
    }

    private fun isBedrock(player: Player): Boolean {
        if (!Bukkit.getPluginManager().isPluginEnabled("floodgate")) return false
        val api = static("org.geysermc.floodgate.api.FloodgateApi", "getInstance")
        return call("org.geysermc.floodgate.api.FloodgateApi", api, "isFloodgatePlayer", player.uniqueId) == true
    }

    private fun supportsDialog(player: Player): Boolean {
        val protocol =
            if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                val api = static("com.viaversion.viaversion.api.Via", "getAPI")
                (call("com.viaversion.viaversion.api.ViaAPI", api, "getPlayerVersion", player.uniqueId) as Number).toInt()
            } else {
                player.protocolVersion
            }
        // ViaVersion ProtocolVersion.v1_21_6 is the first dialog-capable Java wire protocol.
        return protocol >= 771
    }

    private fun native(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        choices: List<MenuChoice>,
        action: (Int, Map<String, String>) -> Unit,
    ) {
        val prefix = "io.papermc.paper.registry.data.dialog."
        val inputs =
            fields.map { field ->
                val builder = static(prefix + "input.DialogInput", "text", field.key, Component.text(field.label))
                call(prefix + "input.TextDialogInput\$Builder", builder, "initial", field.value)
                call(prefix + "input.TextDialogInput\$Builder", builder, "maxLength", field.max)
                call(prefix + "input.TextDialogInput\$Builder", builder, "build")
            }
        val after = Class.forName(prefix + "DialogBase\$DialogAfterAction").getField("CLOSE").get(null)
        val base =
            static(
                prefix + "DialogBase", "create", Component.text(title), null, true, false, after,
                listOf(static(prefix + "body.DialogBody", "plainMessage", Component.text(body))), inputs,
            )
        val optionsBuilder = static("net.kyori.adventure.text.event.ClickCallback\$Options", "builder")
        call("net.kyori.adventure.text.event.ClickCallback\$Options\$Builder", optionsBuilder, "uses", 1)
        call("net.kyori.adventure.text.event.ClickCallback\$Options\$Builder", optionsBuilder, "lifetime", Duration.ofMinutes(3))
        val options = call("net.kyori.adventure.text.event.ClickCallback\$Options\$Builder", optionsBuilder, "build")
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
                                    it.key to (call("io.papermc.paper.dialog.DialogResponseView", response, "getText", it.key) as? String).orEmpty()
                                },
                            )
                        }
                        null
                    }
                val click = static(prefix + "action.DialogAction", "customClick", callback, options)
                static(prefix + "ActionButton", "create", Component.text(choice.label), null, 300, click)
            }
        val type = static(prefix + "type.DialogType", "multiAction", buttons, null, 1)
        val build =
            Consumer<Any> { factory ->
                val builder = call("io.papermc.paper.registry.RegistryBuilderFactory", factory, "empty")
                call(prefix + "DialogRegistryEntry\$Builder", builder, "base", base)
                call(prefix + "DialogRegistryEntry\$Builder", builder, "type", type)
            }
        val dialog = static("io.papermc.paper.dialog.Dialog", "create", build)
        call("net.kyori.adventure.audience.Audience", player, "showDialog", dialog)
    }

    private fun bedrock(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        choices: List<MenuChoice>,
        action: (Int, Map<String, String>) -> Unit,
    ) {
        val formName = "org.geysermc.cumulus.form." + if (fields.isEmpty()) "SimpleForm" else "CustomForm"
        val builder = static(formName, "builder")
        call(formName + "\$Builder", builder, "title", title)
        if (fields.isEmpty()) {
            call(formName + "\$Builder", builder, "content", body)
            choices.forEach { call(formName + "\$Builder", builder, "button", it.label) }
        } else {
            call(formName + "\$Builder", builder, "label", body)
            fields.forEach { call(formName + "\$Builder", builder, "input", it.label, "", it.value) }
        }
        val response =
            Consumer<Any> { result ->
                if (fields.isEmpty()) {
                    action(
                        (call("org.geysermc.cumulus.response.SimpleFormResponse", result, "clickedButtonId") as Number).toInt(),
                        emptyMap(),
                    )
                } else {
                    action(
                        0,
                        fields.mapIndexed {
                                index,
                                field,
                            ->
                            field.key to (call("org.geysermc.cumulus.response.CustomFormResponse", result, "asInput", index + 1) as? String).orEmpty()
                        }.toMap(),
                    )
                }
            }
        call(formName + "\$Builder", builder, "validResultHandler", response)
        val form = call(formName + "\$Builder", builder, "build")
        val api = static("org.geysermc.floodgate.api.FloodgateApi", "getInstance")
        if (call("org.geysermc.floodgate.api.FloodgateApi", api, "sendForm", player.uniqueId, form) == false) {
            throw ReflectiveOperationException("Floodgate did not accept the form")
        }
    }

    private fun static(
        type: String,
        method: String,
        vararg args: Any?,
    ): Any = invoke(type, null, method, args)

    private fun call(
        type: String,
        owner: Any,
        method: String,
        vararg args: Any?,
    ): Any = invoke(type, owner, method, args)

    private fun invoke(
        type: String,
        owner: Any?,
        name: String,
        args: Array<out Any?>,
    ): Any {
        val method =
            Class.forName(type).methods.firstOrNull { method ->
                method.name == name && method.parameterCount == args.size &&
                    method.parameterTypes.withIndex().all {
                            (index, p) ->
                        val value = args[index]
                        value == null || p.isInstance(value) || (p == Integer.TYPE && value is Int) || (p == java.lang.Boolean.TYPE && value is Boolean)
                    }
            }
                ?: throw NoSuchMethodException("$type.$name/${args.size}")
        return method.invoke(owner, *args) ?: Unit
    }
}
