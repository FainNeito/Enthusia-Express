package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.Text
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class JoinNotificationService(private val plugin: JavaPlugin, private val repository: MailStore, private val main: MainThread) : Listener {
    /** Recognize a nonempty completed mail summary. */
    private fun hasMail(summary: io.enthusia.express.domain.MailSummary?) = summary != null && summary.total() > 0

    /** Fetch unread counts asynchronously and notify only the same still-connected player session. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!plugin.config.getBoolean("notifications.join-mail.enabled", true)) return
        val session = event.player
        val id = session.uniqueId
        main.complete(repository.pendingMail(id)) { summary, error ->
            if (error != null) {
                plugin.logger.warning("Could not check joining player's mail: $error")
            } else if (session.isOnline && Bukkit.getPlayer(id) === session && hasMail(summary)) {
                session.sendMessage(Text.msg(plugin.config, "join-mail", mapOf(
                    "packages" to checkNotNull(summary).packages.toString(), "letters" to summary.letters.toString(),
                    "announcements" to summary.announcements.toString(), "total" to summary.total().toString()
                )))
            }
        }
    }
}
