package io.enthusia.express.infrastructure.payment

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger

/** These intents are operator evidence, never instructions to automatically refund money. */
internal object PaymentReconciliation {
    /** Force a small intent before invoking a non-transactional external withdrawal. */
    fun begin(directory: Path, account: UUID, cost: Int): Path {
        Files.createDirectories(directory)
        val id = UUID.randomUUID()
        val path = directory.resolve("$id.pending")
        val content = "operation=$id\naccount=$account\nprovider=EnthusiaCurrency\ncost=$cost\n" +
            "created=${System.currentTimeMillis()}\noutcome=UNKNOWN\n"
        FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
            val bytes = ByteBuffer.wrap(content.toByteArray(Charsets.UTF_8))
            while (bytes.hasRemaining()) channel.write(bytes)
            channel.force(true)
        }
        return path
    }

    /** Cleanup failure must not turn a successful debit into a rejected payment. */
    fun finish(path: Path, logger: Logger) {
        try {
            Files.deleteIfExists(path)
        } catch (error: java.io.IOException) {
            logger.log(Level.WARNING, "Known payment completed but intent $path remains; reconcile before refunding", error)
        } catch (error: SecurityException) {
            logger.log(Level.WARNING, "Cannot remove completed payment intent $path; reconcile before refunding", error)
        }
    }
}
