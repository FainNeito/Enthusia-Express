package io.enthusia.express.infrastructure.payment

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/** A receipt retains the original refund route while database work is asynchronous. */
fun interface PaymentReceipt {
    /** Refund the original payment route once and report whether compensation succeeded. */
    fun refund(): Boolean
}

enum class PaymentSource { PHYSICAL, CURRENCY }

data class ChargeResult(val receipt: PaymentReceipt?, val balance: Double = 0.0,
                        val unavailable: Boolean = false, val source: PaymentSource = PaymentSource.PHYSICAL)

class ShippingPayments(private val plugin: JavaPlugin) {
    /** Select the configured postage provider and refuse incomplete installed-currency integrations. */
    fun charge(player: Player, cost: Int): ChargeResult {
        require(cost >= 0)
        if (cost == 0) return ChargeResult(PaymentReceipt { true })
        val mode = plugin.config.getString("payments.provider", "auto")
        if (mode == "physical") return chargePhysical(player, cost)
        val manager = Bukkit.getPluginManager()
        val currency = manager.getPlugin("EnthusiaCurrency")
        if (currency == null && mode == "auto") return chargePhysical(player, cost)
        if (currency == null || !currency.isEnabled || !manager.isPluginEnabled("Vault")) {
            return ChargeResult(null, unavailable = true)
        }
        // Keep optional Vault types in a separate class, loaded only when Vault is available.
        return VaultShippingPayments.charge(player, cost, currency, plugin.logger)
    }

    /** Check physical Raw Gold, withdraw the fee and retain a receipt for compensation. */
    private fun chargePhysical(player: Player, cost: Int): ChargeResult {
        val contents = player.inventory.storageContents
        val balance = contents.filterNotNull().filter { it.type == Material.RAW_GOLD }.sumOf { it.amount }
        if (balance < cost) return ChargeResult(null, balance.toDouble())
        withdrawPhysical(contents, cost)
        player.inventory.storageContents = contents
        return physicalReceipt(player, cost)
    }

    /** Remove exactly the checked Raw Gold fee across storage slots. */
    private fun withdrawPhysical(contents: Array<ItemStack?>, cost: Int) {
        var remaining = cost
        for (i in contents.indices) {
            if (remaining == 0) break
            val item = contents[i] ?: continue
            if (item.type != Material.RAW_GOLD) continue
            val taken = minOf(item.amount, remaining)
            item.amount -= taken
            if (item.amount == 0) contents[i] = null
            remaining -= taken
        }
    }

    /** Create an idempotent refund that restores physical gold to the current player account. */
    private fun physicalReceipt(player: Player, cost: Int): ChargeResult {
        var refunded = false
        return ChargeResult(PaymentReceipt {
            if (!refunded) {
                val current = Bukkit.getPlayer(player.uniqueId) ?: player
                var refund = cost
                while (refund > 0) {
                    val amount = minOf(64, refund)
                    current.inventory.addItem(ItemStack(Material.RAW_GOLD, amount)).values.forEach {
                        current.world.dropItemNaturally(current.location, it)
                    }
                    refund -= amount
                }
                if (!current.isOnline) current.saveData()
                refunded = true
            }
            true
        })
    }
}
