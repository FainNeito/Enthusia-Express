package io.enthusia.express.infrastructure.util

import org.bukkit.ChatColor
import org.bukkit.configuration.file.FileConfiguration

object Text {
    @JvmStatic
    fun color(input: String?): String = ChatColor.translateAlternateColorCodes('&', input ?: "")

    @JvmStatic
    fun msg(config: FileConfiguration, key: String, vars: Map<String, String>): String {
        val prefix = config.getString("messages.prefix", "") ?: ""
        var value = config.getString("messages.$key", key) ?: key
        for ((name, replacement) in vars) value = value.replace("{$name}", replacement)
        return color(prefix + value)
    }

    @JvmStatic
    fun msg(config: FileConfiguration, key: String): String = msg(config, key, emptyMap())
}
