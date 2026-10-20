package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player

/** Filter presentation is separate from catalog lookup and preview ownership. */
internal class MuseumFilters(private val view: MuseumView, private val run: (Player, String) -> Unit) {
    fun show(
        player: Player,
        current: MuseumFilter,
    ) {
        val choices =
            mutableListOf(
                MenuChoice("All statuses") {
                    run(player, filterCommand("list", current.copy(state = null, curator = null, unassigned = false), 0))
                },
            )
        MuseumState.entries.forEach {
                state ->
            choices.add(MenuChoice(label(state)) { run(player, filterCommand("list", current.copy(state = state), 0)) })
        }
        if (player.hasPermission(MANAGE_PERMISSION)) {
            choices.add(
                MenuChoice("Assigned to me") {
                    run(player, filterCommand("list", current.copy(curator = player.uniqueId, unassigned = false), 0))
                },
            )
            choices.add(MenuChoice("Unassigned") { run(player, filterCommand("list", current.copy(curator = null, unassigned = true), 0)) })
        }
        choices.add(
            MenuChoice("Search title or artist") {
                val fields = listOf(MenuField("search", "Artwork title or artist", current.query))
                if (!view.dialogs.show(
                        player,
                        "Search museum",
                        "Search is literal, not a command or pattern.",
                        fields,
                        listOf(
                            MenuChoice("Search") { input ->
                                run(player, filterCommand("list", current.copy(query=input["search"].orEmpty().take(80)), 0))
                            },
                        ),
                    )
                ) {
                    player.sendMessage(
                        Component.text(
                            "Use /mail mapart search <artwork or artist>",
                        ).clickEvent(ClickEvent.suggestCommand("/mail mapart search ")),
                    )
                }
            },
        )
        view.show(
            player,
            "Museum filters",
            "Filter ${if (current.owner != null) "your submissions" else "the staff queue"}.",
            emptyList(),
            choices,
        )
    }

}
