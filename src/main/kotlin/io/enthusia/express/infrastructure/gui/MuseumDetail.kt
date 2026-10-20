package io.enthusia.express.infrastructure.gui

import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.domain.MuseumState
import io.enthusia.express.domain.MuseumFilter
import org.bukkit.entity.Player

internal const val USE_PERMISSION = "enthusiaexpress.use"
internal const val MANAGE_PERMISSION = "enthusiaexpress.mapart.manage"
internal const val SUBMIT_PERMISSION = "enthusiaexpress.mapart.submit"
internal const val DUPLICATE_PERMISSION = "enthusiaexpress.mapart.duplicates"

internal fun museumPageChoices(entries: List<MuseumEntry>, filter: MuseumFilter, page: Int, run: (String) -> Unit): List<MenuChoice> {
    val choices = entries.map { entry ->
        MenuChoice("${entry.details.title.take(36)} · ${label(entry.state)}") { run("mail mapart view ${entry.submission.mail.id}") }
    }.toMutableList()
    if (page > 0) choices.add(MenuChoice("Previous") { run(filterCommand("list", filter, page - 1)) })
    if (entries.size == 27) choices.add(MenuChoice("Next") { run(filterCommand("list", filter, page + 1)) })
    choices.add(MenuChoice("Filters & search") { run(filterCommand("filters", filter, 0)) })
    choices.add(MenuChoice("Express home") { run("mail") })
    return choices
}

/** Command controls still pass through the command adapter's current actor/revision gates. */
internal fun museumDetailChoices(player: Player, entry: MuseumEntry, run: (String) -> Unit, collect: () -> Unit): List<MenuChoice> {
    val id = entry.submission.mail.id
    val choices = mutableListOf(
        MenuChoice("Preview artwork") { run("mail mapart preview $id") },
        MenuChoice("Full map manifest") { run("mail mapart maps $id") },
    )
    if (player.hasPermission(MANAGE_PERMISSION)) choices.addAll(curatorChoices(entry, run, collect))
    if (entry.submission.mail.sender == player.uniqueId && entry.state == MuseumState.NEEDS_CHANGES) {
        choices.add(MenuChoice("Correct submission details") { run("mail mapart edit $id") })
    }
    choices.add(MenuChoice("Back to submissions") {
        run("mail mapart ${if (player.hasPermission(MANAGE_PERMISSION)) "queue" else "mine"}")
    })
    return choices
}

private fun curatorChoices(entry: MuseumEntry, run: (String) -> Unit, collect: () -> Unit): List<MenuChoice> {
    val id = entry.submission.mail.id
    val choices = mutableListOf<MenuChoice>()
    if (entry.state != MuseumState.DISPLAYED) choices.add(MenuChoice("Assign to me") { run("mail mapart assign $id ${entry.revision}") })
    if (entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.NEEDS_CHANGES)) {
        choices.add(MenuChoice("Accept artwork") { run("mail mapart accept $id ${entry.revision}") })
    }
    if (entry.submission.mail.status == MailStatus.UNCLAIMED) {
        if (entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.ACCEPTED)) {
            choices.add(MenuChoice("Request changes") { run("mail mapart feedback $id") })
        }
        if (entry.state == MuseumState.ACCEPTED) choices.add(MenuChoice("Collect physical maps") { collect() })
    }
    if (entry.state == MuseumState.COLLECTED && !entry.submission.deliveryPending) {
        choices.add(MenuChoice("Mark displayed") { run("mail mapart displayed $id ${entry.revision}") })
    }
    return choices
}

internal fun museumDetailText(entry: MuseumEntry): String {
    val details = entry.details
    val history = entry.history.takeLast(6).joinToString("\n") { "${it.event}: ${it.actor}" }
    val pending = if (entry.submission.deliveryPending) "Inventory delivery acknowledgment is pending." else ""
    return "${details.title}\nArtist: ${details.artist}\nSubmitted by: ${entry.submission.mail.senderName}\n" +
        "${details.width} × ${details.height} maps · ${label(entry.state)}\nCurator: ${entry.curatorName ?: "Unassigned"}\n" +
        "${details.description}\n${entry.tiles.size} unique map IDs · ${entry.tiles.values.sum()} physical maps\n" +
        "${entry.feedback}\nRecent history:\n$history\n$pending"
}
