package com.glance.parcel.platform.paper.selection

import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.api.selection.Selection
import com.glance.parcel.api.selection.SelectionManager
import com.glance.parcel.api.selection.SelectionMode
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

internal class SelectionManagerImpl(
    private val regions: RegionManager,
    private val defaultMode: SelectionMode = SelectionMode.FLAT,
) : SelectionManager {

    private val selections = ConcurrentHashMap<UUID, SelectionImpl>()

    override fun of(player: Player): Selection? = selections[player.uniqueId]

    override fun getOrCreate(player: Player): Selection = get(player)

    override fun clear(player: Player): Boolean =
        selections.remove(player.uniqueId) != null

    override fun load(player: Player, region: Region): Selection =
        get(player).also { it.loadFrom(region) }

    override fun promote(player: Player, key: NamespacedKey): Region {
        val selection = selections[player.uniqueId]
            ?: error("${player.name} has no active selection")
        val region = selection.toRegion(key)
        // Clear on commit: parts accumulate, so a stale selection would leak into the next region.
        selection.clearParts()
        return region
    }

    /**
     * Internal accessor returning the concrete type, so the marquee tool can drive corners and
     * commits without widening the public API.
     *
     * A selection is discarded if the player changes world, since a selection is world-scoped.
     */
    fun get(player: Player): SelectionImpl {
        val existing = selections[player.uniqueId]
        if (existing != null && existing.world() == player.world) return existing
        return SelectionImpl(player.world, regions, defaultMode)
            .also { selections[player.uniqueId] = it }
    }

    fun forget(player: Player) {
        selections.remove(player.uniqueId)
    }
}
