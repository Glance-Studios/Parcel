package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PanelPlacementTest {

    private val thickness = 0.02f

    private fun place(face: Face, x: Int, y: Int, z: Int, w: Int, h: Int) =
        Panels.placementFor(Quad(face, BlockPos(x, y, z), w, h), thickness)

    /** Where the slab actually sits in world space, as (min, max) on each axis. */
    private fun span(p: PanelPlacement) = Triple(
        (p.x + p.tx) to (p.x + p.tx + p.sx),
        (p.y + p.ty) to (p.y + p.ty + p.sy),
        (p.z + p.tz) to (p.z + p.tz + p.sz),
    )

    @Test
    @DisplayName("an UP face sits on top of its block, not inside it")
    fun upFaceOnTop() {
        val (xs, ys, zs) = span(place(Face.UP, 0, 5, 0, 4, 3))

        assertEquals(0.0, xs.first, 1e-6)
        assertEquals(4.0, xs.second, 1e-6, "width runs along X")
        assertEquals(0.0, zs.first, 1e-6)
        assertEquals(3.0, zs.second, 1e-6, "height runs along Z")
        // Block y=5 occupies 5..6, so its top surface is y=6.
        assertEquals(6.0, (ys.first + ys.second) / 2, 1e-6)
    }

    @Test
    @DisplayName("a DOWN face sits under its block")
    fun downFaceUnderneath() {
        val (_, ys, _) = span(place(Face.DOWN, 0, 5, 0, 4, 3))
        assertEquals(5.0, (ys.first + ys.second) / 2, 1e-6)
    }

    @Test
    @DisplayName("north and south sit on opposite sides of the same block")
    fun zFacesStraddle() {
        val north = span(place(Face.NORTH, 0, 0, 7, 2, 2)).third
        val south = span(place(Face.SOUTH, 0, 0, 7, 2, 2)).third

        assertEquals(7.0, (north.first + north.second) / 2, 1e-6)
        assertEquals(8.0, (south.first + south.second) / 2, 1e-6)
    }

    @Test
    @DisplayName("west and east sit on opposite sides of the same block")
    fun xFacesStraddle() {
        val west = span(place(Face.WEST, 3, 0, 0, 2, 2)).first
        val east = span(place(Face.EAST, 3, 0, 0, 2, 2)).first

        assertEquals(3.0, (west.first + west.second) / 2, 1e-6)
        assertEquals(4.0, (east.first + east.second) / 2, 1e-6)
    }

    @Test
    @DisplayName("X-facing quads map width to Z and height to Y")
    fun eastAxisMapping() {
        val p = place(Face.EAST, 0, 0, 0, 5, 9)

        assertEquals(thickness, p.sx, 1e-6f, "thin along the normal")
        assertEquals(9f, p.sy, 1e-6f, "height is Y")
        assertEquals(5f, p.sz, 1e-6f, "width is Z")
    }

    @Test
    @DisplayName("Z-facing quads map width to X and height to Y")
    fun southAxisMapping() {
        val p = place(Face.SOUTH, 0, 0, 0, 5, 9)

        assertEquals(5f, p.sx, 1e-6f)
        assertEquals(9f, p.sy, 1e-6f)
        assertEquals(thickness, p.sz, 1e-6f)
    }

    @Test
    @DisplayName("every face is thin on exactly one axis")
    fun alwaysThinOnOneAxis() {
        Face.entries.forEach { face ->
            val p = place(face, 1, 2, 3, 4, 6)
            val thin = listOf(p.sx, p.sy, p.sz).count { it == thickness }
            assertEquals(1, thin, "$face should be thin on exactly one axis")
        }
    }

    @Test
    @DisplayName("the surface offset moves a panel only along its normal")
    fun surfaceOffsetIsNormalOnly() {
        // The whole safety of the buffer rests on this: if it leaked into an in-plane axis it would
        // silently shift alignment and invalidate the calibrated constants.
        val offset = 0.25
        Face.entries.forEach { face ->
            val flat = span(place(face, 2, 3, 4, 5, 7))
            val lifted = span(Panels.placementFor(Quad(face, BlockPos(2, 3, 4), 5, 7), thickness, offset))

            val moved = listOf(
                flat.first.first != lifted.first.first,
                flat.second.first != lifted.second.first,
                flat.third.first != lifted.third.first,
            ).count { it }
            assertEquals(1, moved, "$face should shift on exactly one axis")
        }
    }

    @Test
    @DisplayName("the surface offset pushes outward, not inward")
    fun surfaceOffsetPushesOutward() {
        val offset = 0.25
        val up = span(Panels.placementFor(Quad(Face.UP, BlockPos(0, 5, 0), 2, 2), thickness, offset))
        val down = span(Panels.placementFor(Quad(Face.DOWN, BlockPos(0, 5, 0), 2, 2), thickness, offset))

        assertEquals(6.0 + offset, (up.second.first + up.second.second) / 2, 1e-6)
        assertEquals(5.0 - offset, (down.second.first + down.second.second) / 2, 1e-6)
    }

    @Test
    @DisplayName("the culling box covers the panel's real extent")
    fun cullingBoxCoversPanel() {
        // The default box is the entity point, which is what makes big panels vanish. Whatever we
        // feed the client has to be at least as large as what is drawn.
        Face.entries.forEach { face ->
            val p = place(face, 0, 0, 0, 12, 30)
            assertTrue(p.widthExtent >= 12f - thickness, "$face width extent too small")
            assertTrue(p.heightExtent > 0f, "$face height extent must be positive")
        }
    }
}
