@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.gui

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MuseumDetails
import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.domain.MuseumState
import io.enthusia.express.infrastructure.util.MainThread
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** Metadata input delegates physical ownership to ShippingService. */
internal class MuseumInput(private val plugin: JavaPlugin, private val store: MailStore,
    private val main: MainThread, private val shipping: ShippingService,
    private val view: MuseumView, private val allowed: (Player) -> Boolean) {
    fun parseDetails(text: String): MuseumDetails {
        val parts = text.split('|', limit = 4)
        require(parts.size == 4) { "Use title|artist|widthxheight|description" }
        val size = parts[2].trim().lowercase().split('x')
        require(size.size == 2) { "Use dimensions such as 2x3" }
        return MuseumDetails(parts[0].trim(), parts[1].trim(), size[0].toInt(), size[1].toInt(), parts[3].trim())
    }

    fun submission(
        player: Player,
        entry: MuseumEntry? = null,
    ) {
        if (!allowed(player) || !player.hasPermission(SUBMIT_PERMISSION)) return
        if (!plugin.config.getBoolean("mapart.enabled", true)) {
            player.sendMessage("§eMuseum submissions are paused.")
            return
        }
        val old = entry?.details
        val fields = fields(player, old)
        val choices =
            listOf(
                MenuChoice(if (entry == null) "Continue to cargo" else "Resubmit details") { input ->
                    submitted(player, entry, input)
                },
                MenuChoice("Cancel") { run(player, "mail") },
            )
        if (!view.dialogs.show(
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

    private fun fields(player: Player, old: MuseumDetails?): List<MenuField> {
        return listOf(
                MenuField("title", "Artwork title", old?.title.orEmpty()),
                MenuField("artist", "Credited artist", old?.artist ?: player.name, 64),
                MenuField("width", "Width in maps", (old?.width ?: 1).toString(), 2),
                MenuField("height", "Height in maps", (old?.height ?: 1).toString(), 2),
                MenuField("description", "Description (optional)", old?.description.orEmpty(), 500),
                MenuField("override", "Duplicate override (staff: type CONFIRM)", "", 7),
            )
    }

    private fun submitted(player: Player, entry: MuseumEntry?, input: Map<String, String>) {
        if (!allowed(player)) return
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
    }

    fun openCargo(
        player: Player,
        details: MuseumDetails,
        override: Boolean,
    ) {
        if (!allowed(player) || !player.hasPermission(SUBMIT_PERMISSION)) return
        if (!plugin.config.getBoolean("mapart.enabled", true)) return
        if (override && !player.hasPermission(DUPLICATE_PERMISSION)) {
            player.sendMessage("§cDuplicate override requires museum staff permission.")
            return
        }
        shipping.openArtwork(player, details, override)
    }

    fun feedback(
        player: Player,
        entry: MuseumEntry,
    ) {
        if (!allowed(player) || !player.hasPermission(MANAGE_PERMISSION)) return
        if (!view.dialogs.show(
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

    private fun review(
        player: Player,
        entry: MuseumEntry,
        state: MuseumState,
        feedback: String,
    ) {
        if (!allowed(player) || !player.hasPermission(MANAGE_PERMISSION)) return
        change(
            player,
            store.reviewArtwork(entry.submission.mail.id, entry.revision, state, player.uniqueId, player.name, feedback),
            entry.submission.mail.id,
        )
    }

    @Suppress("LabeledExpression") // Ignore disconnected callback work before any follow-up navigation.
    fun change(
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

    private fun run(player: Player, command: String) { if (allowed(player)) player.performCommand(command) }
}
