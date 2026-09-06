package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.util.MainThread;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Server;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;

class MainThreadTest {
  @Test
  void disableWaitsForCallbacksAndTheirNestedCompensation() {
    JavaPlugin plugin = mock(JavaPlugin.class);
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
