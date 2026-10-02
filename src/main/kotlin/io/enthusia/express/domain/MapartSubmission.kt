package io.enthusia.express.domain

import java.util.UUID

/** Immutable intake metadata; the linked package row owns the physical map and claim state. */
data class MapartSubmission(
    val mail: MailRecord,
    val mapId: Int?,
    val mapName: String,
    val submittedAt: Long,
    val processedAt: Long?,
    val deliveryPending: Boolean,
    val processedBy: UUID?,
)
