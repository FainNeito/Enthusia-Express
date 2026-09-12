// Private callback contracts document thread ownership and recovery; CodeRabbit requires method documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.util

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.BiConsumer
import java.util.logging.Level
import org.bukkit.plugin.java.JavaPlugin

/** Drains database completion callbacks on the server thread, including graceful disable. */
class MainThread(plugin: JavaPlugin) {
    private val pending = HashSet<CompletableFuture<*>>()
    private val ready = ConcurrentLinkedQueue<Runnable>()
    private val logger = plugin.logger
    private val task = plugin.server.scheduler.runTaskTimer(plugin, Runnable { drain() }, 1, 1)

    /** Track asynchronous work and enqueue its completion callback for the server thread. */
    fun <T> complete(future: CompletableFuture<T>, callback: BiConsumer<T?, Throwable?>) {
        val queued = future.handle { value, error ->
            ready.add(Runnable { callback.accept(value, error) })
            null
        }
        pending.add(queued)
    }

    /** Run queued callbacks on the server thread while isolating individual callback failures. */
    // Third-party callbacks may throw any runtime failure; one must not discard later refunds.
    @Suppress("TooGenericExceptionCaught")
    private fun drain() {
        pending.removeIf { it.isDone }
        while (true) {
            val callback = ready.poll() ?: break
            try {
                callback.run()
            } catch (error: RuntimeException) {
                logger.log(Level.SEVERE, "Mail completion callback failed", error)
            }
        }
    }

    /** Wait for tracked futures and drain every completion before closing backing services. */
    fun close() {
        task.cancel()
        while (pending.isNotEmpty() || ready.isNotEmpty()) {
            pending.toList().forEach { it.join() }
            drain()
        }
    }
}
