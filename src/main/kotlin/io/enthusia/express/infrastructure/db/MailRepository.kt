package io.enthusia.express.infrastructure.db

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
import java.util.concurrent.CompletionException
import java.util.concurrent.Executors
import org.bukkit.plugin.java.JavaPlugin
import org.sqlite.SQLiteConfig

class MailRepository(
    plugin: JavaPlugin?,
    private val dbFile: File,
    private val busyTimeout: Int,
) : MailStore {
    constructor(plugin: JavaPlugin, dbFile: File) :
        this(plugin, dbFile, plugin.config.getInt("database.busy-timeout-ms", 5000))

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "EnthusiaExpress-SQLite").apply { isDaemon = true }
    }
    private lateinit var connection: Connection
    private var closed = false

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
                val columns = HashSet<String>()
                st.executeQuery("PRAGMA table_info(mail)").use { rs ->
                    while (rs.next()) columns.add(rs.getString("name"))
                }
                if ("delivery_pending" !in columns)
                    st.execute("ALTER TABLE mail ADD COLUMN delivery_pending INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    override fun insertPackage(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                               payload: ByteArray, packedCount: Int, returnDelivery: Boolean): CompletableFuture<Long> =
        insertMail(sender, senderName, recipient, recipientName, MailType.PACKAGE, payload, packedCount, returnDelivery)

    override fun insertMail(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                            type: MailType, payload: ByteArray, packedCount: Int, returned: Boolean): CompletableFuture<Long> {
        val copy = payload.clone()
        return supply { insert(sender, senderName, recipient, recipientName, type, copy, packedCount, returned) }
    }

    private fun insert(sender: UUID?, senderName: String, recipient: UUID, recipientName: String,
                       type: MailType, payload: ByteArray, packedCount: Int, returned: Boolean): Long {
        val now = System.currentTimeMillis()
        val sql = "INSERT INTO" +
            " mail(sender_uuid,sender_name,recipient_uuid,recipient_name,type,status,payload,packed_item_count,created_at,updated_at,unread,return_delivery)" +
            " VALUES(?,?,?,?,?,?,?,?,?,?,1,?)"
        connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
            ps.setString(1, sender?.toString())
            ps.setString(2, senderName)
            ps.setString(3, recipient.toString())
            ps.setString(4, recipientName)
            ps.setString(5, type.name)
            ps.setString(6, if (returned) MailStatus.RETURNED.name else MailStatus.UNCLAIMED.name)
            ps.setBytes(7, payload)
            ps.setInt(8, packedCount)
            ps.setLong(9, now)
            ps.setLong(10, now)
            ps.setInt(11, if (returned) 1 else 0)
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
                for ((recipient, recipientName) in snapshot) {
                    insert(sender, senderName, recipient, recipientName, MailType.ANNOUNCEMENT, copy, 0, false)
                }
                snapshot.size
            }
        }
    }

    override fun listInbox(recipient: UUID, type: MailType): CompletableFuture<List<MailRecord>> = listInbox(recipient, type, 0)

    override fun listInbox(recipient: UUID, type: MailType, page: Int): CompletableFuture<List<MailRecord>> {
        if (page < 0 || page > 1_000_000) return CompletableFuture.failedFuture(IllegalArgumentException("Invalid page"))
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

    override fun get(id: Long): CompletableFuture<MailRecord?> = supply {
        connection.prepareStatement("SELECT * FROM mail WHERE id=?").use { ps ->
            ps.setLong(1, id)
            ps.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        }
    }

    override fun claim(id: Long, recipient: UUID): CompletableFuture<Boolean> = supply {
        val current = connection.prepareStatement(
            "SELECT * FROM mail WHERE id=? AND recipient_uuid=? AND type='PACKAGE' AND status IN (?,?)"
        ).use { ps ->
            ps.setLong(1, id)
            ps.setString(2, recipient.toString())
            ps.setString(3, MailStatus.UNCLAIMED.name)
            ps.setString(4, MailStatus.RETURNED.name)
            ps.executeQuery().use { rs -> if (rs.next()) read(rs) else null }
        } ?: return@supply false
        val next = if (current.status == MailStatus.RETURNED) MailStatus.RETURN_CLAIMED else MailStatus.CLAIMED
        connection.prepareStatement(
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


    override fun insertMailLimited(sender: UUID, senderName: String, recipient: UUID, recipientName: String,
                                   type: MailType, payload: ByteArray, packedCount: Int, enforceLimit: Boolean): CompletableFuture<OptionalLong> {
        if (type == MailType.ANNOUNCEMENT) return CompletableFuture.failedFuture(
            IllegalArgumentException("Announcements do not use outstanding-mail limits"))
        val copy = payload.clone()
        return supply {
            if (!enforceLimit) OptionalLong.of(insert(sender, senderName, recipient, recipientName, type, copy, packedCount, false))
            else inTransaction {
                if (hasOutstanding(sender, recipient, type)) OptionalLong.empty()
                else OptionalLong.of(insert(sender, senderName, recipient, recipientName, type, copy, packedCount, false))
            }
        }
    }

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

    private fun replaceFailedConnection(failure: Exception) {
        try {
            connection.close()
            connection = openConnection()
        } catch (recoveryError: SQLException) {
            failure.addSuppressed(recoveryError)
        }
    }

    private fun restoreAutoCommit(failure: Exception?) {
        try {
            connection.autoCommit = true
        } catch (resetError: SQLException) {
            if (failure == null) {
                replaceFailedConnection(resetError)
                throw resetError
            }
            failure.addSuppressed(resetError)
            replaceFailedConnection(failure)
        }
    }

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

    private fun run(task: () -> Unit): CompletableFuture<Void> = supply { task() }.thenApply { null }

    @Synchronized
    private fun <T> supply(task: () -> T): CompletableFuture<T> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Repository is closed"))
        return CompletableFuture.supplyAsync({
            try {
                task()
            } catch (e: Exception) {
                throw CompletionException(e)
            }
        }, executor)
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
