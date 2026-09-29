// Recovery and integration boundaries retain explicit thread and failure documentation.
@file:Suppress("CommentOverPrivateFunction")

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
    @Volatile private var bindingAttempted = false
    @Volatile private var boundApi: Api? = null

    /** Probe the optional integration once at startup so incompatible Currency builds fail clearly, not per click. */
    fun bindIfPresent() {
        if (closed || bindingAttempted) return
        val currency = Bukkit.getPluginManager().getPlugin("EnthusiaCurrency") ?: return
        bindingAttempted = true
        if (!currency.isEnabled) {
            plugin.logger.severe("EnthusiaCurrency ${currency.description.version} is installed but disabled; refusing mail asset mutation")
            return
        }
        boundApi = resolve(currency)
        if (boundApi != null) {
            plugin.logger.info("Bound EnthusiaCurrency ${currency.description.version} moderation API v$API_VERSION for mail movement leases.")
        }
    }

    /** Fail closed when Currency is installed but its moderation service is unavailable or incompatible. */
    override fun acquire(playerId: UUID): MovementLease? {
        if (closed) return null
        val currency = Bukkit.getPluginManager().getPlugin("EnthusiaCurrency") ?: return NoopMovementLease
        if (!currency.isEnabled) {
            if (!bindingAttempted) bindIfPresent()
            return null
        }
        if (!bindingAttempted) bindIfPresent()
        val api = boundApi ?: return null
        val lease = ReflectiveLease(api, playerId, UUID.randomUUID())
        if (!lease.acquireInitial()) return null
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
            type.getMethod("renewMovementLock", UUID::class.java, UUID::class.java, Duration::class.java),
            type.getMethod("releaseMovementLock", UUID::class.java, UUID::class.java),
        )
    } catch (error: ClassNotFoundException) {
        plugin.logger.severe(
            "EnthusiaCurrency ${currency.description.version} does not publish $API_CLASS. " +
                "Install a Currency build with moderation API v$API_VERSION (current source is 1.4.4); refusing mail asset mutation."
        )
        null
    } catch (error: NoSuchMethodException) {
        plugin.logger.severe(
            "EnthusiaCurrency ${currency.description.version} exposes an incompatible moderation API: ${error.message}; " +
                "expected v$API_VERSION. Refusing mail asset mutation."
        )
        null
    } catch (error: Throwable) {
        plugin.logger.log(
            Level.SEVERE,
            "Cannot bind EnthusiaCurrency ${currency.description.version} moderation API v$API_VERSION; refusing mail asset mutation",
            error,
        )
        null
    }

    private data class Api(val service: Any, val acquire: Method, val renew: Method, val release: Method)

    /** Acquire once, then use the API's renewal contract so expired ownership is never silently reacquired. */
    private inner class ReflectiveLease(
        private val api: Api,
        private val playerId: UUID,
        private val operationId: UUID,
    ) : MovementLease {
        private var renewal: BukkitTask? = null
        @Volatile private var leaseClosed = false

        fun acquireInitial(): Boolean = invokeLease(api.acquire, "acquire")

        fun startRenewal() {
            renewal = Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
                if (!leaseClosed && !ensureOwned()) {
                    plugin.logger.severe("Lost EnthusiaCurrency movement lease for $playerId; later asset mutation will fail closed")
                }
            }, RENEW_TICKS, RENEW_TICKS)
        }

        override fun ensureOwned(): Boolean = invokeLease(api.renew, "renew")

        @Suppress("TooGenericExceptionCaught")
        private fun invokeLease(method: Method, action: String): Boolean {
            if (leaseClosed || closed) return false
            return try {
                method.invoke(api.service, playerId, operationId, LEASE_DURATION) == true
            } catch (error: Throwable) {
                plugin.logger.log(Level.SEVERE, "Cannot $action EnthusiaCurrency movement lease for $playerId", error)
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
