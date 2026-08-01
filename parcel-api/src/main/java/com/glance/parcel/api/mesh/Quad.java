package com.glance.parcel.api.mesh;

import com.glance.parcel.api.math.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * One merged rectangle of a region's surface, as produced by the greedy mesher.
 *
 * <p>A region's mesh is the set of quads that are exposed - interior faces between touching parts
 * never appear, and coplanar neighbours are merged into the largest rectangles that fit.
 *
 * <p>This is deliberately renderer-agnostic. Parcel's own visualiser turns each quad into a
 * mirrored display panel, but a consumer is free to draw them any way it likes.
 *
 * @param face   which way the quad faces
 * @param origin the minimum corner of the quad, in block coordinates
 * @param width  extent along {@link Face#widthAxis()}, in blocks, always at least 1
 * @param height extent along {@link Face#heightAxis()}, in blocks, always at least 1
 */
public record Quad(@NotNull Face face, @NotNull BlockPos origin, int width, int height) {

    public Quad {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Quad extents must be positive, got " + width + "x" + height);
        }
    }

    /**
     * @return the area of this quad in square blocks
     */
    public int area() {
        return width * height;
    }
}
