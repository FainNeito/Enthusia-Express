package io.enthusia.express.infrastructure.payment

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin

/** A receipt retains the original refund route while database work is asynchronous. */
fun interface PaymentReceipt {
    fun refund(): Boolean
}

data class ChargeResult(val receipt: PaymentReceipt?, val balance: Double = 0.0, val unavailable: Boolean = false)

class ShippingPayments(private val plugin: JavaPlugin) {
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

    private fun chargePhysical(player: Player, cost: Int): ChargeResult {
        val contents = player.inventory.storageContents
        val balance = contents.filterNotNull().filter { it.type == Material.RAW_GOLD }.sumOf { it.amount }
        if (balance < cost) return ChargeResult(null, balance.toDouble())
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
        player.inventory.storageContents = contents
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
