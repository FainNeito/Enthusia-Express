// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.hook

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CombatLogXHook(private val plugin: JavaPlugin) {
    private var lastWarning = 0L

    /** Report whether CombatLogX is installed and enabled. */
    fun isAvailable(): Boolean {
        val dependency = plugin.server.pluginManager.getPlugin("CombatLogX")
        return dependency != null && dependency.isEnabled
    }

    /** Apply combat protection, failing closed for a present but broken integration. */
    // Fail closed for incompatible plugin implementations, including unchecked provider failures.
    @Suppress("TooGenericExceptionCaught")
    fun mayUseMail(player: Player): Boolean {
        val dependency = plugin.server.pluginManager.getPlugin("CombatLogX")
        if (dependency == null || !dependency.isEnabled) return !plugin.config.getBoolean("mail.require-combatlogx", true)
        try {
            return !CombatLogXAdapter.isInCombat(dependency, player)
        } catch (error: RuntimeException) {
            warn(error)
        } catch (error: LinkageError) {
            warn(error)
        }
        return false
    }

    /** Rate-limit integration diagnostics to one warning per minute. */
    private fun warn(error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastWarning > 60000) {
            plugin.logger.warning("CombatLogX hook failed; mail access denied: $error")
            lastWarning = now
        }
    }
}
