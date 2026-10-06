package io.enthusia.express.application

import io.enthusia.express.application.api.StoreMailBlockApi
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The public block API must follow the same rules as /mail block and write through the store. */
class MailBlockApiTest {
    private class FakeBlocks : MailBlocks {
        val blocked = mutableMapOf<Pair<UUID, UUID>, String>()
        override fun setBlocked(owner: UUID, sender: UUID, senderName: String, enabled: Boolean): CompletableFuture<Void> {
            if (enabled) blocked[owner to sender] = senderName else blocked.remove(owner to sender)
            return CompletableFuture.completedFuture(null)
        }
        override fun isBlocked(owner: UUID, sender: UUID): CompletableFuture<Boolean> =
            CompletableFuture.completedFuture((owner to sender) in blocked)
        override fun listBlocked(owner: UUID, page: Int): CompletableFuture<List<String>> =
            CompletableFuture.completedFuture(blocked.filterKeys { it.first == owner }.values.toList())
    }

    private val owner = UUID.randomUUID()
    private val sender = UUID.randomUUID()

    /** Verifies that block and unblock round-trip through the store. */
    @Test
    fun `block and unblock write through the store`() {
        val store = FakeBlocks()
        val api = StoreMailBlockApi(store)
        api.setBlocked(owner, sender, " FainNoir ", true).join()
        assertTrue(api.isBlocked(owner, sender).join())
        assertEquals("FainNoir", store.blocked[owner to sender])
        api.setBlocked(owner, sender, "FainNoir", false).join()
        assertFalse(api.isBlocked(owner, sender).join())
    }

    /** Verifies that self-blocks and invalid names are refused without touching the store. */
    @Test
    fun `self blocks and invalid names are refused`() {
        val store = FakeBlocks()
        val api = StoreMailBlockApi(store)
        assertThrows(ExecutionException::class.java) { api.setBlocked(owner, owner, "Self", true).get() }
        listOf("", "   ", "a".repeat(65), "bad\nname").forEach { name ->
            assertThrows(ExecutionException::class.java) { api.setBlocked(owner, sender, name, true).get() }
        }
        assertTrue(store.blocked.isEmpty())
    }

    /** Verifies that unblocking does not depend on a valid sender name. */
    @Test
    fun `unblock works without a valid name`() {
        val store = FakeBlocks()
        val api = StoreMailBlockApi(store)
        api.setBlocked(owner, sender, "FainNoir", true).join()
        api.setBlocked(owner, sender, "", false).join()
        assertFalse(api.isBlocked(owner, sender).join())
    }
}
