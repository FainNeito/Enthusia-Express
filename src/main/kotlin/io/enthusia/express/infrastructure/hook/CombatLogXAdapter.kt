package io.enthusia.express.infrastructure.hook

import com.github.sirblobman.combatlogx.api.ICombatLogX
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** Loaded only after the optional dependency is enabled; invoke its public interface directly. */
internal object CombatLogXAdapter {
    /** Call the public CombatLogX interface so non-public implementation classes remain supported. */
    fun isInCombat(dependency: Plugin, player: Player): Boolean {
        val api = dependency as? ICombatLogX ?: error("CombatLogX does not implement ICombatLogX")
        return checkNotNull(api.combatManager) { "CombatLogX has no combat manager" }.isInCombat(player)
    }
}
