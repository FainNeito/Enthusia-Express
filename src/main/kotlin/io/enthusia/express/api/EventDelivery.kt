package io.enthusia.express.api

import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public mail-delivery service for event plugins such as EnthusiaHolidays (REQ-050).
 * Registered in Bukkit's ServicesManager:
 *
 *     Bukkit.getServicesManager().load(EventDelivery::class.java)
 *
 * Signatures use JDK and Bukkit types only, so callers may also use reflection.
 */
interface EventDelivery {
    /**
     * Mails [items] (1–27 stacks) to [recipient] as one shulker-box package from
     * [senderName] — no postage, outstanding limits or sender blocks — that is never
     * returned, expired or purged. Call on the server thread; the future completes
     * off-thread with the package's mail id.
     *
     * [token] makes retries idempotent: the same token always resolves to the first
     * package and never stores a second one; reusing it for a different recipient fails.
     * The caller keeps ownership of [items] until the future completes successfully.
     */
    fun deliverPackage(recipient: UUID, recipientName: String, senderName: String,
                       items: List<ItemStack>, token: UUID): CompletableFuture<Long>
}
