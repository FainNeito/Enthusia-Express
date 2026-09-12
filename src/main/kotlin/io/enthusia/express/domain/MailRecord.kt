package io.enthusia.express.domain

import java.util.UUID

data class MailRecord(
    @get:JvmName("id") val id: Long,
    @get:JvmName("sender") val sender: UUID?,
    @get:JvmName("senderName") val senderName: String,
    @get:JvmName("recipient") val recipient: UUID,
    @get:JvmName("recipientName") val recipientName: String,
    @get:JvmName("type") val type: MailType,
    @get:JvmName("status") val status: MailStatus,
    @get:JvmName("payload") val payload: ByteArray,
    @get:JvmName("packedItemCount") val packedItemCount: Int,
    @get:JvmName("createdAt") val createdAt: Long,
    @get:JvmName("updatedAt") val updatedAt: Long,
    @get:JvmName("unread") val unread: Boolean,
    @get:JvmName("returnDelivery") val returnDelivery: Boolean,
)
