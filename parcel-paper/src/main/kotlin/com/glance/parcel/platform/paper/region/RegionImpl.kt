package com.glance.parcel.platform.paper.region

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Quad
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionEditor
import com.glance.parcel.api.shape.Shape
import com.glance.parcel.platform.paper.mesh.GreedyMesher
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import java.util.Optional

private val EMPTY_BOX = BlockBox(BlockPos(0, 0, 0), BlockPos(0, 0, 0))

/**
 * A region as an ordered list of parts.
 *
 * Bounds and mesh are both derived and cached; any edit drops both. Nothing is ever baked into a
 * block set, which is what makes [RegionEditor.undo] exact rather than a snapshot restore.
 */
internal class RegionImpl(
    private val key: NamespacedKey,
    private val world: World,
    parts: List<Part> = emptyList(),
    private val onChanged: (RegionImpl) -> Unit = {},
) : Region {

    private val partList = ArrayList(parts)

    private var cachedBounds: BlockBox? = null
    private var cachedMesh: List<Quad>? = null
    private var boundsComputed = false

    override fun key(): NamespacedKey = key

    override fun world(): World = world

    override fun parts(): List<Part> = java.util.List.copyOf(partList)

    override fun bounds(): BlockBox = additiveBounds() ?: EMPTY_BOX

    override fun isEmpty(): Boolean = partList.none { it.op() == Op.ADD }

    override fun contains(x: Int, y: Int, z: Int): Boolean {
        val bounds = additiveBounds() ?: return false
        if (!bounds.contains(x, y, z)) return false

        // Ordered fold, last writer wins. A subtract after an add carves; an add after that fills.
        var inside = false
        for (part in partList) {
            if (part.shape().contains(x, y, z)) {
                inside = part.op() == Op.ADD
            }
        }
        return inside
    }

    override fun contains(location: Location): Boolean {
        if (location.world != world) return false
        return contains(location.blockX, location.blockY, location.blockZ)
    }

    override fun mesh(): List<Quad> {
        cachedMesh?.let { return it }
        val bounds = additiveBounds() ?: return emptyList<Quad>().also { cachedMesh = it }
        val mesh = GreedyMesher.mesh(bounds) { x, y, z -> contains(x, y, z) }
        val immutable = java.util.List.copyOf(mesh)
        cachedMesh = immutable
        return immutable
    }

    override fun edit(): RegionEditor = EditorImpl()

    /**
     * Union of the ADD parts only - a subtraction can never make a region larger, so including
     * them would inflate the bounds and slow every containment test.
     */
    private fun additiveBounds(): BlockBox? {
        if (!boundsComputed) {
            cachedBounds = partList
                .filter { it.op() == Op.ADD }
                .map { it.shape().bounds() }
                .reduceOrNull { acc, box -> acc.union(box) }
            boundsComputed = true
        }
        return cachedBounds
    }

    private fun invalidate() {
        cachedBounds = null
        boundsComputed = false
        cachedMesh = null
    }

    internal fun replaceParts(parts: List<Part>) {
        partList.clear()
        partList.addAll(parts)
        invalidate()
    }

    private inner class EditorImpl : RegionEditor {

        private val pending = ArrayList(partList)

        override fun add(shape: Shape): RegionEditor = addPart(Part.add(shape))

        override fun subtract(shape: Shape): RegionEditor = addPart(Part.subtract(shape))

        override fun addPart(part: Part): RegionEditor {
            pending += part
            return this
        }

        override fun removePart(index: Int): RegionEditor {
            pending.removeAt(index)
            return this
        }

        override fun undo(): Optional<Part> =
            if (pending.isEmpty()) Optional.empty()
            else Optional.of(pending.removeAt(pending.size - 1))

        override fun clear(): RegionEditor {
            pending.clear()
            return this
        }

        override fun commit(): Region {
            replaceParts(pending)
            onChanged(this@RegionImpl)
            return this@RegionImpl
        }
    }
}
