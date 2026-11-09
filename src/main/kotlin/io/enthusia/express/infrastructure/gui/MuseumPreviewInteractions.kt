@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/** Cancel an interaction before it could use the real item hidden by a cosmetic map. */
internal class MuseumPreviewInteractions(private val preview: MuseumPreview, private val end: (Player) -> Unit) : Listener {
    private fun interrupt(player: Player): Boolean {
        if (!preview.active(player)) return false
        end(player)
        return true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun interact(event: PlayerInteractEvent) { if (interrupt(event.player)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun entity(event: PlayerInteractEntityEvent) { if (interrupt(event.player)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun entityAt(event: PlayerInteractAtEntityEvent) { if (interrupt(event.player)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun attack(event: EntityDamageByEntityEvent) { (event.damager as? Player)?.let { if (interrupt(it)) event.isCancelled = true } }
    @EventHandler(priority = EventPriority.LOWEST)
    fun swap(event: PlayerSwapHandItemsEvent) { if (interrupt(event.player)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drop(event: PlayerDropItemEvent) { if (interrupt(event.player)) event.isCancelled = true }
    @EventHandler(priority = EventPriority.LOWEST)
    fun click(event: InventoryClickEvent) { (event.whoClicked as? Player)?.let { if (interrupt(it)) event.isCancelled = true } }
    @EventHandler(priority = EventPriority.LOWEST)
    fun drag(event: InventoryDragEvent) { (event.whoClicked as? Player)?.let { if (interrupt(it)) event.isCancelled = true } }
}
