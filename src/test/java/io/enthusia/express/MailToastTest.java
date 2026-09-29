package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.enthusia.express.application.MailStore;
import io.enthusia.express.infrastructure.util.MainThread;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.function.BiConsumer;
import io.enthusia.express.infrastructure.db.MailRepository;
import io.enthusia.express.domain.MailType;
import io.enthusia.express.domain.MailNotification;
import io.enthusia.express.infrastructure.hook.NativeMailToast;
import io.enthusia.express.infrastructure.hook.NativeToastPackets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MailToastTest {
  @TempDir Path directory;

  @Test void nativeToastsCleanUpOnlyTheirConnectedSession() throws Exception {
    var plugin = mock(JavaPlugin.class);
    when(plugin.getConfig()).thenReturn(new YamlConfiguration());
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    var player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
    var task = mock(org.bukkit.scheduler.BukkitTask.class);
    when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), eq(100L))).thenReturn(task);
    var packets = mock(NativeToastPackets.class);
    Object key = new Object();
    when(packets.key(anyString())).thenReturn(key);
    var notice = new MailNotification(1, "Alex", MailType.LETTER, 1);
    try (var bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
      bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
      var renderer = new NativeMailToast(plugin);
      var field = renderer.getClass().getDeclaredField("bridge");
      field.setAccessible(true);
      field.set(renderer, packets);
      renderer.accept(player, notice);
      verify(packets).show(player, key, notice);
      renderer.clear(id);
      verify(packets).remove(player, key);
      verify(task).cancel();
      clearInvocations(packets);
      renderer.accept(player, notice);
      bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mock(Player.class));
      renderer.close();
      verify(packets, never()).remove(any(), any());
    }
  }

  @Test void toastDoesNotRequirePacketEvents() throws Exception {
    var descriptor = java.nio.file.Files.readString(Path.of("src/main/resources/plugin.yml"));
    assertFalse(descriptor.toLowerCase(java.util.Locale.ROOT).contains("packetevents"));
    assertNotNull(Class.forName("io.enthusia.express.infrastructure.hook.NativeMailToast"));
  }

  /** Missing optional packet support must leave a readable, non-mutating fallback. */
  @Test void missingNativeBindingsFallBackToChat() {
    var plugin = mock(JavaPlugin.class);
    var config = new YamlConfiguration();
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    var manager = mock(org.bukkit.plugin.PluginManager.class);
    var player = mock(Player.class);
    when(player.isOnline()).thenReturn(true);
    try (var bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
      var renderer = new NativeMailToast(plugin);
      renderer.accept(player, new MailNotification(1, "Alex", MailType.PACKAGE, 1));
      verify(player).sendMessage(net.kyori.adventure.text.Component.text("Package from Alex"));
      clearInvocations(player);
      config.set("notifications.toast.chat-fallback", false);
      renderer.accept(player, new MailNotification(1, "Alex", MailType.PACKAGE, 1));
      verify(player, never()).sendMessage(any(net.kyori.adventure.text.Component.class));
      renderer.close();
    }
  }

  /** Completion from an obsolete login must never notify a replacement session. */
  @Test void staleSessionAndRepeatedPollsDoNotDuplicateToasts() throws Exception {
    var plugin = mock(JavaPlugin.class);
    var config = new YamlConfiguration();
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    var repository = mock(MailStore.class);
    var main = mock(MainThread.class);
    var player = mock(Player.class);
    UUID id = UUID.randomUUID();
    when(player.getUniqueId()).thenReturn(id);
    when(player.isOnline()).thenReturn(true);
    var noticeType = Class.forName("io.enthusia.express.domain.MailNotification");
    Object notice = noticeType.getConstructor(int.class, String.class, MailType.class, long.class)
        .newInstance(1, "Alex", MailType.LETTER, 10L);
    var future = new CompletableFuture<Object>();
    var query = MailStore.class.getMethod("mailNotification", UUID.class, long.class);
    doReturn(future).when(repository);
    query.invoke(repository, id, 0L);
    @SuppressWarnings("unchecked") BiConsumer<Object, Throwable>[] callback = new BiConsumer[1];
    doAnswer(call -> { callback[0] = call.getArgument(1); return null; }).when(main).complete(any(), any());
    @SuppressWarnings("unchecked") BiConsumer<Player, Object> renderer = mock(BiConsumer.class);
    Class<?> serviceType = Class.forName("io.enthusia.express.infrastructure.mail.MailToastService");
    Object service = serviceType.getConstructor(JavaPlugin.class, MailStore.class, MainThread.class, BiConsumer.class)
        .newInstance(plugin, repository, main, renderer);
    var join = mock(PlayerJoinEvent.class);
    when(join.getPlayer()).thenReturn(player);
    try (var bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
      serviceType.getMethod("onJoin", PlayerJoinEvent.class).invoke(service, join);
      serviceType.getMethod("poll").invoke(service);
      serviceType.getMethod("poll").invoke(service);
      verify(main, times(1)).complete(any(), any());
      callback[0].accept(notice, null);
      verify(renderer).accept(player, notice);
      serviceType.getMethod("onJoin", PlayerJoinEvent.class).invoke(service, join);
      serviceType.getMethod("poll").invoke(service);
      serviceType.getMethod("poll").invoke(service);
      bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(mock(Player.class));
      callback[0].accept(notice, null);
      verify(renderer, times(1)).accept(any(), any());
      serviceType.getMethod("close").invoke(service);
      callback[0].accept(notice, null);
      verify(renderer, times(1)).accept(any(), any());
    }
  }

  /** Toast wording remains compact, distinguishes types, and summarizes grouped mail. */
  @Test void singleAndGroupedToastText() throws Exception {
    Class<?> notice = Class.forName("io.enthusia.express.domain.MailNotification");
    var constructor = notice.getConstructor(int.class, String.class, MailType.class, long.class);
    Object letter = constructor.newInstance(1, "Alex", MailType.LETTER, 1L);
    Object parcel = constructor.newInstance(1, "Steve", MailType.PACKAGE, 2L);
    Object group = constructor.newInstance(4, null, null, 5L);
    assertEquals("Letter from Alex", property(letter, "text"));
    assertEquals("Package from Steve", property(parcel, "text"));
    assertEquals("You've got mail!\n4 items received", property(group, "text"));
    Object unsafe = constructor.newInstance(1, "§kBad\nName", MailType.LETTER, 6L);
    String safeText = (String) property(unsafe, "text");
    assertFalse(safeText.contains("§"));
    assertFalse(safeText.contains("\n"));
  }

  /** Only unseen arrivals contribute, while the watermark advances past read history. */
  @Test void toastSnapshotCountsOnlyPendingArrivals() throws Exception {
    var recipient = UUID.randomUUID();
    var sender = UUID.randomUUID();
    var repository = new MailRepository(null, directory.resolve("mail.db").toFile(), 5000);
    try {
      repository.initialize().join();
      long first = repository.insertMail(sender, "Alex", recipient, "Receiver", MailType.LETTER, new byte[]{1}, 0, false).join();
      Object single = snapshot(repository, recipient, 0);
      assertEquals(1, property(single, "getCount"));
      assertEquals("Alex", property(single, "getSender"));
      assertEquals(MailType.LETTER, property(single, "getType"));
      long second = repository.insertMail(sender, "Alex", recipient, "Receiver", MailType.PACKAGE, new byte[]{2}, 1, false).join();
      assertEquals(2, property(snapshot(repository, recipient, 0), "getCount"));
      Object recent = snapshot(repository, recipient, first);
      assertEquals(1, property(recent, "getCount"));
      assertEquals(MailType.PACKAGE, property(recent, "getType"));
      repository.markRead(first, recipient).join();
      assertEquals(1, property(snapshot(repository, recipient, 0), "getCount"));
      Object noNew = snapshot(repository, recipient, second);
      assertEquals(0, property(noNew, "getCount"));
      assertEquals(second, property(noNew, "getWatermark"));
      assertEquals(0, property(snapshot(repository, UUID.randomUUID(), 0), "getCount"));
    } finally { repository.close(); }
  }

  private Object snapshot(MailRepository repository, UUID recipient, long after) throws Exception {
    var method = MailRepository.class.getMethod("mailNotification", UUID.class, long.class);
    return ((CompletableFuture<?>) method.invoke(repository, recipient, after)).join();
  }

  private Object property(Object value, String name) throws Exception {
    return value.getClass().getMethod(name).invoke(value);
  }
}
