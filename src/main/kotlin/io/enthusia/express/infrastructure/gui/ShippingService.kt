// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MailBlockedException
import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailType
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.hook.MovementLease
import io.enthusia.express.infrastructure.hook.MovementLocks
import io.enthusia.express.infrastructure.payment.PaymentReceipt
import io.enthusia.express.infrastructure.payment.ShippingPayments
import io.enthusia.express.infrastructure.util.ContainerScanner
import io.enthusia.express.infrastructure.util.ItemCodec
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import io.enthusia.express.infrastructure.util.Text
import java.util.UUID
import org.bukkit.Bukkit
import io.enthusia.express.infrastructure.payment.ShippingRecovery
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
    private val movementLocks: MovementLocks = MovementLocks.NOOP,
) {
    private val TARGET_ONLINE = "target-online"
    private val theme = GuiTheme(plugin)
    private val payments = ShippingPayments(plugin)
    private val recovery by lazy { ShippingRecovery(plugin.dataFolder.toPath().resolve("shipping-recovery"), plugin.logger) }
    private val refundReceipts = HashMap<UUID, PaymentReceipt>()
    private data class ReservedPayment(val receipt: PaymentReceipt, val unit: String,
        val lease: MovementLease, val intent: ShippingRecovery.Record)
    private val placeholderKey = NamespacedKey(plugin, "shipping-placeholder")
    private val inventories = HashMap<UUID, Inventory>()
    private data class Quote(val payload: ByteArray, val count: Int, val cost: Int, val unit: String)
    private val quotes = HashMap<UUID, Quote>()
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
            sender.sendMessage(Text.msg(plugin.config, TARGET_ONLINE))
            return
        }
        sender.closeInventory()
        targets[sender.uniqueId] = target.uniqueId
        val inv = Bukkit.createInventory(null, 27, theme.title("shipping", TITLE_PREFIX))
        inv.setItem(CANCEL_SLOT, button("shipping.cancel", Material.BARRIER, "§cCancel"))
        quotes.remove(sender.uniqueId)
        inv.setItem(4, button("shipping.recipient", Material.PAPER, "§7To: ${target.name ?: "recipient"}"))
        inv.setItem(CONFIRM_SLOT, button("shipping.quote", Material.LIME_CONCRETE, "§eView postage"))
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
            sender.sendMessage(Text.msg(plugin.config, TARGET_ONLINE))
            sender.closeInventory()
            return
        }
        val shipment = prepareShipment(sender, inv) ?: return
        if (confirmQuote(sender, inv, shipment)) {
            pending.add(sender.uniqueId)
            main.complete(repository.isBlocked(target.uniqueId, sender.uniqueId)) { blocked, error ->
                checkedSend(sender, inv, target, blocked, error)
            }
        }
    }

    /** Require a second click on the same cargo and price before taking payment. */
    private fun confirmQuote(sender: Player, inv: Inventory, shipment: PreparedShipment): Boolean {
        val unit = payments.priceUnit()
        val previous = quotes[sender.uniqueId]
        val unchanged = previous?.payload?.contentEquals(shipment.payload) == true &&
            previous.count == shipment.count && previous.cost == shipment.cost
        if (unchanged && previous?.unit == unit) {
            return true
        }
        quotes[sender.uniqueId] = Quote(shipment.payload.copyOf(), shipment.count, shipment.cost, unit)
        val control = button("shipping.confirm", Material.LIME_CONCRETE, "§aSend package")
        val meta = control.itemMeta!!
        meta.lore = listOf("§eCost: ${shipment.cost} $unit", "§7${shipment.count} packed items", "§aClick again to send")
        control.itemMeta = meta
        inv.setItem(CONFIRM_SLOT, control)
        sender.sendMessage(Text.msgOrDefault(plugin.config, "package-quote",
            "&ePostage: {cost} {currency} for {items} packed items. Click Send package to confirm.",
            mapOf("cost" to shipment.cost.toString(), "currency" to unit, "items" to shipment.count.toString())))
        return false
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
        if (payload.size > plugin.config.getInt("mail.max-package-payload-bytes", 262144)) {
            sender.sendMessage(Text.msgOrDefault(plugin.config, "package-too-large", "&cThat package contains too much item data. Split its contents into smaller packages."))
            return null
        }
        return PreparedShipment(payloadItem, payload, count, cost)
    }

    /** Ignore callbacks after disable, disconnect or closing the owned inventory. */
    private fun currentShippingSession(sender: Player, inv: Inventory) =
        plugin.isEnabled && sender.isOnline && Bukkit.getPlayer(sender.uniqueId) === sender && owns(sender, inv)

    /** Recheck the live inventory and quote after the asynchronous block lookup, before charging. */
    private fun checkedSend(sender: Player, inv: Inventory, target: OfflinePlayer, blocked: Boolean?, error: Throwable?) {
        pending.remove(sender.uniqueId)
        if (!currentShippingSession(sender, inv)) return
        if (!validateShippingAccess(sender, true)) return
        if (error != null) {
            sender.sendMessage(Text.msg(plugin.config, "database-error"))
            return
        }
        if (blocked != false) {
            sender.sendMessage(Text.msgOrDefault(plugin.config, "recipient-not-accepting", "&cThat player is not accepting your mail."))
            return
        }
        if (target.isOnline) {
            sender.sendMessage(Text.msg(plugin.config, TARGET_ONLINE))
            return
        }
        val current = prepareShipment(sender, inv) ?: return
        if (confirmQuote(sender, inv, current)) chargeAndSubmit(sender, inv, target, current)
    }

    /** Charge one payment route while holding the same asset lease used by EnthusiaStaff. */
    private fun chargeAndSubmit(sender: Player, inv: Inventory, target: OfflinePlayer, shipment: PreparedShipment) {
        val lease = movementLocks.acquire(sender.uniqueId)
        if (lease == null) {
            sender.sendMessage("§eYour inventory is being used by another server operation. Try again shortly.")
            return
        }
        var handedOff = false
        try {
            val unit = payments.priceUnit()
            val intent = prepareRecovery(sender, target, shipment, unit) ?: return
            quotes.remove(sender.uniqueId)
            // Own the shared movement lease before cargo or currency changes.
            pending.add(sender.uniqueId)
            inv.setItem(PACKAGE_SLOT, null)
            val receipt = takePayment(sender, inv, shipment)
            if (receipt == null) {
                finishRecovery(intent)
                return
            }
            val payment = ReservedPayment(receipt, unit, lease, intent)
            if (!currentShippingSession(sender, inv) || target.isOnline || !eligibleSender(sender)) {
                deferCompensation(sender, payment, "§eShipment cancelled; your cargo and fee will be returned.")
                pending.remove(sender.uniqueId)
                return
            }
            submitReserved(sender, target, shipment, payment)
            handedOff = true
        } finally {
            if (!handedOff) lease.close()
        }
    }

    /** Reserve payment while preserving cargo on rejection and ambiguous provider failures. */
    @Suppress("TooGenericExceptionCaught")
    private fun takePayment(sender: Player, inv: Inventory, shipment: PreparedShipment): PaymentReceipt? {
        val payment = try {
            payments.charge(sender, shipment.cost)
        } catch (failure: RuntimeException) {
            returnReservedCargo(sender, inv, shipment.payloadItem)
            pending.remove(sender.uniqueId)
            plugin.logger.log(java.util.logging.Level.SEVERE, "Payment outcome uncertain for ${sender.uniqueId}; reconcile provider before refunding postage", failure)
            sender.sendMessage("§cPayment failed. Your package was returned; contact an administrator to check the fee.")
            return null
        }
        val receipt = payment.receipt
        if (receipt == null) {
            returnReservedCargo(sender, inv, shipment.payloadItem)
            pending.remove(sender.uniqueId)
            if (payment.reconciliationId != null) {
                sender.sendMessage("§cPayment outcome is uncertain. Your cargo was returned; ask an administrator to reconcile fee reference ${payment.reconciliationId}.")
                return null
            }
            sender.sendMessage(Text.msg(plugin.config,
                paymentFailureMessage(payment),
                mapOf("cost" to shipment.cost.toString(), "have" to java.math.BigDecimal.valueOf(payment.balance).stripTrailingZeros().toPlainString())))
            return null
        }
        return receipt
    }

    /** Recheck permission and combat changes caused by payment listeners. */
    private fun eligibleSender(sender: Player): Boolean = combatHook.mayUseMail(sender) &&
        sender.hasPermission("enthusiaexpress.use") && sender.hasPermission("enthusiaexpress.packages.send")

    /** Hold the movement lease until storage accepts the shipment or compensation is complete. */
    private fun submitReserved(sender: Player, target: OfflinePlayer, shipment: PreparedShipment,
                               payment: ReservedPayment) {
        sender.closeInventory()
        targets.remove(sender.uniqueId)
        val targetName = target.name ?: target.uniqueId.toString()
        pending.add(sender.uniqueId)
        val submission = Submission(sender, targetName, shipment, payment)
        main.complete(repository.insertMailLimited(sender.uniqueId, sender.name, target.uniqueId, targetName,
            MailType.PACKAGE, shipment.payload, shipment.count, plugin.config.getBoolean("mail.limits.one-outstanding-package-per-recipient", false))) { result, error ->
            completeShipment(submission, result, error)
        }
    }

    private data class Submission(val sender: Player, val targetName: String,
                                  val shipment: PreparedShipment, val payment: ReservedPayment)

    /** Resolve persistence before releasing the lease or scheduling durable compensation. */
    private fun completeShipment(submission: Submission, result: java.util.OptionalLong?, error: Throwable?) {
        val (sender, targetName, shipment, payment) = submission
        pending.remove(sender.uniqueId)
        if (error == null && result?.isPresent == true) {
            payment.lease.close()
            finishRecovery(payment.intent)
            sounds.play(sender, SoundFeedback.Cue.PACKAGE_SEND)
            sender.sendMessage(Text.msg(plugin.config, "package-sent", mapOf("target" to targetName,
                "currency" to payment.unit, "cost" to shipment.cost.toString(), "items" to shipment.count.toString())))
            return
        }
        val message = when {
            MailBlockedException.causedBy(error) ->
                Text.msgOrDefault(plugin.config, "recipient-not-accepting", "&cThat player is not accepting your mail.")
            error != null -> "§cShipment failed; your package and fee are queued for recovery."
            else -> Text.msg(plugin.config, "outstanding-package")
        }
        if (error != null && !MailBlockedException.causedBy(error))
            plugin.logger.severe("Package insert failed: " + error.message)
        deferCompensation(sender, payment, message)
    }

    /** Refuse reservation when the recovery intent cannot be forced to disk. */
    private fun prepareRecovery(sender: Player, target: OfflinePlayer, shipment: PreparedShipment,
                                unit: String): ShippingRecovery.Record? = try {
        recovery.prepare(sender.uniqueId, target.uniqueId, shipment.cost, unit, shipment.payload)
    } catch (error: java.io.IOException) {
        plugin.logger.severe("Shipping recovery is unavailable: ${error.message}")
        sender.sendMessage("§cMail recovery storage is unavailable. Nothing was charged.")
        null
    }

    /** Persist a definite failed send before either refunding it or waiting for another lease. */
    private fun deferCompensation(sender: Player, payment: ReservedPayment, message: String) {
        try {
            recovery.transition(payment.intent, ShippingRecovery.Phase.PENDING)
            refundReceipts[payment.intent.id] = payment.receipt
            sender.sendMessage(message)
            if (payment.lease.ensureOwned()) {
                compensate(payment.intent, payment.receipt)
            } else {
                sender.sendMessage("§eYour package and postage are saved for recovery when your account unlocks.")
            }
        } catch (error: java.io.IOException) {
            plugin.logger.severe("Retain shipping recovery ${payment.intent.id}; reconciliation required: ${error.message}")
            sender.sendMessage("§cShipment recovery requires administrator assistance. Reference: ${payment.intent.id}")
        } finally {
            payment.lease.close()
        }
    }

    /** Retry a bounded batch on the server thread; offline accounts wait for their next join. */
    fun retryCompensations() {
        if (!plugin.isEnabled) return
        for (record in recovery.pending()) retryCompensation(record)
    }

    /** Rebuild the original payment route only after acquiring the player's shared movement lease. */
    private fun retryCompensation(record: ShippingRecovery.Record) {
        val player = Bukkit.getPlayer(record.sender)?.takeIf { it.isOnline } ?: return
        val lease = movementLocks.acquire(record.sender) ?: return
        try {
            if (!lease.ensureOwned()) return
            val receipt = refundReceipts[record.id] ?: payments.recoveryReceipt(player, record.cost, record.route) ?: return
            compensate(record, receipt)
        } finally {
            lease.close()
        }
    }

    /** Mark mutation as uncertain before touching assets; never automatically repeat a partial refund. */
    @Suppress("TooGenericExceptionCaught")
    private fun compensate(record: ShippingRecovery.Record, receipt: PaymentReceipt) {
        val current = Bukkit.getPlayer(record.sender)?.takeIf { it.isOnline } ?: return
        try {
            val cargo = ItemCodec.decode(record.cargo)
            // Recovery must not create untracked dropped items when the sender has no room.
            if (current.inventory.firstEmpty() == -1) return
            recovery.transition(record, ShippingRecovery.Phase.APPLYING)
            refundReceipts.remove(record.id)
            val refunded = refundPlayer(current, cargo, receipt)
            current.saveData()
            if (refunded) {
                recovery.finish(record)
                current.sendMessage("§eYour failed shipment's package and postage were returned.")
            }
        } catch (error: Exception) {
            plugin.logger.log(java.util.logging.Level.SEVERE,
                "Shipping recovery ${record.id} failed; inspect its phase before any manual refund", error)
        }
    }

    /** A cleanup error must not cause a second refund or turn a successful send into a failure. */
    private fun finishRecovery(record: ShippingRecovery.Record) {
        try {
            recovery.finish(record)
        } catch (error: java.io.IOException) {
            plugin.logger.severe("Completed shipment recovery ${record.id} remains for reconciliation: ${error.message}")
        }
    }

    /** Return the reservation to its original empty slot or the current player, never both. */
    private fun returnReservedCargo(sender: Player, inv: Inventory, cargo: ItemStack) {
        val current = inv.getItem(PACKAGE_SLOT)
        if (currentShippingSession(sender, inv) && cargoSlotEmpty(current)) {
            inv.setItem(PACKAGE_SLOT, cargo)
        } else {
            val player = Bukkit.getPlayer(sender.uniqueId) ?: sender
            give(player, cargo)
            if (!player.isOnline) player.saveData()
        }
    }

    /** Empty placement guidance does not own player cargo. */
    private fun cargoSlotEmpty(item: ItemStack?): Boolean = item == null || item.type.isAir || isPlaceholder(item)

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
        quotes.remove(player.uniqueId)
    }

    /** Recognize the tagged gray placement marker instead of ordinary player cargo. */
    fun isPlaceholder(item: ItemStack?): Boolean = item?.itemMeta?.persistentDataContainer?.has(placeholderKey, PersistentDataType.BYTE) == true

    /** Show placement guidance only when the cargo slot is empty. */
    fun refreshPlaceholder(inventory: Inventory) {
        val current = inventory.getItem(PACKAGE_SLOT)
        if (current != null && !current.type.isAir) return
        val marker = button("shipping.placeholder", Material.GRAY_STAINED_GLASS_PANE, "§7Place package here")
        val meta = marker.itemMeta!!
        meta.lore = listOf("§7Shulker boxes or bundles", "§7Click View postage for cost")
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

    /** Create a named decorative shipping control. */
    private fun button(key: String, material: Material, name: String): ItemStack {
        val stack = theme.item(key, material)
        val meta = stack.itemMeta!!
        meta.setDisplayName(name)
        stack.itemMeta = meta
        return stack
    }


    companion object {
        const val TITLE_PREFIX = "Send package"
        const val PACKAGE_SLOT = 13
        const val CONFIRM_SLOT = 15
        const val CANCEL_SLOT = 11

        /** Return an item to player storage and drop only inventory overflow. */
        private fun give(player: Player, item: ItemStack) {
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }


    }
}
