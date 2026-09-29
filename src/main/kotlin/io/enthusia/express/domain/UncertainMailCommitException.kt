package io.enthusia.express.domain

/** A commit may have succeeded despite its response failing; automatic refunds would duplicate cargo. */
class UncertainMailCommitException(cause: Exception) : RuntimeException("Mail commit outcome requires reconciliation", cause) {
    companion object {
        @JvmStatic
        fun causedBy(error: Throwable?): Boolean = generateSequence(error) { it.cause }
            .take(32).any { it is UncertainMailCommitException }
    }
}
