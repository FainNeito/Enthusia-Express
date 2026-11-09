package io.enthusia.express.infrastructure.db

import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.domain.MuseumHistory
import io.enthusia.express.domain.MuseumNotice
import java.sql.Connection
import java.util.UUID

/** Audit SQL participates in the catalog owner's serialized transaction. */
internal class MuseumAudit(private val connection: () -> Connection) {
    fun notify(
        entry: MuseumEntry,
        text: String,
    ) {
        val recipient = entry.submission.mail.sender ?: return
        connection().prepareStatement("INSERT INTO museum_updates(recipient,message) VALUES(?,?)").use {
                ps ->
            ps.setString(1, recipient.toString())
            ps.setString(2, text)
            ps.executeUpdate()
        }
    }

    fun history(
        id: Long,
        event: String,
        actor: String,
        note: String,
    ) {
        connection().prepareStatement("INSERT INTO museum_history(mail_id,event,actor,note,created_at) VALUES(?,?,?,?,?)").use { ps ->
            ps.setLong(1, id)
            ps.setString(2, event)
            ps.setString(3, actor.take(64))
            ps.setString(4, note)
            ps.setLong(5, System.currentTimeMillis())
            ps.executeUpdate()
        }
    }

    fun history(id: Long): List<MuseumHistory> =
        connection().prepareStatement(
            "SELECT event,actor,note,created_at FROM museum_history WHERE mail_id=? ORDER BY id DESC LIMIT 20",
        ).use {
                ps ->
            ps.setLong(1, id)
            ps.executeQuery().use {
                    rs ->
                buildList { while (rs.next()) add(MuseumHistory(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4))) }
            }.reversed()
        }

    fun notices(owner: UUID): List<MuseumNotice> =
        connection().prepareStatement("SELECT id,message FROM museum_updates WHERE recipient=? ORDER BY id LIMIT 10").use { ps ->
            ps.setString(1, owner.toString())
            ps.executeQuery().use {
                    rs ->
                buildList { while (rs.next()) add(MuseumNotice(rs.getLong(1), owner, rs.getString(2))) }
            }
        }

    fun acknowledge(
        id: Long,
        owner: UUID,
    ): Boolean =
        connection().prepareStatement(
            "DELETE FROM museum_updates WHERE id=? AND recipient=?",
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, owner.toString())
            ps.executeUpdate() == 1
        }
}
