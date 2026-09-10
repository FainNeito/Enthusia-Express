// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

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

// This lifecycle owner keeps inventory identity, pending payments and close compensation together.
@Suppress("TooManyFunctions")
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

    /** Centralize permission, in-flight-send and combat checks before accepting shipping actions. */
    private fun validateShippingAccess(sender: Player, closeBlocked: Boolean): Boolean {
        if (!sender.hasPermission("enthusiaexpress.use") || !sender.hasPermission("enthusiaexpress.packages.send")) {
            sender.sendMessage(Text.msg(plugin.config, "no-permission"))
            return false
        }
        if (pending.contains(sender.uniqueId)) {
            sender.sendMessage("§eYour shipment is still being saved.")
            return false
        }
        if (!combatHook.mayUseMail(sender)) {
            sender.sendMessage(Text.msg(plugin.config, if (combatHook.isAvailable()) "combat-blocked" else "combatlogx-missing"))
            if (closeBlocked) sender.closeInventory()
            return false
        }
        return true
    }

    /** Create the sender-owned cargo menu for an offline recipient. */
    fun open(sender: Player, target: OfflinePlayer) {
        if (!validateShippingAccess(sender, false)) return
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

    /** Compare the exact inventory instance with the sender-owned shipping menu. */
    fun owns(player: Player, inventory: Inventory?): Boolean = inventory != null && inventories[player.uniqueId] === inventory

    /** Move confirm or cancel handling out of the click event and reject stale inventories. */
    fun defer(player: Player, inventory: Inventory, confirm: Boolean) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (owns(player, inventory) && player.openInventory.topInventory === inventory) {
                if (confirm) confirm(player, inventory) else cancel(player)
            }
        })
    }

    /** Close the shipping menu so its close handler returns any cargo. */
    fun cancel(player: Player) { player.closeInventory() }

    /** Revalidate sender and recipient state, then prepare and submit the current cargo. */
    fun confirm(sender: Player, inv: Inventory) {
        if (!validateShippingAccess(sender, true)) return
        if (!owns(sender, inv)) return
        val targetId = targets[sender.uniqueId] ?: return
        val target = Bukkit.getOfflinePlayer(targetId)
        if (target.isOnline) {
            sender.sendMessage(Text.msg(plugin.config, "target-online"))
            sender.closeInventory()
            return
        }
        val shipment = prepareShipment(sender, inv) ?: return
        chargeAndSubmit(sender, inv, target, shipment)
    }

    private data class PreparedShipment(val payloadItem: ItemStack, val payload: ByteArray, val count: Int, val cost: Int)

    /** Validate nesting and payload limits before calculating postage or removing items. */
    // Item serializers are supplied by Paper and may reject malformed/version-specific metadata.
    @Suppress("TooGenericExceptionCaught")
    private fun prepareShipment(sender: Player, inv: Inventory): PreparedShipment? {
        val packageItem = inv.getItem(PACKAGE_SLOT)
        if (isPlaceholder(packageItem) || !ContainerScanner.isAllowedShippingContainer(packageItem)) {
            sender.sendMessage(Text.msg(plugin.config, "invalid-container"))
            return null
        }
        checkNotNull(packageItem)
        if (packageItem.amount != 1) {
            sender.sendMessage("§cSend one container at a time.")
            return null
        }
        val count: Int
        val cost: Int
        try {
            count = ContainerScanner.countPackedItems(packageItem, plugin.config.getInt("mail.max-recursive-container-depth", 8))
            cost = Math.multiplyExact(count, plugin.config.getInt("mail.raw-gold-per-item", 1))
        } catch (e: IllegalArgumentException) {
            sender.sendMessage("§cContainer nesting or shipment cost exceeds the configured limits.")
            return null
        } catch (e: ArithmeticException) {
            sender.sendMessage("§cContainer nesting or shipment cost exceeds the configured limits.")
            return null
        }
        if (count <= 0) {
            sender.sendMessage(Text.msg(plugin.config, "empty-container"))
            return null
        }
        // Copy and encode on the primary thread before database work.
        val payloadItem = packageItem.clone()
        val payload: ByteArray
        try {
            payload = ItemCodec.encode(payloadItem)
        } catch (e: RuntimeException) {
            plugin.logger.log(java.util.logging.Level.WARNING, "Cannot serialize package for ${sender.uniqueId}", e)
            sender.sendMessage("§cCould not encode that container.")
            return null
        }
        return PreparedShipment(payloadItem, payload, count, cost)
    }

    /** Charge one payment route, reserve cargo and compensate rejected or failed persistence. */
    private fun chargeAndSubmit(sender: Player, inv: Inventory, target: OfflinePlayer, shipment: PreparedShipment) {
        val payment = payments.charge(sender, shipment.cost)
        val receipt = payment.receipt
        if (receipt == null) {
            sender.sendMessage(Text.msg(plugin.config,
                paymentFailureMessage(payment),
                mapOf("cost" to shipment.cost.toString(), "have" to java.math.BigDecimal.valueOf(payment.balance).stripTrailingZeros().toPlainString())))
            return
        }
        inv.setItem(PACKAGE_SLOT, null)
        sender.closeInventory()
        targets.remove(sender.uniqueId)
        val targetName = target.name ?: target.uniqueId.toString()
        pending.add(sender.uniqueId)
        main.complete(repository.insertMailLimited(sender.uniqueId, sender.name, target.uniqueId, targetName,
            MailType.PACKAGE, shipment.payload, shipment.count, plugin.config.getBoolean("mail.limits.one-outstanding-package-per-recipient", false))) { result, error ->
            pending.remove(sender.uniqueId)
            if (error != null) {
                if (refundPlayer(sender, shipment.payloadItem, receipt))
                    sender.sendMessage("§cShipment failed; your package and fee were refunded.")
                plugin.logger.severe("Package insert failed: " + error.message)
            } else if (result!!.isEmpty) {
                if (refundPlayer(sender, shipment.payloadItem, receipt))
                    sender.sendMessage(Text.msg(plugin.config, "outstanding-package"))
            } else {
                sounds.play(sender, SoundFeedback.Cue.PACKAGE_SEND)
                sender.sendMessage(Text.msg(plugin.config, "package-sent", mapOf("target" to targetName, "cost" to shipment.cost.toString(), "items" to shipment.count.toString())))
            }
        }
    }

    /** Choose unavailable, physical-gold or currency messaging from the actual payment result. */
    private fun paymentFailureMessage(payment: io.enthusia.express.infrastructure.payment.ChargeResult): String = when {
        payment.unavailable -> "payment-unavailable"
        payment.source == io.enthusia.express.infrastructure.payment.PaymentSource.CURRENCY -> "insufficient-currency"
        else -> "insufficient-gold"
    }

    /** Remove and return cargo during the close event, never returning the placement marker. */
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

    /** Recognize the tagged gray placement marker instead of ordinary player cargo. */
    fun isPlaceholder(item: ItemStack?): Boolean = item?.type == Material.GRAY_STAINED_GLASS_PANE &&
        item.itemMeta?.persistentDataContainer?.has(placeholderKey, PersistentDataType.BYTE) == true

    /** Show placement guidance only when the cargo slot is empty. */
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

    /** Refresh placement guidance next tick only for the same shipping session. */
    fun deferPlaceholderRefresh(player: Player, inventory: Inventory) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (owns(player, inventory)) refreshPlaceholder(inventory)
        })
    }

    /** Defer cursor-to-cargo placement until the inventory click event has completed. */
    fun deferPlaceholderDeposit(player: Player, inventory: Inventory) {
        Bukkit.getScheduler().runTask(plugin, Runnable { depositCursor(player, inventory) })
    }

    /** Transfer a nonempty cursor into the marker slot only while the same menu still owns it. */
    private fun depositCursor(player: Player, inventory: Inventory) {
        if (!player.isOnline || !owns(player, inventory)) return
        if (player.openInventory.topInventory !== inventory || !isPlaceholder(inventory.getItem(PACKAGE_SLOT))) return
        val cursor: ItemStack? = player.itemOnCursor
        if (cursor == null || cursor.type.isAir || isPlaceholder(cursor)) return
        val cargo = cursor.clone()
        player.setItemOnCursor(null)
        inventory.setItem(PACKAGE_SLOT, cargo)
    }

    /** Return cargo and refund its original payment receipt, reporting refused refunds to the operator. */
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

    /** Close owned shipping menus so cargo returns before pending persistence callbacks drain. */
    fun shutdown() {
        for (player in Bukkit.getOnlinePlayers()) if (inventories.containsKey(player.uniqueId)) player.closeInventory()
    }

    companion object {
        const val TITLE_PREFIX = "Enthusia Express: Ship to "
        const val PACKAGE_SLOT = 13
        const val CONFIRM_SLOT = 15
        const val CANCEL_SLOT = 11

        /** Return an item to player storage and drop only inventory overflow. */
        private fun give(player: Player, item: ItemStack) {
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }

        /** Create a named decorative shipping control. */
        private fun button(material: Material, name: String): ItemStack {
            val stack = ItemStack(material)
            val meta = stack.itemMeta!!
            meta.setDisplayName(name)
            stack.itemMeta = meta
            return stack
        }

    }
}
