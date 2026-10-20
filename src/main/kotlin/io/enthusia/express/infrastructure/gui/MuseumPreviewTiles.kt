package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.infrastructure.util.ContainerScanner
import io.enthusia.express.infrastructure.util.ItemCodec
import org.bukkit.configuration.file.FileConfiguration

/** Older container donations have retained cargo but no indexed tile manifest. */
internal object MuseumPreviewTiles {
    fun read(entry: MuseumEntry, config: FileConfiguration): Map<Int, Int> {
        if (entry.tiles.isNotEmpty()) return entry.tiles
        val payload = entry.submission.mail.payload
        val limit = config.getInt("mail.max-package-payload-bytes", 262144).coerceAtMost(1048576)
        require(payload.isNotEmpty() && payload.size <= limit) { "Legacy artwork payload is unavailable or exceeds the preview limit" }
        val cargo = ItemCodec.decode(payload)
        val tiles = ContainerScanner.mapartManifest(cargo, config.getInt("mail.max-recursive-container-depth", 8))
        require(tiles.isNotEmpty() && tiles.size <= 1024) { "Legacy artwork exceeds the preview tile limit" }
        return tiles
    }
}
