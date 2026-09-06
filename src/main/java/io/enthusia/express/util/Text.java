package io.enthusia.express.util;

import java.util.Map;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public final class Text {
  private Text() {}

  public static String color(String input) {
    return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
  }

  public static String msg(FileConfiguration config, String key, Map<String, String> vars) {
    String prefix = config.getString("messages.prefix", "");
    String value = config.getString("messages." + key, key);
    for (Map.Entry<String, String> entry : vars.entrySet()) {
      value = value.replace("{" + entry.getKey() + "}", entry.getValue());
    }
    return color(prefix + value);
  }

  public static String msg(FileConfiguration config, String key) {
    return msg(config, key, Map.of());
  }
}
