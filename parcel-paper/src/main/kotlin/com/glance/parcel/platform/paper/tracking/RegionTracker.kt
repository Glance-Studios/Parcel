package com.glance.parcel.platform.paper.tracking

import com.glance.parcel.api.event.RegionEnterEvent
import com.glance.parcel.api.event.RegionExitEvent
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * The single place region membership is tracked.
 *
 * Every consumer needs "am I in a region" as an enter/exit signal rather than a poll, and if each
 * one implemented that itself the containment walk would be paid N times over. So it happens once
 * here and is published as [RegionEnterEvent] / [RegionExitEvent].
 *
 * Every enter is guaranteed a matching exit, including on quit and on region deletion.
 */
internal class RegionTracker(
    private val plugin: Plugin,
    private val regions: RegionManager,
    private val intervalTicks: Long,
) : Listener {

    private val occupancy = HashMap<UUID, MutableSet<Region>>()
    private var task: BukkitTask? = null

    fun start() {
        if (task != null) return
        plugin.server.pluginManager.registerEvents(this, plugin)
        task = plugin.server.scheduler.runTaskTimer(plugin, ::tick, intervalTicks, intervalTicks)
    }

    fun stop() {
        task?.cancel()
        task = null
        occupancy.clear()
    }

    private fun tick() {
        for (player in plugin.server.onlinePlayers) {
            update(player)
        }
    }

    private fun update(player: Player) {
        val current = regions.at(player.location)
        // Regions do not override equals, so set membership is identity - which is what we want.
        val previous = occupancy.getOrPut(player.uniqueId) { HashSet() }

        // Exits first, so a consumer swapping between adjacent regions tears down before setting up.
        val exited = previous.filterNot { it in current }
        for (region in exited) {
            previous -= region
            plugin.server.pluginManager.callEvent(RegionExitEvent(player, region))
        }

        for (region in current) {
            if (previous.add(region)) {
                plugin.server.pluginManager.callEvent(RegionEnterEvent(player, region))
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        val left = occupancy.remove(event.player.uniqueId) ?: return
        for (region in left) {
            plugin.server.pluginManager.callEvent(RegionExitEvent(event.player, region))
        }
    }

    /**
     * Called when a region is removed while players may still be inside it, so nobody is left
     * holding state for a region that no longer exists.
     */
    fun onRegionRemoved(region: Region) {
        for ((uuid, occupied) in occupancy) {
            if (!occupied.remove(region)) continue
            val player = plugin.server.getPlayer(uuid) ?: continue
            plugin.server.pluginManager.callEvent(RegionExitEvent(player, region))
        }
    }
}
