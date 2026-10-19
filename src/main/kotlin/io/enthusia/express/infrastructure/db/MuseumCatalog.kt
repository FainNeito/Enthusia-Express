@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.db

import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MapartSubmission
import io.enthusia.express.domain.MuseumDetails
import io.enthusia.express.domain.MuseumEntry
import io.enthusia.express.domain.MuseumFilter
import io.enthusia.express.domain.MuseumHistory
import io.enthusia.express.domain.MuseumNotice
import io.enthusia.express.domain.MuseumState
import java.sql.Connection
import java.util.UUID

/** Called only by MailRepository's serialized worker, inside the caller's transaction for mutations. */
internal class MuseumCatalog(private val connection: () -> Connection) {
    fun initialize() {
        connection().createStatement().use { st ->
            st.execute(
                "CREATE TABLE IF NOT EXISTS museum_artworks(mail_id INTEGER PRIMARY KEY REFERENCES mail(id), title TEXT NOT NULL, artist TEXT NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, description TEXT NOT NULL DEFAULT '', state TEXT NOT NULL, revision INTEGER NOT NULL DEFAULT 0, curator_uuid TEXT, curator_name TEXT, feedback TEXT NOT NULL DEFAULT '')",
            )
            st.execute(
                "CREATE TABLE IF NOT EXISTS museum_tiles(mail_id INTEGER NOT NULL REFERENCES mail(id),map_id INTEGER NOT NULL,copies INTEGER NOT NULL,PRIMARY KEY(mail_id,map_id))",
            )
            st.execute("CREATE INDEX IF NOT EXISTS idx_museum_map ON museum_tiles(map_id)")
            st.execute(
                "CREATE TABLE IF NOT EXISTS museum_updates(id INTEGER PRIMARY KEY AUTOINCREMENT,recipient TEXT NOT NULL,message TEXT NOT NULL)",
            )
            st.execute("CREATE INDEX IF NOT EXISTS idx_museum_updates ON museum_updates(recipient,id)")
            st.execute(
                "CREATE TABLE IF NOT EXISTS museum_history(id INTEGER PRIMARY KEY AUTOINCREMENT,mail_id INTEGER NOT NULL,event TEXT NOT NULL,actor TEXT NOT NULL,note TEXT NOT NULL,created_at INTEGER NOT NULL)",
            )
            st.execute("CREATE INDEX IF NOT EXISTS idx_museum_history ON museum_history(mail_id,id)")
            // Old cargo and claim states remain authoritative; never invent an unclaimed state for delivered maps.
            st.execute(
                "INSERT OR IGNORE INTO museum_artworks(mail_id,title,artist,width,height,state) SELECT s.mail_id,substr(s.map_name,1,80),substr(m.sender_name,1,64),1,1,CASE WHEN s.processed_at IS NOT NULL THEN 'DISPLAYED' WHEN m.status='CLAIMED' AND m.delivery_pending=0 THEN 'COLLECTED' ELSE 'ACCEPTED' END FROM mapart_submissions s JOIN mail m ON m.id=s.mail_id",
            )
            st.execute("INSERT OR IGNORE INTO museum_tiles SELECT mail_id,map_id,1 FROM mapart_submissions WHERE map_id IS NOT NULL")
        }
    }

    fun insert(
        id: Long,
        details: MuseumDetails,
        tiles: Map<Int, Int>,
        state: MuseumState,
    ) {
        connection().prepareStatement(
            "INSERT OR REPLACE INTO museum_artworks(mail_id,title,artist,width,height,description,state) VALUES(?,?,?,?,?,?,?)",
        ).use {
                ps ->
            ps.setLong(1, id)
            ps.setString(2, details.title)
            ps.setString(3, details.artist)
            ps.setInt(4, details.width)
            ps.setInt(5, details.height)
            ps.setString(6, details.description)
            ps.setString(7, state.name)
            ps.executeUpdate()
        }
        connection().prepareStatement("INSERT OR REPLACE INTO museum_tiles(mail_id,map_id,copies) VALUES(?,?,?)").use { ps ->
            tiles.forEach { (map, count) ->
                ps.setLong(1, id)
                ps.setInt(2, map)
                ps.setInt(3, count)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        history(id, if (state == MuseumState.AWAITING_REVIEW) "Submitted" else "Legacy import", "System", "")
    }

    fun duplicates(ids: Set<Int>): Set<Int> {
        require(ids.size <= 1024 && ids.all { it >= 0 }) { "Invalid map identifiers" }
        val result = mutableSetOf<Int>()
        connection().prepareStatement("SELECT 1 FROM museum_tiles WHERE map_id=? LIMIT 1").use { ps ->
            ids.forEach { id ->
                ps.setInt(1, id)
                ps.executeQuery().use { if (it.next()) result.add(id) }
            }
        }
        return result.toSet()
    }

    fun metadata(
        id: Long,
        submission: MapartSubmission,
    ): MuseumEntry {
        connection().prepareStatement("SELECT * FROM museum_artworks WHERE mail_id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "Missing artwork metadata for $id" }
                val stored = MuseumState.valueOf(rs.getString("state"))
                val state =
                    if (submission.processedAt != null) {
                        MuseumState.DISPLAYED
                    } else if (stored == MuseumState.ACCEPTED && submission.mail.status == MailStatus.CLAIMED && !submission.deliveryPending) {
                        MuseumState.COLLECTED
                    } else {
                        stored
                    }
                val legacy = MuseumDetails.legacy(rs.getString("title"), rs.getString("artist"))
                return MuseumEntry(
                    submission,
                    MuseumDetails(
                        legacy.title,
                        legacy.artist,
                        rs.getInt("width"),
                        rs.getInt("height"),
                        rs.getString("description"),
                    ),
                    state,
                    rs.getLong(
                        "revision",
                    ),
                    rs.getString(
                        "curator_uuid",
                    )?.let(UUID::fromString),
                    rs.getString("curator_name"), rs.getString("feedback"), tiles(id), history(id),
                )
            }
        }
    }

    private fun tiles(id: Long): Map<Int, Int> =
        connection().prepareStatement("SELECT map_id,copies FROM museum_tiles WHERE mail_id=? ORDER BY map_id").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> buildMap { while (rs.next()) put(rs.getInt(1), rs.getInt(2)) } }
        }

    fun ids(
        page: Int,
        filter: MuseumFilter,
    ): List<Long> {
        require(page in 0..1_000_000)
        val sql = "SELECT a.mail_id FROM museum_artworks a JOIN mail m ON m.id=a.mail_id WHERE (? IS NULL OR m.sender_uuid=?) AND (?='' OR instr(lower(a.title),lower(?))>0 OR instr(lower(a.artist),lower(?))>0) AND (? IS NULL OR (CASE WHEN a.state='ACCEPTED' AND m.status='CLAIMED' AND m.delivery_pending=0 THEN 'COLLECTED' ELSE a.state END)=?) AND (? IS NULL OR a.curator_uuid=?) AND (?=0 OR a.curator_uuid IS NULL) ORDER BY m.created_at DESC,a.mail_id DESC LIMIT 27 OFFSET ?"
        return connection().prepareStatement(sql).use { ps ->
            ps.setString(1, filter.owner?.toString())
            ps.setString(2, filter.owner?.toString())
            (3..5).forEach { ps.setString(it, filter.query) }
            ps.setString(6, filter.state?.name)
            ps.setString(7, filter.state?.name)
            ps.setString(8, filter.curator?.toString())
            ps.setString(9, filter.curator?.toString())
            ps.setInt(10, if (filter.unassigned) 1 else 0)
            ps.setLong(11, page.toLong() * 27)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getLong(1)) } }
        }
    }

    fun review(
        entry: MuseumEntry,
        revision: Long,
        state: MuseumState,
        manager: UUID,
        name: String,
        feedback: String,
    ): Boolean {
        require(
            feedback.length <= 500 && feedback.none { it.code < 32 || it == '§' },
        ) { "Feedback must be plain text up to 500 characters" }
        val waiting = entry.submission.mail.status == MailStatus.UNCLAIMED && !entry.submission.deliveryPending
        val allowed =
            when (state) {
                MuseumState.ACCEPTED -> waiting && entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.NEEDS_CHANGES)
                MuseumState.NEEDS_CHANGES -> waiting && entry.state in setOf(MuseumState.AWAITING_REVIEW, MuseumState.ACCEPTED) && feedback.isNotBlank()
                MuseumState.DISPLAYED -> entry.state == MuseumState.COLLECTED && !entry.submission.deliveryPending
                else -> false
            }
        if (!allowed) return false
        val changed =
            connection().prepareStatement(
                "UPDATE museum_artworks SET state=?,feedback=?,revision=revision+1 WHERE mail_id=? AND revision=?",
            ).use {
                    ps ->
                ps.setString(1, state.name)
                ps.setString(2, feedback)
                ps.setLong(3, entry.submission.mail.id)
                ps.setLong(4, revision)
                ps.executeUpdate() == 1
            }
        if (changed) {
            history(entry.submission.mail.id, state.name, name, feedback)
            if (state == MuseumState.DISPLAYED) {
                connection().prepareStatement("UPDATE mapart_submissions SET processed_at=?,processed_by=? WHERE mail_id=?").use {
                        ps ->
                    ps.setLong(1, System.currentTimeMillis())
                    ps.setString(2, manager.toString())
                    ps.setLong(3, entry.submission.mail.id)
                    ps.executeUpdate()
                }
            }
            notify(
                entry,
                "${entry.details.title}: ${state.name.lowercase().replace(
                    '_',
                    ' ',
                )}. Reviewed by $name.${if (feedback.isBlank()) "" else " $feedback"}",
            )
        }
        return changed
    }

    fun assign(
        entry: MuseumEntry,
        revision: Long,
        manager: UUID,
        name: String,
    ): Boolean {
        val changed =
            connection().prepareStatement(
                "UPDATE museum_artworks SET curator_uuid=?,curator_name=?,revision=revision+1 WHERE mail_id=? AND revision=? AND state<>'DISPLAYED'",
            ).use {
                    ps ->
                ps.setString(1, manager.toString())
                ps.setString(2, name.take(64))
                ps.setLong(3, entry.submission.mail.id)
                ps.setLong(4, revision)
                ps.executeUpdate() == 1
            }
        if (changed) history(entry.submission.mail.id, "Assigned curator", name, "")
        return changed
    }

    fun revise(
        entry: MuseumEntry,
        revision: Long,
        sender: UUID,
        details: MuseumDetails,
    ): Boolean {
        if (entry.submission.mail.sender != sender || entry.state != MuseumState.NEEDS_CHANGES || details.width * details.height != entry.tiles.values.sum()) return false
        val changed =
            connection().prepareStatement(
                "UPDATE museum_artworks SET title=?,artist=?,width=?,height=?,description=?,state='AWAITING_REVIEW',feedback='',revision=revision+1 WHERE mail_id=? AND revision=? AND state='NEEDS_CHANGES'",
            ).use {
                    ps ->
                ps.setString(1, details.title)
                ps.setString(2, details.artist)
                ps.setInt(3, details.width)
                ps.setInt(4, details.height)
                ps.setString(5, details.description)
                ps.setLong(6, entry.submission.mail.id)
                ps.setLong(7, revision)
                ps.executeUpdate() == 1
            }
        if (changed) history(entry.submission.mail.id, "Revised and resubmitted", entry.submission.mail.senderName, "")
        return changed
    }

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

    fun collected(entry: MuseumEntry) {
        val changed =
            connection().prepareStatement(
                "UPDATE museum_artworks SET state='COLLECTED',revision=revision+1 WHERE mail_id=? AND state='ACCEPTED'",
            ).use {
                    ps ->
                ps.setLong(1, entry.submission.mail.id)
                ps.executeUpdate() == 1
            }
        if (changed) {
            history(entry.submission.mail.id, "Collected", entry.submission.mail.recipientName, "")
            notify(entry, "${entry.details.title}: collected by ${entry.submission.mail.recipientName}; awaiting museum display.")
        }
    }

    private fun history(
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

    private fun history(id: Long): List<MuseumHistory> =
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
