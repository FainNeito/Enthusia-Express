@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.db

import io.enthusia.express.application.MailStore
import io.enthusia.express.domain.MailRecord
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
import java.util.logging.Level
import java.util.logging.Logger

/** Used only by the delivery journal's single worker; these receipts certify non-delivery. */
internal class ClaimRestorations(private val directory: Path, private val repository: MailStore, private val logger: Logger) {
    private val suffix = ".restore"
    private val pending = HashMap<String, MailRecord>()

    /** Retain only compensation metadata, never the potentially large item payload. */
    fun record(record: MailRecord): Boolean {
        require(record.id > 0 && record.claimGeneration >= 0 && record.type == MailType.PACKAGE &&
            record.status in setOf(MailStatus.UNCLAIMED, MailStatus.RETURNED))
        val key = "${record.id}-${record.claimGeneration}"
        val metadata = record.copy(payload = byteArrayOf())
        pending[key] = metadata
        return restore(key, metadata)
    }

    /** Retry a finite snapshot; damaged receipts remain available for operator inspection. */
    @Suppress("TooGenericExceptionCaught")
    fun retry() {
        try {
            Files.createDirectories(directory)
            Files.list(directory).use { paths ->
                paths.filter { it.fileName.toString().endsWith(suffix) || it.fileName.toString().endsWith(".tmp") }.forEach { read(it) }
            }
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Cannot scan undelivered claim receipts", error)
        }
        pending.toMap().forEach { (key, record) -> restore(key, record) }
    }

    /** Parse bounded metadata and reject names that do not identify its exact generation. */
    @Suppress("TooGenericExceptionCaught")
    private fun read(path: Path) {
        try {
            require(Files.size(path) <= 512)
            val content = Files.readString(path)
            val temporary = path.fileName.toString().endsWith(".tmp")
            require(!temporary || content.endsWith("\n"))
            val fields = content.trim().split('\n')
            require(fields.size == 5)
            val id = fields[0].toLong()
            val recipient = UUID.fromString(fields[1])
            val status = MailStatus.valueOf(fields[2])
            val updated = fields[3].toLong()
            val generation = fields[4].toLong()
            require(id > 0 && generation >= 0 && status in setOf(MailStatus.UNCLAIMED, MailStatus.RETURNED))
            val key = "$id-$generation"
            require(path.fileName.toString() == key + if (temporary) ".tmp" else suffix)
            pending.putIfAbsent(key, MailRecord(id, null, "", recipient, "", MailType.PACKAGE, status,
                byteArrayOf(), 0, 0, updated, true, status == MailStatus.RETURNED, generation))
        } catch (error: Exception) {
            logger.log(Level.SEVERE, "Invalid undelivered claim receipt $path; administrator review required", error)
        }
    }

    /** Persist before touching SQLite; a successful retry advances the generation atomically. */
    @Suppress("TooGenericExceptionCaught")
    private fun restore(key: String, record: MailRecord): Boolean {
        try {
            persist(key, record)
            if (!repository.restoreClaim(record).join()) {
                val current = repository.get(record.id).join()
                // A higher generation proves this attempt was restored already. Never undo its successor.
                if (current == null || current.recipient != record.recipient || current.claimGeneration <= record.claimGeneration) {
                    logger.severe("Undelivered claim $key is uncertain; retain its receipt for administrator review")
                    return false
                }
            }
            Files.deleteIfExists(directory.resolve(key + suffix))
            pending.remove(key)
            return true
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Undelivered claim $key will be retried; retain recovery receipts", error)
            return false
        }
    }

    /** Force content before atomically publishing the retry intent on the local filesystem. */
    private fun persist(key: String, record: MailRecord) {
        Files.createDirectories(directory)
        val temporary = directory.resolve("$key.tmp")
        val content = "${record.id}\n${record.recipient}\n${record.status}\n${record.updatedAt}\n${record.claimGeneration}\n"
        FileChannel.open(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        val receipt = directory.resolve(key + suffix)
        try {
            Files.move(temporary, receipt, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            Files.move(temporary, receipt, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
