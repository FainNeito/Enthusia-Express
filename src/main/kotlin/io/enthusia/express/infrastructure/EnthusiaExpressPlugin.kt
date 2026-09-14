// Recovery and integration boundaries retain explicit thread and failure documentation.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure

import io.enthusia.express.infrastructure.command.MailCommand
import io.enthusia.express.infrastructure.db.DeliveryAcknowledgments
import io.enthusia.express.infrastructure.db.MailRepository
import io.enthusia.express.infrastructure.gui.GuiListener
import io.enthusia.express.infrastructure.gui.MailboxService
import io.enthusia.express.infrastructure.gui.ShippingService
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.hook.EnthusiaCurrencyMovementLocks
import io.enthusia.express.infrastructure.mail.BookMailService
import io.enthusia.express.infrastructure.mail.ExpirationService
import io.enthusia.express.infrastructure.mail.JoinNotificationService
import io.enthusia.express.infrastructure.util.ConfigValidation
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.util.SoundFeedback
import java.io.File
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

class EnthusiaExpressPlugin : JavaPlugin() {
    private var main: MainThread? = null
    private var repository: MailRepository? = null
    private var shippingService: ShippingService? = null
    private var mailboxService: MailboxService? = null
    private var expirationService: ExpirationService? = null
    private var acknowledgments: DeliveryAcknowledgments? = null
    private var movementLocks: EnthusiaCurrencyMovementLocks? = null
    private var compensationTask: org.bukkit.scheduler.BukkitTask? = null
    private var mailboxTask: org.bukkit.scheduler.BukkitTask? = null
    private var acknowledgmentTask: org.bukkit.scheduler.BukkitTask? = null

    /** Initialize storage, completion dispatch and optional integrations, then register mail commands and listeners. */
    override fun onEnable() {
        saveDefaultConfig()
        ConfigValidation.validate(config)
        val main = MainThread(this).also { this.main = it }
        val repository = MailRepository(this, File(dataFolder, "mail.db")).also { this.repository = it }
        repository.initialize().join()
        val acknowledgments = DeliveryAcknowledgments(dataFolder.toPath().resolve("delivery-receipts"), repository, logger)
            .also { this.acknowledgments = it }
        startReceiptRetries(main, acknowledgments)
        val sounds = SoundFeedback(this).also { it.validate() }
        val combatHook = CombatLogXHook(this)
        val movementLocks = EnthusiaCurrencyMovementLocks(this).also { this.movementLocks = it }
        val shipping = ShippingService(this, repository, combatHook, main, sounds, movementLocks).also { shippingService = it }
        compensationTask = Bukkit.getScheduler().runTaskTimer(this, Runnable { shipping.retryCompensations() }, 1L, 100L)
        val mailbox = MailboxService(this, repository, combatHook, main, sounds, acknowledgments, movementLocks).also { mailboxService = it }
        mailboxTask = Bukkit.getScheduler().runTaskTimer(this, Runnable { mailbox.loadPendingPages() }, 1L, 1L)
        val expiration = ExpirationService(this, repository).also { expirationService = it }
        val command = MailCommand(this, shipping, mailbox, combatHook, BookMailService(this, repository, combatHook, main, sounds), main,
            io.enthusia.express.infrastructure.mail.MailBlockService(this, repository, main))
        getCommand("mail")!!.setExecutor(command)
        getCommand("mail")!!.tabCompleter = command
        Bukkit.getPluginManager().registerEvents(command.recipientNames, this)
        Bukkit.getPluginManager().registerEvents(GuiListener(shipping, mailbox), this)
        Bukkit.getPluginManager().registerEvents(JoinNotificationService(this, repository, main), this)
        expiration.start()
        logger.info("Enthusia Express enabled.")
    }

    /** Keep receipt retry scheduling separate from service construction. */
    private fun startReceiptRetries(main: MainThread, acknowledgments: DeliveryAcknowledgments) {
        val retryReceipts = Runnable {
            main.complete(acknowledgments.retry()) { _, failure ->
                if (failure != null) logger.severe("Cannot retry delivery acknowledgments: $failure")
            }
        }
        acknowledgmentTask = Bukkit.getScheduler().runTaskTimer(this, retryReceipts, 1L, 100L)
    }

    /** Stop recurring work, return open cargo, drain completions and close the receipt journal before SQLite. */
    override fun onDisable() {
        compensationTask?.cancel()
        mailboxTask?.cancel()
        acknowledgmentTask?.cancel()
        expirationService?.stop()
        shippingService?.shutdown()
        mailboxService?.shutdown()
        main?.close()
        movementLocks?.close()
        acknowledgments?.close()
        repository?.close()
    }
}
