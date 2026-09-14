// Recovery and integration boundaries retain explicit thread and failure documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.payment

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Base64
import java.util.Properties
import java.util.UUID
import java.util.logging.Logger

/** Main-thread journal; only PENDING records are safe to retry automatically. */
class ShippingRecovery(private val directory: Path, private val logger: Logger) {
    enum class Phase { PREPARED, PENDING, APPLYING, COMPLETE }

    data class Record(val id: UUID, val values: Properties) {
        val sender: UUID get() = UUID.fromString(values.getProperty(KEY_SENDER))
        val cost: Int get() = values.getProperty(KEY_COST).toInt()
        val route: String get() = values.getProperty(KEY_ROUTE)
        val cargo: ByteArray get() = Base64.getDecoder().decode(values.getProperty(KEY_CARGO))
        val phase: Phase get() = Phase.valueOf(values.getProperty(KEY_PHASE))
    }

    private val records = linkedMapOf<UUID, Record>()
    private var loaded = false

    /** Force cargo and payment intent before either inventory or an external balance changes. */
    fun prepare(sender: UUID, recipient: UUID, cost: Int, route: String, cargo: ByteArray): Record {
        val values = Properties().apply {
            setProperty(KEY_VERSION, "1")
            setProperty(KEY_SENDER, sender.toString())
            setProperty(KEY_RECIPIENT, recipient.toString())
            setProperty(KEY_COST, cost.toString())
            setProperty(KEY_ROUTE, route)
            setProperty(KEY_CARGO, Base64.getEncoder().encodeToString(cargo))
            setProperty("created", System.currentTimeMillis().toString())
            setProperty(KEY_PHASE, Phase.PREPARED.name)
        }
        val record = Record(UUID.randomUUID(), values)
        persist(record)
        records[record.id] = record
        return record
    }

    /** Never update the in-memory phase until its replacement is durable. */
    fun transition(record: Record, phase: Phase) {
        val replacement = Properties().apply { putAll(record.values); setProperty(KEY_PHASE, phase.name) }
        persist(Record(record.id, replacement))
        record.values.setProperty(KEY_PHASE, phase.name)
        if (phase == Phase.PENDING) records[record.id] = record else records.remove(record.id)
    }

    /** A retained COMPLETE marker makes cleanup failures harmless on the next startup. */
    fun finish(record: Record) {
        transition(record, Phase.COMPLETE)
        Files.deleteIfExists(directory.resolve("${record.id}.properties"))
        records.remove(record.id)
    }

    /** Load once, then give each bounded retry pass a snapshot of definite unattempted refunds. */
    fun pending(): List<Record> {
        if (!loaded) load()
        val batch = records.values.filter { it.phase == Phase.PENDING }.take(16)
        for (record in batch) {
            records.remove(record.id)
            records[record.id] = record
        }
        return batch
    }

    /** Interrupted writes and uncertain mutations are retained for reconciliation, never replayed. */
    private fun load() {
        Files.createDirectories(directory)
        Files.list(directory).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".properties") }.forEach { path ->
                read(path)
            }
        }
        loaded = true
    }

    /** Validate recovery metadata before permitting it to drive an inventory or currency mutation. */
    private fun read(path: Path) {
        try {
            require(Files.size(path) in 1L..12_000_000L)
            val id = UUID.fromString(path.fileName.toString().removeSuffix(".properties"))
            val values = Properties().apply { Files.newInputStream(path).use { load(it) } }
            require(listOf(KEY_VERSION, KEY_SENDER, KEY_RECIPIENT, KEY_COST, KEY_ROUTE, KEY_CARGO, KEY_PHASE)
                .all { values.getProperty(it) != null })
            val record = Record(id, values)
            require(values.getProperty(KEY_VERSION) == "1")
            require(record.cost >= 0 && record.route in setOf("Raw Gold", "currency"))
            require(record.cargo.size in 1..8_388_608)
            record.sender
            UUID.fromString(values.getProperty(KEY_RECIPIENT))
            if (record.phase == Phase.PENDING) records.putIfAbsent(id, record)
            else if (record.phase != Phase.COMPLETE && id !in records)
                logger.severe("Shipping recovery $path is ${record.phase}; reconcile cargo and postage before any replay")
        } catch (error: java.io.IOException) {
            logger.severe("Cannot read shipping recovery $path: ${error.message}")
        } catch (error: IllegalArgumentException) {
            logger.severe("Invalid shipping recovery $path: ${error.message}")
        }
    }

    /** Atomic replacement is required so an APPLYING marker cannot revert to PENDING on restart. */
    private fun persist(record: Record) {
        Files.createDirectories(directory)
        val bytes = ByteArrayOutputStream().use { output ->
            record.values.store(output, "Enthusia Express shipping recovery; do not replay uncertain records")
            output.toByteArray()
        }
        val temporary = directory.resolve("${record.id}.tmp")
        FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        Files.move(temporary, directory.resolve("${record.id}.properties"),
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    private companion object {
        const val KEY_SENDER = "sender"
        const val KEY_RECIPIENT = "recipient"
        const val KEY_COST = "cost"
        const val KEY_ROUTE = "route"
        const val KEY_CARGO = "cargo"
        const val KEY_PHASE = "phase"
        const val KEY_VERSION = "version"
    }

}
