package com.glance.parcel.platform.paper.storage

import com.glance.parcel.api.region.Part
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Previous shapes for each region, on disk.
 *
 * Kept out of the region files for the same reason styles are: a region's own YAML stays purely
 * current-state, so a corrupt or deleted history can never damage the region it describes.
 *
 * Persisted rather than held in memory because the failure it guards against is not "I noticed
 * immediately" - it is someone walking into a region on Thursday that was broken on Tuesday.
 */
internal class HistoryStore(
    private val root: File,
    private val parts: PartCodec,
) {

    fun load(key: NamespacedKey): List<List<Part>> {
        val file = fileFor(key)
        if (!file.isFile) return emptyList()

        val yaml = YamlConfiguration.loadConfiguration(file)
        return yaml.getKeys(false)
            .sortedByDescending { it.toIntOrNull() ?: 0 }
            .mapNotNull { index ->
                @Suppress("UNCHECKED_CAST")
                val raw = yaml.getMapList("$index.parts") as List<Map<String, Any?>>
                raw.mapNotNull(parts::read).takeIf { it.isNotEmpty() || raw.isEmpty() }
            }
    }

    fun save(key: NamespacedKey, entries: List<List<Part>>) {
        val file = fileFor(key)
        if (entries.isEmpty()) {
            file.delete()
            return
        }

        val yaml = YamlConfiguration()
        entries.forEachIndexed { index, snapshot ->
            yaml.set("${entries.size - index}.parts", snapshot.map(parts::write))
        }
        file.parentFile?.mkdirs()
        yaml.save(file)
    }

    fun delete(key: NamespacedKey) {
        fileFor(key).delete()
    }

    private fun fileFor(key: NamespacedKey) =
        File(File(root, key.namespace), "${key.key}.yml")
}
