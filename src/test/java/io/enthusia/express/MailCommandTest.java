package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.command.MailCommand;
import io.enthusia.express.infrastructure.gui.MailboxService;
import io.enthusia.express.infrastructure.gui.ShippingService;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.infrastructure.mail.BookMailService;
import io.enthusia.express.infrastructure.util.MainThread;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class MailCommandTest {
    /** The sent command opens sender history and completes its categories. */
    @Test
    void sentCommandOpensHistory() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player sender = mock(Player.class);
            when(sender.hasPermission(anyString())).thenReturn(true);
            CombatLogXHook combat = mock(CombatLogXHook.class);
            when(combat.mayUseMail(sender)).thenReturn(true);
            MailboxService mailbox = mock(MailboxService.class);
            MailCommand command = new MailCommand(mock(JavaPlugin.class), mock(ShippingService.class),
                    mailbox, combat, mock(BookMailService.class), mock(MainThread.class));
            command.onCommand(sender, mock(Command.class), "mail", new String[] {"sent", "letters"});
            verify(mailbox).openSent(sender, io.enthusia.express.domain.MailType.LETTER);
            assertEquals(List.of("letters"), command.onTabComplete(sender, mock(Command.class), "mail",
                    new String[] {"sent", "le"}));
        }
    }

    /** Offline names are available without reading player files during completion. */
    @Test
    void knownOfflineNamesAreSuggestedFromStartupSnapshot() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getName()).thenReturn("OfflineAlice");
            bukkit.when(Bukkit::getOfflinePlayers).thenReturn(new OfflinePlayer[] {target});
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            Player sender = mock(Player.class);
            when(sender.hasPermission(anyString())).thenReturn(true);
            MailCommand command = new MailCommand(mock(JavaPlugin.class), mock(ShippingService.class),
                    mock(MailboxService.class), mock(CombatLogXHook.class), mock(BookMailService.class), mock(MainThread.class));
            bukkit.clearInvocations();
            assertEquals(List.of("OfflineAlice"), command.onTabComplete(sender, mock(Command.class), "mail",
                    new String[] {"letter", "off"}));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
        }
    }

    /** Verifies a cache miss schedules UUID lookup before opening shipping. */
    @Test
    void uncachedKnownRecipientResolvesAwayFromCommandDispatch() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            JavaPlugin plugin = mock(JavaPlugin.class, RETURNS_DEEP_STUBS);
            when(plugin.getConfig()).thenReturn(new YamlConfiguration());
            when(plugin.isEnabled()).thenReturn(true);
            Player sender = mock(Player.class);
            UUID senderId = UUID.randomUUID();
            UUID recipientId = UUID.randomUUID();
            when(sender.getUniqueId()).thenReturn(senderId);
            when(sender.hasPermission(anyString())).thenReturn(true);
            when(sender.isOnline()).thenReturn(true);
            OfflinePlayer target = mock(OfflinePlayer.class);
            when(target.getUniqueId()).thenReturn(recipientId);
            when(target.hasPlayedBefore()).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(senderId)).thenReturn(sender);
            bukkit.when(() -> Bukkit.getPlayerUniqueId("Alice")).thenReturn(recipientId);
            bukkit.when(() -> Bukkit.getOfflinePlayer(recipientId)).thenReturn(target);
            CombatLogXHook combat = mock(CombatLogXHook.class);
            when(combat.mayUseMail(sender)).thenReturn(true);
            ShippingService shipping = mock(ShippingService.class);
            BukkitScheduler scheduler = plugin.getServer().getScheduler();
            AtomicReference<Runnable> lookup = new AtomicReference<>();
            doAnswer(call -> { lookup.set(call.getArgument(1)); return null; })
                    .when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
            MainThread main = mock(MainThread.class);
            doAnswer(call -> {
                CompletableFuture<?> future = call.getArgument(0);
                BiConsumer<Object, Throwable> callback = call.getArgument(1);
                future.whenComplete(callback);
                return null;
            }).when(main).complete(any(), any());
            MailCommand command = new MailCommand(plugin, shipping, mock(MailboxService.class),
                    combat, mock(BookMailService.class), main);
            command.onCommand(sender, mock(Command.class), "mail", new String[] {"send", "Alice"});
            verifyNoInteractions(shipping);
            bukkit.verify(() -> Bukkit.getPlayerUniqueId("Alice"), never());
            assertNotNull(lookup.get(), "Must defer UUID resolution");
            lookup.get().run();
            verify(shipping).open(sender, target);
        }
    }

    /** Verifies that tab completion does not enumerate offline player files. */
    @Test
    void tabCompletionDoesNotEnumerateOfflinePlayerFiles() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player sender = mock(Player.class);
            Player target = mock(Player.class);
            when(sender.hasPermission("enthusiaexpress.admin.announce")).thenReturn(true);
            when(target.getName()).thenReturn("Alice");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target));
            MailCommand command = new MailCommand(mock(JavaPlugin.class), mock(ShippingService.class),
                    mock(MailboxService.class), mock(CombatLogXHook.class), mock(BookMailService.class), mock(MainThread.class));
            bukkit.clearInvocations();
            bukkit.when(Bukkit::getOfflinePlayers).thenThrow(new AssertionError("Disk scan during completion"));
            assertEquals(List.of("all", "Alice"), command.onTabComplete(sender, mock(Command.class), "mail",
                    new String[] {"announce", "al"}));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
        }
    }
}
