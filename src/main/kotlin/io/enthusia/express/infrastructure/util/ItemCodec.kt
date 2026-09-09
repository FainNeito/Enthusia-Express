package io.enthusia.express.infrastructure.util

import org.bukkit.inventory.ItemStack

object ItemCodec {
    @JvmStatic
    fun encode(stack: ItemStack): ByteArray = stack.serializeAsBytes()

    @JvmStatic
    fun decode(data: ByteArray): ItemStack = ItemStack.deserializeBytes(data)
}
