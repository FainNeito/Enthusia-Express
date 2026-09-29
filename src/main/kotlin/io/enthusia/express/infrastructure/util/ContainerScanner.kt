// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.util

import java.util.ArrayDeque
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta

object ContainerScanner {
    /** Validate museum cargo without unpacking or rewriting the original payload. */
    @JvmStatic
    fun countMapartItems(stack: ItemStack?, maxDepth: Int): Int {
        if (stack == null || stack.type.isAir) return 0
        val pending = ArrayDeque<Pair<ItemStack, Int>>()
        pending.add(stack to 0)
        var visited = 0
        var count = 0
        while (pending.isNotEmpty()) {
            require(++visited <= 8192) { "Mapart traversal exceeds safe work budget" }
            val (item, depth) = pending.removeLast()
            if (item.type.isAir) continue
            if (item.type.name == "FILLED_MAP") {
                require(item.amount in 1..64) { "Invalid map stack quantity" }
                count = Math.addExact(count, item.amount)
            } else {
                require(isAllowedShippingContainer(item) && item.amount == 1) { "Only filled maps and single containers may be submitted" }
                val children = contents(item, depth, maxOf(1, maxDepth))
                while (children.hasNext()) {
                    val child = children.next() ?: continue
                    require(pending.size + visited < 8192) { "Mapart traversal exceeds safe work budget" }
                    pending.add(child to depth + 1)
                }
            }
        }
        return count
    }
    /** Accept nonempty shulker-box or bundle item types as shipment containers. */
    @JvmStatic
    fun isAllowedShippingContainer(stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir) return false
        return stack.type.name.endsWith("SHULKER_BOX") || stack.itemMeta is BundleMeta
    }

    /** Count nested contents iteratively with checked arithmetic and a bounded nesting depth. */
    @JvmStatic
    fun countPackedItems(stack: ItemStack?, maxDepth: Int): Int {
        if (stack == null) return 0
        val limit = maxOf(1, maxDepth)
        val frames = ArrayDeque<Frame>()
        frames.push(Frame(contents(stack, 0, limit), 0, 0))
        var visited = 0
        while (frames.isNotEmpty()) {
            require(++visited <= 8192) { "Container traversal exceeds safe work budget" }
            val frame = frames.peek()
            if (frame.children.hasNext()) {
                visitChild(frames, limit)
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

    /** Advance one explicit traversal frame without recursion. */
    private fun visitChild(frames: ArrayDeque<Frame>, limit: Int) {
        val frame = frames.peek()
        val child = frame.children.next() ?: return
        if (child.type.isAir) return
        val amount = child.amount
        if (isAllowedShippingContainer(child)) {
            val depth = frame.depth + 1
            frames.push(Frame(contents(child, depth, limit), depth, amount))
        } else {
            frame.total = Math.addExact(frame.total, amount)
        }
    }

    /** Read a container child iterator while enforcing the nesting-depth boundary. */
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
