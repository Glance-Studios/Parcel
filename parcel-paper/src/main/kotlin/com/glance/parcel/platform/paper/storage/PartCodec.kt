package com.glance.parcel.platform.paper.storage

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.api.shape.Prism
import com.glance.parcel.api.shape.Shape

/**
 * Reads and writes a [Part] as a plain map.
 *
 * Extracted so regions and their history share one definition of what a stored part looks like -
 * two copies would drift the moment a shape type is added, and history written by one and read by
 * the other would silently lose parts.
 */
internal class PartCodec {

    fun write(part: Part): Map<String, Any> {
        val bounds = part.shape().bounds()
        return mapOf(
            "op" to part.op().name,
            "type" to part.shape().typeId(),
            "min" to listOf(bounds.min().x(), bounds.min().y(), bounds.min().z()),
            "max" to listOf(bounds.max().x(), bounds.max().y(), bounds.max().z()),
        )
    }

    fun read(raw: Map<String, Any?>): Part? {
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
