package io.enthusia.express.infrastructure.gui

import net.kyori.adventure.text.Component
import org.bukkit.plugin.Plugin

/** Optional glyph lookup kept behind the Nexo runtime's class loader. */
fun interface NexoGlyphSource {
    fun component(nexo: Plugin, id: String): Component?
}

/** Public Nexo1.29.1 APIs, verified against the actual companion JAR. */
class ReflectiveNexoGlyphs : NexoGlyphSource {
    override fun component(nexo: Plugin, id: String): Component? {
        val api = Class.forName("com.nexomc.nexo.NexoPlugin", true, nexo.javaClass.classLoader)
        val instance = api.getMethod("instance").invoke(null)
        val manager = api.getMethod("fontManager").invoke(instance)
        val type = Class.forName("com.nexomc.nexo.fonts.FontManager", true, nexo.javaClass.classLoader)
        val glyph = type.getMethod("glyphFromID", String::class.java).invoke(manager, id) ?: return null
        val glyphType = Class.forName("com.nexomc.nexo.glyphs.Glyph", true, nexo.javaClass.classLoader)
        return glyphType.getMethod("getComponent").invoke(glyph) as Component
    }
}
