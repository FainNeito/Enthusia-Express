package io.enthusia.express.command;

import io.enthusia.express.gui.MailboxService;
import io.enthusia.express.gui.ShippingService;
import io.enthusia.express.hook.CombatLogXHook;
import io.enthusia.express.mail.MailType;
import io.enthusia.express.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class MailCommand implements CommandExecutor, TabCompleter {
  private final io.enthusia.express.mail.BookMailService books;
  private final JavaPlugin plugin;
  private final ShippingService shipping;
  private final MailboxService mailbox;
  private final CombatLogXHook combatHook;

  public MailCommand(
      JavaPlugin plugin,
      ShippingService shipping,
      MailboxService mailbox,
      CombatLogXHook combatHook,
      io.enthusia.express.mail.BookMailService books) {
    this.books = books;
    this.plugin = plugin;
    this.shipping = shipping;
    this.mailbox = mailbox;
    this.combatHook = combatHook;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Players only.");
      return true;
    }
    if (!player.hasPermission("enthusiaexpress.use")) {
      player.sendMessage(Text.msg(plugin.getConfig(), "no-permission"));
      return true;
    }
    if (!combatHook.mayUseMail(player)) {
      player.sendMessage(
          Text.msg(
              plugin.getConfig(),
              combatHook.isAvailable() ? "combat-blocked" : "combatlogx-missing"));
      return true;
    }
    if (args.length == 0 || args[0].equalsIgnoreCase("inbox")) {
      MailType type = MailType.PACKAGE;
      if (args.length >= 2) {
        type =
            switch (args[1].toLowerCase(Locale.ROOT)) {
              case "letters", "letter" -> MailType.LETTER;
              case "announcements", "announcement", "admin" -> MailType.ANNOUNCEMENT;
              default -> MailType.PACKAGE;
            };
      }
      mailbox.open(player, type);
      return true;
    }
    if (args[0].equalsIgnoreCase("announce")
        && args.length == 2
        && args[1].equalsIgnoreCase("all")) {
      books.send(player, null, true, true);
      return true;
    }
    if (List.of("send", "letter", "announce").contains(args[0].toLowerCase(Locale.ROOT))) {
      String permission =
          switch (args[0].toLowerCase(Locale.ROOT)) {
            case "letter" -> "enthusiaexpress.letters.send";
            case "announce" -> "enthusiaexpress.admin.announce";
            default -> "enthusiaexpress.packages.send";
          };
      if (!player.hasPermission(permission)) {
        player.sendMessage(Text.msg(plugin.getConfig(), "no-permission"));
        return true;
      }
      if (args.length != 2) {
        player.sendMessage(
            "\u00a7eUsage: /mail <send|letter|announce> <player> (announce also accepts all)");
        return true;
      }
      OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(args[1]);
      if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
        player.sendMessage(Text.msg(plugin.getConfig(), "target-never-joined"));
        return true;
      }
      if (!args[0].equalsIgnoreCase("announce")
          && target.getUniqueId().equals(player.getUniqueId())) {
        player.sendMessage("\u00a7cYou cannot mail yourself.");
        return true;
      }
      if (args[0].equalsIgnoreCase("send")) shipping.open(player, target);
      else books.send(player, target, args[0].equalsIgnoreCase("announce"), false);
      return true;
    }
    player.sendMessage(
        "\u00a7e/mail send <OfflinePlayer> \u00a77or \u00a7e/mail inbox [packages|letters|announcements]");
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    if (args.length == 1)
      return List.of("send", "inbox", "letter", "announce").stream()
          .filter(
              s -> !s.equals("announce") || sender.hasPermission("enthusiaexpress.admin.announce"))
          .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
          .toList();
    if (args.length == 2 && args[0].equalsIgnoreCase("inbox"))
      return List.of("packages", "letters", "announcements");
    if (args.length == 2
        && List.of("send", "letter", "announce").contains(args[0].toLowerCase(Locale.ROOT))) {
      if (args[0].equalsIgnoreCase("announce")
          && !sender.hasPermission("enthusiaexpress.admin.announce")) return List.of();
      String partial = args[1].toLowerCase(Locale.ROOT);
      List<String> names = new ArrayList<>();
      if (args[0].equalsIgnoreCase("announce") && "all".startsWith(partial)) names.add("all");
      for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
        if ((!args[0].equalsIgnoreCase("announce") && p.isOnline()) || p.getName() == null)
          continue;
        if (p.getName().toLowerCase(Locale.ROOT).startsWith(partial)) names.add(p.getName());
        if (names.size() >= 20) break;
      }
      return names;
    }
    return List.of();
  }
}
