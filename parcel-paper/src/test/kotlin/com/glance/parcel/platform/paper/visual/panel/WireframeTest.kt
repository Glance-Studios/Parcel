package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WireframeTest {

    private fun upQuad(width: Int, height: Int) =
        Quad(Face.UP, BlockPos(0, 64, 0), width, height)

    private fun outline(quad: Quad, resolution: Double): List<Triple<Double, Double, Double>> {
        val out = mutableListOf<Triple<Double, Double, Double>>()
        Wireframe.outline(quad, resolution, lift = 0.0) { x, y, z -> out += Triple(x, y, z) }
        return out
    }

    private fun grid(quad: Quad, spacing: Double, resolution: Double): List<Triple<Double, Double, Double>> {
        val out = mutableListOf<Triple<Double, Double, Double>>()
        Wireframe.points(quad, spacing, resolution, lift = 0.0) { x, y, z -> out += Triple(x, y, z) }
        return out
    }

    @Test
    @DisplayName("the outline stays on the quad border and leaves the interior empty")
    fun bordersOnly() {
        val all = outline(upQuad(4, 4), resolution = 1.0)

        // An UP quad runs width along X and height along Z, from its origin.
        assertTrue(
            all.all { (x, _, z) -> x == 0.0 || x == 4.0 || z == 0.0 || z == 4.0 },
            "no point should fall inside the rectangle",
        )
        assertFalse(all.any { (x, _, z) -> x == 2.0 && z == 2.0 }, "the middle must be empty")
    }

    @Test
    @DisplayName("all four corners are drawn, once each")
    fun cornersDrawnOnce() {
        val all = outline(upQuad(4, 4), resolution = 1.0)
        val set = all.toSet()

        assertEquals(all.size, set.size, "a duplicated corner is budget spent on nothing")
        // UP places the sheet on top of the block, hence 65 rather than 64.
        assertTrue(Triple(0.0, 65.0, 0.0) in set)
        assertTrue(Triple(4.0, 65.0, 0.0) in set)
        assertTrue(Triple(0.0, 65.0, 4.0) in set)
        assertTrue(Triple(4.0, 65.0, 4.0) in set)
    }

    @Test
    @DisplayName("the far edges are reached even when the resolution does not divide them")
    fun farEdgesAlwaysReached() {
        val all = outline(upQuad(5, 5), resolution = 2.0).toSet()

        assertTrue(Triple(5.0, 65.0, 5.0) in all, "corner must be exact, not 4.0")
    }

    @Test
    @DisplayName("outlining a flat sheet costs far less than gridding it")
    fun cheaperThanGrid() {
        val big = upQuad(40, 40)

        val asGrid = grid(big, spacing = 4.0, resolution = 0.5).size
        val asOutline = outline(big, resolution = 0.5).size

        assertTrue(asOutline < asGrid / 5, "expected a large saving, got $asOutline vs $asGrid")
    }

    @Test
    @DisplayName("a one-block quad still closes")
    fun singleBlockQuad() {
        val all = outline(upQuad(1, 1), resolution = 1.0).toSet()

        assertEquals(4, all.size, "a unit quad is its four corners")
    }

    @Test
    @DisplayName("degenerate resolution does not hang")
    fun degenerateResolution() {
        assertTrue(outline(upQuad(2, 2), resolution = 0.0).isNotEmpty())
        assertTrue(outline(upQuad(2, 2), resolution = -3.0).isNotEmpty())
    }
}
