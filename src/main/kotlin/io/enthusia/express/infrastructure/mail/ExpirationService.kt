package io.enthusia.express.infrastructure.mail

import io.enthusia.express.application.MailStore
import java.util.concurrent.TimeUnit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

class ExpirationService(private val plugin: JavaPlugin, private val repository: MailStore) {
    private var task: BukkitTask? = null

    fun start() {
        // The repository work itself is asynchronous; this merely schedules periodic checks.
        task = plugin.server.scheduler.runTaskTimer(
            plugin, Runnable { tick() }, 20L * 60L,
            20L * plugin.config.getLong("mail.expiration-check-seconds", 600L),
        )
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val returnHours = plugin.config.getLong("mail.return-after-hours", 168L)
        val purgeHours = plugin.config.getLong("mail.purge-returned-after-hours", 168L)
        val textHours = plugin.config.getLong("mail.text-retention-hours", 720L)
        repository.expire(
            now, now - TimeUnit.HOURS.toMillis(returnHours),
            now - TimeUnit.HOURS.toMillis(purgeHours), now - TimeUnit.HOURS.toMillis(textHours),
        ).exceptionally { error ->
            plugin.logger.severe("Mail expiration failed: $error")
            0
        }
    }

    fun stop() {
        task?.cancel()
    }
}
