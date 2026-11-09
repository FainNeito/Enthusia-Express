@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumState
import java.util.UUID

internal fun label(state: MuseumState) = state.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }


internal fun filterCommand(
    action: String,
    filter: MuseumFilter,
    page: Int,
): String =
    "mail mapart $action ${filter.owner ?: "-"} ${java.net.URLEncoder.encode(
        filter.query,
        java.nio.charset.StandardCharsets.UTF_8,
    ).ifEmpty { "-" }} ${filter.state?.name ?: "-"} ${filter.curator ?: "-"} ${filter.unassigned} $page"


internal fun parseFilter(args: Array<String>): MuseumFilter =
    MuseumFilter(
        args.getOrNull(2)?.takeIf { it != "-" }?.let(UUID::fromString),
        args.getOrNull(
            3,
        )?.takeIf { it != "-" }?.let { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8) }.orEmpty(),
        args.getOrNull(4)?.takeIf { it != "-" }?.let(MuseumState::valueOf),
        args.getOrNull(5)?.takeIf { it != "-" }?.let(UUID::fromString),
        args.getOrNull(6) == "true",
    )
