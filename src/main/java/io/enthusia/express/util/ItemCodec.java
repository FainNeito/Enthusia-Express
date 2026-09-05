package io.enthusia.express.util;

import org.bukkit.inventory.ItemStack;

public final class ItemCodec {
  private ItemCodec() {}

  public static byte[] encode(ItemStack stack) {
    return stack.serializeAsBytes();
  }

  public static ItemStack decode(byte[] data) {
    return ItemStack.deserializeBytes(data);
  }
}
