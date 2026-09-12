package io.enthusia.express.infrastructure.command

import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

/** Cache known recipients once at startup; completion never enumerates player files. */
class RecipientNames : Listener {
    private val names = HashMap<UUID, String>()

    init {
        Bukkit.getOfflinePlayers()?.forEach { player -> player.name?.let { names[player.uniqueId] = it } }
    }

    /** Refresh a player's cached name when they join, including renamed accounts. */
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) { names[event.player.uniqueId] = event.player.name }

    /** Suggest cached and currently online names without blocking disk or profile lookups. */
    fun matching(partial: String): List<String> = (names.values + Bukkit.getOnlinePlayers().map { it.name })
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
        .filter { it.startsWith(partial, ignoreCase = true) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)
}
