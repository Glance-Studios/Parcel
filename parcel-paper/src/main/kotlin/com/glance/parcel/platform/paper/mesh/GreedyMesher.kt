package com.glance.parcel.platform.paper.mesh

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad

/**
 * Turns a solid/empty predicate into the merged rectangles of its exposed surface.
 *
 * This is the standard voxel greedy mesher, and it is *exact* here rather than an approximation:
 * Parcel regions are block-aligned by construction, so there is no sub-block geometry to lose.
 *
 * Two consequences worth knowing:
 *  - Interior faces never appear. Where two parts touch, neither draws a face into the other.
 *  - Subtraction needs no special handling. A carved hole is simply blocks the predicate rejects,
 *    and the walls of the hole fall out as ordinary exposed faces.
 *
 * Cost is O(volume). This is an edit-time operation - never call it per tick.
 */
object GreedyMesher {

    /** Above this many blocks, meshing is refused rather than silently stalling the server. */
    const val DEFAULT_MAX_VOLUME: Long = 8_000_000L

    /**
     * @param bounds the box to sweep, which must contain every solid block
     * @param solid whether a given block is inside the volume; must return false outside [bounds]
     */
    @JvmStatic
    @JvmOverloads
    fun mesh(
        bounds: BlockBox,
        maxVolume: Long = DEFAULT_MAX_VOLUME,
        solid: (Int, Int, Int) -> Boolean,
    ): List<Quad> {
        val volume = bounds.volume()
        require(volume <= maxVolume) {
            "Region is too large to mesh: $volume blocks exceeds the $maxVolume limit"
        }

        val quads = ArrayList<Quad>()
        meshY(bounds, solid, quads)
        meshZ(bounds, solid, quads)
        meshX(bounds, solid, quads)
        return quads
    }

    /** Boundaries between y and y+1. In plane: X is width, Z is height. */
    private fun meshY(b: BlockBox, solid: (Int, Int, Int) -> Boolean, out: MutableList<Quad>) {
        val minX = b.min().x()
        val minZ = b.min().z()
        val su = b.sizeX()
        val sv = b.sizeZ()
        val up = BooleanArray(su * sv)
        val down = BooleanArray(su * sv)

        for (y in b.min().y() - 1..b.max().y()) {
            var anyUp = false
            var anyDown = false
            for (v in 0 until sv) {
                for (u in 0 until su) {
                    val below = solid(minX + u, y, minZ + v)
                    val above = solid(minX + u, y + 1, minZ + v)
                    val i = v * su + u
                    up[i] = below && !above
                    down[i] = !below && above
                    if (up[i]) anyUp = true
                    if (down[i]) anyDown = true
                }
            }
            if (anyUp) greedy(up, su, sv) { u, v, w, h ->
                out += Quad(Face.UP, BlockPos(minX + u, y, minZ + v), w, h)
            }
            if (anyDown) greedy(down, su, sv) { u, v, w, h ->
                out += Quad(Face.DOWN, BlockPos(minX + u, y + 1, minZ + v), w, h)
            }
        }
    }

    /** Boundaries between z and z+1. In plane: X is width, Y is height. */
    private fun meshZ(b: BlockBox, solid: (Int, Int, Int) -> Boolean, out: MutableList<Quad>) {
        val minX = b.min().x()
        val minY = b.min().y()
        val su = b.sizeX()
        val sv = b.sizeY()
        val south = BooleanArray(su * sv)
        val north = BooleanArray(su * sv)

        for (z in b.min().z() - 1..b.max().z()) {
            var anySouth = false
            var anyNorth = false
            for (v in 0 until sv) {
                for (u in 0 until su) {
                    val near = solid(minX + u, minY + v, z)
                    val far = solid(minX + u, minY + v, z + 1)
                    val i = v * su + u
                    south[i] = near && !far
                    north[i] = !near && far
                    if (south[i]) anySouth = true
                    if (north[i]) anyNorth = true
                }
            }
            if (anySouth) greedy(south, su, sv) { u, v, w, h ->
                out += Quad(Face.SOUTH, BlockPos(minX + u, minY + v, z), w, h)
            }
            if (anyNorth) greedy(north, su, sv) { u, v, w, h ->
                out += Quad(Face.NORTH, BlockPos(minX + u, minY + v, z + 1), w, h)
            }
        }
    }

    /** Boundaries between x and x+1. In plane: Z is width, Y is height. */
    private fun meshX(b: BlockBox, solid: (Int, Int, Int) -> Boolean, out: MutableList<Quad>) {
        val minY = b.min().y()
        val minZ = b.min().z()
        val su = b.sizeZ()
        val sv = b.sizeY()
        val east = BooleanArray(su * sv)
        val west = BooleanArray(su * sv)

        for (x in b.min().x() - 1..b.max().x()) {
            var anyEast = false
            var anyWest = false
            for (v in 0 until sv) {
                for (u in 0 until su) {
                    val near = solid(x, minY + v, minZ + u)
                    val far = solid(x + 1, minY + v, minZ + u)
                    val i = v * su + u
                    east[i] = near && !far
                    west[i] = !near && far
                    if (east[i]) anyEast = true
                    if (west[i]) anyWest = true
                }
            }
            if (anyEast) greedy(east, su, sv) { u, v, w, h ->
                out += Quad(Face.EAST, BlockPos(x, minY + v, minZ + u), w, h)
            }
            if (anyWest) greedy(west, su, sv) { u, v, w, h ->
                out += Quad(Face.WEST, BlockPos(x + 1, minY + v, minZ + u), w, h)
            }
        }
    }

    /**
     * Merges a boolean mask into the largest rectangles that fit, consuming the mask as it goes.
     *
     * Grow along u first, then along v while every cell of the current width is still set.
     */
    private inline fun greedy(
        mask: BooleanArray,
        su: Int,
        sv: Int,
        emit: (u: Int, v: Int, w: Int, h: Int) -> Unit,
    ) {
        for (v in 0 until sv) {
            var u = 0
            while (u < su) {
                if (!mask[v * su + u]) {
                    u++
                    continue
                }

                var w = 1
                while (u + w < su && mask[v * su + u + w]) w++

                var h = 1
                grow@ while (v + h < sv) {
                    for (k in 0 until w) {
                        if (!mask[(v + h) * su + u + k]) break@grow
                    }
                    h++
                }

                for (dv in 0 until h) {
                    for (du in 0 until w) {
                        mask[(v + dv) * su + u + du] = false
                    }
                }

                emit(u, v, w, h)
                u += w
            }
        }
    }
}
