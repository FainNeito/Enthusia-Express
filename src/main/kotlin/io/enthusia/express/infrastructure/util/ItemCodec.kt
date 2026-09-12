package io.enthusia.express.infrastructure.util

import org.bukkit.inventory.ItemStack

object ItemCodec {
    /** Serialize an item snapshot with Paper item serialization for persistence. */
    @JvmStatic
    fun encode(stack: ItemStack): ByteArray = stack.serializeAsBytes()

    /** Restore a stored item using Paper serialization, propagating invalid-payload failures. */
    @JvmStatic
    fun decode(data: ByteArray): ItemStack = ItemStack.deserializeBytes(data)
}
