// Private display contracts document read-only controls consistently with the reviewed services.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MailType
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Compact mailbox controls keep category, direction and page separate from the window title. */
object MailboxControls {
    /** Render a visibly selected tab and distinct direction/page controls. */
    fun render(inventory: Inventory, type: MailType, page: Int, sent: Boolean) {
        val location = if (sent) "Sent mail" else "Inbox"
        inventory.setItem(0, item(Material.ARROW, "§ePrevious page"))
        inventory.setItem(1, category(Material.CHEST, "Packages", type == MailType.PACKAGE, location))
        inventory.setItem(4, category(Material.WRITABLE_BOOK, "Letters", type == MailType.LETTER, location))
        inventory.setItem(7, category(Material.BELL, "Announcements", type == MailType.ANNOUNCEMENT, location))
        inventory.setItem(8, item(Material.ARROW, "§eNext page"))
        inventory.setItem(3, item(Material.ENDER_CHEST, if (sent) "§eOpen inbox" else "§eView sent mail",
            listOf("§7Currently: $location")))
        inventory.setItem(5, item(Material.PAPER, "§f$location", listOf("§7Page ${page + 1}")))
    }

    /** Use both color and text to identify the current category. */
    private fun category(material: Material, name: String, selected: Boolean, location: String): ItemStack =
        item(if (selected) Material.LIME_DYE else material, if (selected) "§a▶ $name" else "§7$name",
            listOf(if (selected) "§aSelected" else "§7Click to select", "§7$location"))

    /** Build a display-only control with compact tooltip lines. */
    private fun item(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        meta.lore = lore
        item.itemMeta = meta
        return item
    }
}
