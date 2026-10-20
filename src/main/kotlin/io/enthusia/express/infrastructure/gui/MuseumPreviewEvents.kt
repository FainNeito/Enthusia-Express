@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractAtEntityEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import java.util.UUID

/** Interrupt cosmetic viewing before an interaction could use a hidden real held item. */
internal class MuseumPreviewEvents(private val preview: MuseumPreview, private val invalidate: (UUID) -> Unit) : Listener {
    private fun end(player: Player) { invalidate(player.uniqueId); preview.close(player) }
    private fun interrupt(player: Player): Boolean {
        if (!preview.active(player)) return false
        end(player)
        return true
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun command(event: PlayerCommandPreprocessEvent) = end(event.player)
    @EventHandler(priority = EventPriority.LOWEST)
    fun open(event: InventoryOpenEvent) { (event.player as? Player)?.let { end(it) } }
    @EventHandler fun quit(event: PlayerQuitEvent) = end(event.player)
    @EventHandler fun death(event: PlayerDeathEvent) = end(event.entity)
    @EventHandler fun teleport(event: PlayerTeleportEvent) = end(event.player)
    @EventHandler fun held(event: PlayerItemHeldEvent) = end(event.player)
    @EventHandler fun sneak(event: PlayerToggleSneakEvent) { if (event.isSneaking) end(event.player) }
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
