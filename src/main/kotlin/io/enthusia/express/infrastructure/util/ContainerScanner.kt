package io.enthusia.express.infrastructure.util

import java.util.ArrayDeque
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta

object ContainerScanner {
    @JvmStatic
    fun isAllowedShippingContainer(stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir) return false
        return stack.type.name.endsWith("SHULKER_BOX") || stack.itemMeta is BundleMeta
    }

    @JvmStatic
    fun countPackedItems(stack: ItemStack?, maxDepth: Int): Int {
        if (stack == null) return 0
        val limit = maxOf(1, maxDepth)
        val frames = ArrayDeque<Frame>()
        frames.push(Frame(contents(stack, 0, limit), 0, 0))
        while (frames.isNotEmpty()) {
            val frame = frames.peek()
            if (frame.children.hasNext()) {
                val child = frame.children.next() ?: continue
                if (child.type.isAir) continue
                val amount = child.amount
                if (isAllowedShippingContainer(child)) {
                    val depth = frame.depth + 1
                    frames.push(Frame(contents(child, depth, limit), depth, amount))
                } else {
                    frame.total = Math.addExact(frame.total, amount)
                }
            } else {
                frames.pop()
                if (frames.isEmpty()) return frame.total
                val subtotal = Math.addExact(frame.amount, Math.multiplyExact(frame.amount, frame.total))
                val parent = frames.peek()
                parent.total = Math.addExact(parent.total, subtotal)
            }
        }
        return 0
    }

    private fun contents(container: ItemStack, depth: Int, maxDepth: Int): Iterator<ItemStack?> {
        require(depth < maxDepth) { "Container nesting exceeds maximum depth" }
        val meta = container.itemMeta
        if (meta is BlockStateMeta) {
            val state = meta.blockState
            if (state is ShulkerBox) return state.inventory.contents.iterator()
        } else if (meta is BundleMeta) {
            return meta.items.iterator()
        }
        return emptyList<ItemStack?>().iterator()
    }

    private class Frame(val children: Iterator<ItemStack?>, val depth: Int, val amount: Int) {
        var total: Int = 0
    }
}
