@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumState
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.util.MainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/** Inventory/dialog presentation over the same repository and recoverable shipping/claim paths. */
class MuseumMenus(
    private val plugin: JavaPlugin,
    private val store: MailStore,
    private val main: MainThread,
    private val combat: CombatLogXHook,
    private val shipping: ShippingService,
    private val mailbox: MailboxService,
) {
    private val requests = HashMap<UUID, UUID>()
    private val view = MuseumView(plugin, { allowed(it) }, { requests.remove(it) })
    private val input = MuseumInput(plugin, store, main, shipping, view) { allowed(it) }
    private val commands = MuseumCommands(this, input, store, main, { player, manager -> allowed(player, manager) }, view)
    val listener: Listener get() = view

    private fun allowed(
        player: Player,
        manager: Boolean = false,
    ): Boolean =
        plugin.isEnabled && player.isOnline &&
            Bukkit.getPlayer(player.uniqueId) === player && !player.isDead && player.hasPermission(USE_PERMISSION) &&
            (!manager || player.hasPermission(MANAGE_PERMISSION)) && combat.mayUseMail(player)

    fun home(player: Player) {
        if (!allowed(player)) return
        val choices =
            listOf(
                MenuChoice("Packages") { mailbox.open(player, MailType.PACKAGE) },
                MenuChoice("Letters") { mailbox.open(player, MailType.LETTER) },
                MenuChoice("Announcements") { mailbox.open(player, MailType.ANNOUNCEMENT) },
                MenuChoice("Sent mail") { mailbox.openSent(player, MailType.PACKAGE) },
                MenuChoice("Mail settings & help") { run(player, "mail mapart settings") },
                MenuChoice("Submit museum artwork") { run(player, "mail mapart submit") },
                MenuChoice("My submissions") { run(player, "mail mapart mine") },
            ) +
                if (player.hasPermission(MANAGE_PERMISSION)) {
                    listOf(
                        MenuChoice("Curator queue") {
                            run(player, "mail mapart queue")
                        },
                    )
                } else {
                    emptyList()
                }
        view.show(player, "Enthusia Express", "Choose a mailbox or museum service. Packages still use your inventory.", emptyList(), choices, "home")
    }

    fun command(player: Player, args: Array<String>): Boolean = commands.command(player, args)

    @Suppress("LabeledExpression") // Ignore stale/unauthorized asynchronous results before presenting anything.
    fun browse(
        player: Player,
        filter: MuseumFilter,
        page: Int = 0,
    ) {
        if (!allowed(player)) return
        val scoped = if (player.hasPermission(MANAGE_PERMISSION)) filter else filter.copy(owner = player.uniqueId)
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        main.complete(store.listArtwork(page, scoped)) { entries, error ->
            if (!allowed(player) || requests[player.uniqueId] != request) return@complete
            if (error != null || entries == null) {
                player.sendMessage("§cMuseum records could not be loaded.")
                return@complete
            }
            val actions = museumPageChoices(entries, scoped, page) { run(player, it) }
            view.show(
                player,
                if (scoped.owner != null) "My museum submissions" else "Museum curator queue",
                "Page ${page + 1} · ${entries.size} entries. ${if (entries.isEmpty()) "No matching artwork." else "Choose an artwork for credit, map IDs and review history."}",
                emptyList(),
                actions,
                "catalog",
            )
        }
    }

    internal fun filters(
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

    @Suppress("LabeledExpression") // Ignore stale/unauthorized asynchronous results before presenting anything.
    fun detail(
        player: Player,
        id: Long,
    ) {
        if (!allowed(player)) return
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        main.complete(store.artwork(id)) { entry, error ->
            if (!allowed(player) || requests[player.uniqueId] != request) return@complete
            if (error != null || entry == null || !canView(player, entry)) {
                player.sendMessage("§cArtwork is unavailable.")
                return@complete
            }
            val choices = museumDetailChoices(player, entry, { run(player, it) }) {
                if (allowed(player, true)) mailbox.claimMuseum(player, id)
            }
            view.show(player, entry.details.title, museumDetailText(entry), emptyList(), choices)
        }
    }

    @Suppress("LabeledExpression") // Ignore stale/unauthorized asynchronous results before presenting anything.
    internal fun manifest(
        player: Player,
        id: Long,
        page: Int,
    ) {
        if (!allowed(player) || page !in 0..1000) return
        main.complete(store.artwork(id)) { entry, error ->
            if (!allowed(player)) return@complete
            if (error != null || entry == null || !canView(player, entry)) return@complete
            val maps = entry.tiles.entries.toList()
            val start = page * 20
            val choices = mutableListOf(MenuChoice("Artwork details") { run(player, "mail mapart view $id") })
            if (page > 0) choices.add(MenuChoice("Previous maps") { run(player, "mail mapart maps $id ${page - 1}") })
            if (start + 20 < maps.size) choices.add(MenuChoice("More maps") { run(player, "mail mapart maps $id ${page + 1}") })
            view.show(
                player,
                "Artwork map manifest",
                "Page ${page + 1}\n" + 
                    maps.drop(start).take(20).joinToString("\n") {
                        "Map ${it.key}: ${it.value} copies"
                    },
                emptyList(),
                choices,
            )
        }
    }

    fun shutdown() {
        view.shutdown()
        requests.clear()
    }

    private fun canView(player: Player, entry: io.enthusia.express.domain.MuseumEntry): Boolean =
        entry.submission.mail.sender == player.uniqueId || player.hasPermission(MANAGE_PERMISSION)

    private fun run(
        player: Player,
        command: String,
    ) {
        if (allowed(player)) player.performCommand(command)
    }

}
