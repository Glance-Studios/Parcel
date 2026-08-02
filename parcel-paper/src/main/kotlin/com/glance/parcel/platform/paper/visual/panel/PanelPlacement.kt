package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.mesh.Face
import com.glance.parcel.api.mesh.Quad
import org.joml.Quaternionf

/**
 * Where a display entity goes to cover a meshed quad, and how big it is.
 *
 * A [org.bukkit.entity.BlockDisplay] renders a unit cube spanning `position + translation` to
 * `position + translation + scale`, so for axis-aligned faces the whole thing is a translate and a
 * scale with **no rotation at all** - the scale maps 1:1 onto blocks with nothing to calibrate.
 * That is the main reason the first panel primitive is a block display rather than a text display:
 * a text display's background is sized by its glyphs, so covering an exact area means measuring a
 * magic constant first.
 *
 * Kept free of Bukkit so the face-to-world mapping can be unit tested. Getting a face off by one
 * block, or inside-out, is invisible until you are stood in the right place.
 */
internal data class PanelPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val tx: Float,
    val ty: Float,
    val tz: Float,
    val sx: Float,
    val sy: Float,
    val sz: Float,
) {
    /** Longest horizontal extent, for sizing the client's culling box. */
    val widthExtent: Float get() = maxOf(sx, sz)

    val heightExtent: Float get() = sy
}

/**
 * A text panel's placement: the centre of the face, the rotation that turns the display's own quad
 * to lie in that face, and the size it must cover.
 *
 * Unlike a block display, a text display's quad is sized by its glyphs, so the scale here is in
 * "cover this many blocks" and the renderer converts using the measured base size of an empty
 * background.
 */
internal data class TextPanelPlacement(
    val x: Double,
    val y: Double,
    val z: Double,
    val rotation: Quaternionf,
    val coverWidth: Float,
    val coverHeight: Float,
)

internal object Panels {

    /**
     * How far to lift a panel off the surface it covers, along the face normal.
     *
     * A panel sitting exactly on a block face z-fights with it. Because the normal is perpendicular
     * to both axes the panel is sized on, lifting along it cannot change the panel's size or where
     * its edges land - only its depth. So this is free to tune without recalibrating anything.
     */
    fun normalOffset(face: Face, distance: Double): Triple<Double, Double, Double> = when (face) {
        Face.UP -> Triple(0.0, distance, 0.0)
        Face.DOWN -> Triple(0.0, -distance, 0.0)
        Face.SOUTH -> Triple(0.0, 0.0, distance)
        Face.NORTH -> Triple(0.0, 0.0, -distance)
        Face.EAST -> Triple(distance, 0.0, 0.0)
        Face.WEST -> Triple(-distance, 0.0, 0.0)
    }

    /**
     * Rotation taking the display's own quad, which lies in the XY plane facing +Z, onto each face.
     */
    fun rotationFor(face: Face): Quaternionf = when (face) {
        Face.SOUTH -> Quaternionf()
        Face.NORTH -> Quaternionf().rotateY(Math.PI.toFloat())
        Face.EAST -> Quaternionf().rotateY(HALF_PI)
        Face.WEST -> Quaternionf().rotateY(-HALF_PI)
        Face.UP -> Quaternionf().rotateX(-HALF_PI)
        Face.DOWN -> Quaternionf().rotateX(HALF_PI)
    }

    /**
     * The back half of a mirrored pair.
     *
     * Rotating 180 degrees about the quad's **own** local Y flips it whichever way it is already
     * facing, so this works for horizontal faces as well as vertical ones - which a world-space
     * Y flip would not. Same operation as `Transformation.mirroredY()` in Shaded's core.
     */
    fun mirrored(rotation: Quaternionf): Quaternionf =
        Quaternionf(rotation).rotateY(Math.PI.toFloat())

    /**
     * Centre of the face a quad covers, plus how much area it has to span.
     *
     * @param surfaceOffset lift off the surface along the face normal, to avoid z-fighting
     */
    fun textPlacementFor(quad: Quad, surfaceOffset: Double = 0.0): TextPanelPlacement {
        val o = quad.origin()
        val face = quad.face()
        val w = quad.width().toFloat()
        val h = quad.height().toFloat()
        val ox = o.x().toDouble()
        val oy = o.y().toDouble()
        val oz = o.z().toDouble()

        val base = when (face) {
            Face.UP -> Triple(ox + w / 2, oy + 1.0, oz + h / 2)
            Face.DOWN -> Triple(ox + w / 2, oy, oz + h / 2)
            Face.SOUTH -> Triple(ox + w / 2, oy + h / 2, oz + 1.0)
            Face.NORTH -> Triple(ox + w / 2, oy + h / 2, oz)
            Face.EAST -> Triple(ox + 1.0, oy + h / 2, oz + w / 2)
            Face.WEST -> Triple(ox, oy + h / 2, oz + w / 2)
        }
        val (dx, dy, dz) = normalOffset(face, surfaceOffset)

        return TextPanelPlacement(
            base.first + dx, base.second + dy, base.third + dz,
            rotationFor(face), w, h,
        )
    }

    private const val HALF_PI = (Math.PI / 2.0).toFloat()

    /**
     * @param thickness how thick the slab is, straddling the face plane so it reads as a surface
     *                  rather than sitting on one side of it
     */
    fun placementFor(quad: Quad, thickness: Float, surfaceOffset: Double = 0.0): PanelPlacement {
        val o = quad.origin()
        val w = quad.width().toFloat()
        val h = quad.height().toFloat()
        val half = thickness / 2f

        val (dx, dy, dz) = normalOffset(quad.face(), surfaceOffset)
        val ox = o.x().toDouble() + dx
        val oy = o.y().toDouble() + dy
        val oz = o.z().toDouble() + dz

        return when (quad.face()) {
            // Horizontal faces: width runs along X, height along Z.
            Face.UP -> PanelPlacement(ox, oy + 1.0, oz, 0f, -half, 0f, w, thickness, h)
            Face.DOWN -> PanelPlacement(ox, oy, oz, 0f, -half, 0f, w, thickness, h)

            // Z-facing: width along X, height along Y.
            Face.SOUTH -> PanelPlacement(ox, oy, oz + 1.0, 0f, 0f, -half, w, h, thickness)
            Face.NORTH -> PanelPlacement(ox, oy, oz, 0f, 0f, -half, w, h, thickness)

            // X-facing: width along Z, height along Y.
            Face.EAST -> PanelPlacement(ox + 1.0, oy, oz, -half, 0f, 0f, thickness, h, w)
            Face.WEST -> PanelPlacement(ox, oy, oz, -half, 0f, 0f, thickness, h, w)
        }
    }
}
