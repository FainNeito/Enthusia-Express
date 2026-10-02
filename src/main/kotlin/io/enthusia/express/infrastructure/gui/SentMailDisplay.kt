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

    /** Show destination, type, date, retained content and current delivery state as a readable card. */
    fun icon(entry: SentMailRecord): ItemStack {
        val mail = entry.mail
        val item = ItemStack(when (mail.type) {
            MailType.PACKAGE -> Material.CHEST
            MailType.LETTER -> Material.WRITTEN_BOOK
            MailType.ANNOUNCEMENT -> Material.BELL
        })
        val meta = item.itemMeta!!
        val recipient = entry.recipientName ?: "Unknown (legacy return)"
        val typeName = mail.type.name.lowercase().replaceFirstChar { it.uppercase() }
        val nameColor = when (mail.type) {
            MailType.PACKAGE -> "§e"
            MailType.LETTER -> "§f"
            MailType.ANNOUNCEMENT -> "§6"
        }
        meta.setDisplayName("$nameColor$typeName to §f$recipient")
        meta.lore = buildList {
            add("§8Mail #${mail.id}")
            add("")
            add("§7Sent: §f${dateFormat.format(Instant.ofEpochMilli(mail.createdAt))}")
            add("§7Status: §f${status(entry)}")
            add(contentHint(mail))
            if (mail.type != MailType.PACKAGE && mail.status != MailStatus.PURGED) {
                add("")
                add("§eLeft-click to read sent copy")
            }
        }
        item.itemMeta = meta
        return item
    }

    /** Page snapshots omit payloads; only the persisted purge status proves content expiration. */
    private fun contentHint(mail: MailRecord): String = when {
        mail.status == MailStatus.PURGED -> "§7Contents: §8Expired"
        mail.type == MailType.PACKAGE -> "§7Contents: §f${mail.packedItemCount} packed items"
        else -> "§7Contents: §fSent book copy retained"
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
