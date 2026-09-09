package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.Text
import io.enthusia.express.infrastructure.util.SoundFeedback
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

    fun send(player: Player, target: OfflinePlayer?, announcement: Boolean, broadcast: Boolean) {
        val permission = if (announcement) "enthusiaexpress.admin.announce" else "enthusiaexpress.letters.send"
        if (!player.hasPermission("enthusiaexpress.use") || !player.hasPermission(permission)) {
            player.sendMessage(Text.msg(plugin.config, "no-permission"))
            return
        }
        if (!combat.mayUseMail(player)) {
            player.sendMessage(Text.msg(plugin.config, "combat-blocked"))
            return
        }
        val section = if (announcement) "announcements" else "letters"
        if (!plugin.config.getBoolean("$section.enabled", true)) {
            player.sendMessage(Text.msg(plugin.config, "feature-disabled"))
            return
        }
        if (!announcement && (target == null || target.isOnline || target.uniqueId == player.uniqueId)) {
            player.sendMessage(Text.msg(plugin.config, "target-online"))
            return
        }
        if (pending.contains(player.uniqueId)) {
            player.sendMessage("\u00a7eYour previous message is still being saved.")
            return
        }
        val now = System.currentTimeMillis()
        val cooldown = plugin.config.getLong("$section.cooldown-seconds", 10) * 1000L
        if (now - lastSent.getOrDefault(player.uniqueId, 0L) < cooldown) {
            player.sendMessage(Text.msg(plugin.config, "book-cooldown"))
            return
        }
        val book = player.inventory.itemInMainHand.clone()
        val meta = book.itemMeta
        if (book.type != Material.WRITTEN_BOOK || meta !is BookMeta || !meta.hasPages()) {
            player.sendMessage(Text.msg(plugin.config, "hold-signed-book"))
            return
        }
        if (meta.pageCount > plugin.config.getInt("letters.max-pages", 50)) {
            player.sendMessage(Text.msg(plugin.config, "book-too-large"))
            return
        }
        book.amount = 1
        val payload = ItemCodec.encode(book)
        if (payload.size > plugin.config.getInt("letters.max-payload-bytes", 262144)) {
            player.sendMessage(Text.msg(plugin.config, "book-too-large"))
            return
        }
        val result: CompletableFuture<Int>
        if (broadcast) {
            val recipients = HashMap<UUID, String>()
            for (recipient in Bukkit.getOfflinePlayers()) {
                val name = recipient.name
                if (name != null) recipients[recipient.uniqueId] = name
            }
            for (recipient in Bukkit.getOnlinePlayers()) recipients[recipient.uniqueId] = recipient.name
            result = repository.announce(player.uniqueId, player.name, recipients, payload)
        } else {
            if (target == null) return
            result = if (announcement) repository.insertMail(
                player.uniqueId, player.name, target.uniqueId, target.name ?: target.uniqueId.toString(),
                if (announcement) MailType.ANNOUNCEMENT else MailType.LETTER, payload, 0, false,
            ).thenApply { 1 } else repository.insertMailLimited(
                player.uniqueId, player.name, target.uniqueId, target.name ?: target.uniqueId.toString(),
                MailType.LETTER, payload, 0, plugin.config.getBoolean("mail.limits.one-outstanding-letter-per-recipient", false)
            ).thenApply { if (it.isPresent) 1 else 0 }
        }
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
}
