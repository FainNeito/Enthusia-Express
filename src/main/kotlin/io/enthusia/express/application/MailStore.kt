package io.enthusia.express.application

import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailSummary
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.MapartSubmission
import java.util.OptionalLong
import java.util.UUID
import java.util.concurrent.CompletableFuture

const val MAIL_PAGE_SIZE = 27

interface MailStore : MailQueries, MailWrites, MailClaims, MailLifecycle, MailBlocks, MapartStore

interface MapartStore {
    /** Save map cargo and its intake metadata in one transaction. The token makes retries idempotent. */
    @Suppress("LongParameterList")
    fun insertMapart(sender: UUID, senderName: String, payload: ByteArray,
                     mapId: Int?, mapName: String, token: UUID): CompletableFuture<Long>
    /** Show shared Mapart intake or processed history without exposing payload bytes. */
    fun listMapart(page: Int, processed: Boolean): CompletableFuture<List<MapartSubmission>>
    /** Load intake metadata for an already selected package. */
    fun getMapart(id: Long): CompletableFuture<MapartSubmission?>
    /** Mark a delivered map processed without touching its physical delivery. */
    fun markMapartProcessed(id: Long, manager: UUID): CompletableFuture<Boolean>
    /** Atomically bind an unclaimed shared-queue submission to the claiming manager. */
    fun claimMapart(record: MailRecord, manager: UUID, managerName: String): CompletableFuture<Boolean>
}

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
    /** Snapshot pending arrivals newer than a session watermark without reading payloads. */
    fun mailNotification(recipient: UUID, afterId: Long): CompletableFuture<io.enthusia.express.domain.MailNotification>
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
    /** Reserve only the exact eligible generation observed by the caller. */
    fun claim(record: MailRecord): CompletableFuture<Boolean>
    /** Release a claimed package reservation after inventory delivery; never make it claimable again. */
    fun confirmDelivery(id: Long, recipient: UUID): CompletableFuture<Boolean>
    /** Restore an undelivered pending claim to its original status and timestamp. */
    fun restoreClaim(record: MailRecord): CompletableFuture<Boolean>
    /** Clear unread state only for eligible text mail owned by the recipient. */
    fun markRead(id: Long, recipient: UUID): CompletableFuture<Boolean>
    /** Clear all unread letters and announcements owned by the recipient, across every page. */
    fun markAllTextRead(recipient: UUID): CompletableFuture<Int>
}
