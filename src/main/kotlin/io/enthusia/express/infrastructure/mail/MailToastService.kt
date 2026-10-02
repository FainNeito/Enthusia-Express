// Session callback contracts document thread ownership and stale-login rejection.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailNotification
import io.enthusia.express.infrastructure.util.MainThread
import java.util.UUID
import java.util.function.BiConsumer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin

/** Main-thread session tracking, bounded polling, and grouped read-only arrival notifications. */
class MailToastService(private val plugin: JavaPlugin, private val repository: MailStore,
                       private val main: MainThread, private val renderer: BiConsumer<Player, MailNotification>) : Listener {
    private data class Session(val player: Player, var watermark: Long = 0,
                               var busy: Boolean = false, var nextPoll: Long = 0)
    private val sessions = LinkedHashMap<UUID, Session>()
    private var cycle = 0L
    private var closed = false

    /** Each login receives a fresh pending-mail summary, after the scheduler's initial delay. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!closed && plugin.config.getBoolean("notifications.toast.enabled", true))
            sessions[event.player.uniqueId] = Session(event.player, nextPoll = cycle + 2)
    }

    /** Remove state promptly; late completions retain only their obsolete session object. */
    @EventHandler
    fun onQuit(event: PlayerQuitEvent) { sessions.remove(event.player.uniqueId) }

    /** At most eight queries per tick and one in-flight query per session. */
    fun poll() {
        if (closed) return
        cycle++
        val due = sessions.values.filter { !it.busy && it.nextPoll <= cycle }.take(8)
        for (session in due) {
            // Move polled sessions to the back so a large online population cannot starve later joins.
            sessions.remove(session.player.uniqueId)
            sessions[session.player.uniqueId] = session
            session.busy = true
            session.nextPoll = cycle + 5
            main.complete(repository.mailNotification(session.player.uniqueId, session.watermark)) { notice, error ->
                complete(session, notice, error)
            }
        }
    }

    /** Discard old sessions before advancing a cursor or displaying anything. */
    private fun complete(session: Session, notice: MailNotification?, error: Throwable?) {
        session.busy = false
        val id = session.player.uniqueId
        if (closed || sessions[id] !== session) return
        if (!session.player.isOnline || Bukkit.getPlayer(id) !== session.player) return
        if (error != null) plugin.logger.warning("Could not check mail toast arrivals: $error")
        else if (notice != null) {
            session.watermark = notice.watermark
            if (notice.count > 0) renderer.accept(session.player, notice)
        }
    }

    /** Shutdown prevents callbacks from notifying a disconnected or replacement session. */
    fun close() { closed = true; sessions.clear() }
}
