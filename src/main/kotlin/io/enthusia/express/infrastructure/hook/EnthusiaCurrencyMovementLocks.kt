package io.enthusia.express.infrastructure.hook

import java.lang.reflect.Method
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

/** A lease serializes Express asset mutations with EnthusiaCurrency moderation operations. */
interface MovementLease {
    /** Refresh ownership immediately before a player inventory or currency mutation. */
    fun ensureOwned(): Boolean

    /** Release the operation-owned movement lease without exposing checked exceptions to callers. */
    fun close()
}

/** Optional asset-movement guard. Absence of EnthusiaCurrency is intentionally a no-op. */
fun interface MovementLocks {
    /** Acquire one operation-owned lease, or return null when another operation owns the player. */
    fun acquire(playerId: UUID): MovementLease?

    companion object {
        @JvmField
        val NOOP: MovementLocks = MovementLocks { NoopMovementLease }
    }
}

private object NoopMovementLease : MovementLease {
    override fun ensureOwned(): Boolean = true
    override fun close() = Unit
}

/** Reflection keeps EnthusiaCurrency optional while still honoring its published moderation API. */
class EnthusiaCurrencyMovementLocks(private val plugin: JavaPlugin) : MovementLocks, AutoCloseable {
    private val active = ConcurrentHashMap.newKeySet<ReflectiveLease>()
    @Volatile private var closed = false

    /** Fail closed when Currency is installed but its moderation service is unavailable or incompatible. */
    override fun acquire(playerId: UUID): MovementLease? {
        if (closed) return null
        val currency = Bukkit.getPluginManager().getPlugin("EnthusiaCurrency") ?: return NoopMovementLease
        if (!currency.isEnabled) {
            plugin.logger.warning("EnthusiaCurrency is installed but disabled; refusing mail asset mutation")
            return null
        }
        val api = resolve(currency) ?: return null
        val lease = ReflectiveLease(api, playerId, UUID.randomUUID())
        if (!lease.ensureOwned()) return null
        active.add(lease)
        lease.startRenewal()
        return lease
    }

    /** Resolve the exact service class from EnthusiaCurrency's own class loader. */
    @Suppress("UNCHECKED_CAST", "TooGenericExceptionCaught")
    private fun resolve(currency: Plugin): Api? = try {
        val type = Class.forName(API_CLASS, false, currency.javaClass.classLoader)
        val service = Bukkit.getServicesManager().load(type as Class<Any>) ?: run {
            plugin.logger.severe("EnthusiaCurrency moderation service is not registered; refusing mail asset mutation")
            return null
        }
        val version = (type.getMethod("apiVersion").invoke(service) as? Number)?.toInt()
        if (version != API_VERSION) {
            plugin.logger.severe("Unsupported EnthusiaCurrency moderation API version $version; expected $API_VERSION")
            return null
        }
        Api(
            service,
            type.getMethod("acquireMovementLock", UUID::class.java, UUID::class.java, Duration::class.java),
            type.getMethod("releaseMovementLock", UUID::class.java, UUID::class.java),
        )
    } catch (error: Throwable) {
        plugin.logger.log(Level.SEVERE, "Cannot bind EnthusiaCurrency moderation API; refusing mail asset mutation", error)
        null
    }

    private data class Api(val service: Any, val acquire: Method, val release: Method)

    /** Same operation id may reacquire the Currency lease, which refreshes its expiry. */
    private inner class ReflectiveLease(
        private val api: Api,
        private val playerId: UUID,
        private val operationId: UUID,
    ) : MovementLease {
        private var renewal: BukkitTask? = null
        @Volatile private var leaseClosed = false

        fun startRenewal() {
            renewal = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (!leaseClosed && !ensureOwned()) {
                    plugin.logger.severe("Lost EnthusiaCurrency movement lease for $playerId; later asset mutation will fail closed")
                }
            }, RENEW_TICKS, RENEW_TICKS)
        }

        @Suppress("TooGenericExceptionCaught")
        override fun ensureOwned(): Boolean {
            if (leaseClosed || closed) return false
            return try {
                api.acquire.invoke(api.service, playerId, operationId, LEASE_DURATION) == true
            } catch (error: Throwable) {
                plugin.logger.log(Level.SEVERE, "Cannot refresh EnthusiaCurrency movement lease for $playerId", error)
                false
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override fun close() {
            if (leaseClosed) return
            leaseClosed = true
            renewal?.cancel()
            active.remove(this)
            try {
                api.release.invoke(api.service, playerId, operationId)
            } catch (error: Throwable) {
                plugin.logger.log(Level.WARNING, "Cannot release EnthusiaCurrency movement lease for $playerId", error)
            }
        }
    }

    /** Release any leases that remain after the main-thread completion queue has drained. */
    override fun close() {
        closed = true
        active.toList().forEach { it.close() }
    }

    companion object {
        private const val API_CLASS = "com.enthusia.enthusiacurrency.api.moderation.CurrencyModerationApi"
        private const val API_VERSION = 1
        private val LEASE_DURATION: Duration = Duration.ofSeconds(60)
        private const val RENEW_TICKS = 100L
    }
}
