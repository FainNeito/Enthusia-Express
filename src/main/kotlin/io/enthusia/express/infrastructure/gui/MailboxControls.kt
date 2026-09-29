// Private display contracts document read-only controls consistently with the reviewed services.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MAIL_PAGE_SIZE
import io.enthusia.express.domain.MailType
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/** Consistent mailbox chrome separates navigation, context, content and paging. */
object MailboxControls {
    data class SummaryState(val type: MailType, val page: Int, val sent: Boolean,
                            val count: Int, val unread: Int, val loading: Boolean)
    const val CONTENT_START = 18
    const val CONTENT_END = 44
    const val PREVIOUS_SLOT = 45
    const val MODE_SLOT = 47
    const val PAGE_SLOT = 49
    const val REFRESH_SLOT = 51
    const val CLOSE_SLOT = 52
    const val NEXT_SLOT = 53

    /** Build the fixed header, quiet context strip and bottom toolbar. */
    fun render(inventory: Inventory, type: MailType, page: Int, sent: Boolean, theme: GuiTheme) {
        fill(inventory, 0..8, Material.BLACK_STAINED_GLASS_PANE)
        fill(inventory, 9..17, Material.GRAY_STAINED_GLASS_PANE)
        fill(inventory, 45..53, Material.BLACK_STAINED_GLASS_PANE)
        val location = if (sent) "Sent Mail" else "Inbox"
        inventory.setItem(1, category(Material.CHEST, "Packages", type == MailType.PACKAGE, theme))
        inventory.setItem(4, category(Material.WRITABLE_BOOK, "Letters", type == MailType.LETTER, theme))
        inventory.setItem(7, category(Material.BELL, "Announcements", type == MailType.ANNOUNCEMENT, theme))
        inventory.setItem(13, summary(SummaryState(type, page, sent, 0, 0, loading = true), theme))

        if (page > 0) inventory.setItem(PREVIOUS_SLOT,
            item(theme, "mailbox.previous", Material.ARROW, "§ePrevious Page", listOf("§7Go to page $page.")))
        inventory.setItem(MODE_SLOT, item(theme, if (sent) "mailbox.inbox" else "mailbox.sent",
            Material.ENDER_CHEST, if (sent) "§eOpen Inbox" else "§eView Sent Mail",
            listOf("§7Currently: §f$location")))
        inventory.setItem(PAGE_SLOT, item(theme, "mailbox.page", Material.MAP, "§fPage ${page + 1}",
            listOf("§7Newest mail is shown first.")))
        inventory.setItem(REFRESH_SLOT, item(theme, "mailbox.refresh", Material.CLOCK, "§eRefresh",
            listOf("§7Reload this page.")))
        inventory.setItem(CLOSE_SLOT, item(theme, "mailbox.close", Material.BARRIER, "§cClose"))
    }

    /** Replace the loading summary and reveal next-page navigation only when another page can exist. */
    fun renderLoaded(inventory: Inventory, state: SummaryState, theme: GuiTheme) {
        inventory.setItem(13, summary(state.copy(loading = false), theme))
        if (state.count == MAIL_PAGE_SIZE) inventory.setItem(NEXT_SLOT,
            item(theme, "mailbox.next", Material.ARROW, "§eNext Page", listOf("§7Go to page ${state.page + 2}.")))
        else inventory.setItem(NEXT_SLOT, quiet(Material.BLACK_STAINED_GLASS_PANE))
    }
    /** Place a centered empty-state card in the content area instead of sending chat noise. */
    fun emptyState(inventory: Inventory, type: MailType, page: Int, sent: Boolean) {
        val noun = when (type) {
            MailType.PACKAGE -> "Packages"
            MailType.LETTER -> "Letters"
            MailType.ANNOUNCEMENT -> "Announcements"
        }
        val prefix = if (sent) "Sent " else ""
        inventory.setItem(31, plain(Material.PAPER, "§fNo $prefix$noun Here", listOf(
            "§7There is no ${noun.lowercase()} mail on page ${page + 1}.",
            "§7Try another category or page."
        )))
    }

    /** Use color, icon and text together so the active category is obvious at a glance. */
    private fun category(material: Material, name: String, selected: Boolean, theme: GuiTheme): ItemStack =
        item(theme, "mailbox." + name.lowercase(java.util.Locale.ROOT) + if (selected) "-selected" else "",
            if (selected) Material.LIME_DYE else material,
            if (selected) "§a▶ $name" else "§f$name",
            listOf(if (selected) "§aSelected" else "§7Click to select"))

    private fun summary(state: SummaryState, theme: GuiTheme): ItemStack {
        val name = state.type.name.lowercase().replaceFirstChar { it.uppercase() }
        val location = if (state.sent) "Sent Mail" else "Inbox"
        val lore = if (state.loading) listOf("§7$location", "§7Loading page ${state.page + 1}…")
        else listOf("§7$location §8• §f${state.count} shown",
            if (!state.sent) "§7Unread on page: §a${state.unread}" else "§7Read-only history",
            "§7Page ${state.page + 1}")
        return item(theme, "mailbox.summary", when (state.type) {
            MailType.PACKAGE -> Material.CHEST
            MailType.LETTER -> Material.WRITABLE_BOOK
            MailType.ANNOUNCEMENT -> Material.BELL
        }, "§6$name", lore)
    }
    private fun fill(inventory: Inventory, range: IntRange, material: Material) {
        for (slot in range) inventory.setItem(slot, quiet(material))
    }

    private fun quiet(material: Material): ItemStack = plain(material, " ")

    private fun item(theme: GuiTheme, key: String, material: Material, name: String,
                     lore: List<String> = emptyList()): ItemStack {
        val item = theme.item(key, material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun plain(material: Material, name: String, lore: List<String> = emptyList()): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        meta.lore = lore
        item.itemMeta = meta
        return item
    }
}
