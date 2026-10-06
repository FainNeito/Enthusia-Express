package io.enthusia.express.domain

/** Result of a token-identified event delivery: the package id and whether this call stored it. */
data class EventDeliveryRecord(
    @get:JvmName("mailId") val mailId: Long,
    @get:JvmName("created") val created: Boolean,
)
