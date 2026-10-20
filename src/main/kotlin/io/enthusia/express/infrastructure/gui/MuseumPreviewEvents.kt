@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.UUID

/** Interrupt cosmetic viewing before an interaction could use a hidden real held item. */
internal class MuseumPreviewEvents(private val preview: MuseumPreview, private val invalidate: (UUID) -> Unit) : Listener {
    val interactions: Listener = MuseumPreviewInteractions(preview) { end(it) }
    private fun end(player: Player) { invalidate(player.uniqueId); preview.close(player) }

    @EventHandler(priority = EventPriority.LOWEST)
    fun command(event: PlayerCommandPreprocessEvent) = end(event.player)
    @EventHandler(priority = EventPriority.LOWEST)
    fun open(event: InventoryOpenEvent) { (event.player as? Player)?.let { end(it) } }
    @EventHandler fun quit(event: PlayerQuitEvent) = end(event.player)
    @EventHandler fun death(event: PlayerDeathEvent) = end(event.entity)
    @EventHandler fun teleport(event: PlayerTeleportEvent) = end(event.player)
    @EventHandler fun held(event: PlayerItemHeldEvent) = end(event.player)
    @EventHandler fun sneak(event: PlayerToggleSneakEvent) { if (event.isSneaking) end(event.player) }
}
