// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MAIL_PAGE_SIZE
import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.MapartSubmission
import io.enthusia.express.domain.MapartQueue
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.hook.MovementLease
import io.enthusia.express.infrastructure.hook.MovementLocks
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import io.enthusia.express.infrastructure.util.Text
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.logging.Level
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

// Session ownership and claim state must remain in the same lifecycle owner.
@Suppress("TooManyFunctions")
// Keep explicit injectable dependencies and the existing Java constructor overloads.
class MailboxService @JvmOverloads @Suppress("LongParameterList") constructor(
    private val plugin: JavaPlugin,
    private val repository: MailStore,
    private val combatHook: CombatLogXHook,
    private val main: MainThread,
    private val sounds: SoundFeedback = SoundFeedback(plugin),
    private val acknowledgments: DeliveryAcknowledgments? = null,
    private val movementLocks: MovementLocks = MovementLocks.NOOP,
) {
    private val theme = GuiTheme(plugin)
    private val sessions = HashMap<UUID, Session>()
    private val claiming = HashSet<UUID>()
    private val loading = HashSet<UUID>()
    private val requested = HashMap<UUID, Pair<Player, Session>>()
    private var stopping = false

    private class Session(val inventory: Inventory, val type: MailType, val page: Int, val sent: Boolean,
                          val mapart: Boolean = false, val processed: Boolean = false) {
        val records = HashMap<Int, MailRecord>()
        var loaded = false
    }

    /** Require an online, living, authorized player outside combat before mailbox access. */
    private fun allowed(player: Player, sent: Boolean = false): Boolean =
        !stopping && player.isOnline && !player.isDead && player.hasPermission("enthusiaexpress.use") &&
            player.hasPermission(if (sent) "enthusiaexpress.sent" else "enthusiaexpress.inbox") && combatHook.mayUseMail(player)

    /** Open the first page of a mail category on the server thread. */
    fun open(player: Player, type: MailType) { openPage(player, type, 0) }

    /** Open the sender-owned history without giving access to recipient claims. */
    fun openSent(player: Player, type: MailType) { openPage(player, type, 0, true) }

    /** Any permission holder can access the shared intake and processed-history views. */
    fun openMapart(player: Player, processed: Boolean = false) { openMapartPage(player, 0, processed) }

    private fun managerAccess(player: Player): Boolean {
        return player.hasPermission("enthusiaexpress.mapart.manage") &&
            !stopping && player.isOnline && !player.isDead && player.hasPermission("enthusiaexpress.use") &&
            combatHook.mayUseMail(player)
    }

    private fun openMapartPage(player: Player, page: Int, processed: Boolean) {
        if (!managerAccess(player)) {
            player.sendMessage("§cMapart Manager access is unavailable. Check your permission and combat status.")
            return
        }
        if (page !in 0..1_000_000) return
        val inv = Bukkit.createInventory(null, 54, if (processed) "§6Mapart • Processed" else "§6Mapart • Intake")
        val session = Session(inv, MailType.PACKAGE, page, false, true, processed)
        sessions[player.uniqueId] = session
        player.openInventory(inv)
        requested[player.uniqueId] = player to session
        loadPendingPages()
    }

    /** Keep the current mailbox location visible even when no control is hovered. */
    private fun mailboxTitle(sent: Boolean): String {
        val base = theme.title("mailbox", "§6Enthusia Express")
        return base + " §8• §f" + if (sent) "Sent Mail" else "Inbox"
    }

    /** Create a bounded inbox session and request its rows asynchronously. */
    private fun openPage(player: Player, type: MailType, page: Int, sent: Boolean = false) {
        if (!allowed(player, sent)) {
            player.sendMessage(Text.msg(plugin.config, "mail-unavailable"))
            return
        }
        if (page < 0 || page > 1_000_000) return
        val current = sessions[player.uniqueId]
        val existing = current?.inventory?.takeIf {
            !current.mapart && current.sent == sent && player.openInventory.topInventory === it
        }
        val inv = existing ?: Bukkit.createInventory(null, 54, mailboxTitle(sent))
        if (existing != null) inv.clear()
        val session = Session(inv, type, page, sent)
        sessions[player.uniqueId] = session
        MailboxControls.render(inv, type, page, sent, theme)
        if (existing == null) player.openInventory(inv)
        requested[player.uniqueId] = player to session
        loadPendingPages()
    }

    /** Start at most one lookup per player; repeated navigation replaces the pending view. */
    fun loadPendingPages() {
        for ((id, request) in requested.toMap()) {
            if (id in loading) continue
            requested.remove(id)
            val (player, session) = request
            if (!active(player, session)) continue
            loading.add(id)
            loadPage(player, session)
        }
    }

    /** Dispatch a single bounded page; the scheduler starts the next desired view after completion. */
    private fun loadPage(player: Player, session: Session) {
        if (session.mapart) {
            main.complete(repository.listMapart(session.page, session.processed)) { rows, error ->
                loading.remove(player.uniqueId)
                renderMapart(player, session, rows, error)
            }
            return
        }
        val type = session.type
        val page = session.page
        if (session.sent) {
            main.complete(repository.listSent(player.uniqueId, type, page)) { records, error ->
                loading.remove(player.uniqueId)
                renderSent(player, session, records, error)
            }
            return
        }
        main.complete(repository.listInbox(player.uniqueId, type, page)) { records, error ->
            loading.remove(player.uniqueId)
            renderInbox(player, session, records, error)
        }
    }

    private fun renderMapart(player: Player, session: Session, rows: List<MapartSubmission>?, error: Throwable?) {
        if (!active(player, session)) return
        if (error != null) {
            player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
            return
        }
        val entries = checkNotNull(rows)
        for ((index, entry) in entries.withIndex()) {
            val slot = MailboxControls.CONTENT_START + index
            val icon = ItemStack(Material.FILLED_MAP)
            val meta = icon.itemMeta!!
            meta.setDisplayName("§e${entry.mapName.take(48)}")
            meta.lore = listOf("§7Submission #${entry.mail.id}", "§7Artist: §f${entry.mail.senderName}",
                "§7Map ID: §f${entry.mapId ?: "unknown"}",
                "§7Submitted: §f${dateFormat.format(Instant.ofEpochMilli(entry.submittedAt))}",
                if (entry.mail.status == MailStatus.CLAIMED) "§7Claimed by: §f${entry.mail.recipientName}" else "§7Shared manager queue",
                if (entry.processedBy != null) "§7Processed by: §f${entry.processedBy}" else "§7Not processed",
                if (session.processed) "§aProcessed" else if (entry.mail.status == MailStatus.UNCLAIMED) "§eClick to claim map"
                    else if (entry.deliveryPending) "§eDelivery acknowledgment pending" else "§aClick to mark processed")
            icon.itemMeta = meta
            session.inventory.setItem(slot, icon)
            session.records[slot] = entry.mail
        }
        session.loaded = true
        session.inventory.setItem(MailboxControls.PAGE_SLOT, icon(Material.PAPER, "§fPage ${session.page + 1}"))
        session.inventory.setItem(MailboxControls.MODE_SLOT, icon(Material.BOOK,
            if (session.processed) "§eOpen intake" else "§eView processed"))
        session.inventory.setItem(MailboxControls.REFRESH_SLOT, icon(Material.CLOCK, "§eRefresh"))
        session.inventory.setItem(MailboxControls.CLOSE_SLOT, icon(Material.BARRIER, "§cClose"))
        if (session.page > 0) session.inventory.setItem(MailboxControls.PREVIOUS_SLOT, icon(Material.ARROW, "§ePrevious"))
        if (entries.size == MAIL_PAGE_SIZE) session.inventory.setItem(MailboxControls.NEXT_SLOT, icon(Material.ARROW, "§eNext"))
        if (entries.isEmpty()) session.inventory.setItem(31, icon(Material.MAP, "§7No mapart submissions here"))
    }

    /** Populate only the still-active session after a database lookup completes. */
    private fun renderInbox(player: Player, session: Session, records: List<MailRecord>?, error: Throwable?) {
        val inv = session.inventory

        if (!active(player, session)) return
        if (error != null) {
            player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
            return
        }
        val inboxRecords = checkNotNull(records)
        var slot = MailboxControls.CONTENT_START
        for (record in inboxRecords) {
            val item = mailIcon(record)
            inv.setItem(slot, item)
            session.records[slot++] = record
        }
        session.loaded = true
        MailboxControls.renderLoaded(inv, MailboxControls.SummaryState(session.type, session.page, false,
            inboxRecords.size, inboxRecords.count { it.unread }, false), theme)
        if (inboxRecords.isEmpty()) MailboxControls.emptyState(inv, session.type, session.page, sent = false)
    }

    /** Render only sender-owned history in the still-current session. */
    private fun renderSent(player: Player, session: Session, records: List<io.enthusia.express.domain.SentMailRecord>?, error: Throwable?) {
        if (!active(player, session)) return
        if (error != null) {
            player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
            return
        }
        val entries = checkNotNull(records).filter { it.mail.sender == player.uniqueId }
        entries.forEachIndexed { index, entry ->
            val slot = MailboxControls.CONTENT_START + index
            session.inventory.setItem(slot, SentMailDisplay.icon(entry))
            session.records[slot] = entry.mail
        }
        session.loaded = true
        MailboxControls.renderLoaded(session.inventory, MailboxControls.SummaryState(session.type, session.page, true,
            entries.size, 0, false), theme)
        if (entries.isEmpty()) MailboxControls.emptyState(session.inventory, session.type, session.page, sent = true)
    }

    /** Create a readable mail card without decoding persisted payloads on the main thread. */
    @Suppress("TooGenericExceptionCaught")
    private fun mailIcon(record: MailRecord): ItemStack = try {
        val material = when (record.type) {
            MailType.PACKAGE -> Material.CHEST
            MailType.LETTER -> Material.WRITTEN_BOOK
            MailType.ANNOUNCEMENT -> Material.BELL
        }
        val item = theme.item("mailbox.entry." + record.type.name.lowercase(Locale.ROOT), material)
        val meta = item.itemMeta!!
        val label = when (record.type) {
            MailType.PACKAGE -> "Package from ${record.senderName}"
            MailType.LETTER -> "Letter from ${record.senderName}"
            MailType.ANNOUNCEMENT -> "Announcement from ${record.senderName}"
        }
        val prefix = if (record.unread) "§a● §r" else "§7"
        val color = when (record.type) {
            MailType.PACKAGE -> "§e"
            MailType.LETTER -> "§f"
            MailType.ANNOUNCEMENT -> "§6"
        }
        meta.setDisplayName(prefix + color + label)
        meta.lore = inboxLore(record)
        if (record.unread) meta.setEnchantmentGlintOverride(true)
        item.itemMeta = meta
        item
    } catch (e: RuntimeException) {
        val unreadable = icon(Material.BARRIER, "§cUnreadable mail #" + record.id)
        plugin.logger.warning("Unreadable mail #${record.id}: $e")
        unreadable
    }

    /** Describe sender, age, status and action while leaving persisted payloads untouched. */
    private fun inboxLore(record: MailRecord): List<String> {
        val status = when (record.type) {
            MailType.PACKAGE -> "§aReady to claim"
            else -> if (record.unread) "§aUnread" else "§7Read"
        }
        val action = if (record.type == MailType.PACKAGE) "§eLeft-click to claim" else "§eLeft-click to read"
        val lines = mutableListOf(
            "§8Mail #${record.id}",
            "",
            "§7From: §f${record.senderName}",
            "§7Received: §f${dateFormat.format(Instant.ofEpochMilli(record.createdAt))}",
            "§7Status: $status",
        )
        if (record.type == MailType.PACKAGE) lines.add("§7Contents: §f${record.packedItemCount} packed items")
        lines.add("")
        lines.add(action)
        if (record.type != MailType.PACKAGE && record.unread) lines.add("§eRight-click to mark as read")
        return lines
    }

    /** Check permissions, player state and exact inventory-session identity before asynchronous completion. */
    private fun active(player: Player, session: Session): Boolean =
        (if (session.mapart) managerAccess(player) else allowed(player, session.sent)) &&
        sessions[player.uniqueId] === session && player.openInventory.topInventory === session.inventory

    /** Identify the exact open inventory associated with this player session. */
    fun owns(player: Player): Boolean {
        val session = sessions[player.uniqueId]
        return session != null && player.openInventory.topInventory === session.inventory
    }

    /** Schedule a click for the next tick and reject stale sessions before dispatch. */
    @JvmOverloads
    fun deferClick(player: Player, slot: Int, markAsRead: Boolean = false) {
        val session = sessions[player.uniqueId]
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (session != null && active(player, session)) click(player, slot, markAsRead)
        })
    }

    /** Navigate backward only when a previous page exists. */
    private fun previousPage(player: Player, session: Session) {
        if (session.page > 0) openPage(player, session.type, session.page - 1, session.sent)
    }

    /** Request the next page only after a full current page has loaded. */
    private fun nextPage(player: Player, session: Session) {
        if (session.loaded && session.records.size == MAIL_PAGE_SIZE) openPage(player, session.type, session.page + 1, session.sent)
    }

    /** Handle navigation or reserve one in-flight lookup for a visible mail entry. */
    @JvmOverloads
    fun click(player: Player, slot: Int, markAsRead: Boolean = false) {
        val session = sessions[player.uniqueId]
        if (session == null || !active(player, session)) {
            player.closeInventory()
            return
        }
        if (!markAsRead && navigate(player, session, slot)) return
        val visible = session.records[slot] ?: return
        if (session.mapart) {
            if (session.processed || !claiming.add(player.uniqueId)) return
            main.complete(repository.getMapart(visible.id)) { entry, error ->
                completeMapartLookup(player, session, entry, error)
            }
            return
        }
        if (session.sent) {
            if (!markAsRead) readSent(player, session, visible)
            return
        }
        if (markAsRead) {
            markTextRead(player, session, visible)
            return
        }
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.get(visible.id)) { record, error ->
            completeLookup(player, session, record, error)
        }
    }

    /** Mark only a currently visible unread letter or announcement owned by this inbox recipient. */
    private fun markTextRead(player: Player, session: Session, visible: MailRecord) {
        if (!session.loaded || visible.type == MailType.PACKAGE || !visible.unread) return
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.get(visible.id)) { record, error ->
            completeReadLookup(player, session, visible, record, error)
        }
    }

    private fun completeReadLookup(
        player: Player, session: Session, visible: MailRecord, record: MailRecord?, error: Throwable?,
    ) {
        if (error != null) {
            claiming.remove(player.uniqueId)
            if (active(player, session)) player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
            return
        }
        if (!eligibleTextRead(player, session, visible, record)) {
            claiming.remove(player.uniqueId)
            return
        }
        main.complete(repository.markRead(requireNotNull(record).id, player.uniqueId)) { marked, failure ->
            completeReadWrite(player, session, marked, failure)
        }
    }

    private fun eligibleTextRead(player: Player, session: Session, visible: MailRecord, record: MailRecord?): Boolean {
        if (record == null || !validRecord(player, session, record)) return false
        return record.type == visible.type && record.unread && record.status == MailStatus.UNCLAIMED
    }

    private fun completeReadWrite(player: Player, session: Session, marked: Boolean?, failure: Throwable?) {
        claiming.remove(player.uniqueId)
        if (!active(player, session)) return
        if (failure != null) player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
        else if (marked == true) openPage(player, session.type, session.page)
    }

    /** Clear this recipient's unread letters and announcements with one storage update. */
    private fun markAllTextRead(player: Player, session: Session) {
        if (!session.loaded || session.sent || session.type == MailType.PACKAGE) return
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.markAllTextRead(player.uniqueId)) { changed, failure ->
            completeBulkRead(player, session, changed, failure)
        }
    }

    /** Release the operation guard and refresh only the same authorized inbox session. */
    private fun completeBulkRead(player: Player, session: Session, changed: Int?, failure: Throwable?) {
        claiming.remove(player.uniqueId)
        if (!active(player, session)) return
        if (failure != null) player.sendMessage(Text.msg(plugin.config, DATABASE_ERROR))
        else {
            val count = changed ?: 0
            player.sendMessage("§aMarked $count text ${if (count == 1) "message" else "messages"} as read.")
            openPage(player, session.type, session.page)
        }
    }

    private fun completeMapartLookup(player: Player, session: Session, entry: MapartSubmission?, error: Throwable?) {
        val lookupFailed = error != null || entry == null
        val entryUnavailable = entry != null && (!active(player, session) || entry.processedAt != null)
        if (lookupFailed || entryUnavailable) {
            claiming.remove(player.uniqueId)
            return
        }
        if (entry.mail.status == MailStatus.CLAIMED && !entry.deliveryPending) {
            main.complete(repository.markMapartProcessed(entry.mail.id, player.uniqueId)) { changed, failure ->
                claiming.remove(player.uniqueId)
                if (active(player, session)) {
                    if (failure == null && changed == true) openMapartPage(player, session.page, false)
                    else player.sendMessage("§cCannot mark that map processed; check delivery status.")
                }
            }
            return
        }
        if (entry.mail.status != MailStatus.UNCLAIMED || entry.mail.recipient != MapartQueue.ID) {
            claiming.remove(player.uniqueId)
            return
        }
        completeLookup(player, session, entry.mail, null)
    }

    /** Load only the selected sent book, keeping page queries free of payloads. */
    private fun readSent(player: Player, session: Session, visible: MailRecord) {
        if (visible.sender != player.uniqueId || visible.type == MailType.PACKAGE) return
        if (!claiming.add(player.uniqueId)) return
        main.complete(repository.get(visible.id)) { record, error ->
            claiming.remove(player.uniqueId)
            if (error == null && record != null && active(player, session)) showSentBook(player, record)
        }
    }

    /** Recheck sender ownership and the byte budget before decoding a retained sent book. */
    private fun showSentBook(player: Player, record: MailRecord) {
        if (record.sender != player.uniqueId) return
        if (record.payload.size > plugin.config.getInt("letters.max-payload-bytes", 262144)) {
            player.sendMessage("§cThis legacy book exceeds the safe item-data limit. Contact an administrator.")
            return
        }
        SentMailDisplay.readBook(player, record)
    }

    /** Handle category and page buttons, returning whether the slot was a navigation control. */
    private fun navigate(player: Player, session: Session, slot: Int): Boolean {
        if (session.mapart) {
            when (slot) {
                MailboxControls.MODE_SLOT -> openMapartPage(player, 0, !session.processed)
                MailboxControls.PREVIOUS_SLOT -> if (session.page > 0) openMapartPage(player, session.page - 1, session.processed)
                MailboxControls.NEXT_SLOT -> if (session.loaded && session.records.size == MAIL_PAGE_SIZE)
                    openMapartPage(player, session.page + 1, session.processed)
                MailboxControls.REFRESH_SLOT -> openMapartPage(player, session.page, session.processed)
                MailboxControls.CLOSE_SLOT -> player.closeInventory()
                else -> return false
            }
            return true
        }
        when (slot) {
            1 -> { openPage(player, MailType.PACKAGE, 0, session.sent) }
            4 -> { openPage(player, MailType.LETTER, 0, session.sent) }
            7 -> { openPage(player, MailType.ANNOUNCEMENT, 0, session.sent) }
            MailboxControls.MODE_SLOT -> { openPage(player, session.type, 0, !session.sent) }
            MailboxControls.PREVIOUS_SLOT -> { previousPage(player, session) }
            MailboxControls.NEXT_SLOT -> { nextPage(player, session) }
            MailboxControls.MARK_ALL_READ_SLOT -> { markAllTextRead(player, session) }
            MailboxControls.REFRESH_SLOT -> { openPage(player, session.type, session.page, session.sent) }
            MailboxControls.CLOSE_SLOT -> { player.closeInventory() }
            else -> return false
        }
        return true
    }

    /** Revalidate session ownership and eligible row state after the asynchronous lookup. */
    private fun validRecord(player: Player, session: Session, record: MailRecord) =
        active(player, session) && record.recipient == (if (session.mapart) MapartQueue.ID else player.uniqueId) &&
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
        val byteLimit = plugin.config.getInt(if (record.type == MailType.PACKAGE)
            "mail.max-package-payload-bytes" else "letters.max-payload-bytes", 262144)
        if (record.payload.size > byteLimit) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cThis legacy mail exceeds the safe item-data limit. Contact an administrator; its contents are retained.")
            return
        }
        try {
            item = ItemCodec.decode(record.payload)
        } catch (e: RuntimeException) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cThis mail cannot be decoded; contact an administrator.")
            return
        }
        if (record.type == MailType.PACKAGE) claimPackage(player, record, item, session.mapart) else {
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

    /** Require inventory capacity, claim permission and the shared asset lease before reserving the package. */
    private fun claimPackage(player: Player, record: MailRecord, stack: ItemStack, mapart: Boolean) {
        if (!(if (mapart) managerAccess(player) else player.hasPermission("enthusiaexpress.packages.claim")) ||
            player.inventory.firstEmpty() == -1) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§cYou need claim permission and an empty inventory slot.")
            return
        }
        val lease = movementLocks.acquire(player.uniqueId)
        if (lease == null) {
            claiming.remove(player.uniqueId)
            player.sendMessage("§eYour inventory is being used by another server operation. Try again shortly.")
            return
        }
        var submitted = false
        try {
            val bound = if (mapart) record.copy(recipient = player.uniqueId, recipientName = player.name) else record
            val delivery = ClaimDelivery(player, bound, stack, lease, mapart)
            val reservation = if (mapart) repository.claimMapart(record, player.uniqueId, player.name)
                else repository.claim(record)
            main.complete(reservation) { claimed, error -> completeClaim(delivery, claimed, error) }
            submitted = true
        } finally {
            if (!submitted) {
                lease.close()
                claiming.remove(player.uniqueId)
            }
        }
    }

    private data class ClaimDelivery(val player: Player, val record: MailRecord,
                                     val stack: ItemStack, val lease: MovementLease, val mapart: Boolean)

    /** Recheck lease ownership and delivery eligibility before exposing claimed cargo to the player. */
    private fun completeClaim(delivery: ClaimDelivery, claimed: Boolean?, error: Throwable?) {
        val (player, record, _, lease) = delivery
        if (error != null || claimed != true) {
            lease.close()
            claiming.remove(player.uniqueId)
            player.sendMessage("§cThat package could not be claimed.")
            return
        }
        if (!lease.ensureOwned() || !eligibleForDelivery(player, delivery.mapart)) {
            lease.close()
            restoreUndelivered(player, record)
            return
        }
        if (!deliverInventory(delivery)) return
        // Once released, a Staff snapshot necessarily sees the delivered package in inventory.
        lease.close()
        acknowledgeDelivery(player, record)
        claiming.remove(player.uniqueId)
        sounds.play(player, SoundFeedback.Cue.PACKAGE_CLAIM)
        player.sendMessage("§aPackage claimed.")
        if (owns(player)) {
            val session = sessions[player.uniqueId]
            if (session?.mapart == true) openMapart(player) else open(player, MailType.PACKAGE)
        }
    }

    /** Check player state separately from operation-owned movement locking. */
    private fun eligibleForDelivery(player: Player, mapart: Boolean): Boolean =
        (if (mapart) managerAccess(player) else allowed(player) && player.hasPermission("enthusiaexpress.packages.claim")) &&
        player.inventory.firstEmpty() != -1

    /** Bukkit inventory implementations can fail after partial mutation; rollback covers all runtime failures. */
    @Suppress("TooGenericExceptionCaught")
    private fun deliverInventory(delivery: ClaimDelivery): Boolean {
        val (player, record, stack, lease) = delivery
        val before = snapshotInventory(player, record, lease) ?: return false
        val delivered = try {
            player.inventory.addItem(stack).isEmpty().also { if (it) player.saveData() }
        } catch (error: RuntimeException) {
            recoverFailedInventoryDelivery(player, record, lease, before, error)
            return false
        }
        if (!delivered) recoverFailedInventoryDelivery(player, record, lease, before,
            IllegalStateException("Claimed package did not fit after an empty-slot recheck"))
        return delivered
    }

    /** Capture rollback state before the only player-inventory mutation in package delivery. */
    @Suppress("TooGenericExceptionCaught")
    private fun snapshotInventory(player: Player, record: MailRecord, lease: MovementLease): Array<ItemStack?>? = try {
        player.inventory.storageContents.map { it?.clone() }.toTypedArray()
    } catch (error: RuntimeException) {
        lease.close()
        plugin.logger.log(Level.SEVERE, "Cannot snapshot inventory before package #${record.id} delivery; restoring claim", error)
        restoreUndelivered(player, record)
        null
    }

    /** Roll back a failed delivery before making the database row claimable again. */
    @Suppress("TooGenericExceptionCaught")
    private fun recoverFailedInventoryDelivery(player: Player, record: MailRecord, lease: MovementLease,
                                               before: Array<ItemStack?>, deliveryError: RuntimeException) {
        val rolledBack = try {
            player.inventory.storageContents = before
            player.saveData()
            true
        } catch (rollbackError: RuntimeException) {
            deliveryError.addSuppressed(rollbackError)
            false
        } finally {
            lease.close()
        }
        if (rolledBack) {
            plugin.logger.log(Level.WARNING, "Package #${record.id} inventory delivery failed and was rolled back", deliveryError)
            player.sendMessage("§ePackage delivery was interrupted. Its claim was restored; try again.")
            restoreUndelivered(player, record)
        } else {
            claiming.remove(player.uniqueId)
            plugin.logger.log(Level.SEVERE,
                "Package #${record.id} delivery and inventory rollback both failed; claim remains held for administrator review", deliveryError)
            player.sendMessage("§cPackage delivery is in an uncertain state. Do not retry; contact an administrator.")
        }
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
        val restoration = acknowledgments?.restore(record) ?: repository.restoreClaim(record)
        main.complete(restoration) { restored, failure ->
            claiming.remove(player.uniqueId)
            if (failure != null || restored != true) plugin.logger.severe("Could not restore undelivered claim #${record.id}: $failure")
        }
    }

    /** Discard only the session belonging to the inventory being closed. */
    fun close(player: Player, inventory: Inventory) {
        val session = sessions[player.uniqueId]
        if (session != null && session.inventory === inventory) {
            sessions.remove(player.uniqueId)
            requested.remove(player.uniqueId)
        }
    }

    /** Reject new mailbox access and close owned menus before pending completions drain. */
    fun shutdown() {
        stopping = true
        for (player in Bukkit.getOnlinePlayers()) if (owns(player)) player.closeInventory()
        sessions.clear()
        requested.clear()
    }

    companion object {
        const val TITLE_PREFIX = "Mailbox"
        private const val DATABASE_ERROR = "database-error"
        private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm 'UTC'", Locale.US).withZone(ZoneOffset.UTC)

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
