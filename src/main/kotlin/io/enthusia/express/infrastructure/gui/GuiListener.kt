// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

class GuiListener(private val shipping: ShippingService, private val mailbox: MailboxService) : Listener {
    /** Keep mail menus under plugin control and defer actions that close or replace the inventory. */
    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (shipping.owns(player, top)) {
            shippingClick(event, player)
        } else if (mailbox.owns(player)) {
            event.isCancelled = true
            if (event.rawSlot >= 0 && event.rawSlot < top.size) mailbox.deferClick(player, event.rawSlot)
        }
    }

    /** Allow supported cargo-slot interactions while protecting buttons and the placement marker. */
    private fun shippingClick(event: InventoryClickEvent, player: Player) {
        val top = event.view.topInventory
        val raw = event.rawSlot
        val normal = event.click == ClickType.LEFT || event.click == ClickType.RIGHT
        val allowedSlot = raw == ShippingService.PACKAGE_SLOT || raw >= top.size
        event.isCancelled = !normal || !allowedSlot
        if (!normal) return
        when (raw) {
            ShippingService.PACKAGE_SLOT -> {
                if (shipping.isPlaceholder(top.getItem(raw))) {
                    event.isCancelled = true
                    shipping.deferPlaceholderDeposit(player, top)
                } else shipping.deferPlaceholderRefresh(player, top)
            }
            ShippingService.CANCEL_SLOT -> shipping.defer(player, top, false)
            ShippingService.CONFIRM_SLOT -> shipping.defer(player, top, true)
        }
    }

    /** Prevent drag operations from overwriting protected menu slots. */
    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (mailbox.owns(player) && event.rawSlots.any { it < top.size }) event.isCancelled = true
        if (shipping.owns(player, top) && event.rawSlots.any { it < top.size }) {
            event.isCancelled = true
        }
    }

    /** Return shipping cargo within the close event and discard the matching mailbox session. */
    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        shipping.returnPackageOnClose(player, event.inventory)
        mailbox.close(player, event.inventory)
    }
}
