package io.enthusia.express.infrastructure.hook

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

class CombatLogXHook(private val plugin: JavaPlugin) {
    private var lastWarning = 0L

    fun isAvailable(): Boolean {
        val dependency = plugin.server.pluginManager.getPlugin("CombatLogX")
        return dependency != null && dependency.isEnabled
    }

    fun mayUseMail(player: Player): Boolean {
        val dependency = plugin.server.pluginManager.getPlugin("CombatLogX")
        if (dependency == null || !dependency.isEnabled) return !plugin.config.getBoolean("mail.require-combatlogx", true)
        try {
            val manager = dependency.javaClass.getMethod("getCombatManager").invoke(dependency)
            val result = manager.javaClass.getMethod("isInCombat", Player::class.java).invoke(manager, player)
            if (result !is Boolean) throw IllegalStateException("Unexpected CombatLogX response")
            return !result
        } catch (error: ReflectiveOperationException) {
            warn(error)
        } catch (error: RuntimeException) {
            warn(error)
        } catch (error: LinkageError) {
            warn(error)
        }
        return false
    }

    private fun warn(error: Throwable) {
        val now = System.currentTimeMillis()
        if (now - lastWarning > 60000) {
            plugin.logger.warning("CombatLogX hook failed; mail access denied: $error")
            lastWarning = now
        }
    }
}
