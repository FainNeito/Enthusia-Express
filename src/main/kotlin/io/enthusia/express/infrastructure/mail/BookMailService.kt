package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import io.enthusia.express.infrastructure.util.Text
import java.util.UUID
import java.util.concurrent.CompletableFuture
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.plugin.java.JavaPlugin

/** Sends an immutable copy of the signed book held in the main hand. */
class BookMailService @JvmOverloads constructor(
    private val plugin: JavaPlugin,
    private val repository: MailStore,
    private val combat: CombatLogXHook,
    private val main: MainThread,
    private val sounds: SoundFeedback = SoundFeedback(plugin),
) {
    private val pending = HashSet<UUID>()
    private val lastSent = HashMap<UUID, Long>()

    /** Validate and snapshot a signed book, then report the asynchronous send result once. */
    fun send(player: Player, target: OfflinePlayer?, announcement: Boolean, broadcast: Boolean) {
        if (!validateSend(player, target, announcement)) return
        val payload = prepareBook(player) ?: return
        val result = submit(player, target, announcement, broadcast, payload) ?: return
        pending.add(player.uniqueId)
        main.complete(result) { count, error ->
            pending.remove(player.uniqueId)
            if (error != null) {
                plugin.logger.severe("Book delivery failed: $error")
                player.sendMessage(Text.msg(plugin.config, "database-error"))
            } else if (count == 0 && !announcement) {
                player.sendMessage(Text.msg(plugin.config, "outstanding-letter"))
            } else {
                if (!announcement) sounds.play(player, SoundFeedback.Cue.LETTER_SEND)
                lastSent[player.uniqueId] = System.currentTimeMillis()
                player.sendMessage(Text.msg(plugin.config, "book-sent", mapOf("count" to count!!.toString())))
            }
        }
    }

    /** Reject absent, online or self recipients for ordinary offline letters. */
    private fun invalidLetterRecipient(player: Player, target: OfflinePlayer?) =
        target == null || target.isOnline || target.uniqueId == player.uniqueId

    /** Check send permissions, combat protection, feature enablement and letter recipient policy. */
    private fun validateSend(player: Player, target: OfflinePlayer?, announcement: Boolean): Boolean {
        val permission = if (announcement) "enthusiaexpress.admin.announce" else "enthusiaexpress.letters.send"
        if (!player.hasPermission("enthusiaexpress.use") || !player.hasPermission(permission)) {
            player.sendMessage(Text.msg(plugin.config, "no-permission"))
            return false
        }
        if (!combat.mayUseMail(player)) {
            player.sendMessage(Text.msg(plugin.config, "combat-blocked"))
            return false
        }
        val section = if (announcement) "announcements" else "letters"
        if (!plugin.config.getBoolean("$section.enabled", true)) {
            player.sendMessage(Text.msg(plugin.config, "feature-disabled"))
            return false
        }
        if (!announcement && invalidLetterRecipient(player, target)) {
            player.sendMessage(Text.msg(plugin.config, "target-online"))
            return false
        }
        return readyToSend(player, section)
    }

    /** Reject overlapping saves and sends within the configured cooldown. */
    private fun readyToSend(player: Player, section: String): Boolean {
        if (pending.contains(player.uniqueId)) {
            player.sendMessage("\u00a7eYour previous message is still being saved.")
            return false
        }
        val now = System.currentTimeMillis()
        val cooldown = plugin.config.getLong("$section.cooldown-seconds", 10) * 1000L
        if (now - lastSent.getOrDefault(player.uniqueId, 0L) < cooldown) {
            player.sendMessage(Text.msg(plugin.config, "book-cooldown"))
            return false
        }
        return true
    }

    /** Copy and serialize a signed book after validating page and payload bounds. */
    private fun prepareBook(player: Player): ByteArray? {
        val book = player.inventory.itemInMainHand.clone()
        val meta = book.itemMeta
        if (book.type != Material.WRITTEN_BOOK || meta !is BookMeta || !meta.hasPages()) {
            player.sendMessage(Text.msg(plugin.config, "hold-signed-book"))
            return null
        }
        if (meta.pageCount > plugin.config.getInt("letters.max-pages", 50)) {
            player.sendMessage(Text.msg(plugin.config, "book-too-large"))
            return null
        }
        book.amount = 1
        val payload = ItemCodec.encode(book)
        if (payload.size > plugin.config.getInt("letters.max-payload-bytes", 262144)) {
            player.sendMessage(Text.msg(plugin.config, "book-too-large"))
            return null
        }
        return payload
    }

    /** Choose one-recipient or broadcast persistence while applying the configured letter allowance. */
    private fun submit(player: Player, target: OfflinePlayer?, announcement: Boolean, broadcast: Boolean,
                       payload: ByteArray): CompletableFuture<Int>? {
        if (broadcast) return announceAll(player, payload)
        if (target == null) return null
        val targetName = target.name ?: target.uniqueId.toString()
        return if (announcement) repository.insertMail(
            player.uniqueId, player.name, target.uniqueId, targetName,
            MailType.ANNOUNCEMENT, payload, 0, false,
        ).thenApply { 1 } else repository.insertMailLimited(
            player.uniqueId, player.name, target.uniqueId, targetName,
            MailType.LETTER, payload, 0, plugin.config.getBoolean("mail.limits.one-outstanding-letter-per-recipient", false),
        ).thenApply { if (it.isPresent) 1 else 0 }
    }

    /** Collect known recipients for an explicit administrative broadcast and persist it atomically. */
    private fun announceAll(player: Player, payload: ByteArray): CompletableFuture<Int> {
        val recipients = HashMap<UUID, String>()
        for (recipient in Bukkit.getOfflinePlayers()) {
            val name = recipient.name
            if (name != null) recipients[recipient.uniqueId] = name
        }
        for (recipient in Bukkit.getOnlinePlayers()) recipients[recipient.uniqueId] = recipient.name
        return repository.announce(player.uniqueId, player.name, recipients, payload)
    }
}
