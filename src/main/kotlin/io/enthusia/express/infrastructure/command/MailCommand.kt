package io.enthusia.express.infrastructure.command

import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.gui.MailboxService
import io.enthusia.express.infrastructure.gui.ShippingService
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.mail.BookMailService
import io.enthusia.express.infrastructure.util.Text
import java.util.Locale
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class MailCommand(
    private val plugin: JavaPlugin,
    private val shipping: ShippingService,
    private val mailbox: MailboxService,
    private val combatHook: CombatLogXHook,
    private val books: BookMailService,
) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Players only.")
            return true
        }
        if (!sender.hasPermission("enthusiaexpress.use")) {
            sender.sendMessage(Text.msg(plugin.config, "no-permission"))
            return true
        }
        if (!combatHook.mayUseMail(sender)) {
            sender.sendMessage(Text.msg(plugin.config, if (combatHook.isAvailable()) "combat-blocked" else "combatlogx-missing"))
            return true
        }
        if (args.isEmpty() || args[0].equals("inbox", true)) {
            val type = if (args.size >= 2) when (args[1].lowercase(Locale.ROOT)) {
                "letters", "letter" -> MailType.LETTER
                "announcements", "announcement", "admin" -> MailType.ANNOUNCEMENT
                else -> MailType.PACKAGE
            } else MailType.PACKAGE
            mailbox.open(sender, type)
            return true
        }
        if (args[0].equals("announce", true) && args.size == 2 && args[1].equals("all", true)) {
            books.send(sender, null, true, true)
            return true
        }
        if (args[0].lowercase(Locale.ROOT) in listOf("send", "letter", "announce")) {
            val permission = when (args[0].lowercase(Locale.ROOT)) {
                "letter" -> "enthusiaexpress.letters.send"
                "announce" -> "enthusiaexpress.admin.announce"
                else -> "enthusiaexpress.packages.send"
            }
            if (!sender.hasPermission(permission)) {
                sender.sendMessage(Text.msg(plugin.config, "no-permission"))
                return true
            }
            if (args.size != 2) {
                sender.sendMessage("\u00a7eUsage: /mail <send|letter|announce> <player> (announce also accepts all)")
                return true
            }
            val target = Bukkit.getOfflinePlayerIfCached(args[1])
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline)) {
                sender.sendMessage(Text.msg(plugin.config, "target-never-joined"))
                return true
            }
            if (!args[0].equals("announce", true) && target.uniqueId == sender.uniqueId) {
                sender.sendMessage("\u00a7cYou cannot mail yourself.")
                return true
            }
            if (args[0].equals("send", true)) shipping.open(sender, target)
            else books.send(sender, target, args[0].equals("announce", true), false)
            return true
        }
        sender.sendMessage("\u00a7e/mail send <OfflinePlayer> \u00a77or \u00a7e/mail inbox [packages|letters|announcements]")
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (args.size == 1) return listOf("send", "inbox", "letter", "announce")
            .filter { it != "announce" || sender.hasPermission("enthusiaexpress.admin.announce") }
            .filter { it.startsWith(args[0].lowercase(Locale.ROOT)) }
        if (args.size == 2 && args[0].equals("inbox", true)) return listOf("packages", "letters", "announcements")
        if (args.size == 2 && args[0].lowercase(Locale.ROOT) in listOf("send", "letter", "announce")) {
            if (args[0].equals("announce", true) && !sender.hasPermission("enthusiaexpress.admin.announce")) return emptyList()
            val partial = args[1].lowercase(Locale.ROOT)
            val names = ArrayList<String>()
            if (args[0].equals("announce", true) && "all".startsWith(partial)) names.add("all")
            for (player in Bukkit.getOfflinePlayers()) {
                if (!args[0].equals("announce", true) && player.isOnline) continue
                val name = player.name ?: continue
                if (name.lowercase(Locale.ROOT).startsWith(partial)) names.add(name)
                if (names.size >= 20) break
            }
            return names
        }
        return emptyList()
    }
}
