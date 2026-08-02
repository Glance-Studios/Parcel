package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad

/**
 * Grid lines across a meshed quad, in world space.
 *
 * Same surface the solid panels cover, drawn as a lattice instead - so a wireframe region reads as
 * the *meshed* shape rather than as its parts. Two flush boxes wireframe as one grid across both,
 * with no line down the seam, because the mesher already removed that face.
 *
 * Kept Bukkit-free so the face-to-world mapping is unit testable, exactly like [Panels].
 */
internal object Wireframe {

    /**
     * Emits points along a grid over [quad].
     *
     * @param spacing blocks between grid lines. The far edges are always drawn, so a face is
     *                always closed even when the spacing does not divide it evenly.
     * @param resolution blocks between particles along a line
     * @param lift distance to raise the grid off the surface, along the face normal
     */
    inline fun points(
        quad: Quad,
        spacing: Double,
        resolution: Double,
        lift: Double,
        emit: (Double, Double, Double) -> Unit,
    ) {
        val w = quad.width().toDouble()
        val h = quad.height().toDouble()
        val step = if (spacing <= 0.0) 1.0 else spacing
        val res = if (resolution <= 0.0) 0.5 else resolution

        // Lines running along the height axis, at intervals across the width.
        forEachStop(w, step) { u ->
            forEachStop(h, res) { v -> at(quad, u, v, lift, emit) }
        }
        // And the other way.
        forEachStop(h, step) { v ->
            forEachStop(w, res) { u -> at(quad, u, v, lift, emit) }
        }
    }

    /** Walks 0..[extent] inclusive, always landing exactly on the far edge. */
    inline fun forEachStop(extent: Double, step: Double, emit: (Double) -> Unit) {
        var value = 0.0
        while (value < extent) {
            emit(value)
            value += step
        }
        emit(extent)
    }

    /**
     * Maps a position within a quad's own (width, height) frame into world space.
     *
     * Which world axes those are depends on the face, and it is the same mapping the solid panels
     * use - X-facing quads run their width along Z, everything else along X.
     */
    inline fun at(
        quad: Quad,
        u: Double,
        v: Double,
        lift: Double,
        emit: (Double, Double, Double) -> Unit,
    ) {
        val o = quad.origin()
        val ox = o.x().toDouble()
        val oy = o.y().toDouble()
        val oz = o.z().toDouble()

        when (quad.face()) {
            Face.UP -> emit(ox + u, oy + 1.0 + lift, oz + v)
            Face.DOWN -> emit(ox + u, oy - lift, oz + v)
            Face.SOUTH -> emit(ox + u, oy + v, oz + 1.0 + lift)
            Face.NORTH -> emit(ox + u, oy + v, oz - lift)
            Face.EAST -> emit(ox + 1.0 + lift, oy + v, oz + u)
            Face.WEST -> emit(ox - lift, oy + v, oz + u)
        }
    }
}
