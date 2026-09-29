package io.enthusia.express.domain

/** A payload-free arrival snapshot; the watermark includes already-read history. */
data class MailNotification(val count: Int, val sender: String?, val type: MailType?, val watermark: Long) {
    /** Keep single deliveries short enough for the vanilla toast's title area. */
    fun text(): String {
        if (count != 1) return "You've got mail!\n$count items received"
        val noun = when (type) {
            MailType.PACKAGE -> "Package"
            MailType.LETTER -> "Letter"
            else -> "Notice"
        }
        val name = sender.orEmpty().replace(Regex("§."), "")
            .filter { !it.isISOControl() }.take(16).ifBlank { "Server" }
        return "$noun from $name"
    }
}
