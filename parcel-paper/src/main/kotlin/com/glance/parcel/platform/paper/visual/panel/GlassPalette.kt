package com.glance.parcel.platform.paper.visual.panel

import org.bukkit.Color
import org.bukkit.Material

/**
 * Maps an arbitrary colour onto the nearest stained glass.
 *
 * Block displays cannot take an ARGB colour, so a style that asks for one has to be approximated.
 * Approximating is better than ignoring: a builder who picks red should get red glass, not whatever
 * happens to be in the config.
 */
internal object GlassPalette {

    private val palette: Map<Material, Color> = mapOf(
        Material.WHITE_STAINED_GLASS to Color.fromRGB(0xFF, 0xFF, 0xFF),
        Material.LIGHT_GRAY_STAINED_GLASS to Color.fromRGB(0x9D, 0x9D, 0x97),
        Material.GRAY_STAINED_GLASS to Color.fromRGB(0x47, 0x4F, 0x52),
        Material.BLACK_STAINED_GLASS to Color.fromRGB(0x1D, 0x1D, 0x21),
        Material.BROWN_STAINED_GLASS to Color.fromRGB(0x83, 0x54, 0x32),
        Material.RED_STAINED_GLASS to Color.fromRGB(0xB0, 0x2E, 0x26),
        Material.ORANGE_STAINED_GLASS to Color.fromRGB(0xF9, 0x80, 0x1D),
        Material.YELLOW_STAINED_GLASS to Color.fromRGB(0xFE, 0xD8, 0x3D),
        Material.LIME_STAINED_GLASS to Color.fromRGB(0x80, 0xC7, 0x1F),
        Material.GREEN_STAINED_GLASS to Color.fromRGB(0x5E, 0x7C, 0x16),
        Material.CYAN_STAINED_GLASS to Color.fromRGB(0x16, 0x9C, 0x9C),
        Material.LIGHT_BLUE_STAINED_GLASS to Color.fromRGB(0x3A, 0xB3, 0xDA),
        Material.BLUE_STAINED_GLASS to Color.fromRGB(0x3C, 0x44, 0xAA),
        Material.PURPLE_STAINED_GLASS to Color.fromRGB(0x89, 0x32, 0xB8),
        Material.MAGENTA_STAINED_GLASS to Color.fromRGB(0xC7, 0x4E, 0xBD),
        Material.PINK_STAINED_GLASS to Color.fromRGB(0xF3, 0x8B, 0xAA),
    )

    fun nearest(colour: Color): Material = palette.minBy { (_, candidate) ->
        val dr = colour.red - candidate.red
        val dg = colour.green - candidate.green
        val db = colour.blue - candidate.blue
        dr * dr + dg * dg + db * db
    }.key
}
