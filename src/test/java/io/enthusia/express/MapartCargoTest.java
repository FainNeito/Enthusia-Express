package io.enthusia.express;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import io.enthusia.express.infrastructure.util.ContainerScanner;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.junit.jupiter.api.Test;

class MapartCargoTest {
  private final InventorySafetyTest fixtures = new InventorySafetyTest();
  private ItemStack item(Material material, int count) { return fixtures.item(material, count); }
  private ItemStack bundle(ItemStack... items) { return fixtures.bundle(items); }
  @Test void acceptsFilledMapStacksAndNestedBundles() {
    assertEquals(64, ContainerScanner.countMapartItems(item(Material.FILLED_MAP, 64), 8));
    assertEquals(65, ContainerScanner.countMapartItems(bundle(item(Material.FILLED_MAP, 64), bundle(item(Material.FILLED_MAP, 1))), 8));
  }
  @Test void acceptsShulkerPreservingAllMapCounts() {
    var box = item(Material.PINK_SHULKER_BOX, 1);
    var meta = mock(BlockStateMeta.class);
    var state = mock(ShulkerBox.class);
    var inventory = mock(Inventory.class);
    when(box.getItemMeta()).thenReturn(meta);
    when(meta.getBlockState()).thenReturn(state);
    when(state.getInventory()).thenReturn(inventory);
    var contents = new ItemStack[]{item(Material.FILLED_MAP, 64), bundle(item(Material.FILLED_MAP, 2))};
    when(inventory.getContents()).thenReturn(contents);
    assertEquals(66, ContainerScanner.countMapartItems(box, 8));
  }
  @Test void rejectsMixedCargoMalformedStacksAndExcessiveDepth() {
    assertThrows(IllegalArgumentException.class, () -> ContainerScanner.countMapartItems(bundle(item(Material.FILLED_MAP, 1), item(Material.RAW_GOLD, 1)), 8));
    assertThrows(IllegalArgumentException.class, () -> ContainerScanner.countMapartItems(item(Material.FILLED_MAP, -1), 8));
    assertThrows(IllegalArgumentException.class, () -> ContainerScanner.countMapartItems(bundle(bundle(item(Material.FILLED_MAP, 1))), 1));
    assertEquals(0, ContainerScanner.countMapartItems(bundle(), 8));
  }
}
