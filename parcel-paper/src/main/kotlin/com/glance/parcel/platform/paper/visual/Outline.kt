package com.glance.parcel.platform.paper.visual

import com.glance.parcel.api.math.BlockBox

/**
 * Points along the twelve edges of a box, in world space.
 *
 * Kept pure and Bukkit-free so it can be unit tested - getting the block-to-continuous-space
 * conversion wrong draws an outline one block short on two sides, which is the sort of thing that
 * looks "roughly right" in game and is never noticed.
 */
internal object Outline {

    /**
     * Emits points along every edge of [box].
     *
     * A block box is inclusive, so block `minX..maxX` occupies continuous space `[minX, maxX + 1]`.
     * The outline is drawn around that full footprint, not around the block centres.
     *
     * Long edges are walked at [spacing] and always include both endpoints, so corners are crisp
     * even when the spacing does not divide the length.
     */
    inline fun edges(box: BlockBox, spacing: Double, emit: (Double, Double, Double) -> Unit) {
        val x0 = box.min().x().toDouble()
        val y0 = box.min().y().toDouble()
        val z0 = box.min().z().toDouble()
        val x1 = box.max().x() + 1.0
        val y1 = box.max().y() + 1.0
        val z1 = box.max().z() + 1.0

        val step = if (spacing <= 0.0) 1.0 else spacing

        // Edges running along X, at each of the four (y, z) corners.
        for (y in doubleArrayOf(y0, y1)) {
            for (z in doubleArrayOf(z0, z1)) {
                walk(x0, x1, step) { x -> emit(x, y, z) }
            }
        }
        // Edges running along Y.
        for (x in doubleArrayOf(x0, x1)) {
            for (z in doubleArrayOf(z0, z1)) {
                walk(y0, y1, step) { y -> emit(x, y, z) }
            }
        }
        // Edges running along Z.
        for (x in doubleArrayOf(x0, x1)) {
            for (y in doubleArrayOf(y0, y1)) {
                walk(z0, z1, step) { z -> emit(x, y, z) }
            }
        }
    }

    /** Walks [from]..[to] inclusive, guaranteeing the final endpoint lands exactly on [to]. */
    inline fun walk(from: Double, to: Double, step: Double, emit: (Double) -> Unit) {
        var value = from
        while (value < to) {
            emit(value)
            value += step
        }
        emit(to)
    }
}
