// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.db

import io.enthusia.express.domain.MailBlockedException
import io.enthusia.express.application.MAIL_PAGE_SIZE
import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailSummary
import io.enthusia.express.domain.MailType
import io.enthusia.express.domain.MapartSubmission
import io.enthusia.express.domain.MapartQueue
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.OptionalLong
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import org.bukkit.plugin.java.JavaPlugin
import org.sqlite.SQLiteConfig

// This adapter owns one serialized connection across the four small mail ports.
@Suppress("TooManyFunctions")
class MailRepository(
    plugin: JavaPlugin?,
    private val dbFile: File,
    private val busyTimeout: Int,
) : MailStore {
    constructor(plugin: JavaPlugin, dbFile: File) :
        this(plugin, dbFile, plugin.config.getInt("database.busy-timeout-ms", 5000))

    private val INVALID_PAGE = "Invalid page"
    private val PAGE_COLUMNS = "id,sender_uuid,sender_name,recipient_uuid,recipient_name,type,status," +
        "X'' AS payload,packed_item_count,created_at,updated_at,unread,return_delivery,claim_generation," +
        "delivery_pending,original_recipient_name"
    private val executor = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue<Runnable>(plugin?.config?.getInt("database.max-queued-operations", 256) ?: 256),
        { runnable -> Thread(runnable, "EnthusiaExpress-SQLite").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy())
    private val logger = plugin?.logger ?: java.util.logging.Logger.getLogger(MailRepository::class.java.name)
    private lateinit var connection: Connection
    private var closed = false

    /** Create or migrate storage before accepting asynchronous mail operations. */
    override fun initialize(): CompletableFuture<Void> = run {
        Files.createDirectories(dbFile.absoluteFile.parentFile.toPath())
        Class.forName("org.sqlite.JDBC")
        connection = openConnection()
        inTransaction {
            connection.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS mail (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      sender_uuid TEXT,
                      sender_name TEXT NOT NULL,
                      recipient_uuid TEXT NOT NULL,
                      recipient_name TEXT NOT NULL,
                      type TEXT NOT NULL,
                      status TEXT NOT NULL,
                      payload BLOB NOT NULL,
                      packed_item_count INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL,
                      unread INTEGER NOT NULL DEFAULT 1,
                      return_delivery INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                st.execute("CREATE INDEX IF NOT EXISTS idx_mail_recipient_status ON mail(recipient_uuid, status, type)")
                st.execute("CREATE INDEX IF NOT EXISTS idx_mail_expiration ON mail(status, updated_at)")
                st.execute("CREATE INDEX IF NOT EXISTS idx_mail_sent ON mail(sender_uuid, type, created_at DESC, id DESC)")
                st.execute("CREATE INDEX IF NOT EXISTS idx_mail_notifications ON mail(recipient_uuid, id)")
                st.execute("CREATE TABLE IF NOT EXISTS mail_blocks (owner_uuid TEXT NOT NULL, sender_uuid TEXT NOT NULL, sender_name TEXT NOT NULL, PRIMARY KEY(owner_uuid,sender_uuid))")
                st.execute("""CREATE TABLE IF NOT EXISTS mapart_submissions (
                    mail_id INTEGER PRIMARY KEY REFERENCES mail(id),
                    token TEXT NOT NULL UNIQUE,
                    map_id INTEGER,
                    map_name TEXT NOT NULL,
                    submitted_at INTEGER NOT NULL,
                    processed_at INTEGER,
                    processed_by TEXT
                )""")
                st.execute("CREATE INDEX IF NOT EXISTS idx_mapart_status ON mapart_submissions(processed_at,mail_id)")
                val columns = HashSet<String>()
                st.executeQuery("PRAGMA table_info(mail)").use { rs ->
                    while (rs.next()) columns.add(rs.getString("name"))
                }
                if ("claim_generation" !in columns)
                    st.execute("ALTER TABLE mail ADD COLUMN claim_generation INTEGER NOT NULL DEFAULT 0")
                if ("delivery_pending" !in columns)
                    st.execute("ALTER TABLE mail ADD COLUMN delivery_pending INTEGER NOT NULL DEFAULT 0")
                if ("original_recipient_name" !in columns) {
                    st.execute("ALTER TABLE mail ADD COLUMN original_recipient_name TEXT")
                    st.execute("UPDATE mail SET original_recipient_name=recipient_name WHERE return_delivery=0")
                }
                val mapartColumns = HashSet<String>()
                st.executeQuery("PRAGMA table_info(mapart_submissions)").use { rs ->
                    while (rs.next()) mapartColumns.add(rs.getString("name"))
                }
                if ("processed_by" !in mapartColumns) st.execute("ALTER TABLE mapart_submissions ADD COLUMN processed_by TEXT")
                // A prior test build addressed pending maps to one person. Rehome only unclaimed rows.
                st.execute("UPDATE mail SET recipient_uuid='${MapartQueue.ID}',recipient_name='${MapartQueue.NAME}'," +
                    "original_recipient_name='${MapartQueue.NAME}' WHERE status='UNCLAIMED' AND delivery_pending=0" +
                    " AND id IN (SELECT mail_id FROM mapart_submissions)")
            }
        }
    }

    /** Store a package payload and its return-delivery state. */
    override fun insertPackage(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                               payload: ByteArray, packedCount: Int, returnDelivery: Boolean): CompletableFuture<Long> =
        insertMail(sender, senderName, recipient, recipientName, MailType.PACKAGE, payload, packedCount, returnDelivery)

    /** Store an immutable payload copy and return its generated mail identifier. */
    override fun insertMail(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                            type: MailType, payload: ByteArray, packedCount: Int, returned: Boolean): CompletableFuture<Long> {
        val copy = payload.clone()
        return supply { inTransaction { insert(InsertData(sender, senderName, recipient, recipientName, type, copy, packedCount, returned)) } }
    }

    /** Persist a map and its identifying metadata atomically; duplicate recovery tokens return the first row. */
    override fun insertMapart(sender: UUID, senderName: String, payload: ByteArray,
                              mapId: Int?, mapName: String, token: UUID): CompletableFuture<Long> {
        require(mapName.isNotBlank() && mapName.length <= 128)
        require(payload.isNotEmpty())
        val copy = payload.clone()
        return supply {
            inTransaction {
                val existing = connection.prepareStatement("SELECT mail_id FROM mapart_submissions WHERE token=?").use { ps ->
                    ps.setString(1, token.toString())
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getLong(1) else null }
                }
                if (existing != null) existing else {
                    val id = insert(InsertData(sender, senderName, MapartQueue.ID, MapartQueue.NAME,
                        MailType.PACKAGE, copy, 1, false), false)
                    connection.prepareStatement("INSERT INTO mapart_submissions(mail_id,token,map_id,map_name,submitted_at) VALUES(?,?,?,?,?)").use { ps ->
                        ps.setLong(1, id)
                        ps.setString(2, token.toString())
                        if (mapId == null) ps.setNull(3, java.sql.Types.INTEGER) else ps.setInt(3, mapId)
                        ps.setString(4, mapName)
                        ps.setLong(5, System.currentTimeMillis())
                        ps.executeUpdate()
                    }
                    id
                }
            }
        }
    }

    /** Keep manager intake separate from normal player mail, including processed history. */
    override fun listMapart(page: Int, processed: Boolean): CompletableFuture<List<MapartSubmission>> {
        if (page < 0 || page > 1_000_000) return CompletableFuture.failedFuture(IllegalArgumentException(INVALID_PAGE))
        return supply {
            val rows = ArrayList<MapartSubmission>()
            val sql = "SELECT $PAGE_COLUMNS,s.map_id,s.map_name,s.submitted_at,s.processed_at,s.processed_by FROM mapart_submissions s JOIN mail m ON m.id=s.mail_id" +
                " WHERE s.processed_at IS ${if (processed) "NOT NULL" else "NULL"}" +
                " ORDER BY s.submitted_at DESC,s.mail_id DESC LIMIT $MAIL_PAGE_SIZE OFFSET ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setInt(1, page * MAIL_PAGE_SIZE)
                ps.executeQuery().use { rs -> while (rs.next()) rows.add(readMapart(rs)) }
            }
            rows
        }
    }

    /** Resolve a selected intake row without exposing unrelated package records. */
    override fun getMapart(id: Long): CompletableFuture<MapartSubmission?> = supply {
        connection.prepareStatement("SELECT m.*,s.map_id,s.map_name,s.submitted_at,s.processed_at,s.processed_by FROM mapart_submissions s JOIN mail m ON m.id=s.mail_id WHERE m.id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) readMapart(rs) else null }
        }
    }

    /** Require a confirmed inventory delivery before the manager can mark a map processed. */
    override fun markMapartProcessed(id: Long, manager: UUID): CompletableFuture<Boolean> = supply {
        connection.prepareStatement("UPDATE mapart_submissions SET processed_at=?,processed_by=? WHERE mail_id=?" +
            " AND processed_at IS NULL AND EXISTS" +
            " (SELECT 1 FROM mail WHERE id=? AND status='CLAIMED' AND delivery_pending=0)").use { ps ->
            ps.setLong(1, System.currentTimeMillis())
            ps.setString(2, manager.toString())
            ps.setLong(3, id)
            ps.setLong(4, id)
            ps.executeUpdate() == 1
        }
    }

    /** Reserve one shared-queue map for one manager, preventing a second manager from claiming it. */
    override fun claimMapart(record: MailRecord, manager: UUID, managerName: String): CompletableFuture<Boolean> = supply {
        if (record.type != MailType.PACKAGE || record.status != MailStatus.UNCLAIMED || record.recipient != MapartQueue.ID) false
        else connection.prepareStatement("UPDATE mail SET recipient_uuid=?,recipient_name=?,status='CLAIMED'," +
            "unread=0,delivery_pending=1,updated_at=? WHERE id=? AND recipient_uuid=? AND status='UNCLAIMED'" +
            " AND claim_generation=? AND id IN (SELECT mail_id FROM mapart_submissions WHERE processed_at IS NULL)").use { ps ->
            ps.setString(1, manager.toString())
            ps.setString(2, managerName)
            ps.setLong(3, System.currentTimeMillis())
            ps.setLong(4, record.id)
            ps.setString(5, MapartQueue.ID.toString())
            ps.setLong(6, record.claimGeneration)
            ps.executeUpdate() == 1
        }
    }

    private fun readMapart(rs: ResultSet): MapartSubmission = MapartSubmission(
        read(rs), rs.getInt("map_id").let { if (rs.wasNull()) null else it }, rs.getString("map_name"),
        rs.getLong("submitted_at"), rs.getLong("processed_at").let { if (rs.wasNull()) null else it },
        rs.getInt("delivery_pending") != 0, rs.getString("processed_by")?.let(UUID::fromString))

    private data class InsertData(
        val sender: UUID?, val senderName: String, val recipient: UUID, val recipientName: String,
        val type: MailType, val payload: ByteArray, val packedCount: Int, val returned: Boolean,
    )

    /** Preserve recovery deliveries while enforcing recipient preferences for new mail. */
    private fun rejectBlocked(data: InsertData) = !data.returned && data.sender != null && blocked(data.recipient, data.sender)

    /** Bind a prepared mail record to the shared connection and return its generated identifier. */
    private fun insert(data: InsertData, respectPersonalBlocks: Boolean = true): Long {
        if (respectPersonalBlocks && rejectBlocked(data))
            throw MailBlockedException()
        val now = System.currentTimeMillis()
        val sql = "INSERT INTO" +
            " mail(sender_uuid,sender_name,recipient_uuid,recipient_name,type,status,payload,packed_item_count,created_at,updated_at,unread,return_delivery,original_recipient_name)" +
            " VALUES(?,?,?,?,?,?,?,?,?,?,1,?,?)"
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, data.sender?.toString())
            ps.setString(2, data.senderName)
            ps.setString(3, data.recipient.toString())
            ps.setString(4, data.recipientName)
            ps.setString(5, data.type.name)
            ps.setString(6, if (data.returned) MailStatus.RETURNED.name else MailStatus.UNCLAIMED.name)
            ps.setBytes(7, data.payload)
            ps.setInt(8, data.packedCount)
            ps.setLong(9, now)
            ps.setLong(10, now)
            ps.setInt(11, if (data.returned) 1 else 0)
            ps.setString(12, if (data.returned) null else data.recipientName)
            ps.executeUpdate()
            ps.generatedKeys.use { rs ->
                if (!rs.next()) throw SQLException("Missing generated mail ID")
                return rs.getLong(1)
            }
        }
    }

    /** Snapshot recipients; the entire broadcast commits or rolls back together. */
    override fun announce(sender: UUID?, senderName: String, recipients: Map<UUID, String>, payload: ByteArray): CompletableFuture<Int> {
        val snapshot = java.util.Map.copyOf(recipients)
        val copy = payload.clone()
        return supply {
            inTransaction {
                var delivered = 0
                for ((recipient, recipientName) in snapshot) {
                    if (sender == null || !blocked(recipient, sender)) {
                        insert(InsertData(sender, senderName, recipient, recipientName, MailType.ANNOUNCEMENT, copy, 0, false))
                        delivered++
                    }
                }
                delivered
            }
        }
    }

    /** Load one bounded inbox page for the requested recipient and mail type. */
    override fun listInbox(recipient: UUID, type: MailType): CompletableFuture<List<MailRecord>> = listInbox(recipient, type, 0)

    /** Load one bounded inbox page for the requested recipient and mail type. */
    override fun listInbox(recipient: UUID, type: MailType, page: Int): CompletableFuture<List<MailRecord>> {
        if (page < 0 || page > 1_000_000) return CompletableFuture.failedFuture(IllegalArgumentException(INVALID_PAGE))
        return supply {
            val out = ArrayList<MailRecord>()
            val sql = "SELECT $PAGE_COLUMNS FROM mail WHERE recipient_uuid=? AND type=? AND status IN (?,?)" +
                " AND id NOT IN (SELECT mail_id FROM mapart_submissions) ORDER BY" +
                " created_at DESC, id DESC LIMIT $MAIL_PAGE_SIZE OFFSET ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, recipient.toString())
                ps.setString(2, type.name)
                ps.setString(3, MailStatus.UNCLAIMED.name)
                ps.setString(4, MailStatus.RETURNED.name)
                ps.setInt(5, page * MAIL_PAGE_SIZE)
                ps.executeQuery().use { rs -> while (rs.next()) out.add(read(rs)) }
            }
            out
        }
    }

    /** Query only this sender's retained rows with stable pagination and original recipient metadata. */
    override fun listSent(sender: UUID, type: MailType, page: Int): CompletableFuture<List<io.enthusia.express.domain.SentMailRecord>> {
        if (page < 0 || page > 1_000_000) return CompletableFuture.failedFuture(IllegalArgumentException(INVALID_PAGE))
        return supply {
            val out = ArrayList<io.enthusia.express.domain.SentMailRecord>()
            connection.prepareStatement("SELECT $PAGE_COLUMNS FROM mail WHERE sender_uuid=? AND type=?" +
                " AND id NOT IN (SELECT mail_id FROM mapart_submissions) ORDER BY created_at DESC, id DESC LIMIT $MAIL_PAGE_SIZE OFFSET ?").use { ps ->
                ps.setString(1, sender.toString())
                ps.setString(2, type.name)
                ps.setInt(3, page * MAIL_PAGE_SIZE)
                ps.executeQuery().use { rs ->
                    while (rs.next()) out.add(io.enthusia.express.domain.SentMailRecord(
                        read(rs), rs.getString("original_recipient_name"), rs.getInt("delivery_pending") != 0))
                }
            }
            out
        }
    }

    /** Look up a mail row by identifier, returning null when absent. */
    override fun get(id: Long): CompletableFuture<MailRecord?> = supply {
        connection.prepareStatement("SELECT * FROM mail WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }
    }

    /** Reserve an eligible package once while retaining its sending allowance until delivery. */
    override fun claim(id: Long, recipient: UUID): CompletableFuture<Boolean> = supply {
        val current = connection.prepareStatement(
            "SELECT * FROM mail WHERE id=? AND recipient_uuid=? AND type='PACKAGE' AND status IN (?,?)"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, recipient.toString())
            ps.setString(3, MailStatus.UNCLAIMED.name)
            ps.setString(4, MailStatus.RETURNED.name)
            ps.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }
        if (current == null) false else updateClaim(current)
    }

    /** Reject stale snapshots so compensation always identifies the exact reservation. */
    override fun claim(record: MailRecord): CompletableFuture<Boolean> = supply {
        if (record.type != MailType.PACKAGE || record.status !in setOf(MailStatus.UNCLAIMED, MailStatus.RETURNED)) false
        else updateClaim(record)
    }

    /** Conditionally transition the observed package into its claimed state with a held delivery reservation. */
    private fun updateClaim(current: MailRecord): Boolean {
        val id = current.id
        val recipient = current.recipient
        val next = if (current.status == MailStatus.RETURNED) MailStatus.RETURN_CLAIMED else MailStatus.CLAIMED
        return connection.prepareStatement(
            "UPDATE mail SET status=?, unread=0, delivery_pending=1, updated_at=? WHERE id=? AND recipient_uuid=? AND status=? AND type='PACKAGE' AND claim_generation=?"
        ).use { ps ->
            ps.setString(1, next.name)
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, id)
            ps.setString(4, recipient.toString())
            ps.setString(5, current.status.name)
            ps.setLong(6, current.claimGeneration)
            ps.executeUpdate() == 1
        }
    }

    /** Restore an undelivered pending claim to its original status and timestamp. */
    override fun restoreClaim(record: MailRecord): CompletableFuture<Boolean> = supply {
        require(record.type == MailType.PACKAGE && record.status in setOf(MailStatus.UNCLAIMED, MailStatus.RETURNED))
        val mapart = connection.prepareStatement("SELECT 1 FROM mapart_submissions WHERE mail_id=?").use { ps ->
            ps.setLong(1, record.id)
            ps.executeQuery().use { it.next() }
        }
        if (mapart) connection.prepareStatement(
            "UPDATE mail SET recipient_uuid=?,recipient_name=?,status='UNCLAIMED',unread=1," +
                "delivery_pending=0,claim_generation=claim_generation+1,updated_at=?" +
                " WHERE id=? AND recipient_uuid=? AND status='CLAIMED' AND delivery_pending=1" +
                " AND claim_generation=? AND id IN (SELECT mail_id FROM mapart_submissions WHERE processed_at IS NULL)"
        ).use { ps ->
            ps.setString(1, MapartQueue.ID.toString())
            ps.setString(2, MapartQueue.NAME)
            ps.setLong(3, record.updatedAt)
            ps.setLong(4, record.id)
            ps.setString(5, record.recipient.toString())
            ps.setLong(6, record.claimGeneration)
            ps.executeUpdate() == 1
        } else connection.prepareStatement(
            "UPDATE mail SET status=?, unread=1, delivery_pending=0, claim_generation=claim_generation+1, updated_at=? WHERE id=? AND recipient_uuid=? AND status=? AND delivery_pending=1 AND type='PACKAGE' AND claim_generation=?"
        ).use { ps ->
            ps.setString(1, record.status.name)
            ps.setLong(2, record.updatedAt)
            ps.setLong(3, record.id)
            ps.setString(4, record.recipient.toString())
            ps.setString(5, if (record.status == MailStatus.RETURNED) "RETURN_CLAIMED" else "CLAIMED")
            ps.setLong(6, record.claimGeneration)
            ps.executeUpdate() == 1
        }
    }

    /** Release the sending allowance only after the server has delivered the package. */
    override fun confirmDelivery(id: Long, recipient: UUID): CompletableFuture<Boolean> = supply {
        connection.prepareStatement(
            "UPDATE mail SET delivery_pending=0 WHERE id=? AND recipient_uuid=?" +
                " AND type='PACKAGE' AND status IN ('CLAIMED','RETURN_CLAIMED') AND delivery_pending=1"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, recipient.toString())
            ps.executeUpdate() == 1
        }
    }

    /** Clear unread state only for eligible text mail owned by the recipient. */
    override fun markRead(id: Long, recipient: UUID): CompletableFuture<Boolean> = supply {
        connection.prepareStatement(
            "UPDATE mail SET unread=0 WHERE id=? AND recipient_uuid=? AND type IN ('LETTER','ANNOUNCEMENT') AND status='UNCLAIMED'"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, recipient.toString())
            ps.executeUpdate() == 1
        }
    }

    /** One recipient-scoped update clears both text categories without loading their payloads. */
    override fun markAllTextRead(recipient: UUID): CompletableFuture<Int> = supply {
        connection.prepareStatement(
            "UPDATE mail SET unread=0 WHERE recipient_uuid=? AND type IN ('LETTER','ANNOUNCEMENT')" +
                " AND status='UNCLAIMED' AND unread=1"
        ).use { ps ->
            ps.setString(1, recipient.toString())
            ps.executeUpdate()
        }
    }

    /** One transaction, no stale read/modify/write window; text mail never enters RTS. */
    override fun expire(now: Long, returnCutoff: Long, purgeCutoff: Long, textCutoff: Long): CompletableFuture<Int> = supply {
        inTransaction {
            var changed = connection.prepareStatement(
                "UPDATE mail SET status='PURGED', payload=X'', updated_at=? WHERE" +
                    " ((type='PACKAGE' AND status='RETURNED' AND updated_at<?) OR (type IN" +
                    " ('LETTER','ANNOUNCEMENT') AND status='UNCLAIMED' AND created_at<?))" +
                    " AND id NOT IN (SELECT mail_id FROM mapart_submissions)"
            ).use { ps ->
                ps.setLong(1, now)
                ps.setLong(2, purgeCutoff)
                ps.setLong(3, textCutoff)
                ps.executeUpdate()
            }
            changed += connection.prepareStatement(
                "UPDATE mail SET recipient_uuid=COALESCE(sender_uuid,recipient_uuid)," +
                    " recipient_name=CASE WHEN sender_uuid IS NULL THEN recipient_name ELSE" +
                    " sender_name END, status=CASE WHEN sender_uuid IS NULL THEN 'PURGED'" +
                    " ELSE 'RETURNED' END, payload=CASE WHEN sender_uuid IS NULL THEN X''" +
                    " ELSE payload END, unread=1, return_delivery=1, updated_at=? WHERE" +
                " type='PACKAGE' AND status='UNCLAIMED' AND updated_at<?" +
                    " AND id NOT IN (SELECT mail_id FROM mapart_submissions)"
            ).use { ps ->
                ps.setLong(1, now)
                ps.setLong(2, returnCutoff)
                ps.executeUpdate()
            }
            changed
        }
    }


    /** Atomically check the sender-recipient allowance and insert mail, returning empty when occupied. */
    override fun insertMailLimited(sender: UUID, senderName: String, recipient: UUID, recipientName: String,
                                   type: MailType, payload: ByteArray, packedCount: Int, enforceLimit: Boolean): CompletableFuture<OptionalLong> {
        if (type == MailType.ANNOUNCEMENT) return CompletableFuture.failedFuture(
            IllegalArgumentException("Announcements do not use outstanding-mail limits"))
        val copy = payload.clone()
        return supply {
            inTransaction {
                if (blocked(recipient, sender)) throw MailBlockedException()
                if (enforceLimit && hasOutstanding(sender, recipient, type)) OptionalLong.empty()
                else OptionalLong.of(insert(InsertData(sender, senderName, recipient, recipientName, type, copy, packedCount, false)))
            }
        }
    }

    /** Check unresolved mail and pending normal-package deliveries for the same sender and recipient. */
    private fun hasOutstanding(sender: UUID, recipient: UUID, type: MailType): Boolean {
        val sql = "SELECT 1 FROM mail WHERE sender_uuid=? AND recipient_uuid=? AND type=?" +
            " AND ((status='UNCLAIMED' AND (?='PACKAGE' OR unread=1))" +
            " OR (type='PACKAGE' AND status='CLAIMED' AND delivery_pending=1)) LIMIT 1"
        return connection.prepareStatement(sql).use { ps ->
            ps.setString(1, sender.toString())
            ps.setString(2, recipient.toString())
            ps.setString(3, type.name)
            ps.setString(4, type.name)
            ps.executeQuery().use { it.next() }
        }
    }

    /** Serialize block updates with delivery transactions and reject self-blocks. */
    override fun setBlocked(owner: UUID, sender: UUID, senderName: String, enabled: Boolean): CompletableFuture<Void> {
        if (owner == sender) return CompletableFuture.failedFuture(IllegalArgumentException("Cannot block yourself"))
        return run {
            val sql = if (enabled) "INSERT INTO mail_blocks(owner_uuid,sender_uuid,sender_name) VALUES(?,?,?) ON CONFLICT(owner_uuid,sender_uuid) DO UPDATE SET sender_name=excluded.sender_name"
                else "DELETE FROM mail_blocks WHERE owner_uuid=? AND sender_uuid=?"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, owner.toString())
                ps.setString(2, sender.toString())
                if (enabled) ps.setString(3, senderName)
                ps.executeUpdate()
            }
        }
    }

    /** Read block preferences on the serialized connection. */
    override fun isBlocked(owner: UUID, sender: UUID): CompletableFuture<Boolean> = supply { blocked(owner, sender) }

    /** Inspect a block within the caller's current storage transaction. */
    private fun blocked(owner: UUID, sender: UUID): Boolean =
        connection.prepareStatement("SELECT 1 FROM mail_blocks WHERE owner_uuid=? AND sender_uuid=?").use { ps ->
            ps.setString(1, owner.toString())
            ps.setString(2, sender.toString())
            ps.executeQuery().use { it.next() }
        }

    /** List only this player's preferences using bounded stable pagination. */
    override fun listBlocked(owner: UUID, page: Int): CompletableFuture<List<String>> {
        if (page < 0 || page > 1_000_000) return CompletableFuture.failedFuture(IllegalArgumentException(INVALID_PAGE))
        return supply {
            val names = ArrayList<String>()
            connection.prepareStatement("SELECT sender_name FROM mail_blocks WHERE owner_uuid=? ORDER BY sender_name,sender_uuid LIMIT 20 OFFSET ?").use { ps ->
                ps.setString(1, owner.toString())
                ps.setInt(2, page * 20)
                ps.executeQuery().use { rs -> while (rs.next()) names.add(rs.getString(1)) }
            }
            names
        }
    }

    /** Count packages and unread text mail for a recipient notification. */
    override fun pendingMail(recipient: UUID): CompletableFuture<MailSummary> = supply {
        val sql = "SELECT" +
            " SUM(CASE WHEN type='PACKAGE' AND status IN ('UNCLAIMED','RETURNED') THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN type='LETTER' AND status='UNCLAIMED' AND unread=1 THEN 1 ELSE 0 END)," +
            " SUM(CASE WHEN type='ANNOUNCEMENT' AND status='UNCLAIMED' AND unread=1 THEN 1 ELSE 0 END)" +
            " FROM mail WHERE recipient_uuid=? AND id NOT IN (SELECT mail_id FROM mapart_submissions)"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, recipient.toString())
            ps.executeQuery().use { rs ->
                if (rs.next()) MailSummary(rs.getInt(1), rs.getInt(2), rs.getInt(3)) else MailSummary(0, 0, 0)
            }
        }
    }

    /** Count new pending rows and advance past all history in one consistent SQLite statement. */
    override fun mailNotification(recipient: UUID, afterId: Long): CompletableFuture<io.enthusia.express.domain.MailNotification> = supply {
        val sql = "SELECT MAX(id), SUM(CASE WHEN pending THEN 1 ELSE 0 END)," +
            " MIN(CASE WHEN pending THEN sender_name END), MIN(CASE WHEN pending THEN type END)" +
            " FROM (SELECT id, sender_name, type, ((type='PACKAGE' AND status IN ('UNCLAIMED','RETURNED'))" +
            " OR (type IN ('LETTER','ANNOUNCEMENT') AND status='UNCLAIMED' AND unread=1)) AS pending" +
            " FROM mail WHERE id > ? AND recipient_uuid=? AND id NOT IN (SELECT mail_id FROM mapart_submissions))"
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, afterId)
            statement.setString(2, recipient.toString())
            statement.executeQuery().use { rows ->
                rows.next()
                val count = rows.getInt(2)
                io.enthusia.express.domain.MailNotification(count, if (count == 1) rows.getString(3) else null,
                    if (count == 1) MailType.valueOf(rows.getString(4)) else null, maxOf(afterId, rows.getLong(1)))
            }
        }
    }

    /** Open SQLite with immediate transactions, WAL and bounded retry for concurrent initialization. */
    private fun openConnection(): Connection {
        val deadline = System.nanoTime() + busyTimeout * 1_000_000L
        while (true) {
            val opened = SQLiteConfig().apply {
                setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE)
                setBusyTimeout(busyTimeout)
                enforceForeignKeys(true)
            }.createConnection("jdbc:sqlite:" + dbFile.absolutePath)
            try {
                opened.createStatement().use { statement ->
                    statement.execute("PRAGMA journal_mode=WAL")
                    statement.execute("PRAGMA synchronous=FULL")
                }
                return opened
            } catch (error: SQLException) {
                try { opened.close() } catch (closing: SQLException) { error.addSuppressed(closing) }
                // Concurrent first opens can collide while changing journal mode despite busy_timeout.
                if (error.errorCode !in setOf(5, 6) || System.nanoTime() >= deadline) throw error
                Thread.sleep(10)
            }
        }
    }

    /** Commit one serialized operation or roll it back while preserving the original transaction outcome. */
    // Roll back checked JDBC failures and unchecked task failures before reusing the connection.
    @Suppress("TooGenericExceptionCaught")
    private fun <T> inTransaction(task: () -> T): T {
        var failure: Exception? = null
        try {
            connection.autoCommit = false
            val result = task()
            try {
                connection.commit()
            } catch (commitError: Exception) {
                throw io.enthusia.express.domain.UncertainMailCommitException(commitError)
            }
            return result
        } catch (error: Exception) {
            failure = error
            try {
                connection.rollback()
            } catch (rollbackError: SQLException) {
                error.addSuppressed(rollbackError)
                replaceFailedConnection(error)
            }
            throw error
        } finally {
            restoreAutoCommit(failure)
        }
    }

    /** Retire a damaged connection and attempt recovery without replacing the original failure. */
    // Recovery must not mask the original transaction outcome, including unchecked driver failures.
    @Suppress("TooGenericExceptionCaught")
    private fun replaceFailedConnection(failure: Exception) {
        try {
            connection.close()
        } catch (closingError: Exception) {
            failure.addSuppressed(closingError)
        }
        try {
            connection = openConnection()
        } catch (recoveryError: Exception) {
            if (recoveryError is InterruptedException) Thread.currentThread().interrupt()
            failure.addSuppressed(recoveryError)
        }
    }

    /** Restore connection state without reporting an already committed transaction as a failed send. */
    // Cleanup cannot turn an already committed send into a failure that refunds its cargo and fee.
    @Suppress("TooGenericExceptionCaught")
    private fun restoreAutoCommit(failure: Exception?) {
        try {
            connection.autoCommit = true
        } catch (resetError: Exception) {
            val problem = failure ?: resetError
            if (failure != null) failure.addSuppressed(resetError)
            replaceFailedConnection(problem)
            if (failure == null) logger.log(java.util.logging.Level.WARNING,
                "Mail transaction committed; connection reset failed and recovery was attempted", problem)
        }
    }

    /** Materialize a mail record from the current result-set row. */
    private fun read(rs: ResultSet): MailRecord {
        val sender = rs.getString("sender_uuid")
        return MailRecord(
            rs.getLong("id"), sender?.let(UUID::fromString), rs.getString("sender_name"),
            UUID.fromString(rs.getString("recipient_uuid")), rs.getString("recipient_name"),
            MailType.valueOf(rs.getString("type")), MailStatus.valueOf(rs.getString("status")),
            rs.getBytes("payload"), rs.getInt("packed_item_count"), rs.getLong("created_at"),
            rs.getLong("updated_at"), rs.getInt("unread") != 0, rs.getInt("return_delivery") != 0, rs.getLong("claim_generation"),
        )
    }

    /** Queue a storage operation that has no result value. */
    private fun run(task: () -> Unit): CompletableFuture<Void> = supply { task() }.thenApply { null }

    /** Serialize storage work and reject submissions after shutdown. */
    @Synchronized
    private fun <T> supply(task: () -> T): CompletableFuture<T> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Repository is closed"))
        return try {
            CompletableFuture.supplyAsync({ task() }, executor)
        } catch (busy: RejectedExecutionException) {
            CompletableFuture.failedFuture(busy)
        }
    }

    /** Called after main-thread completion callbacks have drained. */
    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            executor.shutdown()
        }
        while (!executor.awaitTermination(1, TimeUnit.SECONDS)) { /* Drain accepted operations. */ }
        if (::connection.isInitialized) connection.close()
    }
}
