package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.payment.PaymentReceipt
import io.enthusia.express.infrastructure.payment.ShippingPayments
import io.enthusia.express.infrastructure.util.ContainerScanner
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import io.enthusia.express.infrastructure.util.Text
import java.util.UUID
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class ShippingService @JvmOverloads constructor(
    private val plugin: JavaPlugin,
    private val repository: MailStore,
    private val combatHook: CombatLogXHook,
    private val main: MainThread,
    private val sounds: SoundFeedback = SoundFeedback(plugin),
) {
    private val payments = ShippingPayments(plugin)
    private val placeholderKey = NamespacedKey(plugin, "shipping-placeholder")
    private val inventories = HashMap<UUID, Inventory>()
    private val pending = HashSet<UUID>()
    private val targets = HashMap<UUID, UUID>()

    fun open(sender: Player, target: OfflinePlayer) {
        if (!sender.hasPermission("enthusiaexpress.use") || !sender.hasPermission("enthusiaexpress.packages.send")) {
            sender.sendMessage(Text.msg(plugin.config, "no-permission"))
            return
        }
        if (pending.contains(sender.uniqueId)) {
            sender.sendMessage("§eYour shipment is still being saved.")
            return
        }
        if (!combatHook.mayUseMail(sender)) {
            sender.sendMessage(Text.msg(plugin.config, if (combatHook.isAvailable()) "combat-blocked" else "combatlogx-missing"))
            return
        }
        val online = target.player
        if (online != null && online.isOnline) {
            sender.sendMessage(Text.msg(plugin.config, "target-online"))
            return
        }
        sender.closeInventory()
        targets[sender.uniqueId] = target.uniqueId
        val inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + target.name)
        inv.setItem(CANCEL_SLOT, button(Material.BARRIER, "§cCancel"))
        inv.setItem(CONFIRM_SLOT, button(Material.LIME_CONCRETE, "§aConfirm shipment"))
        refreshPlaceholder(inv)
        inventories[sender.uniqueId] = inv
        sender.openInventory(inv)
    }

    fun owns(player: Player, inventory: Inventory?): Boolean = inventory != null && inventories[player.uniqueId] === inventory

    fun defer(player: Player, inventory: Inventory, confirm: Boolean) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!owns(player, inventory) || player.openInventory.topInventory !== inventory) return@Runnable
            if (confirm) confirm(player, inventory) else cancel(player)
        })
    }

    fun cancel(player: Player) { player.closeInventory() }

    fun confirm(sender: Player, inv: Inventory) {
        if (!sender.hasPermission("enthusiaexpress.use") || !sender.hasPermission("enthusiaexpress.packages.send")) {
            sender.sendMessage(Text.msg(plugin.config, "no-permission"))
            return
        }
        if (pending.contains(sender.uniqueId)) {
            sender.sendMessage("§eYour shipment is still being saved.")
            return
        }
        if (!combatHook.mayUseMail(sender)) {
            sender.sendMessage(Text.msg(plugin.config, if (combatHook.isAvailable()) "combat-blocked" else "combatlogx-missing"))
            sender.closeInventory()
            return
        }
        if (!owns(sender, inv)) return
        val targetId = targets[sender.uniqueId] ?: return
        val target = Bukkit.getOfflinePlayer(targetId)
        if (target.isOnline) {
            sender.sendMessage(Text.msg(plugin.config, "target-online"))
            sender.closeInventory()
            return
        }
        val packageItem = inv.getItem(PACKAGE_SLOT)
        if (isPlaceholder(packageItem) || !ContainerScanner.isAllowedShippingContainer(packageItem)) {
            sender.sendMessage(Text.msg(plugin.config, "invalid-container"))
            return
        }
        checkNotNull(packageItem)
        if (packageItem.amount != 1) {
            sender.sendMessage("§cSend one container at a time.")
            return
        }
        val count: Int
        val cost: Int
        try {
            count = ContainerScanner.countPackedItems(packageItem, plugin.config.getInt("mail.max-recursive-container-depth", 8))
            cost = Math.multiplyExact(count, plugin.config.getInt("mail.raw-gold-per-item", 1))
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§cContainer nesting or shipment cost exceeds the configured limits.")
            return
        } catch (e: ArithmeticException) {
            sender.sendMessage("§cContainer nesting or shipment cost exceeds the configured limits.")
            return
        }
        if (count <= 0) {
            sender.sendMessage(Text.msg(plugin.config, "empty-container"))
            return
        }
        // Copy and encode on the primary thread before database work.
        val payloadItem = packageItem.clone()
        val payload: ByteArray
        try {
            payload = ItemCodec.encode(payloadItem)
        } catch (e: RuntimeException) {
            sender.sendMessage("§cCould not encode that container.")
            return
        }
        val payment = payments.charge(sender, cost)
        val receipt = payment.receipt
        if (receipt == null) {
            sender.sendMessage(Text.msg(plugin.config,
                if (payment.unavailable) "payment-unavailable" else "insufficient-gold",
                mapOf("cost" to cost.toString(), "have" to payment.balance.toLong().toString())))
            return
        }
        inv.setItem(PACKAGE_SLOT, null)
        sender.closeInventory()
        targets.remove(sender.uniqueId)
        val targetName = target.name ?: targetId.toString()
        pending.add(sender.uniqueId)
        main.complete(repository.insertMailLimited(sender.uniqueId, sender.name, targetId, targetName,
            MailType.PACKAGE, payload, count, plugin.config.getBoolean("mail.limits.one-outstanding-package-per-recipient", false))) { result, error ->
            pending.remove(sender.uniqueId)
            if (error != null) {
                if (refundPlayer(sender, payloadItem, receipt))
                    sender.sendMessage("§cShipment failed; your package and fee were refunded.")
                plugin.logger.severe("Package insert failed: " + error.message)
            } else if (result!!.isEmpty) {
                if (refundPlayer(sender, payloadItem, receipt))
                    sender.sendMessage(Text.msg(plugin.config, "outstanding-package"))
            } else {
                sounds.play(sender, SoundFeedback.Cue.PACKAGE_SEND)
                sender.sendMessage(Text.msg(plugin.config, "package-sent", mapOf("target" to targetName, "cost" to cost.toString(), "items" to count.toString())))
            }
        }
    }

    fun returnPackageOnClose(player: Player, inv: Inventory) {
        if (!owns(player, inv)) return
        val stack = inv.getItem(PACKAGE_SLOT)
        inv.setItem(PACKAGE_SLOT, null)
        if (stack != null && !isPlaceholder(stack) && !stack.type.isAir) {
            player.inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
        targets.remove(player.uniqueId)
        inventories.remove(player.uniqueId)
    }

    fun isPlaceholder(item: ItemStack?): Boolean = item?.type == Material.GRAY_STAINED_GLASS_PANE &&
        item.itemMeta?.persistentDataContainer?.has(placeholderKey, PersistentDataType.BYTE) == true

    fun refreshPlaceholder(inventory: Inventory) {
        val current = inventory.getItem(PACKAGE_SLOT)
        if (current != null && !current.type.isAir) return
        val marker = button(Material.GRAY_STAINED_GLASS_PANE, "§7Place package here")
        val meta = marker.itemMeta!!
        meta.lore = listOf("§7Accepted: Shulker Boxes and Bundles")
        meta.persistentDataContainer.set(placeholderKey, PersistentDataType.BYTE, 1.toByte())
        marker.itemMeta = meta
        inventory.setItem(PACKAGE_SLOT, marker)
    }

    fun deferPlaceholderRefresh(player: Player, inventory: Inventory) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (owns(player, inventory)) refreshPlaceholder(inventory)
        })
    }

    fun deferPlaceholderDeposit(player: Player, inventory: Inventory) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline || !owns(player, inventory) || player.openInventory.topInventory !== inventory ||
                !isPlaceholder(inventory.getItem(PACKAGE_SLOT))) return@Runnable
            val cursor: ItemStack? = player.itemOnCursor
            if (cursor == null || cursor.type.isAir || isPlaceholder(cursor)) return@Runnable
            val cargo = cursor.clone()
            player.setItemOnCursor(null)
            inventory.setItem(PACKAGE_SLOT, cargo)
        })
    }

    private fun refundPlayer(sender: Player, payloadItem: ItemStack, receipt: PaymentReceipt): Boolean {
        val target = Bukkit.getPlayer(sender.uniqueId) ?: sender
        give(target, payloadItem)
        if (!target.isOnline) target.saveData()
        val refunded = receipt.refund()
        if (!refunded) {
            target.sendMessage("§cYour package was returned, but the fee refund failed. Contact an administrator.")
            plugin.logger.severe("Postage refund requires administrator action for ${sender.uniqueId}")
        }
        return refunded
    }

    fun shutdown() {
        for (player in Bukkit.getOnlinePlayers()) if (inventories.containsKey(player.uniqueId)) player.closeInventory()
    }

    companion object {
        const val TITLE_PREFIX = "Enthusia Express: Ship to "
        const val PACKAGE_SLOT = 13
        const val CONFIRM_SLOT = 15
        const val CANCEL_SLOT = 11

        private fun give(player: Player, item: ItemStack) {
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }

        private fun button(material: Material, name: String): ItemStack {
            val stack = ItemStack(material)
            val meta = stack.itemMeta!!
            meta.setDisplayName(name)
            stack.itemMeta = meta
            return stack
        }

    }
}
