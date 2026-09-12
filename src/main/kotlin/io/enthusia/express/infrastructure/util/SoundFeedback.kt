// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

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

    /** Play a configured success cue for an online player while isolating invalid sound arguments. */
    fun play(player: Player, cue: Cue) {
        if (!plugin.config.getBoolean("sounds.enabled", true)) return
        val settings = settings(cue) ?: return
        try {
            player.playSound(player.location, settings.sound, settings.volume, settings.pitch)
        } catch (error: IllegalArgumentException) {
            warnOnce("sounds.${cue.key}", "Could not play configured sound at sounds.${cue.key}: ${error.message}")
        }
    }

    /** Validate every cue at startup without disabling unrelated valid sounds. */
    fun validate() {
        if (plugin.config.getBoolean("sounds.enabled", true)) Cue.entries.forEach { settings(it) }
    }

    /** Load validated sound, volume and pitch values, returning null for disabled or invalid cues. */
    private fun settings(cue: Cue): Settings? {
        val path = "sounds.${cue.key}"
        val config = plugin.config
        val sound = (config.get("$path.sound", cue.defaultSound) as? String)?.lowercase(Locale.ROOT) ?: ""
        val volume = (config.get("$path.volume", 1.0) as? Number)?.toDouble() ?: Double.NaN
        val pitch = (config.get("$path.pitch", 1.0) as? Number)?.toDouble() ?: Double.NaN
        if (!sound.matches(Regex("[a-z0-9._-]+:[a-z0-9/._-]+")) || !validVolume(volume) || !validPitch(pitch)) {
            warnOnce(path, "Invalid sound configuration at $path; feedback disabled for it.")
            return null
        }
        return Settings(sound, volume.toFloat(), pitch.toFloat())
    }

    /** Accept only finite, nonnegative volumes representable as a float. */
    private fun validVolume(value: Double) = value.isFinite() && value in 0.0..Float.MAX_VALUE.toDouble()

    /** Accept only finite pitches within the supported configured range. */
    private fun validPitch(value: Double) = value.isFinite() && value in 0.5..2.0

    /** Emit each configuration diagnostic at most once. */
    private fun warnOnce(key: String, message: String) {
        if (warned.add(key)) plugin.logger?.warning(message)
    }
}
