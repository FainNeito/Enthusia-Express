package io.enthusia.express.mail;

import io.enthusia.express.db.MailRepository;
import java.util.concurrent.TimeUnit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class ExpirationService {
  private final JavaPlugin plugin;
  private final MailRepository repository;
  private BukkitTask task;

  public ExpirationService(JavaPlugin plugin, MailRepository repository) {
    this.plugin = plugin;
    this.repository = repository;
  }

  public void start() {
    // The repository work itself is asynchronous; this merely schedules periodic checks.
    task =
        plugin
            .getServer()
            .getScheduler()
            .runTaskTimer(
                plugin,
                this::tick,
                20L * 60L,
                20L * plugin.getConfig().getLong("mail.expiration-check-seconds", 600L));
  }

  private void tick() {
    long now = System.currentTimeMillis();
    long returnHours = plugin.getConfig().getLong("mail.return-after-hours", 168L);
    long purgeHours = plugin.getConfig().getLong("mail.purge-returned-after-hours", 168L);
    long textHours = plugin.getConfig().getLong("mail.text-retention-hours", 720L);
    repository
        .expire(
            now,
            now - TimeUnit.HOURS.toMillis(returnHours),
            now - TimeUnit.HOURS.toMillis(purgeHours),
            now - TimeUnit.HOURS.toMillis(textHours))
        .exceptionally(
            error -> {
              plugin.getLogger().severe("Mail expiration failed: " + error);
              return 0;
            });
  }

  public void stop() {
    if (task != null) task.cancel();
  }
}
