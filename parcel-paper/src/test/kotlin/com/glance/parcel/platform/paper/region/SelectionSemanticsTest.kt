package com.glance.parcel.platform.paper.region

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The difference between replacing a region's parts and appending to them.
 *
 * This is the shape of a bug that destroyed a saved region in testing: a selection containing only
 * a carve was *applied* to a region, replacing its geometry with just the subtraction and leaving it
 * with no additive parts at all - an empty region. Every component behaved correctly; the operation
 * was wrong, and nothing tested the difference.
 *
 * Written against the pure fold rather than `SelectionImpl`, so the rule is pinned down without
 * needing a world or a server.
 */
class SelectionSemanticsTest {

    private fun cuboid(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        Cuboid(BlockBox.of(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2)))

    private val region = listOf(Part.add(cuboid(0, 0, 0, 9, 9, 9)))
    private val carveOnly = listOf(Part.subtract(cuboid(4, 4, 4, 5, 5, 5)))

    @Test
    @DisplayName("replacing with a carve-only selection empties the region")
    fun replaceWithCarveEmpties() {
        // The exact failure. Kept as a test rather than only a guard, so the guard cannot be
        // removed by someone who thinks it is over-cautious.
        val replaced = carveOnly

        assertNull(replaced.additiveBounds(), "no additive parts means nothing can be inside")
        assertFalse(replaced.evaluate(0, 0, 0))
        assertFalse(replaced.evaluate(4, 4, 4))
    }

    @Test
    @DisplayName("appending the same carve cuts a hole and keeps the region")
    fun appendCarvesInstead() {
        val appended = region + carveOnly

        assertNotNull(appended.additiveBounds())
        assertTrue(appended.evaluate(0, 0, 0), "outside the carve, still inside the region")
        assertFalse(appended.evaluate(4, 4, 4), "inside the carve")
        assertTrue(appended.evaluate(9, 9, 9))
    }

    @Test
    @DisplayName("appending is additive - the original parts are still there")
    fun appendKeepsOriginal() {
        val appended = region + carveOnly
        assertTrue(appended.containsAll(region))
        assertTrue(appended.size == region.size + carveOnly.size)
    }

    @Test
    @DisplayName("replacing with a selection that has adds is fine")
    fun replaceWithAddsIsFine() {
        val replacement = listOf(Part.add(cuboid(20, 0, 20, 25, 5, 25)))

        assertNotNull(replacement.additiveBounds())
        assertTrue(replacement.evaluate(20, 0, 20))
        assertFalse(replacement.evaluate(0, 0, 0), "the old shape is gone")
    }

    @Test
    @DisplayName("append order matters - a carve appended after an add wins")
    fun appendOrderMatters() {
        val carveThenAdd = carveOnly + region
        val addThenCarve = region + carveOnly

        assertTrue(carveThenAdd.evaluate(4, 4, 4), "the add came last, so it fills")
        assertFalse(addThenCarve.evaluate(4, 4, 4), "the carve came last, so it cuts")
    }
}
