package io.enthusia.express.infrastructure.util

import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration

object Text {
    /** Translate configured ampersand color codes into Bukkit legacy formatting. */
    @JvmStatic
    fun color(input: String?): String = ChatColor.translateAlternateColorCodes('&', input ?: "")

    /** Apply named placeholders and the configured prefix before translating message color codes. */
    @JvmStatic
    fun msg(config: FileConfiguration, key: String, vars: Map<String, String>): String {
        val prefix = config.getString("messages.prefix", "") ?: ""
        var value = config.getString("messages.$key", key) ?: key
        for ((name, replacement) in vars) value = value.replace("{$name}", replacement)
        return color(prefix + value)
    }

    /** Apply named placeholders and the configured prefix before translating message color codes. */
    @JvmStatic
    fun msg(config: FileConfiguration, key: String): String = msg(config, key, emptyMap())
}
