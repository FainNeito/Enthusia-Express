package io.enthusia.express.infrastructure.gui

/** Presentation-only click maps; command adapters retain all permission and mutation gates. */
object PostOfficeLayout {
    /** Three-slot category artwork must have matching click regions. */
    fun categorySlot(slot: Int): Int = if (slot in 0..8) (slot / 3) * 3 + 1 else slot

    fun home(labels: List<String>): Map<Int, Int> {
        val groups = mapOf(
            "Packages" to listOf(9,10,11,18,19,20),
            "Letters" to listOf(12,13,14,21,22,23),
            "Announcements" to listOf(15,16,17,24,25,26),
            "Sent mail" to (36..38).toList(), "Mail settings & help" to (39..41).toList(),
            "Submit museum artwork" to (42..44).toList(), "My submissions" to (45..51).toList(),
            "Curator queue" to listOf(52),
        )
        return buildMap { labels.forEachIndexed { index, label -> groups[label]?.forEach { put(it,index) } } }
    }

    fun catalog(labels: List<String>): Map<Int, Int> = buildMap {
        var entry = 18
        labels.forEachIndexed { index, label ->
            val slot = when (label) {
                "Previous" -> 45
                "Next" -> 53
                "Filters & search" -> 51
                "Express home" -> 52
                else -> if (entry <= 44) entry++ else null
            }
            if (slot != null) put(slot,index)
        }
    }

    fun icon(label: String): String = when (label) {
        "Packages" -> "packages"
        "Letters" -> "letters"
        "Announcements" -> "announcements"
        "Sent mail" -> "sent"
        "Mail settings & help", "Filters & search" -> "settings"
        "Submit museum artwork", "Full map manifest" -> "map"
        "My submissions" -> "museum"
        "Curator queue", "Assign to me" -> "seal"
        "Accept artwork", "Mark displayed", "Collect physical maps" -> "check"
        "Express home", "Back", "Back to submissions", "Previous" -> "back"
        "Next" -> "next"
        else -> "map"
    }
}
