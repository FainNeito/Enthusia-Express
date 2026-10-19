package io.enthusia.express.domain

/** Review approval never grants physical cargo or substitutes for its delivery acknowledgment. */
internal fun MuseumEntry.mayReviewAs(next: MuseumState, feedback: String): Boolean {
    if (next == MuseumState.DISPLAYED) return state == MuseumState.COLLECTED && !submission.deliveryPending
    if (submission.mail.status != MailStatus.UNCLAIMED || submission.deliveryPending) return false
    return when (next) {
        MuseumState.ACCEPTED -> state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.NEEDS_CHANGES)
        MuseumState.NEEDS_CHANGES -> state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.ACCEPTED) && feedback.isNotBlank()
        else -> false
    }
}
