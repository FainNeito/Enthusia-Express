// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments
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

// Session ownership and claim state must remain in the same lifecycle owner.
@Suppress("TooManyFunctions")
class MailboxService @JvmOverloads constructor(
    private val plugin: JavaPlugin,
    private val repository: MailStore,
    private val combatHook: CombatLogXHook,
    private val main: MainThread,
    private val sounds: SoundFeedback = SoundFeedback(plugin),
    private val acknowledgments: DeliveryAcknowledgments? = null,
) {
    private val sessions = HashMap<UUID, Session>()
    private val claiming = HashSet<UUID>()
    private var stopping = false

    private class Session(val inventory: Inventory, val type: MailType, val page: Int) {
        val records = HashMap<Int, MailRecord>()
        var loaded = false
    }

    /** Require an online, living, authorized player outside combat before mailbox access. */
    private fun allowed(player: Player): Boolean =
        !stopping && player.isOnline && !player.isDead && player.hasPermission("enthusiaexpress.use") &&
            player.hasPermission("enthusiaexpress.inbox") && combatHook.mayUseMail(player)

    /** Open the first page of a mail category on the server thread. */
    fun open(player: Player, type: MailType) { openPage(player, type, 0) }

    /** Create a bounded inbox session and request its rows asynchronously. */
    private fun openPage(player: Player, type: MailType, page: Int) {
        if (!allowed(player)) {
            player.sendMessage(Text.msg(plugin.config, "mail-unavailable"))
            return
        }
        if (page < 0 || page > 1_000_000) return
        val existing = sessions[player.uniqueId]?.inventory?.takeIf { player.openInventory.topInventory === it }
        val inv = existing ?: Bukkit.createInventory(null, 54, TITLE_PREFIX)
        if (existing != null) inv.clear()
        val session = Session(inv, type, page)
        sessions[player.uniqueId] = session
        inv.setItem(0, icon(Material.ARROW, "§ePrevious page"))
        inv.setItem(1, icon(Material.CHEST, "§6Packages"))
        inv.setItem(4, icon(Material.WRITABLE_BOOK, "§eLetters"))
        inv.setItem(7, icon(Material.BELL, "§bAnnouncements"))
        inv.setItem(8, icon(Material.ARROW, "§eNext page"))
        inv.setItem(3, icon(Material.PAPER, "§7${type.name.lowercase().replaceFirstChar { it.uppercase() }}: page ${page + 1}"))
        if (existing == null) player.openInventory(inv)
        main.complete(repository.listInbox(player.uniqueId, type, page)) { records, error ->
            renderInbox(player, session, records, error)
        }
    }

    /** Populate only the still-active session after a database lookup completes. */
    private fun renderInbox(player: Player, session: Session, records: List<MailRecord>?, error: Throwable?) {
        val inv = session.inventory

        if (!active(player, session)) return
        if (error != null) {
            player.sendMessage(Text.msg(plugin.config, "database-error"))
            return
        }
        val inboxRecords = checkNotNull(records)
        var slot = 9
        for (record in inboxRecords) {
            val item = mailIcon(record)
            inv.setItem(slot, item)
            session.records[slot++] = record
        }
        session.loaded = true
        if (inboxRecords.isEmpty()) player.sendMessage(Text.msg(plugin.config, "mailbox-empty"))
    }

    /** Create a display copy of mail metadata, using a barrier for unreadable persisted payloads. */
    // Persisted ItemStack data can fail in version-specific serializers; show a barrier for that row.
    @Suppress("TooGenericExceptionCaught")
    private fun mailIcon(record: MailRecord): ItemStack = try {
        val decoded = if (record.type == MailType.PACKAGE) ItemCodec.decode(record.payload) else
            icon(Material.WRITTEN_BOOK, (if (record.unread) "§e[Unread] " else "§7[Read] ") + record.senderName)
        val meta = decoded.itemMeta!!
        meta.lore = listOf("§7From: " + record.senderName, "§7Mail #" + record.id,
            if (record.type == MailType.PACKAGE) "§aClick to claim" else "§aClick to read")
        decoded.itemMeta = meta
        decoded
    } catch (e: RuntimeException) {
        val unreadable = icon(Material.BARRIER, "§cUnreadable mail #" + record.id)
        plugin.logger.warning("Unreadable mail #${record.id}: $e")
        unreadable
    }

    /** Check permissions, player state and exact inventory-session identity before asynchronous completion. */
    private fun active(player: Player, session: Session): Boolean = allowed(player) &&
        sessions[player.uniqueId] === session && player.openInventory.topInventory === session.inventory

    /** Identify the exact open inventory associated with this player session. */
    fun owns(player: Player): Boolean {
        val session = sessions[player.uniqueId]
        return session != null && player.openInventory.topInventory === session.inventory
    }

    /** Schedule a click for the next tick and reject stale sessions before dispatch. */
    fun deferClick(player: Player, slot: Int) {
        val session = sessions[player.uniqueId]
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (session != null && active(player, session)) click(player, slot)
        })
    }

    /** Navigate backward only when a previous page exists. */
    private fun previousPage(player: Player, session: Session) {
        if (session.page > 0) openPage(player, session.type, session.page - 1)
    }

    /** Request the next page only after a full current page has loaded. */
    private fun nextPage(player: Player, session: Session) {
        if (session.loaded && session.records.size == 45) openPage(player, session.type, session.page + 1)
    }

    /** Handle navigation or reserve one in-flight lookup for a visible mail entry. */
    fun click(player: Player, slot: Int) {
        val session = sessions[player.uniqueId]
        if (session == null || !active(player, session)) {
            player.closeInventory()
            return
        }
        if (navigate(player, session, slot)) return
        val visible = session.records[slot] ?: return
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.get(visible.id)) { record, error ->
            completeLookup(player, session, record, error)
        }
    }

    /** Handle category and page buttons, returning whether the slot was a navigation control. */
    private fun navigate(player: Player, session: Session, slot: Int): Boolean {
        when (slot) {
            1 -> { open(player, MailType.PACKAGE) }
            4 -> { open(player, MailType.LETTER) }
            7 -> { open(player, MailType.ANNOUNCEMENT) }
            0 -> { previousPage(player, session) }
            8 -> { nextPage(player, session) }
            else -> return false
        }
        return true
    }

    /** Revalidate session ownership and eligible row state after the asynchronous lookup. */
    private fun validRecord(player: Player, session: Session, record: MailRecord) =
        active(player, session) && record.recipient == player.uniqueId &&
            record.status in setOf(MailStatus.UNCLAIMED, MailStatus.RETURNED)

    /** Decode an eligible row and route it to package claiming or book viewing. */
    // A corrupt payload must release the in-flight click without making the mail claimable twice.
    @Suppress("TooGenericExceptionCaught")
    private fun completeLookup(player: Player, session: Session, record: MailRecord?, error: Throwable?) {
        if (error != null || record == null || !validRecord(player, session, record)) {
            claiming.remove(player.uniqueId)
            return
        }
        val item: ItemStack
        try {
            item = ItemCodec.decode(record.payload)
        } catch (e: RuntimeException) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cThis mail cannot be decoded; contact an administrator.")
            return
        }
        if (record.type == MailType.PACKAGE) claimPackage(player, record, item) else {
            openBook(player, record, item)
        }
    }

    /** Open a decoded book and mark it read without transferring or claiming the original item. */
    // Paper's book-opening boundary may reject decoded data with an unchecked runtime failure.
    @Suppress("TooGenericExceptionCaught")
    private fun openBook(player: Player, record: MailRecord, item: ItemStack) {
        try {
            player.closeInventory()
            player.openBook(item)
            main.complete(repository.markRead(record.id, player.uniqueId)) { marked, failure ->
                if (failure != null) plugin.logger.warning("Cannot mark mail read: $failure")
                else if (marked == true) sounds.play(player, SoundFeedback.Cue.LETTER_OPEN)
            }
        } catch (e: RuntimeException) {
            player.sendMessage("§cThis book could not be opened.")
        } finally {
            claiming.remove(player.uniqueId)
        }
    }

    /** Require inventory capacity and claim permission before reserving the package in storage. */
    private fun claimPackage(player: Player, record: MailRecord, stack: ItemStack) {
        if (!player.hasPermission("enthusiaexpress.packages.claim") || player.inventory.firstEmpty() == -1) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cYou need claim permission and an empty inventory slot.")
            return
        }
        main.complete(repository.claim(record.id, player.uniqueId)) { claimed, error ->
            completeClaim(player, record, stack, claimed, error)
        }
    }

    /** Recheck delivery eligibility, restore undelivered claims, and queue acknowledgments only after giving items. */
    private fun completeClaim(player: Player, record: MailRecord, stack: ItemStack, claimed: Boolean?, error: Throwable?) {
        if (error != null || claimed != true) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cThat package could not be claimed.")
            return
        }
        if (!allowed(player) || !player.hasPermission("enthusiaexpress.packages.claim") || player.inventory.firstEmpty() == -1) {
            restoreUndelivered(player, record)
            return
        }
        player.inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
        acknowledgeDelivery(player, record)
        claiming.remove(player.uniqueId)
        sounds.play(player, SoundFeedback.Cue.PACKAGE_CLAIM)
        player.sendMessage("§aPackage claimed.")
        if (owns(player)) open(player, MailType.PACKAGE)
    }

    /** Record completed inventory delivery for durable retry without restoring or redelivering its items. */
    private fun acknowledgeDelivery(player: Player, record: MailRecord) {
        val journal = acknowledgments
        if (journal != null) {
            main.complete(journal.record(record.id, player.uniqueId)) { _, failure ->
                if (failure != null) plugin.logger.severe("Cannot queue delivered package #${record.id}: $failure")
            }
            return
        }
        main.complete(repository.confirmDelivery(record.id, player.uniqueId)) { acknowledged, failure ->
            if (failure != null || acknowledged != true)
                plugin.logger.severe("Delivered package #${record.id} retains its sending reservation: $failure")
        }
    }

    /** Compensate a successful reservation when player state prevents inventory delivery. */
    private fun restoreUndelivered(player: Player, record: MailRecord) {
        main.complete(repository.restoreClaim(record)) { restored, failure ->
            claiming.remove(player.uniqueId)
            if (failure != null || restored != true) plugin.logger.severe("Could not restore undelivered claim #${record.id}: $failure")
        }
    }

    /** Discard only the session belonging to the inventory being closed. */
    fun close(player: Player, inventory: Inventory) {
        val session = sessions[player.uniqueId]
        if (session != null && session.inventory === inventory) sessions.remove(player.uniqueId)
    }

    /** Reject new mailbox access and close owned menus before pending completions drain. */
    fun shutdown() {
        stopping = true
        for (player in Bukkit.getOnlinePlayers()) if (owns(player)) player.closeInventory()
        sessions.clear()
    }

    companion object {
        const val TITLE_PREFIX = "Mailbox"

        /** Create a menu decoration with a display name and no persisted-mail mutation. */
        private fun icon(material: Material, name: String): ItemStack {
            val stack = ItemStack(material)
            val meta = stack.itemMeta!!
            meta.setDisplayName(name)
            stack.itemMeta = meta
            return stack
        }
    }
}
