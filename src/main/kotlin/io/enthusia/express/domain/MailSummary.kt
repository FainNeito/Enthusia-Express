package io.enthusia.express.domain

data class MailSummary(val packages: Int, val letters: Int, val announcements: Int) {
    fun packages() = packages
    fun letters() = letters
    fun announcements() = announcements
    fun total() = Math.addExact(packages, Math.addExact(letters, announcements))
}
