package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad
import com.glance.parcel.api.region.Part
import com.glance.parcel.api.region.Region
import com.glance.parcel.api.shape.Cuboid
import com.glance.parcel.api.shape.Prism
import com.glance.parcel.platform.paper.mesh.GreedyMesher
import com.glance.parcel.platform.paper.region.additiveBounds
import com.glance.parcel.platform.paper.region.evaluate

/**
 * What to draw for a region, which is not always the same as what the region *is*.
 *
 * A prism spans the world's full height, so its true surface is a floor at bedrock, a ceiling at
 * the build limit, and four walls a few hundred blocks tall. All correct, none of it useful to
 * look at, and it buries whatever you were trying to see.
 *
 * So a region containing prisms is drawn as a **cross-section**: a single horizontal plane at
 * ground level, blanketing the footprint. The region's real geometry is untouched - membership,
 * tracking and enter/exit events all still use the true full-height prism, so a player at Y 200 is
 * still inside a region whose plane is drawn on the ground. Only the mesh changes.
 */
internal object DisplayMesh {

    /**
     * @param flatY the height for a cross-section plane. Resolved by the caller, because it
     *   depends on who is looking - a plane spawned while you are in the air should appear at your
     *   level, not on the ground beneath you.
     */
    fun of(region: Region, flatY: Int): List<Quad> {
        val parts = region.parts()
        if (parts.none { it.shape() is Prism }) return region.mesh()

        val y = flatY.coerceIn(region.world().minHeight, region.world().maxHeight - 1)
        val flattened = parts.map { part ->
            when (val shape = part.shape()) {
                is Prism -> Part(Cuboid(flatten(shape.box(), y)), part.op())
                else -> part
            }
        }

        val bounds = flattened.additiveBounds() ?: return emptyList()
        return runCatching {
            GreedyMesher.mesh(bounds) { x, yy, z -> flattened.evaluate(x, yy, z) }
        }.getOrDefault(emptyList())
            // A single horizontal plane, not a slab. Meshing a one-block box still yields six
            // faces - a top, a bottom, and four one-block rims - and the rims read as a shallow
            // tray rather than a footprint. Only the top is wanted, and both primitives draw from
            // either side anyway, so nothing is lost by dropping the rest.
            .filter { it.face() == Face.UP }
    }

    private fun flatten(box: BlockBox, y: Int) = BlockBox(
        BlockPos(box.min().x(), y, box.min().z()),
        BlockPos(box.max().x(), y, box.max().z()),
    )

    /**
     * Ground level at the footprint's centre - the fallback when nobody is around to sit under.
     *
     * One height for the whole plane rather than following the terrain per column: a heightmap-
     * shaped surface would cost a quad per step and read as noise, where a flat plane reads as a
     * footprint. It can clip a hillside, which is the honest trade.
     */
    fun groundY(region: Region, offset: Int): Int {
        val box = region.bounds()
        val world = region.world()
        val centreX = box.min().x() + box.sizeX() / 2
        val centreZ = box.min().z() + box.sizeZ() / 2

        return (world.getHighestBlockYAt(centreX, centreZ) + offset)
            .coerceIn(world.minHeight, world.maxHeight - 1)
    }

    /** Whether this region is drawn as a cross-section rather than as its true surface. */
    fun isCrossSection(region: Region): Boolean = region.parts().any { it.shape() is Prism }
}
