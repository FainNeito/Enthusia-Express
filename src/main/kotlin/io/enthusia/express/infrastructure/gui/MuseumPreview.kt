@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MuseumEntry
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

private const val PREVIEW_TICKS = 1200

/** Self-only client equipment; never places a preview item in the authoritative inventory. */
internal class MuseumPreview(private val plugin: JavaPlugin, private val allowed: (Player) -> Boolean) {
    private data class Session(val player: Player, val owner: UUID?, var ticks: Int = PREVIEW_TICKS)
    private val sessions = HashMap<UUID, Session>()
    private var task: BukkitTask? = null

    fun active(player: Player): Boolean = sessions[player.uniqueId]?.player === player

    fun show(player: Player, entry: MuseumEntry, page: Int) {
        if (!allowed(player) || !canView(player, entry.submission.mail.sender)) return
        val tiles = entry.tiles.entries.sortedBy { it.key }
        val tile = tiles.getOrNull(page)
        if (tile == null) {
            player.sendMessage("§cThat artwork preview page is unavailable.")
            return
        }
        val map = Bukkit.getMap(tile.key)
        if (map == null) {
            player.sendMessage("§cMap data is unavailable on this server. Nothing was collected.")
            return
        }
        val item = ItemStack(Material.FILLED_MAP)
        val meta = item.itemMeta as MapMeta
        meta.mapView = map
        item.itemMeta = meta
        // Closing existing shipping UI must run its normal compensation lifecycle first.
        player.closeInventory()
        if (!allowed(player) || !canView(player, entry.submission.mail.sender)) return
        close(player)
        sessions[player.uniqueId] = Session(player, entry.submission.mail.sender)
        var shown = false
        try {
            player.sendEquipmentChange(player, mapOf(EquipmentSlot.HAND to item, EquipmentSlot.OFF_HAND to ItemStack(Material.AIR)))
            player.sendMap(map)
            controls(player, entry, page, tiles.size, tile.value)
            if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 1L, 1L)
            shown = true
        } finally {
            // MainThread logs callback failures; cleanup also runs for third-party map renderer exceptions.
            if (!shown) {
                close(player)
                player.sendMessage("§cThe map preview could not be displayed. Nothing was collected.")
            }
        }
    }

    private fun controls(player: Player, entry: MuseumEntry, page: Int, total: Int, copies: Int) {
        val id = entry.submission.mail.id
        player.sendMessage("§e${entry.details.title} §7· Map ${page + 1}/$total · $copies ${if (copies == 1) "copy" else "copies"}")
        player.sendMessage("§7Preview only. Sneak to close; expires after 60 seconds. Tiles are ordered by map ID, not artwork position.")
        var controls = Component.empty()
        if (page > 0) controls = controls.append(link("[Previous] ", "mail mapart preview $id ${page - 1}"))
        if (page + 1 < total) controls = controls.append(link("[Next] ", "mail mapart preview $id ${page + 1}"))
        controls = controls.append(link("[Artwork details] ", "mail mapart view $id"))
            .append(link("[Close preview]", "mail mapart preview-close"))
        player.sendMessage(controls)
    }

    private fun link(label: String, command: String): Component =
        Component.text(label).clickEvent(ClickEvent.runCommand("/$command"))

    private fun canView(player: Player, owner: UUID?): Boolean = owner == player.uniqueId || player.hasPermission(MANAGE_PERMISSION)

    private fun tick() {
        sessions.values.toList().forEach { session ->
            if (--session.ticks <= 0 || !allowed(session.player) || !canView(session.player, session.owner)) close(session.player)
        }
    }

    fun close(player: Player) {
        if (sessions[player.uniqueId]?.player !== player) return
        sessions.remove(player.uniqueId)
        if (player.isOnline && Bukkit.getPlayer(player.uniqueId) === player) player.updateInventory()
        if (sessions.isEmpty()) {
            task?.cancel()
            task = null
        }
    }

    fun shutdown() {
        sessions.values.toList().forEach { close(it.player) }
        task?.cancel()
        task = null
    }
}
