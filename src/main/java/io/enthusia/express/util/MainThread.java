package io.enthusia.express.util;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiConsumer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Tracks database completions so graceful disable cannot discard inventory compensation. */
public final class MainThread {
  private final Set<CompletableFuture<?>> pending = new HashSet<>();
  private final ConcurrentLinkedQueue<Runnable> ready = new ConcurrentLinkedQueue<>();
  private final BukkitTask task;
  private final java.util.logging.Logger logger;

  public MainThread(JavaPlugin plugin) {
    logger = plugin.getLogger();
    task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::drain, 1, 1);
  }

  public <T> void complete(CompletableFuture<T> future, BiConsumer<T, Throwable> callback) {
    CompletableFuture<Void> queued =
        future.handle(
            (value, error) -> {
              ready.add(() -> callback.accept(value, error));
              return null;
            });
    pending.add(queued);
  }

  private void drain() {
    pending.removeIf(CompletableFuture::isDone);
    Runnable callback;
    while ((callback = ready.poll()) != null) {
      try {
        callback.run();
      } catch (RuntimeException error) {
        logger.log(java.util.logging.Level.SEVERE, "Mail completion callback failed", error);
      }
    }
  }

  public void close() {
    task.cancel();
    while (!pending.isEmpty() || !ready.isEmpty()) {
      CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
      drain();
    }
  }
}
