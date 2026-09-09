package io.enthusia.express.application

import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailSummary
import io.enthusia.express.domain.MailType
import java.util.OptionalLong
import java.util.UUID
import java.util.concurrent.CompletableFuture

interface MailStore {
    fun initialize(): CompletableFuture<Void>
    fun insertMailLimited(sender: UUID, senderName: String, recipient: UUID, recipientName: String,
                          type: MailType, payload: ByteArray, packedCount: Int, enforceLimit: Boolean): CompletableFuture<OptionalLong>
    fun pendingMail(recipient: UUID): CompletableFuture<MailSummary>
    fun insertPackage(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                      payload: ByteArray, packedCount: Int, returnDelivery: Boolean): CompletableFuture<Long>
    fun insertMail(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                   type: MailType, payload: ByteArray, packedCount: Int, returned: Boolean): CompletableFuture<Long>
    fun announce(sender: UUID?, senderName: String, recipients: Map<UUID, String>, payload: ByteArray): CompletableFuture<Int>
    fun listInbox(recipient: UUID, type: MailType): CompletableFuture<List<MailRecord>>
    fun listInbox(recipient: UUID, type: MailType, page: Int): CompletableFuture<List<MailRecord>>
    fun get(id: Long): CompletableFuture<MailRecord?>
    fun claim(id: Long, recipient: UUID): CompletableFuture<Boolean>
    fun confirmDelivery(id: Long, recipient: UUID): CompletableFuture<Boolean>
    fun restoreClaim(record: MailRecord): CompletableFuture<Boolean>
    fun markRead(id: Long, recipient: UUID): CompletableFuture<Boolean>
    fun expire(now: Long, returnCutoff: Long, purgeCutoff: Long, textCutoff: Long): CompletableFuture<Int>
    fun close()
}
