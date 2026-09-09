package io.enthusia.express.domain

import java.util.UUID

data class MailRecord(
    val id: Long,
    val sender: UUID?,
    val senderName: String,
    val recipient: UUID,
    val recipientName: String,
    val type: MailType,
    val status: MailStatus,
    val payload: ByteArray,
    val packedItemCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val unread: Boolean,
    val returnDelivery: Boolean,
) {
    fun id() = id
    fun sender() = sender
    fun senderName() = senderName
    fun recipient() = recipient
    fun recipientName() = recipientName
    fun type() = type
    fun status() = status
    fun payload() = payload
    fun packedItemCount() = packedItemCount
    fun createdAt() = createdAt
    fun updatedAt() = updatedAt
    fun unread() = unread
    fun returnDelivery() = returnDelivery
}
