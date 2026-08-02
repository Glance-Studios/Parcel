package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.event.RegionDeleteEvent
import com.glance.parcel.api.event.RegionModifyEvent
import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.mesh.Quad
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
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
    private val regions: RegionManager,
) : Listener {

    private fun regionOf(key: NamespacedKey) = regions.get(key)

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
        /** Blocks above ground to place the cross-section slab drawn for flat regions. */
        val flatOffset: Int,
        val follow: Follow,
        val viewRange: Float,
        val cullingPadding: Float,
        val maxPanels: Int,
        /**
         * Whether a render is visible only to whoever asked for it.
         *
         * On by default: a rendered region is a working overlay, and nobody else needs someone
         * else's working overlay draped over the map. Turn it off for a render everyone should see,
         * like a permanent arena boundary.
         */
        val viewerOnly: Boolean,
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

    /**
     * Settings for a flat region's plane riding under the nearest player.
     *
     * @param radius how close a player must be for the plane to follow them
     * @param offset how far below their feet it sits
     * @param intervalTicks how often the target height is recalculated
     * @param interpolationTicks how long the client is told the move takes. Deliberately longer
     *   than [intervalTicks]: if the announced duration equals the gap, the client sits exactly on
     *   the boundary and any late packet strands it on a finished move. Overshooting keeps it
     *   permanently mid-lerp, so a late update re-aims it instead of stuttering.
     */
    data class Follow(
        val radius: Double,
        val offset: Double,
        val intervalTicks: Long,
        val interpolationTicks: Int,
    )

    /**
     * A live render, and who it belongs to.
     *
     * Panels are real entities, so without an owner every player on the server sees every rendered
     * region - somebody's admin overlay draped over the map. The particle visualisers were always
     * private simply because `player.spawnParticle` is; entities had to be made so deliberately.
     */
    private class Render(val displays: List<Display>, val owner: UUID?)

    private val active = HashMap<NamespacedKey, Render>()
    private var followTask: BukkitTask? = null

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

    /**
     * @param viewer whoever asked for this. A cross-section plane spawns at their height rather
     *   than on the ground, so it appears where they are looking instead of somewhere below them -
     *   which matters even with follow off, since that is then where it stays.
     * @return how many panels were spawned, or -1 if the mesh was refused as too large
     */
    @JvmOverloads
    fun show(
        region: Region,
        style: PanelStyle = styleFor(region),
        viewer: Player? = null,
    ): Int {
        hide(region.key())
        if (region.isEmpty()) return 0

        val flatY = flatHeightFor(region, viewer)
        val mesh = runCatching { DisplayMesh.of(region, flatY) }.getOrNull() ?: return -1
        if (mesh.isEmpty()) return 0
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

        val owner = viewer?.takeIf { settings.viewerOnly }
        active[region.key()] = Render(displays, owner?.uniqueId)
        owner?.let { applyVisibility(displays, it) }
        return displays.size
    }

    /**
     * Hides the render from everyone but its owner.
     *
     * `hideEntity` rather than sending fake entities by packet: packets would keep the world
     * completely clean, but cost a packetevents dependency in an open-source jar for a benefit that
     * mattered most on Folia - which is out of scope. Revisit if that changes.
     */
    private fun applyVisibility(displays: List<Display>, owner: Player) {
        plugin.server.onlinePlayers
            .filter { it.uniqueId != owner.uniqueId }
            .forEach { other -> displays.forEach { other.hideEntity(plugin, it) } }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // Someone joining after a render was spawned would otherwise see it - the hide only ever
        // applied to who was online at the time.
        val joiner = event.player
        active.values
            .filter { it.owner != null && it.owner != joiner.uniqueId }
            .forEach { render -> render.displays.forEach { joiner.hideEntity(plugin, it) } }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        // An owned render with its owner gone is invisible to everyone - litter, not a view.
        active.entries
            .filter { it.value.owner == event.player.uniqueId }
            .map { it.key }
            .forEach(::hide)
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

        // Set once, at spawn. Changing it later would itself restart interpolation, so following
        // planes announce their move duration up front and then only ever teleport.
        display.teleportDuration = settings.follow.interpolationTicks

        display.isPersistent = false
    }

    fun hide(key: NamespacedKey) {
        active.remove(key)?.displays?.forEach { it.remove() }
        wireframes.hide(key)
    }

    fun refresh(region: Region) {
        if (active.containsKey(region.key())) show(region)
    }

    fun hideAll() {
        active.keys.toList().forEach(::hide)
    }

    fun start() {
        if (followTask != null) return
        followTask = plugin.server.scheduler.runTaskTimer(
            plugin, ::followTick, settings.follow.intervalTicks, settings.follow.intervalTicks,
        )
    }

    fun stopFollowing() {
        followTask?.cancel()
        followTask = null
    }

    /**
     * Slides following planes to sit under the nearest player.
     *
     * ⚠️ Only the teleport happens here. Touching the transformation, or any other metadata, would
     * restart the client's interpolation and turn a glide into a stutter - the single most
     * important rule when driving display entities.
     */
    private fun followTick() {
        if (active.isEmpty()) return

        for ((key, render) in active) {
            val displays = render.displays
            if (displays.isEmpty()) continue
            val region = regionOf(key) ?: continue
            val style = styleFor(region)
            if (!style.follow || !DisplayMesh.isCrossSection(region)) continue

            val target = targetHeight(region, render, displays.first()) ?: continue
            for (display in displays) {
                val at = display.location
                // A hair of hysteresis: without it, a player bobbing on a jump re-teleports every
                // pass and the plane never settles.
                if (kotlin.math.abs(at.y - target) < 0.05) continue
                display.teleport(at.clone().apply { y = target })
            }
        }
    }

    /**
     * Where a cross-section plane starts: under whoever asked, else under the nearest player in
     * range, else on the ground.
     */
    private fun flatHeightFor(region: Region, viewer: Player?): Int {
        if (!DisplayMesh.isCrossSection(region)) return DisplayMesh.groundY(region, settings.flatOffset)

        val subject = viewer?.takeIf { it.world == region.world() }
            ?: nearestPlayer(region)
            ?: return DisplayMesh.groundY(region, settings.flatOffset)

        return Math.floor(subject.location.y - settings.follow.offset).toInt()
    }

    private fun nearestPlayer(region: Region): Player? = plugin.server.onlinePlayers
        .filter { it.world == region.world() }
        .filter { withinRadius(it.location.x, it.location.z, region.bounds()) }
        .minByOrNull { it.location.y }

    private fun targetHeight(region: Region, render: Render, sample: Display): Double? {
        val world = region.world()

        // Follow the owner, not whoever happens to be closest. Only the owner can see this render,
        // so tracking anyone else would put the plane at a height that is wrong for the one person
        // looking at it - a bug single-player testing could never surface.
        val subject = render.owner
            ?.let { plugin.server.getPlayer(it) }
            ?: plugin.server.onlinePlayers
                .filter { it.world == world }
                .minByOrNull { it.location.distanceSquared(sample.location) }
            ?: return null

        if (subject.world != world) return null
        if (!withinRadius(subject.location.x, subject.location.z, region.bounds())) return null

        return subject.location.y - settings.follow.offset
    }

    private fun withinRadius(x: Double, z: Double, box: BlockBox): Boolean {
        // Distance to the footprint, not to its centre - a long region should follow you anywhere
        // along it, not only near the middle.
        val dx = maxOf(box.min().x() - x, 0.0, x - (box.max().x() + 1.0))
        val dz = maxOf(box.min().z() - z, 0.0, z - (box.max().z() + 1.0))
        val r = settings.follow.radius
        return dx * dx + dz * dz <= r * r
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
