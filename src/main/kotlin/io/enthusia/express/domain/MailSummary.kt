package io.enthusia.express.domain

data class MailSummary(val packages: Int, val letters: Int, val announcements: Int) {
    /** Return the count of available package deliveries. */
    fun packages() = packages
    /** Return the count of unread letters. */
    fun letters() = letters
    /** Return the count of unread announcements. */
    fun announcements() = announcements
    /** Sum all pending categories for a join notification. */
    fun total() = Math.addExact(packages, Math.addExact(letters, announcements))
}
