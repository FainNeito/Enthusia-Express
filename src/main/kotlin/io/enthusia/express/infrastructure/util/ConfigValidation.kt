package io.enthusia.express.infrastructure.util

import org.bukkit.configuration.file.FileConfiguration

object ConfigValidation {
    @JvmStatic
    fun validate(config: FileConfiguration) {
        require(config.getString("payments.provider", "auto") in setOf("auto", "physical", "enthusia-currency")) {
            "payments.provider must be auto, physical, or enthusia-currency"
        }
        for (key in listOf("mail.require-combatlogx", "letters.enabled", "announcements.enabled", "mail.limits.one-outstanding-package-per-recipient",
            "mail.limits.one-outstanding-letter-per-recipient", "notifications.join-mail.enabled", "sounds.enabled")) {
            require(!config.contains(key) || config.isBoolean(key)) { "$key must be true or false" }
        }
        range(config, "database.busy-timeout-ms", 5000, 1, 60000)
        range(config, "mail.raw-gold-per-item", 1, 0, 1000000)
        range(config, "mail.max-recursive-container-depth", 8, 1, 32)
        range(config, "mail.return-after-hours", 168, 1, 876000)
        range(config, "mail.purge-returned-after-hours", 168, 1, 876000)
        range(config, "mail.text-retention-hours", 720, 1, 876000)
        range(config, "mail.expiration-check-seconds", 600, 1, 86400)
        range(config, "letters.max-pages", 50, 1, 100)
        range(config, "letters.max-payload-bytes", 262144, 1024, 1048576)
        range(config, "letters.cooldown-seconds", 10, 0, 86400)
        range(config, "announcements.cooldown-seconds", 10, 0, 86400)
    }

    private fun range(config: FileConfiguration, key: String, fallback: Long, min: Long, max: Long) {
        require(!config.contains(key) || config.isInt(key) || config.isLong(key)) { "$key must be an integer" }
        require(config.getLong(key, fallback) in min..max) { "$key must be between $min and $max" }
    }
}
