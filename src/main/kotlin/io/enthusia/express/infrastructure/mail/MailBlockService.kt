// Private callback contracts document session ownership consistently with other mail services.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.Text
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** Player-owned block preferences are persisted off-thread and acknowledged to the same session. */
class MailBlockService(private val plugin: JavaPlugin, private val repository: MailStore, private val main: MainThread) {
    /** Validate ownership before changing a player's block preference. */
    fun change(player: Player, target: OfflinePlayer, enabled: Boolean) {
        if (!allowed(player)) return
        if (player.uniqueId == target.uniqueId) {
            player.sendMessage("§cYou cannot block yourself.")
            return
        }
        val name = target.name ?: target.uniqueId.toString()
        main.complete(repository.setBlocked(player.uniqueId, target.uniqueId, name, enabled)) { _, error ->
            if (current(player)) {
                if (error != null) player.sendMessage(Text.msg(plugin.config, "database-error"))
                else player.sendMessage(Text.msgOrDefault(plugin.config, if (enabled) "player-blocked" else "player-unblocked",
                    if (enabled) "&aBlocked mail from {player}." else "&aUnblocked mail from {player}.", mapOf("player" to name)))
            }
        }
    }

    /** Show a bounded page of the caller's blocked sender names. */
    fun list(player: Player, page: Int) {
        if (!allowed(player)) return
        if (page !in 1..1_000_001) {
            player.sendMessage("§eUsage: /mail blocked [page]")
            return
        }
        main.complete(repository.listBlocked(player.uniqueId, page - 1)) { names, error ->
            if (current(player)) {
                if (error != null) player.sendMessage(Text.msg(plugin.config, "database-error"))
                else player.sendMessage("§eBlocked players (page $page): " + checkNotNull(names).joinToString(", ").ifEmpty { "none" })
            }
        }
    }

    /** Require the block-management permission on every entry point. */
    private fun allowed(player: Player): Boolean {
        val allowed = player.hasPermission("enthusiaexpress.use") && player.hasPermission("enthusiaexpress.block")
        if (!allowed) player.sendMessage(Text.msg(plugin.config, "no-permission"))
        return allowed
    }

    /** Never send a delayed preference result to a replacement login session. */
    private fun current(player: Player) = plugin.isEnabled && player.isOnline && Bukkit.getPlayer(player.uniqueId) === player
}
