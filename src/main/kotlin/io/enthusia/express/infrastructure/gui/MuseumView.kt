@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/** Platform presentation owns only its protected navigation inventory. */
internal class MuseumView(val plugin: JavaPlugin, private val allowed: (Player) -> Boolean,
    private val onQuit: (UUID) -> Unit) : Listener {
    val dialogs = DialogMenus(plugin)
    private val theme = GuiTheme(plugin)
    private data class Chest(val inventory: Inventory, val actions: Map<Int, MenuChoice>, val closeSlot: Int? = null)
    private val chests = HashMap<UUID, Chest>()
    fun show(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        actions: List<MenuChoice>,
        kind: String = "museum",
    ) {
        if (!allowed(player)) return
        val skin = theme.forViewer(player)
        val navigation = fields.isEmpty() && skin.postOffice && kind in setOf("home", "catalog")
        if (!navigation && dialogs.show(player, title, body, fields, actions)) return
        val rich = fields.isEmpty() && skin.postOffice
        player.closeInventory()
        val inv = Bukkit.createInventory(null, 54, skin.title(if (rich) kind else "museum", title.take(32)))
        val bounded = actions.take(45)
        val slots = if (!rich) bounded.indices.associateWith { it } else when (kind) {
            "home" -> PostOfficeLayout.home(bounded.map { it.label })
            "catalog" -> PostOfficeLayout.catalog(bounded.map { it.label })
            else -> bounded.indices.associateWith { it }
        }
        if (rich) for (slot in 0..53) inv.setItem(slot, skin.quiet(Material.GRAY_STAINED_GLASS_PANE))
        val anchors = mapOf("Packages" to 10,"Letters" to 13,"Announcements" to 16,
            "Sent mail" to 37,"Mail settings & help" to 40,"Submit museum artwork" to 43,
            "My submissions" to 46,"Curator queue" to 52)
        slots.forEach { (slot, index) ->
            val choice = bounded[index]
            val hidden = rich && kind == "home" && anchors[choice.label] != slot
            val item = if (hidden) skin.quiet(Material.PAPER)
                else skin.item(if (rich) "museum." + PostOfficeLayout.icon(choice.label) else "museum.control", Material.PAPER)
            val meta = item.itemMeta!!
            meta.setDisplayName(choice.label.take(70))
            meta.lore = body.lines().flatMap { it.chunked(44) }.take(20)
            item.itemMeta = meta
            inv.setItem(slot, item)
        }
        val closeSlot = if (rich && kind == "home") 53 else null
        if (closeSlot != null) {
            val close = skin.item("mailbox.close", Material.BARRIER)
            val meta = close.itemMeta!!
            meta.setDisplayName("§cClose")
            close.itemMeta = meta
            inv.setItem(closeSlot, close)
        }
        chests[player.uniqueId] = Chest(inv, slots.mapValues { bounded[it.value] }, closeSlot)
        player.openInventory(inv)
    }


    @EventHandler fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val chest = chests[player.uniqueId]?.takeIf { it.inventory === event.view.topInventory } ?: return
        event.isCancelled = true
        if (event.click != org.bukkit.event.inventory.ClickType.LEFT) return
        if (event.rawSlot == chest.closeSlot) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (chests[player.uniqueId] === chest && player.openInventory.topInventory === chest.inventory) player.closeInventory()
            })
            return
        }
        val choice = chest.actions[event.rawSlot] ?: return
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                if (allowed(player) && chests[player.uniqueId] === chest && player.openInventory.topInventory === chest.inventory) {
                    choice.action(emptyMap())
                }
            },
        )
    }


    @EventHandler fun drag(event: InventoryDragEvent) {
        if (chests[event.whoClicked.uniqueId]?.inventory === event.view.topInventory) event.isCancelled = true
    }


    @EventHandler fun close(event: InventoryCloseEvent) {
        if (chests[event.player.uniqueId]?.inventory === event.inventory) chests.remove(event.player.uniqueId)
    }


    @EventHandler fun quit(event: PlayerQuitEvent) {
        chests.remove(event.player.uniqueId)
        onQuit(event.player.uniqueId)
    }


    fun shutdown() { dialogs.close(); chests.clear() }
}
