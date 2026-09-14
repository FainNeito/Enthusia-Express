// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.payment

import java.util.logging.Logger
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/** EnthusiaCurrency owns both bank and physical-currency accounting. Never charge items a second time. */
internal object VaultShippingPayments {
    /** Use one authoritative EnthusiaCurrency withdrawal for combined bank and item balances. */
    // Vault does not specify provider exception types; any unchecked failure must retain cargo.
    @Suppress("TooGenericExceptionCaught")
    fun charge(player: Player, cost: Int, currency: Plugin, logger: Logger, directory: java.nio.file.Path): ChargeResult {
        val economy = Bukkit.getServicesManager().getRegistrations(Economy::class.java)
            .firstOrNull { it.plugin === currency && it.provider.name == "EnthusiaCurrency" && it.provider.isEnabled }
            ?.provider ?: return ChargeResult(null, unavailable = true)
        val account: OfflinePlayer = player
        val intent = try {
            PaymentReconciliation.begin(directory, account.uniqueId, cost)
        } catch (error: Exception) {
            logger.log(java.util.logging.Level.SEVERE, "Cannot persist payment intent; currency withdrawal refused", error)
            return ChargeResult(null, unavailable = true, source = PaymentSource.CURRENCY)
        }
        val response = try {
            economy.withdrawPlayer(account, cost.toDouble())
        } catch (error: RuntimeException) {
            logger.log(java.util.logging.Level.SEVERE, "Currency withdrawal outcome uncertain; retain $intent and reconcile before refunding", error)
            return ChargeResult(null, unavailable = true, source = PaymentSource.CURRENCY,
                reconciliationId = intent.fileName.toString())
        }
        PaymentReconciliation.finish(intent, logger)
        if (!response.transactionSuccess()) return ChargeResult(null, response.balance, source = PaymentSource.CURRENCY)
        return receipt(economy, account, cost, currency, logger)
    }

    /** Recovery never falls back to another Vault provider or to physical currency. */
    fun recoveryReceipt(account: OfflinePlayer, cost: Int, currency: Plugin, logger: Logger): PaymentReceipt? {
        val economy = Bukkit.getServicesManager().getRegistrations(Economy::class.java)
            .firstOrNull { it.plugin === currency && it.provider.name == "EnthusiaCurrency" && it.provider.isEnabled }
            ?.provider ?: return null
        return receipt(economy, account, cost, currency, logger).receipt
    }

    /** Retain the original currency provider and account for an idempotent asynchronous-send refund. */
    // Provider implementations can throw unchecked exceptions while issuing a refund.
    @Suppress("TooGenericExceptionCaught")
    private fun receipt(economy: Economy, account: OfflinePlayer, cost: Int, currency: Plugin, logger: Logger): ChargeResult {
        var refunded = false
        return ChargeResult(PaymentReceipt {
            if (refunded) true else {
                val successful = try {
                    currency.isEnabled && economy.isEnabled &&
                        economy.depositPlayer(account, cost.toDouble()).transactionSuccess()
                } catch (error: RuntimeException) {
                    logger.severe("Currency refund failed for ${account.uniqueId}: ${error.message}")
                    false
                }
                if (successful) refunded = true
                successful
            }
        })
    }
}
