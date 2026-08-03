package com.glance.parcel.platform.paper

import com.glance.parcel.api.ParcelAPI
import com.glance.parcel.api.event.ParcelReadyEvent
import com.glance.parcel.api.selection.SelectionMode
import com.glance.parcel.platform.paper.command.DevCommands
import com.glance.parcel.platform.paper.command.MarqueeCommands
import com.glance.parcel.platform.paper.menu.RegionBrowser
import com.glance.parcel.platform.paper.command.ParcelCommandManager
import com.glance.parcel.platform.paper.command.RegionCommands
import com.glance.parcel.platform.paper.marquee.MarqueeListener
import com.glance.parcel.platform.paper.marquee.MarqueeWand
import com.glance.parcel.platform.paper.region.RegionManagerImpl
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import com.glance.parcel.platform.paper.storage.HistoryStore
import com.glance.parcel.platform.paper.storage.PartCodec
import com.glance.parcel.platform.paper.storage.YamlRegionRepository
import com.glance.parcel.platform.paper.tracking.RegionTracker
import com.glance.parcel.platform.paper.visual.OutlineRenderer
import com.glance.parcel.platform.paper.visual.panel.Displays
import com.glance.parcel.platform.paper.visual.panel.PanelCalibration
import com.glance.parcel.platform.paper.visual.panel.PanelPrimitive
import com.glance.parcel.platform.paper.visual.panel.PanelRenderer
import com.glance.parcel.platform.paper.visual.panel.PanelStyle
import com.glance.parcel.platform.paper.visual.panel.PanelStyleDialog
import com.glance.parcel.platform.paper.visual.panel.StyleStore
import com.glance.parcel.platform.paper.visual.panel.WireframeRenderer
import org.bukkit.Material
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
    private lateinit var outlines: OutlineRenderer
    private lateinit var panels: PanelRenderer
    private lateinit var calibration: PanelCalibration
    private lateinit var styles: StyleStore
    private lateinit var displays: Displays
    private lateinit var wireframes: WireframeRenderer

    override fun onEnable() {
        saveDefaultConfig()
        // Before anything reads a value: saveDefaultConfig only writes the file once, so without
        // this an old install silently runs on code defaults for every option added since.
        if (ConfigMigration.run(this).isNotEmpty()) reloadConfig()

        val codec = PartCodec()
        val repository = YamlRegionRepository(File(dataFolder, "regions"), codec)
        val history = HistoryStore(File(dataFolder, "history"), codec)
        regions = RegionManagerImpl(this, repository, history)

        val selections = SelectionManagerImpl(
            regions,
            defaultMode = runCatching {
                SelectionMode.valueOf(
                    (config.getString("marquee.default-mode") ?: "FLAT").uppercase()
                )
            }.getOrElse {
                logger.warning("Unknown marquee.default-mode, falling back to FLAT")
                SelectionMode.FLAT
            },
        )
        outlines = OutlineRenderer(
            plugin = this,
            regions = regions,
            selections = selections,
            settings = OutlineRenderer.Settings(
                intervalTicks = config.getLong("outline.interval-ticks", 4L).coerceAtLeast(1L),
                range = config.getDouble("outline.range", 48.0),
                spacing = config.getDouble("outline.spacing", 1.0),
                maxPoints = config.getInt("outline.max-points", 600),
                flatOffset = config.getDouble("outline.flat-offset", 0.0),
            ),
        )

        val wandMaterial = Material.matchMaterial(
            config.getString("marquee.wand-material") ?: "GOLDEN_AXE"
        ) ?: Material.GOLDEN_AXE.also {
            logger.warning("Unknown marquee.wand-material, falling back to GOLDEN_AXE")
        }
        val wand = MarqueeWand(this, wandMaterial, selections)

        server.pluginManager.registerEvents(
            MarqueeListener(this, wand, selections, config.getBoolean("marquee.debug", false)),
            this,
        )

        styles = StyleStore(File(dataFolder, "styles.yml")).apply { load() }

        displays = Displays(this)
        // Catches anything a previous enable left standing - a plugin reload disables us without
        // unloading the world, so displays with nothing left to own them would otherwise linger.
        displays.sweep().takeIf { it > 0 }?.let { logger.info("Removed $it orphaned display(s)") }

        wireframes = WireframeRenderer(
            plugin = this,
            regions = regions,
            settings = WireframeRenderer.Settings(
                intervalTicks = config.getLong("panels.wireframe.interval-ticks", 4L).coerceAtLeast(1L),
                range = config.getDouble("panels.wireframe.range", 48.0),
                resolution = config.getDouble("panels.wireframe.resolution", 0.5),
                lift = config.getDouble("panels.surface-offset", 0.01),
                maxPoints = config.getInt("panels.wireframe.max-points", 900),
                flatOffset = config.getInt("panels.flat-offset", 1),
                followRadius = config.getDouble("panels.follow.radius", 32.0),
                followOffset = config.getDouble("panels.wireframe.follow-offset", 0.0),
            ),
        )

        panels = PanelRenderer(
            plugin = this,
            styles = styles,
            wireframes = wireframes,
            regions = regions,
            displays = displays,
            settings = PanelRenderer.Settings(
                defaultStyle = PanelStyle(
                    primitive = runCatching {
                        PanelPrimitive.valueOf(
                            (config.getString("panels.primitive") ?: "TEXT").uppercase()
                        )
                    }.getOrElse {
                        logger.warning("Unknown panels.primitive, falling back to TEXT")
                        PanelPrimitive.TEXT
                    },
                    red = config.getInt("panels.colour.red", 85).coerceIn(0, 255),
                    green = config.getInt("panels.colour.green", 200).coerceIn(0, 255),
                    blue = config.getInt("panels.colour.blue", 255).coerceIn(0, 255),
                    alpha = config.getInt("panels.colour.alpha", 90).coerceIn(0, 255),
                ),
                thickness = config.getDouble("panels.thickness", 0.02).toFloat(),
                surfaceOffset = config.getDouble("panels.surface-offset", 0.01),
                flatOffset = config.getInt("panels.flat-offset", 1),
                viewRange = config.getDouble("panels.view-range", 4.0).toFloat(),
                cullingPadding = config.getDouble("panels.culling-padding", 2.0).toFloat(),
                maxPanels = config.getInt("panels.max-panels", 512),
                viewerOnly = config.getBoolean("panels.viewer-only", true),
                follow = PanelRenderer.Follow(
                    radius = config.getDouble("panels.follow.radius", 32.0),
                    offset = config.getDouble("panels.follow.offset", 3.0),
                    intervalTicks = config.getLong("panels.follow.interval-ticks", 5L)
                        .coerceAtLeast(1L),
                    interpolationTicks = config.getInt("panels.follow.interpolation-ticks", 9),
                ),
                // Measured with /parcel calibrate on 1.21.11 - see config.yml for why these are
                // exact eighths and quarters rather than round-ish numbers.
                textBaseWidth = config.getDouble("panels.text-base-width", 0.125).toFloat(),
                textBaseHeight = config.getDouble("panels.text-base-height", 0.25).toFloat(),
                textAnchorX = config.getDouble("panels.text-anchor-x", 0.0125).toFloat(),
                textAnchorY = config.getDouble("panels.text-anchor-y", 0.125).toFloat(),
            ),
        )
        server.pluginManager.registerEvents(panels, this)
        server.pluginManager.registerEvents(MarkedRegions, this)

        // Registered here rather than earlier because renders() needs the panel renderer, which
        // needs its config parsed first. Nothing between reads the service - the renderers all take
        // regions and selections directly - and consumers are told to wait for ParcelReadyEvent
        // regardless, which still fires last.
        val api = ParcelAPIImpl(regions, selections, RenderManagerImpl(panels, regions))
        server.servicesManager.register(ParcelAPI::class.java, api, this, ServicePriority.Normal)

        val styleDialog = PanelStyleDialog(this, styles, panels) { panels.settingsDefaultStyle() }

        val browser = RegionBrowser(this, regions, panels, styleDialog)
        val marking = RegionMarking(panels, wand)
        server.pluginManager.registerEvents(browser, this)

        val commands = ParcelCommandManager(this)
        commands.register(
            RegionCommands(
                this, regions, selections, outlines, panels, styles, styleDialog, browser,
                marking,
            ),
            MarqueeCommands(selections, wand),
        )

        // Off in production. Nothing is registered when disabled, so the calibration commands do
        // not appear in help or tab completion and its listener is never hooked up - the tools are
        // inert even if someone still has one in a chest.
        if (config.getBoolean("dev-tools", false)) {
            calibration = PanelCalibration(this, displays)
            server.pluginManager.registerEvents(calibration, this)
            commands.register(DevCommands(calibration))
            logger.info("Dev tools enabled: /parcel calibrate is available")
        }

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
                if (config.getBoolean("outline.enabled", true)) outlines.start()
                wireframes.start()
                panels.start()
                server.pluginManager.callEvent(ParcelReadyEvent(api))
            })
        }
    }

    override fun onDisable() {
        if (::tracker.isInitialized) {
            tracker.stop()
        }
        if (::outlines.isInitialized) {
            outlines.stop()
        }
        if (::wireframes.isInitialized) {
            wireframes.stop()
        }
        if (::panels.isInitialized) {
            panels.stopFollowing()
        }
        if (::panels.isInitialized) {
            // Non-persistent displays would not survive a restart anyway, but leaving them for a
            // reload to orphan is untidy.
            panels.hideAll()
        }
        if (::calibration.isInitialized) {
            calibration.stopAll()
        }
        if (::regions.isInitialized) {
            // Block briefly on shutdown - losing an unsaved edit is worse than a slow stop.
            runCatching { regions.saveAll().get(10, TimeUnit.SECONDS) }
                .onFailure { logger.log(Level.SEVERE, "Failed to save regions on shutdown", it) }
        }
        server.servicesManager.unregisterAll(this)
    }
}
