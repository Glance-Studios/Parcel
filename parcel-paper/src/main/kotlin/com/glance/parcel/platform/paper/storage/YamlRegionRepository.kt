package com.glance.parcel.platform.paper.storage

import com.glance.parcel.api.storage.RegionRepository
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * One YAML file per region, under `regions/<namespace>/<key>.yml`.
 *
 * Regions are config-sized data, so this stays hand-editable and diffable. Anything needing shared
 * storage across a network implements [RegionRepository] instead - nothing else in Parcel knows
 * where regions live.
 */
internal class YamlRegionRepository(
    private val root: File,
    private val parts: PartCodec,
) : RegionRepository {

    override fun loadAll(): CompletableFuture<Collection<RegionRepository.Record>> =
        CompletableFuture.supplyAsync {
            if (!root.isDirectory) return@supplyAsync emptyList()

            val records = ArrayList<RegionRepository.Record>()
            root.listFiles { file -> file.isDirectory }?.forEach { namespaceDir ->
                namespaceDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                    runCatching { read(namespaceDir.name, file) }
                        .onSuccess { record -> record?.let(records::add) }
                        .onFailure { error ->
                            throw IllegalStateException("Failed to read region file $file", error)
                        }
                }
            }
            records
        }

    override fun save(record: RegionRepository.Record): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val file = fileFor(record.key())
            file.parentFile.mkdirs()

            val yaml = YamlConfiguration()
            yaml.set("world", record.world())
            yaml.set("parts", record.parts().map(parts::write))
            yaml.save(file)
        }

    override fun delete(key: NamespacedKey): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            fileFor(key).delete()
        }

    private fun fileFor(key: NamespacedKey) =
        File(File(root, key.namespace), "${key.key}.yml")

    private fun read(namespace: String, file: File): RegionRepository.Record? {
        val yaml = YamlConfiguration.loadConfiguration(file)
        val world = yaml.getString("world") ?: return null
        val key = NamespacedKey(namespace, file.nameWithoutExtension)

        // Named to avoid shadowing the codec this class was constructed with.
        val stored = yaml.getMapList("parts").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            parts.read(raw as Map<String, Any?>)
        }
        return RegionRepository.Record(key, world, stored)
    }

}
