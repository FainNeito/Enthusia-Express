// All native reflection is isolated here; missing signatures disable only the cosmetic renderer.
@file:Suppress("CommentOverPrivateFunction")

package io.enthusia.express.infrastructure.hook

import io.enthusia.express.domain.MailNotification
import io.enthusia.express.domain.MailType
import java.util.Optional
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/** Builds transient advancement packets using the server's own encoder, with no registration. */
internal class NativeToastPackets {
    private val advancement = native("advancements.Advancement")
    private val holder = native("advancements.AdvancementHolder").constructors.firstOrNull {
        it.parameterCount == 2 && it.parameterTypes[1] == advancement
    } ?: throw NoSuchMethodException("AdvancementHolder constructor")
    private val location = holder.parameterTypes[0]
    private val parse = location.getMethod("parse", String::class.java)
    private val component = native("network.chat.Component")
    private val literal = component.getMethod("literal", String::class.java)
    private val frame = native("advancements.AdvancementType")
    private val bool = Boolean::class.javaPrimitiveType!!
    private val icons = NativeToastIcon(Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack"))
    private val display = native("advancements.DisplayInfo").getConstructor(
        icons.type, component, component, Optional::class.java, frame, bool, bool, bool)
    private val requirements = native("advancements.AdvancementRequirements")
    private val requirement = requirements.getConstructor(List::class.java).newInstance(listOf(listOf("mail")))
    private val rewards = native("advancements.AdvancementRewards")
    private val entry = advancement.getConstructor(Optional::class.java, Optional::class.java,
        rewards, Map::class.java, requirements, bool)
    private val progress = native("advancements.AdvancementProgress")
    private val update = progress.getMethod("update", requirements)
    private val grant = progress.getMethod("grantProgress", String::class.java)
    private val handle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle")
    private val connection = native("server.level.ServerPlayer").getField("connection")
    private val send = native("server.network.ServerCommonPacketListenerImpl")
        .getMethod("send", native("network.protocol.Packet"))
    private val packet = packetConstructor()

    fun key(value: String): Any = parse.invoke(null, value)

    fun show(player: Player, key: Any, notice: MailNotification) {
        val icon = if (notice.count == 1 && notice.type != MailType.PACKAGE) Material.PAPER else Material.CHEST
        val info = display.newInstance(icons.convert(ItemStack(icon)),
            literal.invoke(null, notice.text()), literal.invoke(null, ""), Optional.empty<Any>(),
            frame.getField("TASK").get(null), true, false, true)
        val value = entry.newInstance(Optional.empty<Any>(), Optional.of(info),
            rewards.getField("EMPTY").get(null), emptyMap<String, Any>(), requirement, false)
        val completed = progress.getConstructor().newInstance()
        update.invoke(completed, requirement)
        grant.invoke(completed, "mail")
        transmit(player, listOf(holder.newInstance(key, value)), emptySet(), mapOf(key to completed), true)
    }

    fun remove(player: Player, key: Any) {
        transmit(player, emptyList(), setOf(key), emptyMap(), false)
    }

    private fun transmit(player: Player, added: List<Any>, removed: Set<Any>, completed: Map<Any, Any>, toast: Boolean) {
        val message = if (packet.parameterCount == 5) packet.newInstance(false, added, removed, completed, toast)
            else packet.newInstance(false, added, removed, completed)
        send.invoke(connection.get(handle.invoke(player)), message)
    }

    private fun packetConstructor(): java.lang.reflect.Constructor<*> {
        val type = native("network.protocol.game.ClientboundUpdateAdvancementsPacket")
        return try { type.getConstructor(bool, Collection::class.java, Set::class.java, Map::class.java, bool) }
        catch (_: NoSuchMethodException) { type.getConstructor(bool, Collection::class.java, Set::class.java, Map::class.java) }
    }

    private fun native(name: String): Class<*> = Class.forName("net.minecraft.$name")
}
