// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

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
                checkNotNull(summary)
                notifyCategory(session, summary.packages, "package", "claim")
                notifyCategory(session, summary.letters, "letter", "read")
                notifyCategory(session, summary.announcements, "announcement", "read")
            }
        }
    }
    /** Announce only categories containing mail, with a matching pickup command. */
    private fun notifyCategory(player: org.bukkit.entity.Player, count: Int, noun: String, action: String) {
        if (count <= 0) return
        val category = "${noun}s"
        player.sendMessage(Text.msgOrDefault(plugin.config, "join-mail-$category",
            "&eYou've got mail! You have {count} {noun} for pickup. Use /mail inbox $category to $action!",
            mapOf("count" to count.toString(), "noun" to if (count == 1) noun else category)))
    }

}
