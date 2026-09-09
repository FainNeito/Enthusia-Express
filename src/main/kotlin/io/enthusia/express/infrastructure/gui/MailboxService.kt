package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import io.enthusia.express.infrastructure.util.Text
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

class MailboxService @JvmOverloads constructor(
    private val plugin: JavaPlugin,
    private val repository: MailStore,
    private val combatHook: CombatLogXHook,
    private val main: MainThread,
    private val sounds: SoundFeedback = SoundFeedback(plugin),
) {
    private val sessions = HashMap<UUID, Session>()
    private val claiming = HashSet<UUID>()
    private var stopping = false

    private class Session(val inventory: Inventory, val type: MailType, val page: Int) {
        val records = HashMap<Int, MailRecord>()
        var loaded = false
    }

    private fun allowed(player: Player): Boolean =
        !stopping && player.isOnline && !player.isDead && player.hasPermission("enthusiaexpress.use") &&
            player.hasPermission("enthusiaexpress.inbox") && combatHook.mayUseMail(player)

    fun open(player: Player, type: MailType) { openPage(player, type, 0) }

    private fun openPage(player: Player, type: MailType, page: Int) {
        if (!allowed(player)) {
            player.sendMessage(Text.msg(plugin.config, "mail-unavailable"))
            return
        }
        if (page < 0 || page > 1_000_000) return
        player.closeInventory()
        val inv = Bukkit.createInventory(null, 54, "$TITLE_PREFIX - $type ${page + 1}")
        val session = Session(inv, type, page)
        sessions[player.uniqueId] = session
        inv.setItem(0, icon(Material.ARROW, "§ePrevious page"))
        inv.setItem(1, icon(Material.CHEST, "§6Packages"))
        inv.setItem(4, icon(Material.WRITABLE_BOOK, "§eLetters"))
        inv.setItem(7, icon(Material.BELL, "§bAnnouncements"))
        inv.setItem(8, icon(Material.ARROW, "§eNext page"))
        player.openInventory(inv)
        main.complete(repository.listInbox(player.uniqueId, type, page)) inbox@{ records, error ->
            if (!active(player, session)) return@inbox
            if (error != null) {
                player.sendMessage(Text.msg(plugin.config, "database-error"))
                return@inbox
            }
            val inboxRecords = checkNotNull(records)
            var slot = 9
            for (record in inboxRecords) {
                val item: ItemStack = try {
                    val decoded = if (record.type() == MailType.PACKAGE) ItemCodec.decode(record.payload()) else
                        icon(Material.WRITTEN_BOOK, (if (record.unread()) "§e[Unread] " else "§7[Read] ") + record.senderName())
                    val meta = decoded.itemMeta!!
                    meta.lore = listOf("§7From: " + record.senderName(), "§7Mail #" + record.id(),
                        if (record.type() == MailType.PACKAGE) "§aClick to claim" else "§aClick to read")
                    decoded.itemMeta = meta
                    decoded
                } catch (e: RuntimeException) {
                    val unreadable = icon(Material.BARRIER, "§cUnreadable mail #" + record.id())
                    plugin.logger.warning("Unreadable mail #${record.id()}: $e")
                    unreadable
                }
                inv.setItem(slot, item)
                session.records[slot++] = record
            }
            session.loaded = true
            if (inboxRecords.isEmpty()) player.sendMessage(Text.msg(plugin.config, "mailbox-empty"))
        }
    }

    private fun active(player: Player, session: Session): Boolean = allowed(player) &&
        sessions[player.uniqueId] === session && player.openInventory.topInventory === session.inventory

    fun owns(player: Player): Boolean {
        val session = sessions[player.uniqueId]
        return session != null && player.openInventory.topInventory === session.inventory
    }

    fun deferClick(player: Player, slot: Int) {
        val session = sessions[player.uniqueId]
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (session != null && active(player, session)) click(player, slot)
        })
    }

    fun click(player: Player, slot: Int) {
        val session = sessions[player.uniqueId]
        if (session == null || !active(player, session)) {
            player.closeInventory()
            return
        }
        when (slot) {
            1 -> { open(player, MailType.PACKAGE); return }
            4 -> { open(player, MailType.LETTER); return }
            7 -> { open(player, MailType.ANNOUNCEMENT); return }
            0 -> { if (session.page > 0) openPage(player, session.type, session.page - 1); return }
            8 -> { if (session.loaded && session.records.size == 45) openPage(player, session.type, session.page + 1); return }
        }
        val visible = session.records[slot] ?: return
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.get(visible.id())) lookup@{ record, error ->
            if (error != null || record == null || !active(player, session) || record.recipient() != player.uniqueId ||
                (record.status() != MailStatus.UNCLAIMED && record.status() != MailStatus.RETURNED)) {
                claiming.remove(player.uniqueId)
                return@lookup
            }
            val item: ItemStack
            try {
                item = ItemCodec.decode(record.payload())
            } catch (e: RuntimeException) {
                claiming.remove(player.uniqueId)
                player.sendMessage("§cThis mail cannot be decoded; contact an administrator.")
                return@lookup
            }
            if (record.type() == MailType.PACKAGE) claimPackage(player, record, item) else {
                try {
                    player.closeInventory()
                    player.openBook(item)
                    main.complete(repository.markRead(record.id(), player.uniqueId)) { marked, failure ->
                        if (failure != null) plugin.logger.warning("Cannot mark mail read: $failure")
                        else if (marked == true) sounds.play(player, SoundFeedback.Cue.LETTER_OPEN)
                    }
                } catch (e: RuntimeException) {
                    player.sendMessage("§cThis book could not be opened.")
                } finally {
                    claiming.remove(player.uniqueId)
                }
            }
        }
    }

    private fun claimPackage(player: Player, record: MailRecord, stack: ItemStack) {
        if (!player.hasPermission("enthusiaexpress.packages.claim") || player.inventory.firstEmpty() == -1) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cYou need claim permission and an empty inventory slot.")
            return
        }
        main.complete(repository.claim(record.id(), player.uniqueId)) claim@{ claimed, error ->
            if (error != null || claimed != true) {
                claiming.remove(player.uniqueId)
                player.sendMessage("§cThat package could not be claimed.")
                return@claim
            }
            if (!allowed(player) || !player.hasPermission("enthusiaexpress.packages.claim") || player.inventory.firstEmpty() == -1) {
                main.complete(repository.restoreClaim(record)) { restored, failure ->
                    claiming.remove(player.uniqueId)
                    if (failure != null || restored != true) plugin.logger.severe("Could not restore undelivered claim #${record.id()}: $failure")
                }
                return@claim
            }
            player.inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
            main.complete(repository.confirmDelivery(record.id(), player.uniqueId)) { acknowledged, failure ->
                if (failure != null || acknowledged != true)
                    plugin.logger.severe("Delivered package #${record.id()} retains its sending reservation: $failure")
            }
            claiming.remove(player.uniqueId)
            sounds.play(player, SoundFeedback.Cue.PACKAGE_CLAIM)
            player.sendMessage("§aPackage claimed.")
            if (owns(player)) open(player, MailType.PACKAGE)
        }
    }

    fun close(player: Player, inventory: Inventory) {
        val session = sessions[player.uniqueId]
        if (session != null && session.inventory === inventory) sessions.remove(player.uniqueId)
    }

    fun shutdown() {
        stopping = true
        for (player in Bukkit.getOnlinePlayers()) if (owns(player)) player.closeInventory()
        sessions.clear()
    }

    companion object {
        const val TITLE_PREFIX = "Enthusia Express Mailbox"

        private fun icon(material: Material, name: String): ItemStack {
            val stack = ItemStack(material)
            val meta = stack.itemMeta!!
            meta.setDisplayName(name)
            stack.itemMeta = meta
            return stack
        }
    }
}
