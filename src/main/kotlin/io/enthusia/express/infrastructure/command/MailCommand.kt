// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.command

import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.gui.MailboxService
import io.enthusia.express.infrastructure.gui.ShippingService
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.mail.BookMailService
import io.enthusia.express.infrastructure.util.Text
import io.enthusia.express.infrastructure.util.MainThread
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

private const val INBOX = "inbox"
private const val NO_PERMISSION = "no-permission"
private const val UNKNOWN_RECIPIENT = "target-never-joined"

private enum class SendAction(val key: String, val permission: String) {
    PACKAGE("send", "enthusiaexpress.packages.send"),
    LETTER("letter", "enthusiaexpress.letters.send"),
    ANNOUNCE("announce", "enthusiaexpress.admin.announce")
}

class MailCommand(
    private val plugin: JavaPlugin,
    private val shipping: ShippingService,
    private val mailbox: MailboxService,
    private val combatHook: CombatLogXHook,
    private val books: BookMailService,
    private val main: MainThread,
) : CommandExecutor, TabCompleter {
    val recipientNames = RecipientNames()
    private val resolving = HashSet<UUID>()
    /** Validate the player command context and dispatch one supported mail action. */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) sender.sendMessage("Players only.")
        else if (validateAccess(sender)) dispatch(sender, args)
        return true
    }

    /** Reject non-player or unauthorized use before any mail UI or persistence operation. */
    private fun validateAccess(sender: Player): Boolean {
        val rejection = when {
            !sender.hasPermission("enthusiaexpress.use") -> NO_PERMISSION
            combatHook.mayUseMail(sender) -> null
            combatHook.isAvailable() -> "combat-blocked"
            else -> "combatlogx-missing"
        }
        if (rejection != null) sender.sendMessage(Text.msg(plugin.config, rejection))
        return rejection == null
    }

    /** Route an authorized command to inbox, package, letter or announcement handling. */
    private fun dispatch(sender: Player, args: Array<String>) {
        val subcommand = args.firstOrNull()?.lowercase(Locale.ROOT) ?: INBOX
        if (subcommand == INBOX) mailbox.open(sender, inboxType(args.getOrNull(1)))
        else {
            val action = SendAction.entries.firstOrNull { it.key == subcommand }
            if (action == null) sender.sendMessage("§e/mail send <OfflinePlayer> §7or §e/mail inbox [packages|letters|announcements]")
            else send(sender, action, args)
        }
    }

    /** Map an inbox argument to a supported mail category. */
    private fun inboxType(category: String?): MailType = when (category?.lowercase(Locale.ROOT)) {
        "letters", SendAction.LETTER.key -> MailType.LETTER
        "announcements", "announcement", "admin" -> MailType.ANNOUNCEMENT
        else -> MailType.PACKAGE
    }

    /** Validate send arguments and resolve broadcast or recipient delivery. */
    private fun send(sender: Player, action: SendAction, args: Array<String>) {
        if (!sender.hasPermission(action.permission)) sender.sendMessage(Text.msg(plugin.config, NO_PERMISSION))
        else if (args.size != 2) sender.sendMessage("§eUsage: /mail <send|letter|announce> <player> (announce also accepts all)")
        else if (action == SendAction.ANNOUNCE && args[1].equals("all", true)) books.send(sender, null, true, true)
        else sendToRecipient(sender, action, args[1])
    }

    /** Use the fast cache path or defer a potentially blocking UUID lookup. */
    private fun sendToRecipient(sender: Player, action: SendAction, name: String) {
        val target = Bukkit.getOfflinePlayerIfCached(name)
        if (target != null) deliverToRecipient(sender, action, target)
        else resolveRecipient(sender, action, name)
    }

    /** Resolve through Paper's profile source off-thread, then recheck the same sender session. */
    private fun resolveRecipient(sender: Player, action: SendAction, name: String) {
        if (!resolving.add(sender.uniqueId)) return
        val lookup = CompletableFuture.supplyAsync({ Bukkit.getPlayerUniqueId(name) },
            { task -> plugin.server.scheduler.runTaskAsynchronously(plugin, task) })
            .orTimeout(30, TimeUnit.SECONDS)
        main.complete(lookup) { id, error ->
            resolving.remove(sender.uniqueId)
            if (error != null) plugin.logger.log(Level.WARNING, "Mail recipient lookup failed", error)
            val sameSession = plugin.isEnabled && sender.isOnline && Bukkit.getPlayer(sender.uniqueId) === sender
            if (sameSession && validateAccess(sender) && sender.hasPermission(action.permission)) {
                deliverToRecipient(sender, action, id?.let { Bukkit.getOfflinePlayer(it) })
            }
        }
    }

    /** Reject unknown and self recipients before delegating to the server-thread mail service. */
    private fun deliverToRecipient(sender: Player, action: SendAction, target: OfflinePlayer?) {
        when {
            target == null -> sender.sendMessage(Text.msg(plugin.config, UNKNOWN_RECIPIENT))
            !target.hasPlayedBefore() && !target.isOnline -> sender.sendMessage(Text.msg(plugin.config, UNKNOWN_RECIPIENT))
            action != SendAction.ANNOUNCE && target.uniqueId == sender.uniqueId -> sender.sendMessage("§cYou cannot mail yourself.")
            action == SendAction.PACKAGE -> shipping.open(sender, target)
            else -> books.send(sender, target, action == SendAction.ANNOUNCE, false)
        }
    }

    /** Suggest permitted commands and cached names without enumerating offline player files. */
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> =
        when (args.size) {
            1 -> (SendAction.entries.filter { sender.hasPermission(it.permission) }.map { it.key } + INBOX)
                .filter { it.startsWith(args[0].lowercase(Locale.ROOT)) }
            2 -> argumentSuggestions(sender, args)
            else -> emptyList()
        }

    /** Return matching inbox categories or cached recipient names, capped at twenty results. */
    private fun argumentSuggestions(sender: CommandSender, args: Array<String>): List<String> {
        if (args[0].equals(INBOX, true)) return listOf("packages", "letters", "announcements")
        val action = SendAction.entries.firstOrNull { it.key.equals(args[0], true) } ?: return emptyList()
        if (!sender.hasPermission(action.permission)) return emptyList()
        val partial = args[1].lowercase(Locale.ROOT)
        val broadcast = if (action == SendAction.ANNOUNCE && "all".startsWith(partial)) listOf("all") else emptyList()
        return (broadcast + recipientNames.matching(partial)).take(20)
    }
}
