package com.glance.parcel.platform.paper.selection

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.api.selection.Selection
import com.glance.parcel.api.selection.SelectionMode
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.api.shape.Prism
import com.glance.parcel.api.shape.Shape
import org.bukkit.NamespacedKey
import org.bukkit.World

/**
 * A player's working selection: the parts they have committed, plus the two corners of the shape
 * they are currently marking.
 *
 * The mode is a property of the next *commit*, not of the selection as a whole - so one selection
 * can mix a full-height footprint with a bounded carve-out.
 */
internal class SelectionImpl(
    private val world: World,
    private val regions: RegionManager,
) : Selection {

    private val committed = ArrayList<Part>()

    private var cornerA: BlockPos? = null
    private var cornerB: BlockPos? = null
    private var selectionMode = SelectionMode.VOLUME

    override fun world(): World = world

    override fun parts(): List<Part> = java.util.List.copyOf(committed)

    override fun pendingA(): BlockPos? = cornerA

    override fun pendingB(): BlockPos? = cornerB

    override fun mode(): SelectionMode = selectionMode

    override fun isEmpty(): Boolean = committed.isEmpty()

    override fun bounds(): BlockBox? = committed
        .map { it.shape().bounds() }
        .reduceOrNull { acc, box -> acc.union(box) }

    override fun toRegion(key: NamespacedKey): Region {
        check(committed.isNotEmpty()) { "Cannot create a region from an empty selection" }
        val region = regions.create(key, world)
        val editor = region.edit()
        committed.forEach(editor::addPart)
        return editor.commit()
    }

    override fun applyTo(region: Region): Region {
        check(committed.isNotEmpty()) { "Cannot apply an empty selection to a region" }
        require(region.world() == world) {
            "Selection is in ${world.name} but ${region.key()} is in ${region.world().name}"
        }

        val editor = region.edit().clear()
        committed.forEach(editor::addPart)
        return editor.commit()
    }

    fun setCornerA(pos: BlockPos) {
        cornerA = pos
    }

    fun setCornerB(pos: BlockPos) {
        cornerB = pos
    }

    fun setMode(mode: SelectionMode) {
        selectionMode = mode
    }

    fun cycleMode(): SelectionMode {
        selectionMode = when (selectionMode) {
            SelectionMode.VOLUME -> SelectionMode.FLAT
            SelectionMode.FLAT -> SelectionMode.VOLUME
        }
        return selectionMode
    }

    /**
     * @return the shape the pending corners describe, or null if both corners are not yet marked
     */
    fun pendingShape(): Shape? {
        val a = cornerA ?: return null
        val b = cornerB ?: return null
        return when (selectionMode) {
            SelectionMode.VOLUME -> Cuboid.of(a, b)
            SelectionMode.FLAT -> Prism.of(world, a, b)
        }
    }

    /**
     * Commits the pending shape as a part and clears the corners.
     *
     * @return the committed part, or null if there was no complete pending shape
     */
    fun commitPending(op: com.glance.parcel.api.region.Op): Part? {
        val shape = pendingShape() ?: return null
        val part = Part(shape, op)
        committed += part
        cornerA = null
        cornerB = null
        return part
    }

    fun undo(): Part? =
        if (committed.isEmpty()) null else committed.removeAt(committed.size - 1)

    fun clearParts() {
        committed.clear()
        cornerA = null
        cornerB = null
    }

    fun loadFrom(region: Region) {
        committed.clear()
        committed.addAll(region.parts())
        cornerA = null
        cornerB = null
    }
}
