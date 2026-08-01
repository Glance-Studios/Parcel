package com.glance.parcel.api.math;

import org.jetbrains.annotations.NotNull;

/**
 * An immutable, inclusive axis-aligned block box.
 *
 * <p>Inclusive on both ends: a box from (0,0,0) to (0,0,0) contains exactly one block.
 */
public record BlockBox(@NotNull BlockPos min, @NotNull BlockPos max) {

    /**
     * Builds a box from two arbitrary corners, normalising min/max.
     */
    @NotNull
    public static BlockBox of(@NotNull BlockPos a, @NotNull BlockPos b) {
        return new BlockBox(a.min(b), a.max(b));
    }

    public boolean contains(int x, int y, int z) {
        return x >= min.x() && x <= max.x()
            && y >= min.y() && y <= max.y()
            && z >= min.z() && z <= max.z();
    }

    public boolean intersects(@NotNull BlockBox other) {
        return min.x() <= other.max.x() && max.x() >= other.min.x()
            && min.y() <= other.max.y() && max.y() >= other.min.y()
            && min.z() <= other.max.z() && max.z() >= other.min.z();
    }

    /**
     * Smallest box containing both this and {@code other}.
     */
    @NotNull
    public BlockBox union(@NotNull BlockBox other) {
        return new BlockBox(min.min(other.min), max.max(other.max));
    }

    public int sizeX() {
        return max.x() - min.x() + 1;
    }

    public int sizeY() {
        return max.y() - min.y() + 1;
    }

    public int sizeZ() {
        return max.z() - min.z() + 1;
    }

    /**
     * @return block volume, as a long so large regions do not silently overflow
     */
    public long volume() {
        return (long) sizeX() * sizeY() * sizeZ();
    }
}
