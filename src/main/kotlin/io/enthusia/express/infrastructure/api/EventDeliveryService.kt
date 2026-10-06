package io.enthusia.express.infrastructure.api

import io.enthusia.express.api.EventDelivery
import io.enthusia.express.application.MailStore
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** [EventDelivery] backed by the mail store; validation failures become failed futures, never exceptions. */
class EventDeliveryService(private val store: MailStore, private val packager: EventPackager) : EventDelivery {
    override fun deliverPackage(recipient: UUID, recipientName: String, senderName: String,
                                items: List<ItemStack>, token: UUID): CompletableFuture<Long> = try {
        require(items.isNotEmpty()) { "Event package needs at least one item" }
        require(items.size <= MAX_STACKS) { "Event package holds at most $MAX_STACKS stacks" }
        require(items.none { it.isEmpty }) { "Event package cannot contain empty stacks" }
        val packed = packager.pack(items, senderName)
        store.insertEventPackage(recipient, recipientName, senderName, packed.payload, packed.packedCount, token)
            .thenApply { it.mailId }
    } catch (invalid: IllegalArgumentException) {
        CompletableFuture.failedFuture(invalid)
    }

    private companion object {
        const val MAX_STACKS = 27
    }
}
