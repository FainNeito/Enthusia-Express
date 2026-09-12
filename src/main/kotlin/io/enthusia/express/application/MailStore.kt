package io.enthusia.express.application

import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailSummary
import io.enthusia.express.domain.MailType
import java.util.OptionalLong
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface MailStore : MailQueries, MailWrites, MailClaims, MailLifecycle, MailBlocks

interface MailBlocks {
    /** Persist or remove a recipient's sender block by UUID. */
    fun setBlocked(owner: UUID, sender: UUID, senderName: String, enabled: Boolean): CompletableFuture<Void>
    /** Check whether a recipient currently blocks a sender. */
    fun isBlocked(owner: UUID, sender: UUID): CompletableFuture<Boolean>
    /** List one bounded page of the owner's blocked player names. */
    fun listBlocked(owner: UUID, page: Int): CompletableFuture<List<String>>
}

interface MailLifecycle {
    /** Create or migrate storage before accepting asynchronous mail operations. */
    fun initialize(): CompletableFuture<Void>
    /** Apply the supplied retention cutoffs without reclaiming delivered packages. */
    fun expire(now: Long, returnCutoff: Long, purgeCutoff: Long, textCutoff: Long): CompletableFuture<Int>
    /** Finish queued storage work and release its resources. */
    fun close()
}

interface MailWrites {
    /** Atomically check the sender-recipient allowance and insert mail, returning empty when occupied. */
    // Preserve the tested Java calling contract; adapters group insert data internally.
    @Suppress("LongParameterList")
    fun insertMailLimited(sender: UUID, senderName: String, recipient: UUID, recipientName: String,
                          type: MailType, payload: ByteArray, packedCount: Int, enforceLimit: Boolean): CompletableFuture<OptionalLong>
    /** Store a package payload and its return-delivery state. */
    @Suppress("LongParameterList")
    fun insertPackage(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                      payload: ByteArray, packedCount: Int, returnDelivery: Boolean): CompletableFuture<Long>
    /** Store an immutable payload copy and return its generated mail identifier. */
    @Suppress("LongParameterList")
    fun insertMail(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                   type: MailType, payload: ByteArray, packedCount: Int, returned: Boolean): CompletableFuture<Long>
    /** Persist one independent announcement per recipient as a single transaction. */
    fun announce(sender: UUID?, senderName: String, recipients: Map<UUID, String>, payload: ByteArray): CompletableFuture<Int>
}

interface MailQueries {
    /** Load sender-owned history, including claimed, returned and expired entries. */
    fun listSent(sender: UUID, type: MailType, page: Int): CompletableFuture<List<io.enthusia.express.domain.SentMailRecord>>
    /** Count packages and unread text mail for a recipient notification. */
    fun pendingMail(recipient: UUID): CompletableFuture<MailSummary>
    /** Load one bounded inbox page for the requested recipient and mail type. */
    fun listInbox(recipient: UUID, type: MailType): CompletableFuture<List<MailRecord>>
    /** Load one bounded inbox page for the requested recipient and mail type. */
    fun listInbox(recipient: UUID, type: MailType, page: Int): CompletableFuture<List<MailRecord>>
    /** Look up a mail row by identifier, returning null when absent. */
    fun get(id: Long): CompletableFuture<MailRecord?>
}

interface MailClaims {
    /** Reserve an eligible package once while retaining its sending allowance until delivery. */
    fun claim(id: Long, recipient: UUID): CompletableFuture<Boolean>
    /** Release a claimed package reservation after inventory delivery; never make it claimable again. */
    fun confirmDelivery(id: Long, recipient: UUID): CompletableFuture<Boolean>
    /** Restore an undelivered pending claim to its original status and timestamp. */
    fun restoreClaim(record: MailRecord): CompletableFuture<Boolean>
    /** Clear unread state only for eligible text mail owned by the recipient. */
    fun markRead(id: Long, recipient: UUID): CompletableFuture<Boolean>
}
