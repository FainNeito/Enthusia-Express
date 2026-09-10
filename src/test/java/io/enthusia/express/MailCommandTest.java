package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.command.MailCommand;
import io.enthusia.express.infrastructure.gui.MailboxService;
import io.enthusia.express.infrastructure.gui.ShippingService;
import io.enthusia.express.infrastructure.hook.CombatLogXHook;
import io.enthusia.express.infrastructure.mail.BookMailService;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class MailCommandTest {
    /** Verifies that tab completion does not enumerate offline player files. */
    @Test
    void tabCompletionDoesNotEnumerateOfflinePlayerFiles() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            Player sender = mock(Player.class);
            Player target = mock(Player.class);
            when(sender.hasPermission("enthusiaexpress.admin.announce")).thenReturn(true);
            when(target.getName()).thenReturn("Alice");
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(target));
            bukkit.when(Bukkit::getOfflinePlayers).thenThrow(new AssertionError("Disk scan during completion"));
            MailCommand command = new MailCommand(mock(JavaPlugin.class), mock(ShippingService.class),
                    mock(MailboxService.class), mock(CombatLogXHook.class), mock(BookMailService.class));
            assertEquals(List.of("all", "Alice"), command.onTabComplete(sender, mock(Command.class), "mail",
                    new String[] {"announce", "al"}));
            bukkit.verify(Bukkit::getOfflinePlayers, never());
        }
    }
}
