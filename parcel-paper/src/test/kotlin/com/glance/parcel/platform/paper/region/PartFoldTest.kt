package com.glance.parcel.platform.paper.region

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PartFoldTest {

    private fun cuboid(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        Cuboid(BlockBox.of(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2)))

    @Test
    @DisplayName("an empty part list contains nothing")
    fun emptyContainsNothing() {
        assertFalse(emptyList<Part>().evaluate(0, 0, 0))
        assertNull(emptyList<Part>().additiveBounds())
    }

    @Test
    @DisplayName("subtract carves out of a preceding add")
    fun subtractCarves() {
        val parts = listOf(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.subtract(cuboid(4, 4, 4, 5, 5, 5)),
        )

        assertTrue(parts.evaluate(0, 0, 0))
        assertFalse(parts.evaluate(4, 4, 4), "inside the carve")
        assertFalse(parts.evaluate(5, 5, 5))
        assertTrue(parts.evaluate(6, 6, 6), "just outside the carve")
    }

    @Test
    @DisplayName("last writer wins, so a later add refills a carve")
    fun lastWriterWins() {
        val parts = listOf(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.subtract(cuboid(4, 4, 4, 5, 5, 5)),
            Part.add(cuboid(5, 5, 5, 5, 5, 5)),
        )

        assertFalse(parts.evaluate(4, 4, 4), "still carved")
        assertTrue(parts.evaluate(5, 5, 5), "refilled by the later add")
    }

    @Test
    @DisplayName("order is not commutative")
    fun orderMatters() {
        val addThenSubtract = listOf(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.subtract(cuboid(0, 0, 0, 4, 4, 4)),
        )
        val subtractThenAdd = listOf(
            Part.subtract(cuboid(0, 0, 0, 4, 4, 4)),
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
        )

        assertFalse(addThenSubtract.evaluate(2, 2, 2))
        assertTrue(subtractThenAdd.evaluate(2, 2, 2))
    }

    @Test
    @DisplayName("bounds ignore subtractive parts, which can never enlarge a region")
    fun boundsIgnoreSubtractions() {
        val parts = listOf(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.subtract(cuboid(-100, -100, -100, 100, 100, 100)),
        )

        val bounds = parts.additiveBounds()!!
        assertEquals(BlockPos(0, 0, 0), bounds.min())
        assertEquals(BlockPos(9, 9, 9), bounds.max())
    }

    @Test
    @DisplayName("bounds union every additive part")
    fun boundsUnionAdds() {
        val parts = listOf(
            Part.add(cuboid(0, 0, 0, 1, 1, 1)),
            Part.add(cuboid(10, 20, 30, 11, 21, 31)),
        )

        val bounds = parts.additiveBounds()!!
        assertEquals(BlockPos(0, 0, 0), bounds.min())
        assertEquals(BlockPos(11, 21, 31), bounds.max())
    }

    @Test
    @DisplayName("a list of only subtractions has no bounds and contains nothing")
    fun onlySubtractions() {
        val parts = listOf(Part.subtract(cuboid(0, 0, 0, 9, 9, 9)))

        assertNull(parts.additiveBounds())
        assertFalse(parts.evaluate(5, 5, 5))
    }
}
