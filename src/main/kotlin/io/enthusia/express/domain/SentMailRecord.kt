package io.enthusia.express.domain

/** A sender-owned history entry; old returned rows may have lost their original recipient. */
data class SentMailRecord(val mail: MailRecord, val recipientName: String?, val deliveryPending: Boolean)
