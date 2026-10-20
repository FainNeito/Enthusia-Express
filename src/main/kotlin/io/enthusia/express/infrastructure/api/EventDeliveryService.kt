// Ownership and reconciliation helpers retain their private contracts for review.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.api

import io.enthusia.express.api.EventDelivery
import io.enthusia.express.application.MailStore
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentHashMap

/** [EventDelivery] backed by durable tokens and private, cancellation-isolated in-flight outcomes. */
class EventDeliveryService(private val store: MailStore, private val packager: EventPackager) : EventDelivery {
    private val inFlight = ConcurrentHashMap<UUID, PendingDelivery>()

    override fun deliverPackage(recipient: UUID, recipientName: String, senderName: String,
                                items: List<ItemStack>, token: UUID): CompletableFuture<Long> {
        val pending = PendingDelivery(recipient)
        val existing = inFlight.putIfAbsent(token, pending)
        if (existing != null) {
            if (existing.recipient != recipient) return CompletableFuture.failedFuture(
                IllegalStateException("Event delivery token is in flight for another recipient"))
            return existing.outcome.thenApply { it }
        }

        // Only this first caller touches Bukkit items; asynchronous callbacks handle storage results only.
        packAndStore(recipient, recipientName, senderName, items, token)
            .handle<CompletableFuture<Long>> { id, failure ->
                if (failure == null) CompletableFuture.completedFuture(id)
                else reconcile(recipient, token, unwrap(failure))
            }.thenCompose { it }.whenComplete { id, failure ->
                try {
                    if (failure == null) pending.outcome.complete(id)
                    else pending.outcome.completeExceptionally(unwrap(failure))
                } finally {
                    // Reentrant completion callbacks may safely join the settled owner before removal.
                    inFlight.remove(token, pending)
                }
            }
        // Neither the owner nor a joining caller receives the promise that controls registry lifetime.
        return pending.outcome.thenApply { it }
    }

    /** Validate and pack on the calling server thread, then submit immutable bytes to storage. */
    @Suppress("TooGenericExceptionCaught")
    private fun packAndStore(recipient: UUID, recipientName: String, senderName: String,
                             items: List<ItemStack>, token: UUID): CompletableFuture<Long> = try {
        require(items.isNotEmpty()) { "Event package needs at least one item" }
        require(items.size <= MAX_STACKS) { "Event package holds at most $MAX_STACKS stacks" }
        // Reflective callers can smuggle nulls past Kotlin's type system.
        @Suppress("SENSELESS_COMPARISON")
        require(items.none { it == null || it.isEmpty }) { "Event package cannot contain empty stacks" }
        val packed = packager.pack(items, senderName)
        store.insertEventPackage(recipient, recipientName, senderName, packed.payload, packed.packedCount, token)
            .thenApply { it.mailId }
    } catch (failure: Throwable) {
        // A synchronous failure must settle the private owner just like an asynchronous storage failure.
        CompletableFuture.failedFuture(failure)
    }

    /** Consult the serialized token store before treating a failed retry as known-not-stored. */
    @Suppress("TooGenericExceptionCaught")
    private fun reconcile(recipient: UUID, token: UUID, original: Throwable): CompletableFuture<Long> = try {
        store.findEventDelivery(recipient, token).handle { stored, failure ->
            if (failure != null) throw unresolved(original, unwrap(failure))
            if (stored == null) throw unresolved(original, IllegalStateException("Missing token lookup result"))
            if (stored.isPresent) stored.asLong else throw CompletionException(original)
        }
    } catch (failure: Throwable) {
        CompletableFuture.failedFuture(unresolved(original, unwrap(failure)))
    }

    /** Lookup uncertainty must never be mistaken for a permanent cargo rejection by a consumer. */
    private fun unresolved(original: Throwable, lookup: Throwable): IllegalStateException =
        IllegalStateException("Unable to reconcile event delivery ownership; retain cargo and retry the same token").also {
            it.addSuppressed(lookup)
            if (original !== lookup) it.addSuppressed(original)
        }

    /** Remove asynchronous transport wrappers while preserving the actual storage or validation failure. */
    private fun unwrap(failure: Throwable): Throwable {
        var result = failure
        while (result is CompletionException && result.cause != null && result.cause !== result) result = result.cause!!
        return result
    }

    /** The operation owner is private; caller-visible futures are independent dependent stages. */
    private class PendingDelivery(val recipient: UUID) {
        val outcome = CompletableFuture<Long>()
    }

    private companion object {
        const val MAX_STACKS = 27
    }
}
