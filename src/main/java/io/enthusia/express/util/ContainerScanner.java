package io.enthusia.express.util;

import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;

public final class ContainerScanner {
  private ContainerScanner() {}

  public static boolean isAllowedShippingContainer(ItemStack stack) {
    if (stack == null || stack.getType().isAir()) return false;
    return stack.getType().name().endsWith("SHULKER_BOX")
        || stack.getItemMeta() instanceof BundleMeta;
  }

  public static int countPackedItems(ItemStack stack, int maxDepth) {
    return countContents(stack, 0, Math.max(1, maxDepth));
  }

  private static int countContents(ItemStack container, int depth, int maxDepth) {
    if (container == null) return 0;
    if (depth >= maxDepth)
      throw new IllegalArgumentException("Container nesting exceeds maximum depth");
    ItemMeta meta = container.getItemMeta();
    int total = 0;
    if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
      for (ItemStack child : shulker.getInventory().getContents()) {
        total = Math.addExact(total, countChild(child, depth + 1, maxDepth));
      }
    } else if (meta instanceof BundleMeta bundle) {
      for (ItemStack child : bundle.getItems()) {
        total = Math.addExact(total, countChild(child, depth + 1, maxDepth));
      }
    }
    return total;
  }

  private static int countChild(ItemStack child, int depth, int maxDepth) {
    if (child == null || child.getType().isAir()) return 0;
    int own = child.getAmount();
    if (isAllowedShippingContainer(child)) {
      // Nested contents are counted in addition to the physical nested container item(s).
      return Math.addExact(own, Math.multiplyExact(own, countContents(child, depth, maxDepth)));
    }
    return own;
  }
}
