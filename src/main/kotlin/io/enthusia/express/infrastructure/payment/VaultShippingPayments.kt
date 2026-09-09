package io.enthusia.express.infrastructure.payment

import java.util.logging.Logger
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** EnthusiaCurrency owns both bank and physical-currency accounting. Never charge items a second time. */
internal object VaultShippingPayments {
    fun charge(player: Player, cost: Int, currency: Plugin, logger: Logger): ChargeResult {
        val economy = Bukkit.getServicesManager().getRegistrations(Economy::class.java)
            .firstOrNull { it.plugin === currency && it.provider.name == "EnthusiaCurrency" && it.provider.isEnabled }
            ?.provider ?: return ChargeResult(null, unavailable = true)
        val account: OfflinePlayer = player
        val response = try {
            economy.withdrawPlayer(account, cost.toDouble())
        } catch (error: RuntimeException) {
            logger.severe("Currency withdrawal failed for ${player.uniqueId}: ${error.message}")
            return ChargeResult(null, unavailable = true)
        }
        if (!response.transactionSuccess()) return ChargeResult(null, response.balance)
        var refunded = false
        return ChargeResult(PaymentReceipt {
            if (refunded) true else {
                val successful = try {
                    currency.isEnabled && economy.isEnabled &&
                        economy.depositPlayer(account, cost.toDouble()).transactionSuccess()
                } catch (error: RuntimeException) {
                    logger.severe("Currency refund failed for ${player.uniqueId}: ${error.message}")
                    false
                }
                if (successful) refunded = true
                successful
            }
        })
    }
}
