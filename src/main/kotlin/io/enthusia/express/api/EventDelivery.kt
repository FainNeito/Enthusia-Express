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
     * returned, expired or purged. Call on the server thread. Completion can run
     * off-thread and returns the durable package's mail id, including an earlier delivery.
     *
     * Use one [token] and recipient for one ownership transfer. Overlapping calls in this
     * service instance join the same operation; a different recipient fails conservatively.
     * A saved token resolves to its original package even if retry validation now fails.
     * Cancelling or completing a caller's future does not stop or settle the storage operation.
     *
     * The caller retains [items] until normal completion confirms ownership has transferred.
     * An IllegalArgumentException means this submission was rejected and token lookup proved
     * no matching stored delivery. Other failures, cancellation and timeouts leave ownership
     * uncertain: retain the cargo and reconcile with the same token and recipient. The caller
     * must persist that token and prevent independent sends or manual release across restart;
     * the in-flight guard is local to this service instance and cannot coordinate other senders.
     */
    fun deliverPackage(recipient: UUID, recipientName: String, senderName: String,
                       items: List<ItemStack>, token: UUID): CompletableFuture<Long>
}
