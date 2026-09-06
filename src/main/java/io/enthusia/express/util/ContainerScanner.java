package io.enthusia.express.util;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
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
    if (stack == null) return 0;
    int limit = Math.max(1, maxDepth);
    ArrayDeque<Frame> frames = new ArrayDeque<>();
    frames.push(new Frame(contents(stack, 0, limit), 0, 0));
    while (!frames.isEmpty()) {
      Frame frame = frames.peek();
      if (frame.children.hasNext()) {
        ItemStack child = frame.children.next();
        if (child == null || child.getType().isAir()) continue;
        int amount = child.getAmount();
        if (isAllowedShippingContainer(child)) {
          int depth = frame.depth + 1;
          frames.push(new Frame(contents(child, depth, limit), depth, amount));
        } else {
          frame.total = Math.addExact(frame.total, amount);
        }
      } else {
        frames.pop();
        if (frames.isEmpty()) return frame.total;
        // Include the nested container itself and every copy of its contents.
        int subtotal = Math.addExact(frame.amount, Math.multiplyExact(frame.amount, frame.total));
        Frame parent = frames.peek();
        parent.total = Math.addExact(parent.total, subtotal);
      }
    }
    return 0;
  }

  private static Iterator<ItemStack> contents(ItemStack container, int depth, int maxDepth) {
    if (depth >= maxDepth)
      throw new IllegalArgumentException("Container nesting exceeds maximum depth");
    ItemMeta meta = container.getItemMeta();
    if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
      return Arrays.asList(shulker.getInventory().getContents()).iterator();
    } else if (meta instanceof BundleMeta bundle) {
      return bundle.getItems().iterator();
    }
    return Collections.emptyIterator();
  }

  private static final class Frame {
    final Iterator<ItemStack> children;
    final int depth;
    final int amount;
    int total;

    Frame(Iterator<ItemStack> children, int depth, int amount) {
      this.children = children;
      this.depth = depth;
      this.amount = amount;
    }
  }
}
