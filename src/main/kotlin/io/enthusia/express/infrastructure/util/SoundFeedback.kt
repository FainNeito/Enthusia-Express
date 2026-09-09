package io.enthusia.express.infrastructure.util

import java.util.Locale
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** Cosmetic configuration failures never interrupt persisted mail operations. */
class SoundFeedback(private val plugin: JavaPlugin) {
    enum class Cue(val key: String, val defaultSound: String) {
        PACKAGE_SEND("package-send", "minecraft:block.note_block.pling"),
        PACKAGE_CLAIM("package-claim", "minecraft:entity.item.pickup"),
        LETTER_SEND("letter-send", "minecraft:item.book.page_turn"),
        LETTER_OPEN("letter-open", "minecraft:item.book.page_turn")
    }

    private val warned = HashSet<String>()
    private data class Settings(val sound: String, val volume: Float, val pitch: Float)

    fun play(player: Player, cue: Cue) {
        if (!plugin.config.getBoolean("sounds.enabled", true)) return
        val settings = settings(cue) ?: return
        try {
            player.playSound(player.location, settings.sound, settings.volume, settings.pitch)
        } catch (error: RuntimeException) {
            warnOnce("sounds.${cue.key}", "Could not play configured sound at sounds.${cue.key}: ${error.message}")
        }
    }

    fun validate() {
        if (plugin.config.getBoolean("sounds.enabled", true)) Cue.entries.forEach { settings(it) }
    }

    private fun settings(cue: Cue): Settings? {
        val path = "sounds.${cue.key}"
        val config = plugin.config
        val sound = (config.get("$path.sound", cue.defaultSound) as? String)?.lowercase(Locale.ROOT) ?: ""
        val volume = (config.get("$path.volume", 1.0) as? Number)?.toDouble() ?: Double.NaN
        val pitch = (config.get("$path.pitch", 1.0) as? Number)?.toDouble() ?: Double.NaN
        if (!sound.matches(Regex("[a-z0-9._-]+:[a-z0-9/._-]+")) || !volume.isFinite() || volume < 0 ||
            volume > Float.MAX_VALUE || !pitch.isFinite() || pitch < 0.5 || pitch > 2.0) {
            warnOnce(path, "Invalid sound configuration at $path; feedback disabled for it.")
            return null
        }
        return Settings(sound, volume.toFloat(), pitch.toFloat())
    }

    private fun warnOnce(key: String, message: String) {
        if (warned.add(key)) plugin.logger?.warning(message)
    }
}
