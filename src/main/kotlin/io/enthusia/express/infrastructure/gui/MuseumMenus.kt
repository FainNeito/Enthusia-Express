@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.MuseumDetails
import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumState
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.util.MainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerQuitEvent
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
) : Listener {
    private val dialogs = DialogMenus(plugin)
    private val theme = GuiTheme(plugin)

    private data class Chest(val inventory: Inventory, val actions: List<MenuChoice>)

    private val chests = HashMap<UUID, Chest>()
    private val requests = HashMap<UUID, UUID>()

    private fun allowed(
        player: Player,
        manager: Boolean = false,
    ): Boolean =
        plugin.isEnabled && player.isOnline &&
            Bukkit.getPlayer(player.uniqueId) === player && !player.isDead && player.hasPermission("enthusiaexpress.use") &&
            (!manager || player.hasPermission("enthusiaexpress.mapart.manage")) && combat.mayUseMail(player)

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
                if (player.hasPermission("enthusiaexpress.mapart.manage")) {
                    listOf(
                        MenuChoice("Curator queue") {
                            run(player, "mail mapart queue")
                        },
                    )
                } else {
                    emptyList()
                }
        show(player, "Enthusia Express", "Choose a mailbox or museum service. Packages still use your inventory.", emptyList(), choices)
    }

    fun command(
        player: Player,
        args: Array<String>,
    ): Boolean {
        if (!allowed(player)) return true
        val action = args.getOrNull(1)?.lowercase() ?: "mine"
        try {
            when (action) {
                "submit" -> submission(player)
                "settings" ->
                    show(
                        player,
                        "Mail settings & help",
                        "Notifications: ${plugin.config.getString(
                            "notifications.toast.mode",
                            "native",
                        )}\nDialogs: ${plugin.config.getBoolean(
                            "gui.dialogs.enabled",
                            true,
                        )}\nThese options are configured by the server.\nUse /mail block <player> or /mail unblock <player> to manage who can send you mail.\nSigned letters are sent using the signed book in your hand. Shipment confirmation shows postage before any charge.",
                        emptyList(),
                        listOf(
                            MenuChoice("Blocked players") { run(player, "mail blocked") },
                            MenuChoice("Back") { run(player, "mail") },
                        ),
                    )
                "mine" -> browse(player, MuseumFilter(player.uniqueId))
                "inbox", "queue" -> if (allowed(player, true)) browse(player, MuseumFilter())
                "processed" -> if (allowed(player, true)) browse(player, MuseumFilter(state = MuseumState.DISPLAYED))
                "search" ->
                    browse(
                        player,
                        MuseumFilter(
                            if (player.hasPermission("enthusiaexpress.mapart.manage")) null else player.uniqueId,
                            args.drop(2).joinToString(" "),
                        ),
                    )
                "unassigned" -> if (allowed(player, true)) browse(player, MuseumFilter(unassigned = true))
                "assigned" -> if (allowed(player, true)) browse(player, MuseumFilter(curator = player.uniqueId))
                "view" -> detail(player, requireNotNull(args.getOrNull(2)?.toLongOrNull()))
                "maps" -> manifest(player, requireNotNull(args.getOrNull(2)?.toLongOrNull()), args.getOrNull(3)?.toIntOrNull() ?: 0)
                "list", "filters" -> {
                    val filter = parseFilter(args)
                    if (action == "list") browse(player, filter, args.getOrNull(7)?.toIntOrNull() ?: 0) else filters(player, filter)
                }
                "feedback", "edit" -> {
                    val id = requireNotNull(args.getOrNull(2)?.toLongOrNull())
                    main.complete(store.artwork(id)) { entry, error ->
                        if (error == null && entry != null && allowed(player)) {
                            if (action == "feedback" && allowed(player, true)) {
                                feedback(player, entry)
                            } else if (action == "edit" && entry.submission.mail.sender == player.uniqueId && entry.state == MuseumState.NEEDS_CHANGES) {
                                submission(player, entry)
                            }
                        }
                    }
                }
                "draft", "override" -> {
                    val details = parseDetails(args.drop(2).joinToString(" "))
                    openCargo(player, details, action == "override")
                }
                "accept", "displayed", "assign", "changes" -> {
                    if (!allowed(player, true)) return true
                    val id = requireNotNull(args.getOrNull(2)?.toLongOrNull())
                    val revision = requireNotNull(args.getOrNull(3)?.toLongOrNull())
                    if (action == "assign") {
                        change(player, store.assignArtwork(id, revision, player.uniqueId, player.name), id)
                    } else {
                        change(
                            player,
                            store.reviewArtwork(
                                id,
                                revision,
                                when (action) {
                                    "accept" -> MuseumState.ACCEPTED
                                    "displayed" -> MuseumState.DISPLAYED
                                    else -> MuseumState.NEEDS_CHANGES
                                },
                                player.uniqueId,
                                player.name,
                                args.drop(4).joinToString(" "),
                            ),
                            id,
                        )
                    }
                }
                "revise" -> {
                    val id = requireNotNull(args.getOrNull(2)?.toLongOrNull())
                    val revision = requireNotNull(args.getOrNull(3)?.toLongOrNull())
                    change(player, store.reviseArtwork(id, revision, player.uniqueId, parseDetails(args.drop(4).joinToString(" "))), id)
                }
                else -> player.sendMessage("§e/mail mapart submit|mine|queue|processed|search|assigned|unassigned|view <id>")
            }
        } catch (error: IllegalArgumentException) {
            player.sendMessage("§c${error.message ?: "Invalid museum arguments"}")
        }
        return true
    }

    private fun parseDetails(text: String): MuseumDetails {
        val parts = text.split('|', limit = 4)
        require(parts.size == 4) { "Use title|artist|widthxheight|description" }
        val size = parts[2].trim().lowercase().split('x')
        require(size.size == 2) { "Use dimensions such as 2x3" }
        return MuseumDetails(parts[0].trim(), parts[1].trim(), size[0].toInt(), size[1].toInt(), parts[3].trim())
    }

    private fun submission(
        player: Player,
        entry: MuseumEntry? = null,
    ) {
        if (!allowed(player) || !player.hasPermission("enthusiaexpress.mapart.submit")) return
        if (!plugin.config.getBoolean("mapart.enabled", true)) {
            player.sendMessage("§eMuseum submissions are paused.")
            return
        }
        val old = entry?.details
        val fields =
            listOf(
                MenuField("title", "Artwork title", old?.title.orEmpty()),
                MenuField("artist", "Credited artist", old?.artist ?: player.name, 64),
                MenuField("width", "Width in maps", (old?.width ?: 1).toString(), 2),
                MenuField("height", "Height in maps", (old?.height ?: 1).toString(), 2),
                MenuField("description", "Description (optional)", old?.description.orEmpty(), 500),
                MenuField("override", "Duplicate override (staff: type CONFIRM)", "", 7),
            )
        val choices =
            listOf(
                MenuChoice(if (entry == null) "Continue to cargo" else "Resubmit details") { input ->
                    if (!allowed(player)) return@MenuChoice
                    try {
                        val details =
                            MuseumDetails(
                                input["title"].orEmpty().trim(),
                                input["artist"].orEmpty().trim(),
                                input["width"].orEmpty().toInt(),
                                input["height"].orEmpty().toInt(),
                                input["description"].orEmpty().trim(),
                            )
                        if (entry == null) {
                            openCargo(player, details, input["override"] == "CONFIRM")
                        } else {
                            change(
                                player,
                                store.reviseArtwork(entry.submission.mail.id, entry.revision, player.uniqueId, details),
                                entry.submission.mail.id,
                            )
                        }
                    } catch (
                        error: IllegalArgumentException,
                    ) {
                        player.sendMessage("§c${error.message}. Reopen /mail mapart submit to correct it.")
                    }
                },
                MenuChoice("Cancel") { run(player, "mail") },
            )
        if (!dialogs.show(
                player,
                "Museum artwork",
                "You are donating physical maps. Credit may name someone other than the submitter. Map quantity must match width × height; tile ordering is not inferred.",
                fields,
                choices,
            )
        ) {
            val command = if (entry == null) "/mail mapart draft " else "/mail mapart revise ${entry.submission.mail.id} ${entry.revision} "
            player.sendMessage(
                Component.text(
                    "Enter artwork details: $command title|artist|2x3|description",
                ).clickEvent(ClickEvent.suggestCommand(command + "Title|${player.name}|1x1|")),
            )
        }
    }

    private fun openCargo(
        player: Player,
        details: MuseumDetails,
        override: Boolean,
    ) {
        if (!allowed(player) || !player.hasPermission("enthusiaexpress.mapart.submit")) return
        if (!plugin.config.getBoolean("mapart.enabled", true)) return
        if (override && !player.hasPermission("enthusiaexpress.mapart.duplicates")) {
            player.sendMessage("§cDuplicate override requires museum staff permission.")
            return
        }
        shipping.openArtwork(player, details, override)
    }

    fun browse(
        player: Player,
        filter: MuseumFilter,
        page: Int = 0,
    ) {
        if (!allowed(player)) return
        val scoped = if (player.hasPermission("enthusiaexpress.mapart.manage")) filter else filter.copy(owner = player.uniqueId)
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        main.complete(store.listArtwork(page, scoped)) { entries, error ->
            if (!allowed(player) || requests[player.uniqueId] != request) return@complete
            if (error != null || entries == null) {
                player.sendMessage("§cMuseum records could not be loaded.")
                return@complete
            }
            val actions =
                entries.map {
                        entry ->
                    MenuChoice(
                        "${entry.details.title.take(36)} · ${label(entry.state)}",
                    ) { run(player, "mail mapart view ${entry.submission.mail.id}") }
                }.toMutableList()
            if (page > 0) actions.add(MenuChoice("Previous") { run(player, filterCommand("list", scoped, page - 1)) })
            if (entries.size == 27) actions.add(MenuChoice("Next") { run(player, filterCommand("list", scoped, page + 1)) })
            actions.add(MenuChoice("Filters & search") { run(player, filterCommand("filters", scoped, 0)) })
            actions.add(MenuChoice("Express home") { run(player, "mail") })
            show(
                player,
                if (scoped.owner != null) "My museum submissions" else "Museum curator queue",
                "Page ${page + 1} · ${entries.size} entries. ${if (entries.isEmpty()) "No matching artwork." else "Choose an artwork for credit, map IDs and review history."}",
                emptyList(),
                actions,
            )
        }
    }

    private fun filters(
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
        if (player.hasPermission("enthusiaexpress.mapart.manage")) {
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
                if (!dialogs.show(
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
        show(
            player,
            "Museum filters",
            "Filter ${if (current.owner != null) "your submissions" else "the staff queue"}.",
            emptyList(),
            choices,
        )
    }

    fun detail(
        player: Player,
        id: Long,
    ) {
        if (!allowed(player)) return
        val request = UUID.randomUUID()
        requests[player.uniqueId] = request
        main.complete(store.artwork(id)) { entry, error ->
            if (!allowed(player) || requests[player.uniqueId] != request) return@complete
            if (error != null || entry == null || (entry.submission.mail.sender != player.uniqueId && !player.hasPermission("enthusiaexpress.mapart.manage"))) {
                player.sendMessage("§cArtwork is unavailable.")
                return@complete
            }
            val details = entry.details
            val text = "${details.title}\nArtist: ${details.artist}\nSubmitted by: ${entry.submission.mail.senderName}\n${details.width} × ${details.height} maps · ${label(
                entry.state,
            )}\nCurator: ${entry.curatorName ?: "Unassigned"}\n${details.description}\n${entry.tiles.size} unique map IDs · ${entry.tiles.values.sum()} physical maps\n${entry.feedback}\nRecent history:\n${entry.history.takeLast(
                6,
            ).joinToString(
                "\n",
            ) { "${it.event}: ${it.actor}" }}\n${if (entry.submission.deliveryPending) "Inventory delivery acknowledgment is pending." else ""}"
            val choices = mutableListOf<MenuChoice>()
            choices.add(MenuChoice("Full map manifest") { run(player, "mail mapart maps $id") })
            if (allowed(player, true)) {
                if (entry.state != MuseumState.DISPLAYED) {
                    choices.add(
                        MenuChoice("Assign to me") {
                            if (allowed(player, true)) change(player, store.assignArtwork(id, entry.revision, player.uniqueId, player.name), id)
                        },
                    )
                }
                if (entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.NEEDS_CHANGES)) {
                    choices.add(
                        MenuChoice("Accept artwork") {
                            run(player, "mail mapart accept $id ${entry.revision}")
                        },
                    )
                }
                if (entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.ACCEPTED) && entry.submission.mail.status == MailStatus.UNCLAIMED) {
                    choices.add(
                        MenuChoice(
                            "Request changes",
                        ) {
                            run(player, "mail mapart feedback $id")
                        },
                    )
                }
                if (entry.state == MuseumState.ACCEPTED && entry.submission.mail.status == MailStatus.UNCLAIMED) {
                    choices.add(
                        MenuChoice(
                            "Collect physical maps",
                        ) {
                            if (allowed(player, true)) mailbox.claimMuseum(player, id)
                        },
                    )
                }
                if (entry.state == MuseumState.COLLECTED && !entry.submission.deliveryPending) {
                    choices.add(
                        MenuChoice("Mark displayed") {
                            run(player, "mail mapart displayed $id ${entry.revision}")
                        },
                    )
                }
            }
            if (entry.submission.mail.sender == player.uniqueId && entry.state == MuseumState.NEEDS_CHANGES) {
                choices.add(
                    MenuChoice(
                        "Correct submission details",
                    ) {
                        run(player, "mail mapart edit $id")
                    },
                )
            }
            choices.add(
                MenuChoice("Back to submissions") {
                    run(player, "mail mapart ${if (player.hasPermission("enthusiaexpress.mapart.manage")) "queue" else "mine"}")
                },
            )
            show(player, details.title, text, emptyList(), choices)
        }
    }

    private fun feedback(
        player: Player,
        entry: MuseumEntry,
    ) {
        if (!allowed(player, true)) return
        if (!dialogs.show(
                player,
                "Request artwork changes",
                "Physical maps remain safely held. Explain what the submitter should correct.",
                listOf(MenuField("feedback", "Feedback", "", 500)),
                listOf(
                    MenuChoice("Send feedback") { values ->
                        review(player, entry, MuseumState.NEEDS_CHANGES, values["feedback"].orEmpty())
                    },
                ),
            )
        ) {
            player.sendMessage(
                Component.text(
                    "Use /mail mapart changes ${entry.submission.mail.id} ${entry.revision} <feedback>",
                ).clickEvent(ClickEvent.suggestCommand("/mail mapart changes ${entry.submission.mail.id} ${entry.revision} ")),
            )
        }
    }

    private fun manifest(
        player: Player,
        id: Long,
        page: Int,
    ) {
        if (!allowed(player) || page !in 0..1000) return
        main.complete(store.artwork(id)) { entry, error ->
            if (error != null || entry == null || !allowed(player) || (entry.submission.mail.sender != player.uniqueId && !player.hasPermission("enthusiaexpress.mapart.manage"))) return@complete
            val maps = entry.tiles.entries.toList()
            val start = page * 20
            val choices = mutableListOf(MenuChoice("Artwork details") { run(player, "mail mapart view $id") })
            if (page > 0) choices.add(MenuChoice("Previous maps") { run(player, "mail mapart maps $id ${page - 1}") })
            if (start + 20 < maps.size) choices.add(MenuChoice("More maps") { run(player, "mail mapart maps $id ${page + 1}") })
            show(
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

    private fun review(
        player: Player,
        entry: MuseumEntry,
        state: MuseumState,
        feedback: String,
    ) {
        if (!allowed(player, true)) return
        change(
            player,
            store.reviewArtwork(entry.submission.mail.id, entry.revision, state, player.uniqueId, player.name, feedback),
            entry.submission.mail.id,
        )
    }

    private fun change(
        player: Player,
        future: java.util.concurrent.CompletableFuture<Boolean>,
        id: Long,
    ) {
        main.complete(future) { changed, error ->
            if (!allowed(player)) return@complete
            player.sendMessage(
                if (error == null && changed == true) "§aMuseum record updated." else "§eRecord changed or action is unavailable. Refresh and try again.",
            )
            run(player, "mail mapart view $id")
        }
    }

    private fun show(
        player: Player,
        title: String,
        body: String,
        fields: List<MenuField>,
        actions: List<MenuChoice>,
    ) {
        if (!allowed(player)) return
        if (dialogs.show(player, title, body, fields, actions)) return
        player.closeInventory()
        val inv = Bukkit.createInventory(null, 54, theme.title("museum", title.take(32)))
        actions.take(45).forEachIndexed { index, choice ->
            val item = theme.item("museum.control", Material.PAPER)
            val meta = item.itemMeta!!
            meta.setDisplayName(choice.label.take(70))
            meta.lore = body.lines().flatMap { it.chunked(44) }.take(20)
            item.itemMeta = meta
            inv.setItem(index, item)
        }
        chests[player.uniqueId] = Chest(inv, actions.take(45))
        player.openInventory(inv)
    }

    @EventHandler fun click(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val chest = chests[player.uniqueId]?.takeIf { it.inventory === event.view.topInventory } ?: return
        event.isCancelled = true
        if (event.rawSlot !in chest.actions.indices || event.click != org.bukkit.event.inventory.ClickType.LEFT) return
        Bukkit.getScheduler().runTask(
            plugin,
            Runnable {
                if (allowed(player) && chests[player.uniqueId] === chest && player.openInventory.topInventory === chest.inventory) {
                    chest.actions[event.rawSlot].action(
                        emptyMap(),
                    )
                }
            },
        )
    }

    @EventHandler fun drag(event: InventoryDragEvent) {
        if (chests[event.whoClicked.uniqueId]?.inventory === event.view.topInventory) event.isCancelled = true
    }

    @EventHandler fun close(event: InventoryCloseEvent) {
        if (chests[event.player.uniqueId]?.inventory === event.inventory) chests.remove(event.player.uniqueId)
    }

    @EventHandler fun quit(event: PlayerQuitEvent) {
        chests.remove(event.player.uniqueId)
        requests.remove(event.player.uniqueId)
    }

    fun shutdown() {
        dialogs.close()
        chests.clear()
        requests.clear()
    }

    private fun label(state: MuseumState) = state.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

    private fun run(
        player: Player,
        command: String,
    ) {
        if (allowed(player)) player.performCommand(command)
    }

    private fun filterCommand(
        action: String,
        filter: MuseumFilter,
        page: Int,
    ): String =
        "mail mapart $action ${filter.owner ?: "-"} ${java.net.URLEncoder.encode(
            filter.query,
            java.nio.charset.StandardCharsets.UTF_8,
        ).ifEmpty { "-" }} ${filter.state?.name ?: "-"} ${filter.curator ?: "-"} ${filter.unassigned} $page"

    private fun parseFilter(args: Array<String>): MuseumFilter =
        MuseumFilter(
            args.getOrNull(2)?.takeIf { it != "-" }?.let(UUID::fromString),
            args.getOrNull(
                3,
            )?.takeIf { it != "-" }?.let { java.net.URLDecoder.decode(it, java.nio.charset.StandardCharsets.UTF_8) }.orEmpty(),
            args.getOrNull(4)?.takeIf { it != "-" }?.let(MuseumState::valueOf),
            args.getOrNull(5)?.takeIf { it != "-" }?.let(UUID::fromString),
            args.getOrNull(6) == "true",
        )
}
