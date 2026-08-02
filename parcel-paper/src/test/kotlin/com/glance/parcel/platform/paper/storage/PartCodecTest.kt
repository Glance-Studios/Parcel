package com.glance.parcel.platform.paper.storage

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.api.shape.Prism
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Storage was previously untested, which is uncomfortable for the one component whose failures are
 * permanent - a region read back wrong is a region silently corrupted on disk.
 */
class PartCodecTest {

    private val codec = PartCodec()

    private fun box(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        BlockBox.of(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2))

    @Test
    @DisplayName("a cuboid survives a round trip unchanged")
    fun cuboidRoundTrip() {
        val original = Part.add(Cuboid(box(1, -60, 3, 10, -50, 30)))
        val restored = codec.read(codec.write(original))

        assertEquals(original, restored)
    }

    @Test
    @DisplayName("a carve stays a carve")
    fun opSurvives() {
        val original = Part.subtract(Cuboid(box(0, 0, 0, 4, 4, 4)))
        assertEquals(Op.SUBTRACT, codec.read(codec.write(original))?.op())
    }

    @Test
    @DisplayName("a prism does not come back as a cuboid")
    fun prismStaysPrism() {
        // Both are boxes on disk - only typeId separates them. Losing that would turn a flat region
        // into a bounded one, which renders and behaves completely differently.
        val original = Part.add(Prism(box(0, -64, 0, 15, 319, 15)))
        val restored = codec.read(codec.write(original))

        assertTrue(restored?.shape() is Prism, "expected a prism, got ${restored?.shape()}")
        assertEquals(original.shape().bounds(), restored?.shape()?.bounds())
    }

    @Test
    @DisplayName("negative coordinates survive")
    fun negativesSurvive() {
        val original = Part.add(Cuboid(box(-100, -64, -100, -90, -60, -90)))
        assertEquals(original, codec.read(codec.write(original)))
    }

    @Test
    @DisplayName("malformed entries are dropped, not guessed at")
    fun malformedIsNull() {
        assertNull(codec.read(emptyMap()))
        assertNull(codec.read(mapOf("op" to "SIDEWAYS", "type" to "cuboid")))
        assertNull(codec.read(mapOf("op" to "ADD", "type" to "sphere", "min" to listOf(0, 0, 0), "max" to listOf(1, 1, 1))))
        assertNull(codec.read(mapOf("op" to "ADD", "type" to "cuboid", "min" to listOf(0, 0), "max" to listOf(1, 1, 1))))
    }

    @Test
    @DisplayName("a whole part list round trips in order")
    fun listOrderSurvives() {
        // Order is the entire semantics of a region - a list read back shuffled is a different shape.
        val original = listOf(
            Part.add(Cuboid(box(0, 0, 0, 9, 9, 9))),
            Part.subtract(Cuboid(box(4, 4, 4, 5, 5, 5))),
            Part.add(Cuboid(box(5, 5, 5, 5, 5, 5))),
        )
        val restored = original.map(codec::write).mapNotNull(codec::read)

        assertEquals(original, restored)
    }
}
