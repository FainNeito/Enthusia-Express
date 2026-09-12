package io.enthusia.express;

import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.enthusia.express.infrastructure.gui.GuiTheme;
import io.enthusia.express.infrastructure.gui.NexoItemSource;
import io.enthusia.express.infrastructure.gui.ReflectiveNexoItems;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

public class NexoIntegrationTest {
  /** Custom icons are copies, so GUI metadata changes cannot mutate the Nexo registry template. */
  @Test
  void customIconsAreClonedAndResolvedAgainAfterReload() {
    try (var bukkit = mockStatic(Bukkit.class)) {
      JavaPlugin plugin = configuredPlugin();
      Plugin nexo = install(bukkit);
      NexoItemSource source = mock(NexoItemSource.class);
      ItemStack template = mock(ItemStack.class), copy = mock(ItemStack.class);
      Material customMaterial = mock(Material.class);
      when(template.getType()).thenReturn(customMaterial);
      when(template.clone()).thenReturn(copy);
      when(source.build(nexo, "mail_icon")).thenReturn(template);
      GuiTheme theme = new GuiTheme(plugin, source);
      assertSame(copy, theme.item("mailbox.packages", Material.CHEST));
      verify(copy).setAmount(1);
      verify(template, never()).setAmount(anyInt());
      theme.item("mailbox.packages", Material.CHEST);
      verify(source, times(2)).build(nexo, "mail_icon");
    }
  }

  /** Missing Nexo, unknown IDs and API failures retain usable vanilla controls. */
  @Test
  void missingAndFailingAssetsUseVanillaFallback() {
    try (var bukkit = mockStatic(Bukkit.class);
        var items = mockConstruction(ItemStack.class, (item, context) ->
            when(item.getType()).thenReturn((Material) context.arguments().getFirst()))) {
      JavaPlugin plugin = configuredPlugin();
      NexoItemSource source = mock(NexoItemSource.class);
      GuiTheme theme = new GuiTheme(plugin, source);
      assertEquals(Material.CHEST, theme.item("mailbox.packages", Material.CHEST).getType());
      verifyNoInteractions(source);
      Plugin nexo = install(bukkit);
      assertEquals(Material.CHEST, theme.item("mailbox.packages", Material.CHEST).getType());
      when(source.build(nexo, "mail_icon")).thenThrow(new IllegalStateException("reload"));
      assertEquals(Material.CHEST, theme.item("mailbox.packages", Material.CHEST).getType());
    }
  }

  /** The optional reflection adapter invokes the documented static lookup and builder method. */
  @Test
  void reflectiveApiBuildsTheRequestedItem() {
    assertNotNull(new ReflectiveNexoItems(FakeApi.class.getName()).build(mock(Plugin.class), "mail_icon"));
    assertNull(new ReflectiveNexoItems(FakeApi.class.getName()).build(mock(Plugin.class), "missing"));
  }

  /** Enable one illustrative icon for the adapter tests. */
  private JavaPlugin configuredPlugin() {
    JavaPlugin plugin = mock(JavaPlugin.class);
    YamlConfiguration config = new YamlConfiguration();
    config.set("gui.nexo.enabled", true);
    config.set("gui.nexo.icons.mailbox.packages", "mail_icon");
    when(plugin.getConfig()).thenReturn(config);
    when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
    return plugin;
  }

  /** Register an enabled optional plugin in the mocked server. */
  private Plugin install(MockedStatic<Bukkit> bukkit) {
    PluginManager manager = mock(PluginManager.class);
    Plugin nexo = mock(Plugin.class);
    when(nexo.isEnabled()).thenReturn(true);
    when(manager.getPlugin("Nexo")).thenReturn(nexo);
    bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
    return nexo;
  }

  public static class FakeApi {
    /** Emulate the public registry lookup signature. */
    public static FakeBuilder itemFromId(String id) {
      return id.equals("mail_icon") ? new FakeBuilder() : null;
    }
  }

  public static class FakeBuilder {
    /** Emulate a Nexo item builder without a running server. */
    public ItemStack build() { return mock(ItemStack.class); }
  }
}
