package io.enthusia.express.application

import java.util.UUID

/** Main-thread-owned, single-use action tokens. Reopening a menu invalidates every old action. */
class DialogSessions {
    private data class Session(val token: UUID, val expires: Long)

    private val sessions = HashMap<UUID, Session>()

    fun open(
        owner: UUID,
        now: Long,
    ): UUID {
        sessions.entries.removeIf { it.value.expires < now }
        return UUID.randomUUID().also { sessions[owner] = Session(it, now + 180000) }
    }

    fun accept(
        owner: UUID,
        token: UUID,
        now: Long,
    ): Boolean {
        val session = sessions[owner] ?: return false
        if (session.token != token) return false
        sessions.remove(owner)
        return now <= session.expires
    }

    fun close(owner: UUID) {
        sessions.remove(owner)
    }

    fun clear() {
        sessions.clear()
    }
}
