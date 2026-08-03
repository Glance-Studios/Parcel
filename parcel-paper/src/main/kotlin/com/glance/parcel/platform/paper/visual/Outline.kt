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

    /**
     * Emits points around the footprint of [box] at a single height [y].
     *
     * The cross-section counterpart to [edges], and what a prism gets drawn as. A prism spans the
     * world's full height, so its twelve edges include four 384-block verticals that shoot off into
     * the sky - correct, useless to look at, and they bury whatever you were trying to see. Only
     * the footprint carries information, and drawing it at the viewer's own height puts it where
     * they are actually looking.
     *
     * Corners are emitted once each, not twice, so the budget is not spent drawing the same four
     * points over and over.
     */
    inline fun perimeter(box: BlockBox, y: Double, spacing: Double, emit: (Double, Double, Double) -> Unit) {
        val x0 = box.min().x().toDouble()
        val z0 = box.min().z().toDouble()
        val x1 = box.max().x() + 1.0
        val z1 = box.max().z() + 1.0

        val step = if (spacing <= 0.0) 1.0 else spacing

        // Two full edges along X including both corners, then the Z edges excluding the corners the
        // X pass already covered.
        walk(x0, x1, step) { x -> emit(x, y, z0) }
        walk(x0, x1, step) { x -> emit(x, y, z1) }
        walkInterior(z0, z1, step) { z -> emit(x0, y, z) }
        walkInterior(z0, z1, step) { z -> emit(x1, y, z) }
    }

    /** Walks [from]..[to] exclusive of both endpoints. */
    inline fun walkInterior(from: Double, to: Double, step: Double, emit: (Double) -> Unit) {
        var value = from + step
        while (value < to) {
            emit(value)
            value += step
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
