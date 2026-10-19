// Private fallback contracts document the optional integration boundary.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.infrastructure.util.Text
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin

private const val POST_OFFICE_CONFIG = "gui.nexo.post-office"

/** Isolates optional Nexo calls from normal inventory controls. */
fun interface NexoItemSource {
    /** Build an item by its Nexo registry identifier without transferring it to a player. */
    fun build(nexo: Plugin, id: String): ItemStack?
}

/** Resolve the documented public API through Nexo's loader only when the plugin is present. */
class ReflectiveNexoItems @JvmOverloads constructor(private val apiClassName: String = "com.nexomc.nexo.api.NexoItems") : NexoItemSource {
    /** Build through the public registry API without linking optional classes at startup. */
    override fun build(nexo: Plugin, id: String): ItemStack? {
        val api = Class.forName(apiClassName, true, nexo.javaClass.classLoader)
        val builder = api.getMethod("itemFromId", String::class.java).invoke(null, id) ?: return null
        return builder.javaClass.getMethod("build").invoke(builder) as? ItemStack
    }
}

/** Resolve custom assets when menus open so asynchronous Nexo loading and reloads are respected. */
class GuiTheme @JvmOverloads constructor(private val plugin: JavaPlugin, private val source: NexoItemSource = ReflectiveNexoItems(),
    private val viewer: Player? = null, private val glyphs: NexoGlyphSource = ReflectiveNexoGlyphs()) {
    private val warned = HashSet<String>()

    /** Bind optional presentation to the viewer without keeping a global player reference. */
    fun forViewer(player: Player): GuiTheme = GuiTheme(plugin, source, player, glyphs)

    /** Rich hit targets require the explicit profile, accepted Java pack and loaded blank item. */
    val postOffice: Boolean by lazy {
        if (viewer == null || !plugin.config.getBoolean(POST_OFFICE_CONFIG, false)) false
        else {
            val id = plugin.config.getString("gui.nexo.icons.chrome.blank")
            val nexo = available()
            if (id.isNullOrBlank() || nexo == null) false
            else try {
                source.build(nexo, id)?.type?.isAir == false &&
                    listOf("home","mailbox","sent","shipping","catalog","museum").all {
                        glyphs.component(nexo, "ee_postoffice_$it") != null
                    }
            }
            catch (_: ReflectiveOperationException) { false }
            catch (_: LinkageError) { false }
            catch (_: RuntimeException) { false }
        }
    }

    /** Empty visual hit targets stay real protected inventory controls. */
    fun quiet(material: Material): ItemStack =
        (if (postOffice) item("chrome.blank", material) else ItemStack(material)).also { stack ->
            val meta = stack.itemMeta!!
            meta.setDisplayName(" ")
            stack.itemMeta = meta
        }

    /** Return a configured Nexo glyph title only when the optional integration is enabled. */
    fun title(menu: String, fallback: String): String {
        if (available() == null) return fallback
        if (viewer != null && plugin.config.getBoolean(POST_OFFICE_CONFIG, false) && !postOffice) return fallback
        return plugin.config.getString("gui.nexo.titles.$menu")?.takeIf { it.isNotBlank() }?.let(Text::color) ?: fallback
    }

    /** Copy a custom registry item and fall back to the requested vanilla material on any integration failure. */
    @Suppress("TooGenericExceptionCaught") // Optional third-party builders may throw unchecked failures.
    fun item(key: String, material: Material): ItemStack {
        if (viewer != null && plugin.config.getBoolean(POST_OFFICE_CONFIG, false) && !postOffice) return ItemStack(material)
        val id = plugin.config.getString("gui.nexo.icons.$key")?.takeIf { it.isNotBlank() } ?: return ItemStack(material)
        val nexo = available() ?: return ItemStack(material)
        return try {
            val built = source.build(nexo, id)
            if (built == null || built.type.isAir) {
                warn(id, "item is missing or not loaded")
                ItemStack(material)
            } else built.clone().also { it.amount = 1 }
        } catch (error: ReflectiveOperationException) {
            warn(id, error.javaClass.simpleName)
            ItemStack(material)
        } catch (error: LinkageError) {
            warn(id, error.javaClass.simpleName)
            ItemStack(material)
        } catch (error: RuntimeException) {
            warn(id, error.javaClass.simpleName)
            ItemStack(material)
        }
    }

    /** Avoid Nexo class loading until administrators enable the integration and Nexo is running. */
    private fun available(): Plugin? {
        if (!plugin.config.getBoolean("gui.nexo.enabled", false)) return null
        if (viewer != null && (!viewer.hasResourcePack() || bedrockViewer(viewer))) return null
        return Bukkit.getPluginManager()?.getPlugin("Nexo")?.takeIf { it.isEnabled }
    }

    /** Fail closed if backend Floodgate is present but its identity API cannot be resolved. */
    private fun bedrockViewer(player: Player): Boolean {
        if (Bukkit.getPluginManager()?.isPluginEnabled("floodgate") != true) return false
        return try {
            val api = Class.forName("org.geysermc.floodgate.api.FloodgateApi")
            api.getMethod("isFloodgatePlayer", java.util.UUID::class.java)
                .invoke(api.getMethod("getInstance").invoke(null), player.uniqueId) as Boolean
        } catch (_: ReflectiveOperationException) { true }
        catch (_: LinkageError) { true }
    }

    /** Reserve five font lines for a 42-pixel banner before normal dialog text. */
    fun dialogBody(body: String): net.kyori.adventure.text.Component {
        val plain = net.kyori.adventure.text.Component.text(body)
        if (!postOffice) return plain
        return try {
            val nexo = available() ?: return plain
            val banner = glyphs.component(nexo, "ee_postoffice_banner") ?: return plain
            banner.append(net.kyori.adventure.text.Component.text("\n\n\n\n\n")).append(plain)
        } catch (_: ReflectiveOperationException) { plain }
        catch (_: LinkageError) { plain }
        catch (_: RuntimeException) { plain }
    }

    /** Report a missing asset once while retrying resolution on later menu opens. */
    private fun warn(id: String, reason: String) {
        if (warned.add(id)) plugin.logger.warning("Nexo GUI item '$id' unavailable ($reason); using vanilla icon.")
    }
}
