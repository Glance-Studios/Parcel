package com.glance.parcel.platform.paper.visual

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import org.bukkit.Color
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * Draws box outlines in particles, per player.
 *
 * Deliberately the first visualiser. Particles are per-player natively via
 * [Player.spawnParticle], so this needs no packet layer, no entity bookkeeping and none of the
 * culling unknowns the display panels carry. It also makes the marquee tool usable - marking two
 * corners you cannot see is worse than typing coordinates.
 *
 * Technique is lifted from spawn-protection's `Border.drawWall`, with one change: that used
 * world-wide `sendParticles`, so everyone saw the guides. These are private to the viewer.
 */
internal class OutlineRenderer(
    private val plugin: Plugin,
    private val regions: RegionManager,
    private val selections: SelectionManagerImpl,
    private val settings: Settings,
) : Listener {

    data class Settings(
        val intervalTicks: Long,
        val range: Double,
        val spacing: Double,
        val maxPoints: Int,
    )

    /** Saved regions a player has asked to see, via `/parcel show`. */
    private val watched = HashMap<UUID, MutableSet<NamespacedKey>>()

    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        plugin.server.pluginManager.registerEvents(this, plugin)
        task = plugin.server.scheduler.runTaskTimer(
            plugin, ::tick, settings.intervalTicks, settings.intervalTicks,
        )
    }

    fun stop() {
        task?.cancel()
        task = null
        watched.clear()
    }

    /** @return true if the region is now shown, false if it was hidden */
    fun toggleWatch(player: Player, region: Region): Boolean {
        val keys = watched.getOrPut(player.uniqueId) { HashSet() }
        return if (!keys.add(region.key())) {
            keys.remove(region.key())
            false
        } else {
            true
        }
    }

    fun watchedBy(player: Player): Set<NamespacedKey> = watched[player.uniqueId].orEmpty()

    fun clearWatches(player: Player) {
        watched.remove(player.uniqueId)
    }

    private fun tick() {
        for (player in plugin.server.onlinePlayers) {
            // A budget per player, not per box, so a big selection degrades evenly instead of the
            // first part eating everything.
            val budget = Budget(settings.maxPoints)
            drawSelection(player, budget)
            drawWatched(player, budget)
        }
    }

    private fun drawSelection(player: Player, budget: Budget) {
        val selection = selections.of(player) ?: return
        if (selection.world() != player.world) return

        selection.parts().forEach { part ->
            val colour = if (part.op() == Op.ADD) ADD else CARVE
            draw(player, part.shape().bounds(), colour, budget)
        }

        // The box the player is about to commit, so corners are visible before they mean anything.
        selections.get(player).pendingShape()?.let { draw(player, it.bounds(), PENDING, budget) }
    }

    private fun drawWatched(player: Player, budget: Budget) {
        val keys = watched[player.uniqueId] ?: return
        val stale = keys.filter { regions.get(it) == null }
        keys.removeAll(stale.toSet())

        keys.forEach { key ->
            val region = regions.get(key) ?: return@forEach
            if (region.world() != player.world || region.isEmpty()) return@forEach
            region.parts().forEach { part ->
                val colour = if (part.op() == Op.ADD) WATCHED else CARVE
                draw(player, part.shape().bounds(), colour, budget)
            }
        }
    }

    private fun draw(player: Player, box: BlockBox, colour: Particle.DustOptions, budget: Budget) {
        if (budget.exhausted) return

        val px = player.location.x
        val py = player.location.y
        val pz = player.location.z
        val rangeSq = settings.range * settings.range

        // Only the part of the outline near the viewer is drawn. That is what makes a full-height
        // prism affordable: its 384-block vertical edges would otherwise be ~1500 points on their
        // own, and you can only see a few blocks of them anyway.
        Outline.edges(box, settings.spacing) { x, y, z ->
            if (!budget.exhausted) {
                val dx = x - px
                val dy = y - py
                val dz = z - pz
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    budget.spend()
                    player.spawnParticle(Particle.DUST, x, y, z, 1, 0.0, 0.0, 0.0, 0.0, colour)
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        watched.remove(event.player.uniqueId)
    }

    private class Budget(private val max: Int) {
        private var used = 0
        val exhausted: Boolean get() = used >= max
        fun spend() {
            used++
        }
    }

    private companion object {
        val ADD = Particle.DustOptions(Color.fromRGB(0x55FF55), 0.9f)
        val CARVE = Particle.DustOptions(Color.fromRGB(0xFF5555), 0.9f)
        val PENDING = Particle.DustOptions(Color.fromRGB(0x55FFFF), 1.1f)
        val WATCHED = Particle.DustOptions(Color.fromRGB(0xFFAA00), 0.9f)
    }
}
