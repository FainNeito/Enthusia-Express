package io.enthusia.express;

import static org.mockito.Mockito.*;

import io.enthusia.express.application.MailStore;
import io.enthusia.express.infrastructure.mail.MailBlockService;
import io.enthusia.express.infrastructure.util.MainThread;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class MailBlockServiceTest {
  /** A denied permission cannot mutate or enumerate preferences. */
  @Test
  void deniedPermissionDoesNotAccessPreferences() {
    JavaPlugin plugin = mock(JavaPlugin.class);
    when(plugin.getConfig()).thenReturn(new YamlConfiguration());
    MailStore store = mock(MailStore.class);
    MailBlockService service = new MailBlockService(plugin, store, mock(MainThread.class));
    Player player = mock(Player.class);
    service.change(player, mock(OfflinePlayer.class), true);
    service.list(player, 1);
    verifyNoInteractions(store);
  }

  /** Lists are scoped to the caller and delayed results do not reach a new login. */
  @Test
  void delayedListIsScopedAndSessionSafe() {
    try (var bukkit = mockStatic(Bukkit.class)) {
      JavaPlugin plugin = mock(JavaPlugin.class);
      when(plugin.isEnabled()).thenReturn(true);
      Player player = mock(Player.class);
      UUID owner = UUID.randomUUID();
      when(player.getUniqueId()).thenReturn(owner);
      when(player.isOnline()).thenReturn(true);
      when(player.hasPermission(anyString())).thenReturn(true);
      bukkit.when(() -> Bukkit.getPlayer(owner)).thenReturn(mock(Player.class));
      MailStore store = mock(MailStore.class);
      CompletableFuture<List<String>> pending = new CompletableFuture<>();
      when(store.listBlocked(owner, 1)).thenReturn(pending);
      MainThread main = mock(MainThread.class);
      doAnswer(call -> {
        CompletableFuture<?> future = call.getArgument(0);
        BiConsumer<Object, Throwable> callback = call.getArgument(1);
        future.whenComplete(callback);
        return null;
      }).when(main).complete(any(), any());
      new MailBlockService(plugin, store, main).list(player, 2);
      verify(store).listBlocked(owner, 1);
      pending.complete(List.of("Alice"));
      verify(player, never()).sendMessage(anyString());
    }
  }
}
