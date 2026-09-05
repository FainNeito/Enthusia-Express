package io.enthusia.express.hook;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class CombatLogXHook {
  private final JavaPlugin plugin;
  private long lastWarning;

  public CombatLogXHook(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public boolean isAvailable() {
    Plugin dependency = plugin.getServer().getPluginManager().getPlugin("CombatLogX");
    return dependency != null && dependency.isEnabled();
  }

  public boolean mayUseMail(Player player) {
    Plugin dependency = plugin.getServer().getPluginManager().getPlugin("CombatLogX");
    if (dependency == null || !dependency.isEnabled())
      return !plugin.getConfig().getBoolean("mail.require-combatlogx", true);
    try {
      Object manager = dependency.getClass().getMethod("getCombatManager").invoke(dependency);
      Object result =
          manager.getClass().getMethod("isInCombat", Player.class).invoke(manager, player);
      if (!(result instanceof Boolean tagged))
        throw new IllegalStateException("Unexpected CombatLogX response");
      return !tagged;
    } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
      long now = System.currentTimeMillis();
      if (now - lastWarning > 60000) {
        plugin.getLogger().warning("CombatLogX hook failed; mail access denied: " + error);
        lastWarning = now;
      }
      return false;
    }
  }
}
