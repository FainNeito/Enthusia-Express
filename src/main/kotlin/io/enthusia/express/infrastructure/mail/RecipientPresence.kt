package io.enthusia.express.infrastructure.mail

import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/** Paper owns viewer visibility; hidden sessions have ordinary offline mail semantics. */
object RecipientPresence {
    /** Called only on the server thread, alongside recipient/session validation. */
    @JvmStatic
    fun visiblyOnline(sender: Player, target: OfflinePlayer): Boolean {
        if (!target.isOnline) return false
        val session = target.player ?: return true
        return sender.canSee(session)
    }
}
