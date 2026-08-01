package com.glance.parcel.platform.paper.visual

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OutlineTest {

    private fun box(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        BlockBox.of(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2))

    private fun points(box: BlockBox, spacing: Double): List<Triple<Double, Double, Double>> {
        val out = mutableListOf<Triple<Double, Double, Double>>()
        Outline.edges(box, spacing) { x, y, z -> out += Triple(x, y, z) }
        return out
    }

    @Test
    @DisplayName("the outline wraps the block footprint, not the block centres")
    fun spansFullFootprint() {
        // One block at the origin occupies continuous space 0..1, so its outline is a unit cube.
        val corners = points(box(0, 0, 0, 0, 0, 0), spacing = 1.0).toSet()

        assertEquals(8, corners.size, "a single block outline is its eight corners")
        assertTrue(Triple(0.0, 0.0, 0.0) in corners)
        assertTrue(Triple(1.0, 1.0, 1.0) in corners, "must reach max+1, not stop at the block coord")
    }

    @Test
    @DisplayName("every edge includes both endpoints even when spacing does not divide the length")
    fun endpointsAlwaysDrawn() {
        val corners = points(box(0, 0, 0, 9, 9, 9), spacing = 4.0).toSet()

        // 10 blocks spans 0..10, which 4.0 does not divide.
        assertTrue(Triple(0.0, 0.0, 0.0) in corners)
        assertTrue(Triple(10.0, 10.0, 10.0) in corners)
        assertTrue(Triple(10.0, 0.0, 0.0) in corners, "corner must be exact, not 8.0")
    }

    @Test
    @DisplayName("all twelve edges are drawn")
    fun drawsTwelveEdges() {
        val all = points(box(0, 0, 0, 4, 4, 4), spacing = 1.0)

        // Every point must sit on at least two of the six bounding planes - that is what makes it
        // an edge rather than a face or an interior point.
        val onEdge = all.all { (x, y, z) ->
            var planes = 0
            if (x == 0.0 || x == 5.0) planes++
            if (y == 0.0 || y == 5.0) planes++
            if (z == 0.0 || z == 5.0) planes++
            planes >= 2
        }
        assertTrue(onEdge, "no point should be off the edges")

        val corners = all.filter { (x, y, z) ->
            (x == 0.0 || x == 5.0) && (y == 0.0 || y == 5.0) && (z == 0.0 || z == 5.0)
        }.toSet()
        assertEquals(8, corners.size)
    }

    @Test
    @DisplayName("a tall prism produces points across its whole height")
    fun tallPrism() {
        val all = points(box(0, -64, 0, 4, 319, 4), spacing = 1.0)

        val minY = all.minOf { it.second }
        val maxY = all.maxOf { it.second }
        assertEquals(-64.0, minY)
        assertEquals(320.0, maxY)
        // Worth knowing the raw cost: this is why the renderer range-filters rather than drawing all.
        assertTrue(all.size > 1_500, "expected a large point count, got ${all.size}")
    }

    @Test
    @DisplayName("zero or negative spacing does not hang")
    fun degenerateSpacing() {
        assertTrue(points(box(0, 0, 0, 2, 2, 2), spacing = 0.0).isNotEmpty())
        assertTrue(points(box(0, 0, 0, 2, 2, 2), spacing = -5.0).isNotEmpty())
    }
}
