package com.glance.parcel.platform.paper.visual.panel

import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color

/**
 * How a region is drawn, as opposed to what shape it is.
 *
 * Kept separate from the region on purpose. Regions are shared geometry referenced by key, so two
 * consumers may reasonably want to draw the same region differently - the tavern is one shape that
 * is both an ambience zone and an arena. Baking a colour into the region would make one consumer's
 * presentation preference part of everyone's data, which is the same mistake as copying geometry.
 */
internal data class PanelStyle(
    val primitive: PanelPrimitive,
    val red: Int,
    val green: Int,
    val blue: Int,
    val alpha: Int,
    /** Blocks between wireframe grid lines. Ignored by the solid primitives. */
    val gridSpacing: Float = 1f,
    /** Particle size for wireframe lines. */
    val particleSize: Float = 0.6f,
) {
    val colour: Color get() = Color.fromRGB(red, green, blue)

    val argb: Color get() = Color.fromARGB(alpha, red, green, blue)

    fun hex(): String = "#%02x%02x%02x".format(red, green, blue)

    companion object {

        /**
         * Parses a colour the way a builder would write one: a MiniMessage-style hex (`#55c8ff`)
         * or a named colour (`aqua`). Adventure already knows every name, so there is no table to
         * maintain here.
         */
        fun parseColour(input: String): Color? {
            val trimmed = input.trim().removePrefix("<").removeSuffix(">")
            if (trimmed.isEmpty() || trimmed == "-") return null

            val hex = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            TextColor.fromHexString(hex)?.let { return Color.fromRGB(it.red(), it.green(), it.blue()) }

            return net.kyori.adventure.text.format.NamedTextColor.NAMES.value(trimmed.lowercase())
                ?.let { Color.fromRGB(it.red(), it.green(), it.blue()) }
        }
    }
}
