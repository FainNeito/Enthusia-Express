// Reflection is confined to the optional cosmetic integration boundary.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.hook

import io.enthusia.express.domain.MailNotification
import java.util.UUID
import java.util.function.BiConsumer
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/** Sends transient client packets; never registers or awards a server advancement. */
private const val NATIVE_MODE = "native"
private const val TOAST_MODE = "notifications.toast.mode"

/** Sends transient client packets; never registers or awards a server advancement. */
class NativeMailToast(private val plugin: JavaPlugin) : BiConsumer<Player, MailNotification> {
    private data class Visible(val player: Player, val key: Any, val cleanup: BukkitTask)
    private val visible = HashMap<UUID, Visible>()
    private var warned = false
    private val bridge: NativeToastPackets? = connect()

    override fun accept(player: Player, notice: MailNotification) {
        if (notice.count <= 0 || !player.isOnline) return
        val text=message(notice)
        when(plugin.config.getString(TOAST_MODE,NATIVE_MODE)) {
            "actionbar" -> { player.sendActionBar(Component.text(text.replace('\n',' ')));return }
            "chat" -> { player.sendMessage(Component.text(text.replace('\n',' ')));return }
        }
        try {
            val active = bridge ?: return fallback(player, notice)
            clear(player.uniqueId)
            val key = active.key("enthusiaexpress:mail/" + UUID.randomUUID())
            if(text==notice.text()) active.show(player,key,notice) else active.showWithText(player,key,notice,text)
            val cleanup = Bukkit.getScheduler().runTaskLater(plugin, Runnable { clear(player.uniqueId) }, 100L)
            visible[player.uniqueId] = Visible(player, key, cleanup)
        } catch (error: ReflectiveOperationException) {
            warn(error)
            fallback(player, notice)
        } catch (error: LinkageError) {
            warn(error)
            fallback(player, notice)
        }
    }

    /** Remove only this renderer's identifier from the same connected session. */
    fun clear(id: UUID) {
        val shown = visible.remove(id) ?: return
        shown.cleanup.cancel()
        if (shown.player.isOnline && Bukkit.getPlayer(id) === shown.player) {
            try { bridge?.remove(shown.player, shown.key) }
            catch (error: ReflectiveOperationException) { warn(error) }
            catch (error: LinkageError) { warn(error) }
        }
    }

    /** Cancel cosmetic cleanup tasks and remove temporary client entries on plugin disable. */
    fun close() { visible.keys.toList().forEach { clear(it) } }

    private fun fallback(player: Player, notice: MailNotification) {
        if (plugin.config.getBoolean("notifications.toast.chat-fallback", true))
            player.sendMessage(Component.text(message(notice).replace('\n', ' ')))
    }

    private fun message(notice: MailNotification): String {
        val type=when(notice.type) { io.enthusia.express.domain.MailType.PACKAGE -> "Package";io.enthusia.express.domain.MailType.LETTER -> "Letter";else -> "Notice" }
        val sender=notice.sender.orEmpty().replace(Regex("§."),"").filter { !it.isISOControl() }.take(16).ifBlank { "Server" }
        val key=if(notice.count==1) "single-message" else "group-message"
        return plugin.config.getString("notifications.toast.$key",notice.text()).orEmpty()
            .replace("{sender}",sender).replace("{type}",type).replace("{count}",notice.count.toString()).take(240)
    }

    private fun warn(error: Throwable) {
        if (!warned) {
            warned = true
            plugin.logger.warning("Mail toasts unavailable; using configured chat fallback: ${error.message}")
        }
    }

    private fun connect(): NativeToastPackets? {
        if (!plugin.config.getBoolean("notifications.toast.enabled", true)) return null
        if(plugin.config.getString(TOAST_MODE,NATIVE_MODE)!=NATIVE_MODE) return null
        return try { NativeToastPackets() }
        catch (error: ReflectiveOperationException) { warn(error); null }
        catch (error: LinkageError) { warn(error); null }
    }

}
