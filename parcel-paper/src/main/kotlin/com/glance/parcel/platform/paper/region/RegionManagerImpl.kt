package com.glance.parcel.platform.paper.region

import com.glance.parcel.api.event.RegionCreateEvent
import com.glance.parcel.api.event.RegionDeleteEvent
import com.glance.parcel.api.event.RegionModifyEvent
import com.glance.parcel.api.event.RegionUsageQueryEvent
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.api.storage.RegionRepository
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

internal class RegionManagerImpl(
    private val plugin: Plugin,
    private val repository: RegionRepository,
) : RegionManager {

    private val regions = ConcurrentHashMap<NamespacedKey, RegionImpl>()

    /**
     * Notified when a region stops existing, so anything holding per-player state for it can let
     * go. Wired to the tracker on enable, which is what keeps the "every enter gets an exit"
     * guarantee true across a deletion.
     */
    var onRegionRemoved: (Region) -> Unit = {}

    override fun get(key: NamespacedKey): Region? = regions[key]

    override fun all(): Collection<Region> = java.util.List.copyOf(regions.values)

    override fun inNamespace(namespace: String): Collection<Region> =
        java.util.List.copyOf(regions.values.filter { it.key().namespace == namespace })

    override fun at(location: Location): Collection<Region> {
        val world = location.world ?: return emptyList()
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ

        // Bounds rejection happens inside contains(), so this stays cheap enough for a tick loop.
        return java.util.List.copyOf(
            regions.values.filter { it.world() == world && it.contains(x, y, z) }
        )
    }

    override fun create(key: NamespacedKey, world: World): Region =
        register(key, world, transient = false)

    override fun createTransient(key: NamespacedKey, world: World): Region =
        register(key, world, transient = true)

    private fun register(key: NamespacedKey, world: World, transient: Boolean): Region {
        require(!regions.containsKey(key)) { "A region already exists under $key" }
        val region = RegionImpl(key, world, transient = transient, onChanged = ::persist)
        regions[key] = region
        plugin.server.pluginManager.callEvent(RegionCreateEvent(region))
        return region
    }

    override fun delete(key: NamespacedKey): Boolean {
        val target = regions[key] ?: return false

        // Regions are shared by key, so a delete can break consumers that had no part in it.
        val event = RegionDeleteEvent(target)
        plugin.server.pluginManager.callEvent(event)
        if (event.isCancelled) return false

        val removed = regions.remove(key) ?: return false
        onRegionRemoved(removed)
        if (removed.isTransient()) return true

        repository.delete(removed.key()).exceptionally { error ->
            plugin.logger.log(Level.SEVERE, "Failed to delete region ${removed.key()}", error)
            null
        }
        return true
    }

    override fun usagesOf(region: Region): List<String> {
        val event = RegionUsageQueryEvent(region)
        plugin.server.pluginManager.callEvent(event)
        return event.usages()
    }

    override fun save(region: Region): CompletableFuture<Void> =
        repository.save(region.toRecord())

    override fun saveAll(): CompletableFuture<Void> =
        CompletableFuture.allOf(
            *regions.values
                .filterNot { it.isTransient() }
                .map { repository.save(it.toRecord()) }
                .toTypedArray()
        )

    /**
     * Drops every loaded region and reads them back from disk.
     *
     * Existing regions are announced as removed first, so anything holding per-player state for them
     * lets go before the replacements arrive. Call on the main thread.
     */
    fun reload(): CompletableFuture<Int> {
        // Transient regions survive: a reload re-reads what is on disk, and they were never there.
        // Dropping them would silently destroy state belonging to whichever plugin generated them.
        val persistent = regions.values.filterNot { it.isTransient() }
        persistent.forEach(onRegionRemoved)
        persistent.forEach { regions.remove(it.key()) }
        return loadAll()
    }

    /**
     * Loads every stored region. Regions whose world is not loaded are skipped with a warning
     * rather than dropped, so a temporarily unloaded world does not silently delete data.
     */
    fun loadAll(): CompletableFuture<Int> = repository.loadAll().thenApply { records ->
        var loaded = 0
        for (record in records) {
            val world = Bukkit.getWorld(record.world())
            if (world == null) {
                plugin.logger.warning(
                    "Skipping region ${record.key()}: world '${record.world()}' is not loaded"
                )
                continue
            }
            regions[record.key()] = RegionImpl(
                record.key(), world, record.parts(), transient = false, onChanged = ::persist,
            )
            loaded++
        }
        loaded
    }

    /**
     * Registered as the change hook on every region, so an edit persists itself and announces
     * itself. The announcement matters because consumers share regions by key - an edit made
     * anywhere is an edit everyone bound to that key needs to hear about.
     */
    private fun persist(region: RegionImpl) {
        // The event fires either way - a transient region's shape changing matters just as much to
        // a consumer as a saved one's. Only the write is skipped.
        plugin.server.pluginManager.callEvent(RegionModifyEvent(region))
        if (region.isTransient()) return

        repository.save(region.toRecord()).exceptionally { error ->
            plugin.logger.log(Level.SEVERE, "Failed to save region ${region.key()}", error)
            null
        }
    }

    private fun Region.toRecord() =
        RegionRepository.Record(key(), world().name, parts())
}
