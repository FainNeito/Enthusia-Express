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

    fun <T> complete(future: CompletableFuture<T>, callback: BiConsumer<T?, Throwable?>) {
        val queued = future.handle { value, error ->
            ready.add(Runnable { callback.accept(value, error) })
            null
        }
        pending.add(queued)
    }

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

    fun close() {
        task.cancel()
        while (pending.isNotEmpty() || ready.isNotEmpty()) {
            CompletableFuture.allOf(*pending.toTypedArray()).join()
            drain()
        }
    }
}
