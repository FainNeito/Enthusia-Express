package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumState
import io.enthusia.express.infrastructure.util.MainThread
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

private const val MUSEUM_UNASSIGNED = "unassigned"
private const val MUSEUM_SEARCH = "search"
private const val MUSEUM_PROCESSED = "processed"
private const val MUSEUM_ASSIGNED = "assigned"
private const val MUSEUM_INBOX = "inbox"
private const val MUSEUM_QUEUE = "queue"

/** Command adapters recheck actor permissions before invoking any mutation. */
internal class MuseumCommands(
    private val menus: MuseumMenus,
    private val input: MuseumInput,
    private val store: MailStore,
    private val main: MainThread,
    private val access: (Player, Boolean) -> Boolean,
    private val view: MuseumView,
) {
    private val plugin = view.plugin

    fun command(player: Player, args: Array<String>): Boolean {
        if (access(player, false)) {
            try {
                dispatch(player, args, args.getOrNull(1)?.lowercase() ?: "mine")
            } catch (error: IllegalArgumentException) {
                player.sendMessage("§c${error.message ?: "Invalid museum arguments"}")
            }
        }
        return true
    }

    private fun dispatch(player: Player, args: Array<String>, action: String) {
        when (action) {
            "preview" -> menus.preview(player, requireNotNull(args.getOrNull(2)?.toLongOrNull()),
                if (args.size > 3) requireNotNull(args[3].toIntOrNull()) else 0)
            "preview-close" -> menus.dismissPreview(player)
            "submit" -> input.submission(player)
            "settings" -> settings(player)
            "draft", "override" -> input.openCargo(player, input.parseDetails(args.drop(2).joinToString(" ")), action == "override")
            "feedback", "edit" -> edit(player, args, action)
            "accept", "displayed", "assign", "changes", "revise" -> mutate(player, args, action)
            else -> browse(player, args, action)
        }
    }

    private fun browse(player: Player, args: Array<String>, action: String) {
        when (action) {
            "mine", MUSEUM_INBOX, MUSEUM_QUEUE, MUSEUM_PROCESSED, MUSEUM_SEARCH, MUSEUM_UNASSIGNED, MUSEUM_ASSIGNED -> scope(player, args, action)
            "view" -> menus.detail(player, requireNotNull(args.getOrNull(2)?.toLongOrNull()))
            "maps" -> menus.manifest(player, requireNotNull(args.getOrNull(2)?.toLongOrNull()), args.getOrNull(3)?.toIntOrNull() ?: 0)
            "list", "filters" -> {
                val filter = parseFilter(args)
                if (action == "list") menus.browse(player, filter, args.getOrNull(7)?.toIntOrNull() ?: 0) else menus.filters(player, filter)
            }
            else -> player.sendMessage("§e/mail mapart submit|mine|queue|processed|search|assigned|unassigned|view <id>")
        }
    }

    private fun scope(player: Player, args: Array<String>, action: String) {
        val staffOnly = action in setOf(MUSEUM_INBOX, MUSEUM_QUEUE, MUSEUM_PROCESSED, MUSEUM_UNASSIGNED, MUSEUM_ASSIGNED)
        if (staffOnly && !access(player, true)) return
        val owner = if (player.hasPermission(MANAGE_PERMISSION)) null else player.uniqueId
        val scopes = mapOf(
            "mine" to MuseumFilter(player.uniqueId), MUSEUM_INBOX to MuseumFilter(), MUSEUM_QUEUE to MuseumFilter(),
            MUSEUM_PROCESSED to MuseumFilter(state = MuseumState.DISPLAYED),
            MUSEUM_UNASSIGNED to MuseumFilter(unassigned = true), MUSEUM_ASSIGNED to MuseumFilter(curator = player.uniqueId),
            MUSEUM_SEARCH to MuseumFilter(owner, if (action == MUSEUM_SEARCH) args.drop(2).joinToString(" ") else ""),
        )
        menus.browse(player, scopes.getValue(action))
    }

    private fun edit(player: Player, args: Array<String>, action: String) {
        val id = requireNotNull(args.getOrNull(2)?.toLongOrNull())
        main.complete(store.artwork(id)) { entry, error ->
            if (error == null && entry != null && access(player, false)) {
                if (action == "feedback" && access(player, true)) input.feedback(player, entry)
                else if (action == "edit" && entry.submission.mail.sender == player.uniqueId && entry.state == MuseumState.NEEDS_CHANGES) input.submission(player, entry)
            }
        }
    }

    private fun mutate(player: Player, args: Array<String>, action: String) {
        val id = requireNotNull(args.getOrNull(2)?.toLongOrNull())
        val revision = requireNotNull(args.getOrNull(3)?.toLongOrNull())
        if (action == "revise") {
            input.change(player, store.reviseArtwork(id, revision, player.uniqueId, input.parseDetails(args.drop(4).joinToString(" "))), id)
        } else if (access(player, true)) {
            val future = if (action == "assign") store.assignArtwork(id, revision, player.uniqueId, player.name)
            else store.reviewArtwork(id, revision, when (action) {
                "accept" -> MuseumState.ACCEPTED
                "displayed" -> MuseumState.DISPLAYED
                else -> MuseumState.NEEDS_CHANGES
            }, player.uniqueId, player.name, args.drop(4).joinToString(" "))
            input.change(player, future, id)
        }
    }

    private fun settings(player: Player) {
        val mode = plugin.config.getString("notifications.toast.mode", "native")
        val enabled = plugin.config.getBoolean("gui.dialogs.enabled", true)
        view.show(player, "Mail settings & help", "Notifications: $mode\nDialogs: $enabled\nThese options are configured by the server.\nUse /mail block <player> or /mail unblock <player> to manage who can send you mail.\nSigned letters are sent using the signed book in your hand. Shipment confirmation shows postage before any charge.", emptyList(), listOf(
            MenuChoice("Blocked players") { if (access(player, false)) player.performCommand("mail blocked") },
            MenuChoice("Back") { if (access(player, false)) player.performCommand("mail") },
        ))
    }
}
