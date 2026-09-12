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
    fun render(inventory: Inventory, type: MailType, page: Int, sent: Boolean, theme: GuiTheme) {
        val location = if (sent) "Sent mail" else "Inbox"
        inventory.setItem(0, item(theme, "mailbox.previous", Material.ARROW, "§ePrevious page"))
        inventory.setItem(1, category(Material.CHEST, "Packages", type == MailType.PACKAGE, location, theme))
        inventory.setItem(4, category(Material.WRITABLE_BOOK, "Letters", type == MailType.LETTER, location, theme))
        inventory.setItem(7, category(Material.BELL, "Announcements", type == MailType.ANNOUNCEMENT, location, theme))
        inventory.setItem(8, item(theme, "mailbox.next", Material.ARROW, "§eNext page"))
        inventory.setItem(3, item(theme, if (sent) "mailbox.inbox" else "mailbox.sent", Material.ENDER_CHEST, if (sent) "§eOpen inbox" else "§eView sent mail",
            listOf("§7Currently: $location")))
        inventory.setItem(5, item(theme, "mailbox.page", Material.PAPER, "§f$location", listOf("§7Page ${page + 1}")))
    }

    /** Use both color and text to identify the current category. */
    private fun category(material: Material, name: String, selected: Boolean, location: String, theme: GuiTheme): ItemStack =
        item(theme, "mailbox." + name.lowercase(java.util.Locale.ROOT) + if (selected) "-selected" else "", if (selected) Material.LIME_DYE else material, if (selected) "§a▶ $name" else "§7$name",
            listOf(if (selected) "§aSelected" else "§7Click to select", "§7$location"))

    /** Build a display-only control with compact tooltip lines. */
    private fun item(theme: GuiTheme, key: String, material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = theme.item(key, material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        meta.lore = lore
        item.itemMeta = meta
        return item
    }
}
