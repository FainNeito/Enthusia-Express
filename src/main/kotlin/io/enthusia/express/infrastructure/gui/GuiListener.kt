package io.enthusia.express.infrastructure.gui

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent

class GuiListener(private val shipping: ShippingService, private val mailbox: MailboxService) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (shipping.owns(player, top)) {
            val raw = event.rawSlot
            val normal = event.click == ClickType.LEFT || event.click == ClickType.RIGHT
            val allowedSlot = raw == ShippingService.PACKAGE_SLOT || raw >= top.size
            event.isCancelled = !normal || !allowedSlot
            if (normal && raw == ShippingService.PACKAGE_SLOT) {
                if (shipping.isPlaceholder(top.getItem(raw))) {
                    event.isCancelled = true
                    shipping.deferPlaceholderDeposit(player, top)
                } else shipping.deferPlaceholderRefresh(player, top)
            }
            if (normal && raw == ShippingService.CANCEL_SLOT) shipping.defer(player, top, false)
            if (normal && raw == ShippingService.CONFIRM_SLOT) shipping.defer(player, top, true)
        } else if (mailbox.owns(player)) {
            event.isCancelled = true
            if (event.rawSlot >= 0 && event.rawSlot < top.size) mailbox.deferClick(player, event.rawSlot)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        val top = event.view.topInventory
        if (mailbox.owns(player) && event.rawSlots.any { it < top.size }) event.isCancelled = true
        if (shipping.owns(player, top) && event.rawSlots.any { it < top.size }) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        shipping.returnPackageOnClose(player, event.inventory)
        mailbox.close(player, event.inventory)
    }
}
