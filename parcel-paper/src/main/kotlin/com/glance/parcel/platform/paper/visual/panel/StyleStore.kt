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
internal class StyleStore(private val file: File) {

    private val styles = ConcurrentHashMap<NamespacedKey, PanelStyle>()

    fun load() {
        styles.clear()
        if (!file.isFile) return

        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getKeys(false).forEach { raw ->
            val key = NamespacedKey.fromString(raw) ?: return@forEach
            val section = yaml.getConfigurationSection(raw) ?: return@forEach
            val primitive = runCatching {
                PanelPrimitive.valueOf(section.getString("primitive", "TEXT")!!.uppercase())
            }.getOrElse { PanelPrimitive.TEXT }

            styles[key] = PanelStyle(
                primitive = primitive,
                red = section.getInt("red", 85).coerceIn(0, 255),
                green = section.getInt("green", 200).coerceIn(0, 255),
                blue = section.getInt("blue", 255).coerceIn(0, 255),
                alpha = section.getInt("alpha", 90).coerceIn(0, 255),
            )
        }
    }

    fun get(key: NamespacedKey): PanelStyle? = styles[key]

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
        styles.forEach { (key, style) ->
            yaml.set("$key.primitive", style.primitive.name)
            yaml.set("$key.red", style.red)
            yaml.set("$key.green", style.green)
            yaml.set("$key.blue", style.blue)
            yaml.set("$key.alpha", style.alpha)
        }
        file.parentFile?.mkdirs()
        yaml.save(file)
    }
}
