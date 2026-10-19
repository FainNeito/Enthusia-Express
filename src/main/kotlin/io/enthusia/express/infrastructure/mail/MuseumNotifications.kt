package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.infrastructure.util.MainThread
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/** Durable at-least-once review updates. Offline sessions and failed sends retain their updates. */
class MuseumNotifications(private val plugin: JavaPlugin, private val store: MailStore, private val main: MainThread) {
    private val pending = HashSet<UUID>()

    fun poll() {
        if (!plugin.isEnabled) return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (!player.hasPermission("enthusiaexpress.use") || !pending.add(player.uniqueId)) return@forEach
            main.complete(store.museumNotices(player.uniqueId)) { notices, error ->
                pending.remove(player.uniqueId)
                if (error != null || !plugin.isEnabled || !player.isOnline || Bukkit.getPlayer(player.uniqueId) !== player) return@complete
                notices.orEmpty().forEach { notice ->
                    player.sendMessage(net.kyori.adventure.text.Component.text("Museum update: ${notice.message} Use /mail mapart mine."))
                    main.complete(store.acknowledgeMuseumNotice(notice.id, player.uniqueId)) { _, failure ->
                        if (failure != null) plugin.logger.warning("Museum update acknowledgment will retry: ${notice.id}")
                    }
                }
            }
        }
    }
}
