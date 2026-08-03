package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws wireframe-styled regions as particle grids over their meshed surface.
 *
 * Separate from [PanelRenderer] because the two are different in kind: panels are entities you
 * spawn once and forget, particles have to be re-emitted forever. Same style model drives both, and
 * [PanelRenderer] hands regions over here when their primitive is
 * [PanelPrimitive.WIREFRAME].
 *
 * Drawn per player rather than world-wide, which is what makes range-limiting possible - a large
 * region's grid is far too many points to emit in full, and you can only see the near part anyway.
 */
internal class WireframeRenderer(
    private val plugin: Plugin,
    private val regions: RegionManager,
    private val settings: Settings,
) {

    data class Settings(
        val intervalTicks: Long,
        val range: Double,
        val resolution: Double,
        val lift: Double,
        val maxPoints: Int,
        val flatOffset: Int,
        val followRadius: Double,
        /**
         * How far below the viewer's feet a followed cross-section sits.
         *
         * Separate from the solid panels' own follow offset, and zero by default. A solid plane at
         * eye level is in your face, so panels drop a few blocks; a particle outline is not, and
         * sitting it exactly at your feet is what makes it read the same as the selection outline.
         */
        val followOffset: Double,
    )

    private val active = ConcurrentHashMap<NamespacedKey, PanelStyle>()
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        task = plugin.server.scheduler.runTaskTimer(
            plugin, ::tick, settings.intervalTicks, settings.intervalTicks,
        )
    }

    fun stop() {
        task?.cancel()
        task = null
        active.clear()
    }

    fun show(region: Region, style: PanelStyle) {
        active[region.key()] = style
    }

    fun hide(key: NamespacedKey): Boolean = active.remove(key) != null

    fun isShowing(key: NamespacedKey): Boolean = active.containsKey(key)

    private fun tick() {
        if (active.isEmpty()) return

        for (player in plugin.server.onlinePlayers) {
            // One budget per player across every region, so a busy view degrades evenly rather than
            // the first region consuming everything.
            var remaining = settings.maxPoints
            for ((key, style) in active) {
                if (remaining <= 0) break
                val region = regions.get(key) ?: continue
                if (region.world() != player.world || region.isEmpty()) continue
                remaining -= draw(player, region, style, remaining)
            }
        }
    }

    private fun draw(player: Player, region: Region, style: PanelStyle, budget: Int): Int {
        val mesh = runCatching { DisplayMesh.of(region, DisplayMesh.groundY(region, settings.flatOffset)) }.getOrNull() ?: return 0
        val dust = Particle.DustOptions(style.colour, style.particleSize)
        val flat = DisplayMesh.isCrossSection(region)

        val px = player.location.x
        val py = player.location.y
        val pz = player.location.z
        val rangeSq = settings.range * settings.range

        // Wireframe is redrawn from scratch every pass and is already per player, so following is
        // just an offset applied at draw time - no interpolation, and each viewer gets the plane
        // under their own feet rather than under whoever happens to be nearest.
        val followShift = if (style.follow && flat) {
            val box = region.bounds()
            val dx = maxOf(box.min().x() - px, 0.0, px - (box.max().x() + 1.0))
            val dz = maxOf(box.min().z() - pz, 0.0, pz - (box.max().z() + 1.0))
            if (dx * dx + dz * dz <= settings.followRadius * settings.followRadius) {
                (py - settings.followOffset) - (mesh.firstOrNull()?.origin()?.y()?.toDouble() ?: py)
            } else {
                0.0
            }
        } else {
            0.0
        }

        var used = 0
        val emit = { x: Double, rawY: Double, z: Double ->
            if (used < budget) {
                val y = rawY + followShift
                val dx = x - px
                val dy = y - py
                val dz = z - pz
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    used++
                    player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, dust)
                }
            }
        }

        for (quad in mesh) {
            if (used >= budget) break
            if (flat) {
                // A flat region is one horizontal sheet, and a lattice across it reads as a floor
                // rather than a boundary. Outlining each meshed rectangle gives the same look as
                // the selection outline while still showing carved holes as real holes.
                Wireframe.outline(quad, settings.resolution, settings.lift, emit)
            } else {
                Wireframe.points(
                    quad,
                    style.gridSpacing.toDouble(),
                    settings.resolution,
                    settings.lift,
                    emit,
                )
            }
        }
        return used
    }
}
