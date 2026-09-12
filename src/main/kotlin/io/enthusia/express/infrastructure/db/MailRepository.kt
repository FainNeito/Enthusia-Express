// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.db

import io.enthusia.express.domain.MailBlockedException
import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailSummary
import io.enthusia.express.domain.MailType
import java.io.File
import java.nio.file.Files
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.util.OptionalLong
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
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
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EnthusiaExpress-SQLite").apply { isDaemon = true }
    }
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
                st.execute("CREATE TABLE IF NOT EXISTS mail_blocks (owner_uuid TEXT NOT NULL, sender_uuid TEXT NOT NULL, sender_name TEXT NOT NULL, PRIMARY KEY(owner_uuid,sender_uuid))")
                val columns = HashSet<String>()
                st.executeQuery("PRAGMA table_info(mail)").use { rs ->
                    while (rs.next()) columns.add(rs.getString("name"))
                }
                if ("delivery_pending" !in columns)
                    st.execute("ALTER TABLE mail ADD COLUMN delivery_pending INTEGER NOT NULL DEFAULT 0")
                if ("original_recipient_name" !in columns) {
                    st.execute("ALTER TABLE mail ADD COLUMN original_recipient_name TEXT")
                    st.execute("UPDATE mail SET original_recipient_name=recipient_name WHERE return_delivery=0")
                }
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

    private data class InsertData(
        val sender: UUID?, val senderName: String, val recipient: UUID, val recipientName: String,
        val type: MailType, val payload: ByteArray, val packedCount: Int, val returned: Boolean,
    )

    /** Preserve recovery deliveries while enforcing recipient preferences for new mail. */
    private fun rejectBlocked(data: InsertData) = !data.returned && data.sender != null && blocked(data.recipient, data.sender)

    /** Bind a prepared mail record to the shared connection and return its generated identifier. */
    private fun insert(data: InsertData): Long {
        if (rejectBlocked(data))
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
            val sql = "SELECT * FROM mail WHERE recipient_uuid=? AND type=? AND status IN (?,?) ORDER BY" +
                " created_at DESC, id DESC LIMIT 45 OFFSET ?"
            connection.prepareStatement(sql).use { ps ->
                ps.setString(1, recipient.toString())
                ps.setString(2, type.name)
                ps.setString(3, MailStatus.UNCLAIMED.name)
                ps.setString(4, MailStatus.RETURNED.name)
                ps.setInt(5, page * 45)
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
            connection.prepareStatement("SELECT * FROM mail WHERE sender_uuid=? AND type=? ORDER BY created_at DESC, id DESC LIMIT 45 OFFSET ?").use { ps ->
                ps.setString(1, sender.toString())
                ps.setString(2, type.name)
                ps.setInt(3, page * 45)
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

    /** Conditionally transition the observed package into its claimed state with a held delivery reservation. */
    private fun updateClaim(current: MailRecord): Boolean {
        val id = current.id
        val recipient = current.recipient
        val next = if (current.status == MailStatus.RETURNED) MailStatus.RETURN_CLAIMED else MailStatus.CLAIMED
        return connection.prepareStatement(
            "UPDATE mail SET status=?, unread=0, delivery_pending=1, updated_at=? WHERE id=? AND recipient_uuid=? AND status=?"
        ).use { ps ->
            ps.setString(1, next.name)
            ps.setLong(2, System.currentTimeMillis())
            ps.setLong(3, id)
            ps.setString(4, recipient.toString())
            ps.setString(5, current.status.name)
            ps.executeUpdate() == 1
        }
    }

    /** Restore an undelivered pending claim to its original status and timestamp. */
    override fun restoreClaim(record: MailRecord): CompletableFuture<Boolean> = supply {
        connection.prepareStatement(
            "UPDATE mail SET status=?, unread=1, delivery_pending=0, updated_at=? WHERE id=? AND recipient_uuid=? AND status=? AND delivery_pending=1"
        ).use { ps ->
            ps.setString(1, record.status.name)
            ps.setLong(2, record.updatedAt)
            ps.setLong(3, record.id)
            ps.setString(4, record.recipient.toString())
            ps.setString(5, if (record.status == MailStatus.RETURNED) "RETURN_CLAIMED" else "CLAIMED")
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

    /** One transaction, no stale read/modify/write window; text mail never enters RTS. */
    override fun expire(now: Long, returnCutoff: Long, purgeCutoff: Long, textCutoff: Long): CompletableFuture<Int> = supply {
        inTransaction {
            var changed = connection.prepareStatement(
                "UPDATE mail SET status='PURGED', payload=X'', updated_at=? WHERE" +
                    " (type='PACKAGE' AND status='RETURNED' AND updated_at<?) OR (type IN" +
                    " ('LETTER','ANNOUNCEMENT') AND status='UNCLAIMED' AND created_at<?)"
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
                    " type='PACKAGE' AND status='UNCLAIMED' AND updated_at<?"
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
            " FROM mail WHERE recipient_uuid=?"
        connection.prepareStatement(sql).use { ps ->
            ps.setString(1, recipient.toString())
            ps.executeQuery().use { rs ->
                if (rs.next()) MailSummary(rs.getInt(1), rs.getInt(2), rs.getInt(3)) else MailSummary(0, 0, 0)
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
            connection.commit()
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
            rs.getLong("updated_at"), rs.getInt("unread") != 0, rs.getInt("return_delivery") != 0,
        )
    }

    /** Queue a storage operation that has no result value. */
    private fun run(task: () -> Unit): CompletableFuture<Void> = supply { task() }.thenApply { null }

    /** Serialize storage work and reject submissions after shutdown. */
    @Synchronized
    private fun <T> supply(task: () -> T): CompletableFuture<T> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Repository is closed"))
        return CompletableFuture.supplyAsync({ task() }, executor)
    }

    /** Called after main-thread completion callbacks have drained. */
    override fun close() {
        val closing: CompletableFuture<Void>
        synchronized(this) {
            if (closed) return
            closing = run { if (::connection.isInitialized) connection.close() }
            closed = true
            executor.shutdown()
        }
        closing.join()
    }
}
