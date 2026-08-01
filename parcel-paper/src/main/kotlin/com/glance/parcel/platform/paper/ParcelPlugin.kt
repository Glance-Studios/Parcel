package com.glance.parcel.platform.paper

import com.glance.parcel.api.ParcelAPI
import com.glance.parcel.api.event.ParcelReadyEvent
import com.glance.parcel.platform.paper.region.RegionManagerImpl
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import com.glance.parcel.platform.paper.storage.YamlRegionRepository
import com.glance.parcel.platform.paper.tracking.RegionTracker
import org.bukkit.plugin.ServicePriority
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * No dependency injection here on purpose - Parcel is a handful of collaborators with an obvious
 * construction order, and wiring them by hand in one place is easier to follow than a container.
 */
class ParcelPlugin : JavaPlugin() {

    private lateinit var regions: RegionManagerImpl
    private lateinit var tracker: RegionTracker

    override fun onEnable() {
        saveDefaultConfig()

        val repository = YamlRegionRepository(File(dataFolder, "regions"))
        regions = RegionManagerImpl(this, repository)

        val selections = SelectionManagerImpl(regions)
        val api = ParcelAPIImpl(regions, selections)

        server.servicesManager.register(ParcelAPI::class.java, api, this, ServicePriority.Normal)

        tracker = RegionTracker(
            plugin = this,
            regions = regions,
            intervalTicks = config.getLong("tracking.interval-ticks", 5L).coerceAtLeast(1L),
        )

        // Without this a player standing in a deleted region would never get an exit event,
        // breaking the guarantee RegionExitEvent documents.
        regions.onRegionRemoved = tracker::onRegionRemoved

        // Region loading is async, so consumers must not query until ParcelReadyEvent fires.
        regions.loadAll().whenComplete { count, error ->
            server.scheduler.runTask(this, Runnable {
                if (error != null) {
                    logger.log(Level.SEVERE, "Failed to load regions", error)
                    return@Runnable
                }
                logger.info("Loaded $count region(s)")
                tracker.start()
                server.pluginManager.callEvent(ParcelReadyEvent(api))
            })
        }
    }

    override fun onDisable() {
        if (::tracker.isInitialized) {
            tracker.stop()
        }
        if (::regions.isInitialized) {
            // Block briefly on shutdown - losing an unsaved edit is worse than a slow stop.
            runCatching { regions.saveAll().get(10, TimeUnit.SECONDS) }
                .onFailure { logger.log(Level.SEVERE, "Failed to save regions on shutdown", it) }
        }
        server.servicesManager.unregisterAll(this)
    }
}
