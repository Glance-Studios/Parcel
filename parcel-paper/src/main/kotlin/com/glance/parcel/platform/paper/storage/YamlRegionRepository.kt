package com.glance.parcel.platform.paper.storage

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.api.shape.Prism
import com.glance.parcel.api.shape.Shape
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
internal class YamlRegionRepository(private val root: File) : RegionRepository {

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
            yaml.set("parts", record.parts().map(::writePart))
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

        val parts = yaml.getMapList("parts").mapNotNull { raw ->
            @Suppress("UNCHECKED_CAST")
            readPart(raw as Map<String, Any?>)
        }
        return RegionRepository.Record(key, world, parts)
    }

    private fun writePart(part: Part): Map<String, Any> {
        val bounds = part.shape().bounds()
        return mapOf(
            "op" to part.op().name,
            "type" to part.shape().typeId(),
            "min" to listOf(bounds.min().x(), bounds.min().y(), bounds.min().z()),
            "max" to listOf(bounds.max().x(), bounds.max().y(), bounds.max().z()),
        )
    }

    private fun readPart(raw: Map<String, Any?>): Part? {
        val op = runCatching { Op.valueOf(raw["op"] as String) }.getOrNull() ?: return null
        val min = readPos(raw["min"]) ?: return null
        val max = readPos(raw["max"]) ?: return null
        val box = BlockBox.of(min, max)

        val shape: Shape = when (raw["type"] as? String) {
            Prism.TYPE_ID -> Prism(box)
            Cuboid.TYPE_ID -> Cuboid(box)
            else -> return null
        }
        return Part(shape, op)
    }

    private fun readPos(raw: Any?): BlockPos? {
        val values = (raw as? List<*>)?.mapNotNull { (it as? Number)?.toInt() } ?: return null
        if (values.size != 3) return null
        return BlockPos(values[0], values[1], values[2])
    }
}
