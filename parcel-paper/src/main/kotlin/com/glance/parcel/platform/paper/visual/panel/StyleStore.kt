package com.glance.parcel.platform.paper.visual.panel

import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-region style defaults, stored **outside** the region files.
 *
 * A region's own YAML stays purely geometric. Style is a view concern, so keeping it in its own file
 * means the shared source of truth for shape is never contaminated by presentation, and deleting or
 * corrupting a style can never damage a region.
 */
internal class StyleStore(
    private val file: File,
    /** For entries written before `follow` existed - otherwise they would ignore the config. */
    private val defaultFollow: Boolean = false,
) {

    private val styles = ConcurrentHashMap<NamespacedKey, PanelStyle>()

    /**
     * The style every region without one of its own uses.
     *
     * Null until somebody edits it, at which point the config's values stop being the answer. Kept
     * here rather than in config because it is now something you change in game with a dialog, and
     * a value that lives in two places drifts.
     */
    @Volatile
    private var default: PanelStyle? = null

    fun load() {
        styles.clear()
        // Cleared rather than left alone: a reload after the section was deleted from the file must
        // fall back to the config, not keep serving a default that no longer exists on disk.
        default = null
        if (!file.isFile) return

        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getConfigurationSection(DEFAULT_KEY)?.let { default = read(it) }

        yaml.getKeys(false).forEach { raw ->
            // The default is not a region, so it must not be parsed as one.
            if (raw == DEFAULT_KEY) return@forEach
            val key = NamespacedKey.fromString(raw) ?: return@forEach
            val section = yaml.getConfigurationSection(raw) ?: return@forEach
            styles[key] = read(section)
        }
    }

    private fun read(section: org.bukkit.configuration.ConfigurationSection) = PanelStyle(
        primitive = runCatching {
            PanelPrimitive.valueOf(section.getString("primitive", "TEXT")!!.uppercase())
        }.getOrElse { PanelPrimitive.TEXT },
        red = section.getInt("red", 85).coerceIn(0, 255),
        green = section.getInt("green", 200).coerceIn(0, 255),
        blue = section.getInt("blue", 255).coerceIn(0, 255),
        alpha = section.getInt("alpha", 90).coerceIn(0, 255),
        gridSpacing = section.getDouble("grid-spacing", 1.0).toFloat().coerceIn(0.25f, 8f),
        particleSize = section.getDouble("particle-size", 0.6).toFloat(),
        follow = section.getBoolean("follow", defaultFollow),
        heightOffset = section.getInt("height-offset", 0).coerceIn(-64, 64),
    )

    fun get(key: NamespacedKey): PanelStyle? = styles[key]

    /** The saved default, or null if it has never been edited. */
    fun default(): PanelStyle? = default

    fun setDefault(style: PanelStyle) {
        default = style
        save()
    }

    fun set(key: NamespacedKey, style: PanelStyle) {
        styles[key] = style
        save()
    }

    fun clear(key: NamespacedKey): Boolean {
        val removed = styles.remove(key) != null
        if (removed) save()
        return removed
    }

    private fun save() {
        val yaml = YamlConfiguration()
        default?.let { write(yaml, DEFAULT_KEY, it) }
        styles.forEach { (key, style) -> write(yaml, key.toString(), style) }
        file.parentFile?.mkdirs()
        yaml.save(file)
    }

    private fun write(yaml: YamlConfiguration, path: String, style: PanelStyle) {
        yaml.set("$path.primitive", style.primitive.name)
        yaml.set("$path.red", style.red)
        yaml.set("$path.green", style.green)
        yaml.set("$path.blue", style.blue)
        yaml.set("$path.alpha", style.alpha)
        yaml.set("$path.grid-spacing", style.gridSpacing)
        yaml.set("$path.particle-size", style.particleSize)
        yaml.set("$path.follow", style.follow)
        yaml.set("$path.height-offset", style.heightOffset)
    }

    private companion object {
        /**
         * Reserved top-level key for the default style.
         *
         * Not a valid NamespacedKey - no colon - so it can never collide with a region, and an old
         * file without it simply has no saved default.
         */
        const val DEFAULT_KEY = "default"
    }
}
