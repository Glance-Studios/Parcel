package com.glance.parcel.platform.paper.mesh

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.platform.paper.region.additiveBounds
import com.glance.parcel.platform.paper.region.evaluate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * The mesher is the piece a rendering bug would be blamed on, so it is verified without a server.
 */
class GreedyMesherTest {

    private fun box(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        BlockBox.of(BlockPos(x1, y1, z1), BlockPos(x2, y2, z2))

    private fun cuboid(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int) =
        Cuboid(box(x1, y1, z1, x2, y2, z2))

    /** Meshes a part list the same way a Region does. */
    private fun mesh(vararg parts: Part): List<Quad> {
        val list = parts.toList()
        val bounds = list.additiveBounds() ?: return emptyList()
        return GreedyMesher.mesh(bounds) { x, y, z -> list.evaluate(x, y, z) }
    }

    private fun List<Quad>.areaOf(face: Face) = filter { it.face == face }.sumOf { it.area() }

    @Test
    @DisplayName("a single block is six 1x1 quads")
    fun singleBlock() {
        val quads = mesh(Part.add(cuboid(0, 0, 0, 0, 0, 0)))

        assertEquals(6, quads.size)
        assertTrue(quads.all { it.width == 1 && it.height == 1 })
        assertEquals(Face.entries.toSet(), quads.map { it.face }.toSet())
    }

    @Test
    @DisplayName("a solid box merges each face into one quad")
    fun solidBoxMergesFaces() {
        // 4 wide (X), 5 tall (Y), 6 deep (Z).
        val quads = mesh(Part.add(cuboid(0, 0, 0, 3, 4, 5)))

        assertEquals(6, quads.size, "a box is six faces, however big it is")
        assertEquals(4 * 6, quads.areaOf(Face.UP))     // X * Z
        assertEquals(6 * 5, quads.areaOf(Face.EAST))   // Z * Y
        assertEquals(4 * 5, quads.areaOf(Face.NORTH))  // X * Y
        assertTrue(quads.all { it.area() > 1 }, "nothing should be left unmerged")
    }

    @Test
    @DisplayName("two flush boxes with equal cross-sections mesh as one box: 6 quads, not 12")
    fun flushEqualBoxesMergeToSix() {
        val quads = mesh(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.add(cuboid(10, 0, 0, 19, 9, 9)),
        )

        assertEquals(6, quads.size)
        // The seam is gone: neither box draws a face into the other.
        assertEquals(0, quads.count { it.face == Face.EAST && it.origin.x() == 9 })
        assertEquals(0, quads.count { it.face == Face.WEST && it.origin.x() == 10 })
        // And the merged top spans both.
        assertEquals(20 * 10, quads.areaOf(Face.UP))
    }

    @Test
    @DisplayName("partial attachment gives 10 quads")
    fun partialAttachment() {
        // A 10x10x10, with a full-width but half-height box on its +X side.
        val quads = mesh(
            Part.add(cuboid(0, 0, 0, 9, 9, 9)),
            Part.add(cuboid(10, 0, 0, 19, 5, 9)),
        )

        assertEquals(10, quads.size)
        assertEquals(1, quads.count { it.face == Face.DOWN }, "bottoms are coplanar, so they merge")
        assertEquals(2, quads.count { it.face == Face.UP }, "tops are at different heights")
        assertEquals(2, quads.count { it.face == Face.NORTH }, "the L meshes into two rectangles")
        assertEquals(2, quads.count { it.face == Face.SOUTH })
        assertEquals(1, quads.count { it.face == Face.WEST })
        // A's exposed +X strip above B, plus B's own +X face.
        assertEquals(2, quads.count { it.face == Face.EAST })
    }

    @Test
    @DisplayName("subtraction carves a cavity whose walls mesh as ordinary faces")
    fun subtractionProducesCavityWalls() {
        val quads = mesh(
            Part.add(cuboid(0, 0, 0, 2, 2, 2)),
            Part.subtract(cuboid(1, 1, 1, 1, 1, 1)),
        )

        // Six outer 3x3 faces, plus the six 1x1 walls of the enclosed hole.
        assertEquals(12, quads.size)
        assertEquals(6, quads.count { it.width == 3 && it.height == 3 })
        assertEquals(6, quads.count { it.width == 1 && it.height == 1 })
    }

    @Test
    @DisplayName("a subtraction that removes everything meshes to nothing")
    fun fullySubtracted() {
        val quads = mesh(
            Part.add(cuboid(0, 0, 0, 4, 4, 4)),
            Part.subtract(cuboid(0, 0, 0, 4, 4, 4)),
        )

        assertTrue(quads.isEmpty())
    }

    @Test
    @DisplayName("an L shape does not merge into a rectangle it does not fill")
    fun lShapeDoesNotOvermerge() {
        // Two boxes meeting at a corner in the XZ plane, one block tall.
        val quads = mesh(
            Part.add(cuboid(0, 0, 0, 4, 0, 1)),
            Part.add(cuboid(0, 0, 2, 1, 0, 4)),
        )

        val topArea = quads.areaOf(Face.UP)
        assertEquals(5 * 2 + 2 * 3, topArea, "the top covers exactly the L, not its bounding box")
        assertEquals(topArea, quads.areaOf(Face.DOWN), "top and bottom must cover the same footprint")
    }

    @Test
    @DisplayName("order matters: a later add fills a hole made by an earlier subtract")
    fun lastWriterWins() {
        val carved = mesh(
            Part.add(cuboid(0, 0, 0, 2, 2, 2)),
            Part.subtract(cuboid(1, 1, 1, 1, 1, 1)),
        )
        val refilled = mesh(
            Part.add(cuboid(0, 0, 0, 2, 2, 2)),
            Part.subtract(cuboid(1, 1, 1, 1, 1, 1)),
            Part(cuboid(1, 1, 1, 1, 1, 1), Op.ADD),
        )

        assertEquals(12, carved.size)
        assertEquals(6, refilled.size, "refilling the hole leaves a plain box")
    }

    @Test
    @DisplayName("meshing refuses regions past the volume cap instead of stalling")
    fun refusesOversizedRegions() {
        val huge = box(0, 0, 0, 999, 999, 999)
        val error = runCatching { GreedyMesher.mesh(huge, maxVolume = 1_000) { _, _, _ -> true } }
        assertTrue(error.isFailure)
        assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }
}
