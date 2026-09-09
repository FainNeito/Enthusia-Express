package io.enthusia.express.infrastructure

import io.enthusia.express.infrastructure.command.MailCommand
import io.enthusia.express.infrastructure.db.MailRepository
import io.enthusia.express.infrastructure.gui.GuiListener
import io.enthusia.express.infrastructure.gui.MailboxService
import io.enthusia.express.infrastructure.gui.ShippingService
import io.enthusia.express.infrastructure.hook.CombatLogXHook
import io.enthusia.express.infrastructure.mail.BookMailService
import io.enthusia.express.infrastructure.mail.ExpirationService
import io.enthusia.express.infrastructure.util.ConfigValidation
import io.enthusia.express.infrastructure.util.MainThread
import io.enthusia.express.infrastructure.mail.JoinNotificationService
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

    override fun onEnable() {
        saveDefaultConfig()
        ConfigValidation.validate(config)
        val main = MainThread(this).also { this.main = it }
        val repository = MailRepository(this, File(dataFolder, "mail.db")).also { this.repository = it }
        repository.initialize().join()
        val sounds = SoundFeedback(this).also { it.validate() }
        val combatHook = CombatLogXHook(this)
        val shipping = ShippingService(this, repository, combatHook, main, sounds).also { shippingService = it }
        val mailbox = MailboxService(this, repository, combatHook, main, sounds).also { mailboxService = it }
        val expiration = ExpirationService(this, repository).also { expirationService = it }
        val command = MailCommand(this, shipping, mailbox, combatHook, BookMailService(this, repository, combatHook, main, sounds))
        getCommand("mail")!!.setExecutor(command)
        getCommand("mail")!!.tabCompleter = command
        Bukkit.getPluginManager().registerEvents(GuiListener(shipping, mailbox), this)
        Bukkit.getPluginManager().registerEvents(JoinNotificationService(this, repository, main), this)
        expiration.start()
        logger.info("Enthusia Express enabled.")
    }

    override fun onDisable() {
        expirationService?.stop()
        shippingService?.shutdown()
        mailboxService?.shutdown()
        main?.close()
        repository?.close()
    }
}
