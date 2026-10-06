package io.enthusia.express.application.api

import io.enthusia.express.application.MailBlocks
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public mail-block contract for other plugins, registered with Bukkit's ServicesManager.
 *
 * Callers pass identities they have already authenticated; this API performs no permission checks of its own and
 * never sends messages to players. Futures complete off the main thread.
 */
interface MailBlockApi {
    /** Whether [owner] currently blocks mail from [sender]. */
    fun isBlocked(owner: UUID, sender: UUID): CompletableFuture<Boolean>

    /** Block or unblock mail from [sender] to [owner]; [senderName] is stored for the owner's blocked list. */
    fun setBlocked(owner: UUID, sender: UUID, senderName: String, blocked: Boolean): CompletableFuture<Void>
}

/** Store-backed implementation with the same rules as `/mail block`: no self-blocks and a bounded display name. */
class StoreMailBlockApi(private val store: MailBlocks) : MailBlockApi {
    override fun isBlocked(owner: UUID, sender: UUID): CompletableFuture<Boolean> = store.isBlocked(owner, sender)

    override fun setBlocked(owner: UUID, sender: UUID, senderName: String, blocked: Boolean): CompletableFuture<Void> {
        if (owner == sender) return CompletableFuture.failedFuture(IllegalArgumentException("A player cannot block their own mail"))
        val name = senderName.trim()
        // The name is only stored when blocking; an unblock must work even if the caller has no valid name.
        if (blocked && !validName(name)) {
            return CompletableFuture.failedFuture(IllegalArgumentException("Invalid sender name"))
        }
        return store.setBlocked(owner, sender, name, blocked)
    }

    private companion object {
        const val MAX_NAME = 64

        /** Same rule as /mail block: non-empty, bounded and free of control characters. */
        fun validName(name: String): Boolean = name.length in 1..MAX_NAME && name.none { it.isISOControl() }
    }
}
