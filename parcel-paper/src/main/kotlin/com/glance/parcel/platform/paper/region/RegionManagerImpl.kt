package com.glance.parcel.platform.paper.region

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

    override fun create(key: NamespacedKey, world: World): Region {
        require(!regions.containsKey(key)) { "A region already exists under $key" }
        val region = RegionImpl(key, world, onChanged = ::persist)
        regions[key] = region
        return region
    }

    override fun delete(key: NamespacedKey): Boolean {
        val removed = regions.remove(key) ?: return false
        repository.delete(removed.key()).exceptionally { error ->
            plugin.logger.log(Level.SEVERE, "Failed to delete region ${removed.key()}", error)
            null
        }
        return true
    }

    override fun save(region: Region): CompletableFuture<Void> =
        repository.save(region.toRecord())

    override fun saveAll(): CompletableFuture<Void> =
        CompletableFuture.allOf(
            *regions.values.map { repository.save(it.toRecord()) }.toTypedArray()
        )

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
            regions[record.key()] = RegionImpl(record.key(), world, record.parts(), ::persist)
            loaded++
        }
        loaded
    }

    /** Registered as the change hook on every region, so an edit persists itself. */
    private fun persist(region: RegionImpl) {
        repository.save(region.toRecord()).exceptionally { error ->
            plugin.logger.log(Level.SEVERE, "Failed to save region ${region.key()}", error)
            null
        }
    }

    private fun Region.toRecord() =
        RegionRepository.Record(key(), world().name, parts())
}
