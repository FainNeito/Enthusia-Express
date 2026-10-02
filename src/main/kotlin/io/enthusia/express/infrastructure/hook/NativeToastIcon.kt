package io.enthusia.express.infrastructure.hook

import org.bukkit.inventory.ItemStack

/** Paper 26 uses immutable templates for advancement icons; older servers use native stacks. */
internal class NativeToastIcon(craftItems: Class<*>) {
    private val conversion = try { craftItems.getMethod("asTemplate", ItemStack::class.java) }
        catch (_: NoSuchMethodException) { craftItems.getMethod("asNMSCopy", ItemStack::class.java) }

    val type: Class<*> = conversion.returnType

    /** Use the same representation selected for the native display constructor. */
    fun convert(item: ItemStack): Any = conversion.invoke(null, item)
}
