package io.enthusia.express.infrastructure.db

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailStatus
import io.enthusia.express.domain.MailType
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

/** Receipts describe items already delivered. Replaying them can only release reservations. */
class DeliveryAcknowledgments(
    private val directory: Path,
    private val repository: MailStore,
    private val logger: Logger,
) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "EnthusiaExpress-DeliveryReceipts").apply { isDaemon = true }
    }
    private val pending = HashMap<Long, UUID>()
    private var retryFuture: CompletableFuture<Void>? = null
    private var closed = false

    /** Queue a receipt after item delivery; persist it before attempting to release the database reservation. */
    @Synchronized
    fun record(id: Long, recipient: UUID): CompletableFuture<Void> {
        require(id > 0)
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Delivery journal is closed"))
        return CompletableFuture.runAsync({
            pending[id] = recipient
            deliverReceipt(id, recipient)
        }, executor)
    }

    /** Coalesce overlapping retry requests onto one serialized background pass. */
    @Synchronized
    fun retry(): CompletableFuture<Void> {
        if (closed) return CompletableFuture.failedFuture(IllegalStateException("Delivery journal is closed"))
        val current = retryFuture
        if (current != null && !current.isDone) return current
        return CompletableFuture.runAsync({ replay() }, executor).also { retryFuture = it }
    }

    /** Load durable receipts and retry a snapshot of pending acknowledgments. */
    private fun replay() {
        loadReceipts()
        pending.toMap().forEach { (id, recipient) -> deliverReceipt(id, recipient) }
    }

    /** Scan persisted receipts off the server thread while isolating damaged entries. */
    // One unreadable receipt must not prevent other delivered packages from being acknowledged.
    @Suppress("TooGenericExceptionCaught")
    private fun loadReceipts() {
        try {
            Files.createDirectories(directory)
            Files.list(directory).use { files ->
                files.filter { it.fileName.toString().endsWith(".ack") }.forEach { readReceipt(it) }
            }
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Cannot scan delivery receipts; reservations remain held", error)
        }
    }

    /** Validate one receipt and retain malformed data for administrator investigation. */
    private fun readReceipt(file: Path) {
        try {
            val id = file.fileName.toString().removeSuffix(".ack").toLong()
            require(id > 0)
            pending.putIfAbsent(id, UUID.fromString(Files.readString(file).trim()))
        } catch (error: IllegalArgumentException) {
            logger.log(Level.SEVERE, "Invalid delivery receipt $file; administrator review required", error)
        } catch (error: java.io.IOException) {
            logger.log(Level.WARNING, "Cannot read delivery receipt $file", error)
        }
    }

    /** Persist and acknowledge one delivered package, retaining failed work without redelivering items. */
    // Disk and asynchronous database failures retain the receipt for later retry, never redelivery.
    @Suppress("TooGenericExceptionCaught")
    private fun deliverReceipt(id: Long, recipient: UUID) {
        try {
            persist(id, recipient)
            if (!acknowledge(id, recipient)) {
                logger.severe("Delivery receipt #$id does not match a delivered package; administrator review required")
                return
            }
            Files.deleteIfExists(directory.resolve("$id.ack"))
            pending.remove(id)
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Delivery acknowledgment #$id will be retried; do not restore its items", error)
        }
    }

    /** Release a matching claim or recognize a previous successful acknowledgment idempotently. */
    private fun acknowledge(id: Long, recipient: UUID): Boolean {
        if (repository.confirmDelivery(id, recipient).join()) return true
        val record = repository.get(id).join() ?: return false
        return record.recipient == recipient && record.type == MailType.PACKAGE &&
            record.status in setOf(MailStatus.CLAIMED, MailStatus.RETURN_CLAIMED)
    }

    /** Force receipt content to disk and publish its filename before acknowledging the database row. */
    private fun persist(id: Long, recipient: UUID) {
        Files.createDirectories(directory)
        val temporary = directory.resolve("$id.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(recipient.toString().toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        val receipt = directory.resolve("$id.ack")
        try {
            Files.move(temporary, receipt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(temporary, receipt, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** Drain queued receipt work, attempt one final replay and retain unresolved receipts for restart. */
    @Synchronized
    override fun close() {
        if (closed) return
        val finishing = CompletableFuture.runAsync({ replay() }, executor)
        closed = true
        executor.shutdown()
        finishing.join()
        if (pending.isNotEmpty()) logger.warning("${pending.size} delivery receipts await recovery; retain the delivery-receipts directory")
    }
}
