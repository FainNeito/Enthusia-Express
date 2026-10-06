package io.enthusia.express.infrastructure.api

import io.enthusia.express.infrastructure.util.ContainerScanner
import io.enthusia.express.infrastructure.util.ItemCodec
import org.bukkit.Material
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta

/** Wraps event items into one shipping container. */
fun interface EventPackager {
    /** Packed payload and its recursive item count. */
    class Packed(@get:JvmName("payload") val payload: ByteArray, @get:JvmName("packedCount") val packedCount: Int)

    /** Pack [items] for [senderName]; throws IllegalArgumentException for cargo the mailbox could not deliver. */
    fun pack(items: List<ItemStack>, senderName: String): Packed
}

/** Production packager: a shulker box labelled with the sender, validated by the shipping scanner. */
class ShulkerEventPackager(private val material: Material, private val maxDepth: Int) : EventPackager {
    init {
        require(material.name.endsWith("SHULKER_BOX")) { "Event package material must be a shulker box" }
    }

    override fun pack(items: List<ItemStack>, senderName: String): EventPackager.Packed {
        val box = ItemStack(material)
        box.editMeta(BlockStateMeta::class.java) { meta ->
            val state = meta.blockState as ShulkerBox
            state.inventory.contents = items.map { it.clone() }.toTypedArray()
            meta.blockState = state
            meta.displayName(net.kyori.adventure.text.Component.text("Package from $senderName"))
        }
        require(ContainerScanner.isAllowedShippingContainer(box)) { "Event package is not a shipping container" }
        val count = ContainerScanner.countPackedItems(box, maxDepth)
        return EventPackager.Packed(ItemCodec.encode(box), count)
    }
}
