package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.util.MainThread;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

class MainThreadTest {
  /** Per-tick admission is bounded while shutdown still drains every accepted completion. */
  @Test void callbackBudgetDefersExcessWorkAndShutdownDrainsIt() {
    JavaPlugin plugin = mock(JavaPlugin.class);
    var config = new org.bukkit.configuration.file.YamlConfiguration();
    config.set("mail.max-completions-per-tick", 2);
    config.set("mail.completion-budget-ms", 60_000);
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    Server server = mock(Server.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.getScheduler()).thenReturn(scheduler);
    var tick = new java.util.concurrent.atomic.AtomicReference<Runnable>();
    when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L))).thenAnswer(call -> {
      tick.set(call.getArgument(1));
      return mock(BukkitTask.class);
    });
    MainThread main = new MainThread(plugin);
    List<Integer> results = new ArrayList<>();
    for (int i = 0; i < 7; i++) main.complete(CompletableFuture.completedFuture(i), (value, error) -> results.add(value));
    tick.get().run();
    assertEquals(2, results.size());
    main.close();
    assertEquals(List.of(0, 1, 2, 3, 4, 5, 6), results);
  }
  /** Verifies that disable waits for callbacks and their nested compensation. */
  @Test
  void disableWaitsForCallbacksAndTheirNestedCompensation() {
    JavaPlugin plugin = mock(JavaPlugin.class);
    when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
    when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
    Server server = mock(Server.class);
    BukkitScheduler scheduler = mock(BukkitScheduler.class);
    when(plugin.getServer()).thenReturn(server);
    when(server.getScheduler()).thenReturn(scheduler);
    when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L)))
        .thenReturn(mock(BukkitTask.class));
    MainThread main = new MainThread(plugin);
    List<String> result = new ArrayList<>();
    Thread owner = Thread.currentThread();
    CompletableFuture<String> write = CompletableFuture.supplyAsync(() -> "saved");
    main.complete(
        write,
        (value, error) -> {
          assertSame(owner, Thread.currentThread());
          result.add(value);
          main.complete(
              CompletableFuture.completedFuture("compensated"),
              (next, failure) -> result.add(next));
        });
    main.close();
    assertEquals(List.of("saved", "compensated"), result);
  }
}
