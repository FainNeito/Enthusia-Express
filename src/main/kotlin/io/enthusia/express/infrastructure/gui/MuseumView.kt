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
    private data class Chest(val inventory: Inventory, val actions: List<MenuChoice>)
    private val chests = HashMap<UUID, Chest>()
    fun show(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        actions: List<MenuChoice>,
    ) {
        if (!allowed(player)) return
        if (dialogs.show(player, title, body, fields, actions)) return
        player.closeInventory()
        val inv = Bukkit.createInventory(null, 54, theme.title("museum", title.take(32)))
        actions.take(45).forEachIndexed { index, choice ->
            val item = theme.item("museum.control", Material.PAPER)
            val meta = item.itemMeta!!
            meta.setDisplayName(choice.label.take(70))
            meta.lore = body.lines().flatMap { it.chunked(44) }.take(20)
            item.itemMeta = meta
            inv.setItem(index, item)
        }
        chests[player.uniqueId] = Chest(inv, actions.take(45))
        player.openInventory(inv)
    }


    @EventHandler fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val chest = chests[player.uniqueId]?.takeIf { it.inventory === event.view.topInventory } ?: return
        event.isCancelled = true
        if (event.rawSlot !in chest.actions.indices || event.click != org.bukkit.event.inventory.ClickType.LEFT) return
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                if (allowed(player) && chests[player.uniqueId] === chest && player.openInventory.topInventory === chest.inventory) {
                    chest.actions[event.rawSlot].action(
                        emptyMap(),
                    )
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
