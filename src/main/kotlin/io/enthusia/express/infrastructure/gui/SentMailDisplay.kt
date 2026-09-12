// Private display contracts document read-only handling consistently with the reviewed services.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.SentMailRecord
import io.enthusia.express.infrastructure.util.ItemCodec
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Sender history is informational; viewing it never claims mail or changes recipient unread state. */
object SentMailDisplay {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)

    /** Show the original destination, content summary, date and current delivery state. */
    fun icon(entry: SentMailRecord): ItemStack {
        val mail = entry.mail
        val item = if (mail.type == MailType.PACKAGE) packagePreview(mail) else ItemStack(Material.WRITTEN_BOOK)
        val meta = item.itemMeta!!
        meta.setDisplayName("§eTo: ${entry.recipientName ?: "Unknown (legacy return)"}")
        meta.lore = listOf("§7${mail.type.name.lowercase().replaceFirstChar { it.uppercase() }} #${mail.id}",
            "§7Sent: ${dateFormat.format(Instant.ofEpochMilli(mail.createdAt))}", "§f${status(entry)}",
            contentHint(mail))
        item.itemMeta = meta
        return item
    }

    /** Keep a package's native container tooltip when its retained payload can be decoded. */
    @Suppress("TooGenericExceptionCaught")
    private fun packagePreview(mail: MailRecord): ItemStack = try {
        if (mail.payload.isEmpty()) ItemStack(Material.CHEST) else ItemCodec.decode(mail.payload) ?: ItemStack(Material.CHEST)
    } catch (error: RuntimeException) {
        ItemStack(Material.CHEST)
    }

    /** Describe retained content without suggesting collection from history. */
    private fun contentHint(mail: MailRecord): String = when {
        mail.payload.isEmpty() -> "§7Contents expired"
        mail.type == MailType.PACKAGE -> "§7${mail.packedItemCount} packed items"
        else -> "§aClick to read sent copy"
    }

    /** Distinguish pending delivery reservations from completed collection and text read state. */
    private fun status(entry: SentMailRecord): String = when (entry.mail.status) {
        MailStatus.PURGED -> "Expired"
        MailStatus.RETURNED -> "Returned to sender"
        MailStatus.RETURN_CLAIMED -> if (entry.deliveryPending) "Return delivery pending" else "Return collected"
        MailStatus.CLAIMED -> if (entry.deliveryPending) "Delivery pending" else "Collected"
        MailStatus.UNCLAIMED -> unreadStatus(entry.mail)
    }

    /** Show pickup state for packages and recipient read state for text mail. */
    private fun unreadStatus(mail: MailRecord): String = if (mail.type == MailType.PACKAGE) "Awaiting pickup"
        else if (mail.unread) "Unread" else "Read"

    /** Open a retained sent book without marking the recipient's copy read. */
    // Paper may reject corrupt or outdated persisted book metadata.
    @Suppress("TooGenericExceptionCaught")
    fun readBook(player: Player, mail: MailRecord) {
        if (mail.type == MailType.PACKAGE) return
        if (mail.payload.isEmpty()) {
            player.sendMessage("§7This mail's contents have expired.")
            return
        }
        try {
            val book = ItemCodec.decode(mail.payload)
            player.closeInventory()
            player.openBook(book)
        } catch (error: RuntimeException) {
            player.sendMessage("§cThis sent copy cannot be opened.")
        }
    }
}
