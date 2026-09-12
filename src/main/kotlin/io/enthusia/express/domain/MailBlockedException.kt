package io.enthusia.express.domain

/** Expected rejection when a recipient has blocked this sender. */
class MailBlockedException : IllegalStateException("Recipient is not accepting this sender's mail") {
    companion object {
        /** Recognize the rejection through bounded asynchronous exception wrappers. */
        fun causedBy(error: Throwable?): Boolean {
            var cause = error
            repeat(8) {
                if (cause is MailBlockedException) return true
                cause = cause?.cause
            }
            return false
        }
    }
}
