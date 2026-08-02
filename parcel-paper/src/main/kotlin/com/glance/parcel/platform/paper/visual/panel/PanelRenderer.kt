package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.event.RegionDeleteEvent
import com.glance.parcel.api.event.RegionModifyEvent
import com.glance.parcel.api.mesh.Quad
import com.glance.parcel.api.region.Region
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Draws a region's meshed surface as translucent display panels.
 *
 * One display per quad, and the mesher has already removed interior faces and merged coplanar
 * neighbours - so two flush boxes are six panels, not twelve.
 *
 * **Spike-stage decisions, deliberately taken to answer one question first:**
 * - **Real entities, not packets.** Packets are the eventual answer (per-player, nothing left in the
 *   world) but client-side culling behaves identically either way, and that is what needs
 *   answering before the abstraction is worth building.
 * - **Block displays, not text displays.** Exact scale-to-block mapping with no calibration. Text
 *   displays give arbitrary ARGB alpha, which is nicer, but their quad is sized by its glyphs.
 *
 * Entities are spawned non-persistent, so a crash or a forgotten toggle cannot leave them saved
 * into the world.
 */
internal class PanelRenderer(
    private val plugin: Plugin,
    private val settings: Settings,
    private val styles: StyleStore,
    private val wireframes: WireframeRenderer,
) : Listener {

    /** Explicit choice, then the region's stored default, then the config default. */
    fun styleFor(region: Region): PanelStyle =
        styles.get(region.key()) ?: settings.defaultStyle

    fun settingsDefaultStyle(): PanelStyle = settings.defaultStyle

    data class Settings(
        val thickness: Float,
        /**
         * Lift off the covered surface, along the face normal. Purely a depth nudge: the normal is
         * perpendicular to both axes the panel is sized on, so this cannot disturb size or
         * alignment and is safe to tune without recalibrating.
         */
        val surfaceOffset: Double,
        val viewRange: Float,
        val cullingPadding: Float,
        val maxPanels: Int,
        /** Fallback when a region has no stored style of its own. */
        val defaultStyle: PanelStyle,
        /**
         * Size in blocks of an empty text display's background at scale 1. Measured rather than
         * derived - a text display's quad is sized by its glyphs and padding, so covering an exact
         * area means knowing what one unit of scale buys. Calibrate against BLOCK panels, which
         * are exact.
         */
        val textBaseWidth: Float,
        val textBaseHeight: Float,
        /**
         * Where the quad sits relative to its entity, per unit of scale. A text display's text
         * hangs above its position like a nametag rather than being centred on it, so covering an
         * exact area needs a translation too - and because scaling moves the quad, the correction
         * scales with it. Measured by `/parcel calibrate`.
         */
        val textAnchorX: Float,
        val textAnchorY: Float,
    )

    private val active = HashMap<NamespacedKey, List<Display>>()

    fun isShowing(region: Region): Boolean =
        active.containsKey(region.key()) || wireframes.isShowing(region.key())

    /** @return true if the region is now shown */
    fun toggle(region: Region): Boolean {
        if (active.containsKey(region.key())) {
            hide(region.key())
            return false
        }
        show(region)
        return true
    }

    /** @return how many panels were spawned, or -1 if the mesh was refused as too large */
    @JvmOverloads
    fun show(region: Region, style: PanelStyle = styleFor(region)): Int {
        hide(region.key())
        if (region.isEmpty()) return 0

        val mesh = runCatching { region.mesh() }.getOrNull() ?: return -1
        if (mesh.size > settings.maxPanels) return -1

        // Particles are re-emitted forever rather than spawned once, so that path lives elsewhere.
        if (style.primitive == PanelPrimitive.WIREFRAME) {
            wireframes.show(region, style)
            return mesh.size
        }

        val world = region.world()
        val displays = when (style.primitive) {
            PanelPrimitive.WIREFRAME -> emptyList() // handled above
            PanelPrimitive.BLOCK -> mesh.map { spawnBlockPanel(world, it, style) }
            // Two per quad: a text display renders from one side only.
            PanelPrimitive.TEXT -> mesh.flatMap { quad ->
                val p = Panels.textPlacementFor(quad, settings.surfaceOffset)
                listOf(
                    spawnTextPanel(world, p, p.rotation, style),
                    spawnTextPanel(world, p, Panels.mirrored(p.rotation), style),
                )
            }
        }

        active[region.key()] = displays
        return displays.size
    }

    private fun spawnBlockPanel(world: World, quad: Quad, style: PanelStyle): BlockDisplay {
        val p = Panels.placementFor(quad, settings.thickness, settings.surfaceOffset)
        return world.spawn(Location(world, p.x, p.y, p.z), BlockDisplay::class.java) { display ->
            // Block displays cannot take an arbitrary colour, so honour the request as closely as
            // the glass palette allows rather than ignoring it.
            display.block = GlassPalette.nearest(style.colour).createBlockData()
            display.transformation = Transformation(
                Vector3f(p.tx, p.ty, p.tz),
                AxisAngle4f(),
                Vector3f(p.sx, p.sy, p.sz),
                AxisAngle4f(),
            )
            applyCommon(display, p.widthExtent, p.heightExtent)
        }
    }

    private fun spawnTextPanel(
        world: World,
        p: TextPanelPlacement,
        rotation: Quaternionf,
        style: PanelStyle,
    ): TextDisplay = world.spawn(
        Location(world, p.x, p.y, p.z),
        TextDisplay::class.java,
    ) { display ->
        // A single space, so only the background quad renders - no glyph, no shadow.
        display.text(Component.text(" "))
        display.backgroundColor = style.argb
        display.billboard = Display.Billboard.FIXED
        display.isSeeThrough = false
        display.isShadowed = false

        val scaleX = p.coverWidth / settings.textBaseWidth
        val scaleY = p.coverHeight / settings.textBaseHeight

        display.transformation = Transformation(
            // Translation is world space and applied after rotation, so the local anchor
            // correction has to be rotated into this face's frame first.
            rotation.transform(
                Vector3f(-settings.textAnchorX * scaleX, -settings.textAnchorY * scaleY, 0f)
            ),
            rotation,
            Vector3f(scaleX, scaleY, 1f),
            Quaternionf(),
        )
        applyCommon(display, maxOf(p.coverWidth, p.coverHeight), p.coverHeight)
    }

    private fun applyCommon(display: Display, width: Float, height: Float) {
        // Fullbright, so a panel underground reads the same as one in daylight.
        display.brightness = Display.Brightness(15, 15)
        display.viewRange = settings.viewRange

        // The client culls a display on its BOUNDING BOX, not on what it renders, and the default
        // box is the entity's own point. A panel scaled across a whole face therefore vanishes the
        // moment its origin leaves the frustum - which, stood inside a region, is most of the time.
        display.displayWidth = width + settings.cullingPadding
        display.displayHeight = height + settings.cullingPadding

        display.isPersistent = false
    }

    fun hide(key: NamespacedKey) {
        active.remove(key)?.forEach { it.remove() }
        wireframes.hide(key)
    }

    fun refresh(region: Region) {
        if (active.containsKey(region.key())) show(region)
    }

    fun hideAll() {
        active.keys.toList().forEach(::hide)
    }

    /**
     * Regions are shared by key, so a shape can change underneath us at any time - from another
     * plugin, or from someone else's marquee. This is exactly what RegionModifyEvent is for: the
     * cached mesh is already invalid by the time we hear, so redraw from scratch.
     */
    @EventHandler
    fun onModify(event: RegionModifyEvent) = refresh(event.region())

    @EventHandler
    fun onDelete(event: RegionDeleteEvent) = hide(event.region().key())
}
