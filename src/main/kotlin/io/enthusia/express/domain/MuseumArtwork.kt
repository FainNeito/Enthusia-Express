package io.enthusia.express.domain

import java.util.UUID

enum class MuseumState { AWAITING_REVIEW, NEEDS_CHANGES, ACCEPTED, COLLECTED, DISPLAYED }

/** Artwork credit is deliberately independent of the donating account. Dimensions do not imply tile ordering. */
data class MuseumDetails(val title: String, val artist: String, val width: Int, val height: Int, val description: String) {
    init {
        require(clean(title, 80) && clean(artist, 64) && clean(description, 500, true)) {
            "Use plain text: title 1–80, artist 1–64, description up to 500 characters"
        }
        require(width in 1..32 && height in 1..32) { "Artwork dimensions must be 1–32 maps per side" }
    }

    private fun clean(
        value: String,
        max: Int,
        empty: Boolean = false,
    ): Boolean = value.length <= max && (empty || value.isNotBlank()) && value.none { it.code < 32 || it.code == 127 || it == '§' }

    companion object {
        /** Existing named maps may carry legacy color codes; normalize only their catalog presentation. */
        @JvmStatic fun legacy(
            title: String,
            artist: String,
        ): MuseumDetails =
            MuseumDetails(
                plain(title, 80).ifBlank { "Untitled artwork" },
                plain(artist, 64).ifBlank { "Unknown artist" },
                1,
                1,
                "",
            )

        private fun plain(
            value: String,
            max: Int,
        ) = value.replace(Regex("§."), "").filter { !it.isISOControl() && it != '§' }.take(max)
    }
}

data class MuseumEntry(
    val submission: MapartSubmission,
    val details: MuseumDetails,
    val state: MuseumState,
    val revision: Long,
    val curator: UUID?,
    val curatorName: String?,
    val feedback: String,
    val tiles: Map<Int, Int>,
    val history: List<MuseumHistory> = emptyList(),
)

data class MuseumHistory(val event: String, val actor: String, val note: String, val timestamp: Long)

data class MuseumFilter
    @JvmOverloads
    constructor(
        val owner: UUID? = null,
        val query: String = "",
        val state: MuseumState? = null,
        val curator: UUID? = null,
        val unassigned: Boolean = false,
    ) {
        init {
            require(query.length <= 80) { "Search is limited to 80 characters" }
        }
    }

data class MuseumNotice(val id: Long, val recipient: UUID, val message: String)

class DuplicateMuseumMaps(val mapIds: Set<Int>) : IllegalArgumentException(
    "These map IDs are already catalogued: ${mapIds.take(12).joinToString()}",
)
