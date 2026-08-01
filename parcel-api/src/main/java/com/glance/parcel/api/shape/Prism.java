package com.glance.parcel.api.shape;

import com.glance.parcel.api.math.BlockBox;
import com.glance.parcel.api.math.BlockPos;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * A horizontal footprint that spans the full world height. The "2D" authoring mode.
 *
 * <p>The Y extent is stored concretely rather than left unbounded, so every shape is a box and the
 * mesher has exactly one code path. What makes this a prism rather than a {@link Cuboid} is
 * <em>authoring intent</em>: the Y range was derived from the world, not chosen by the builder, so
 * it can be re-derived if the world's height limits change.
 *
 * @param box the resolved bounds, with Y spanning the world height at authoring time
 */
public record Prism(@NotNull BlockBox box) implements Shape {

    public static final String TYPE_ID = "prism";

    /**
     * Builds a prism over a horizontal footprint, taking the Y extent from the world.
     */
    @NotNull
    public static Prism of(@NotNull World world, int x1, int z1, int x2, int z2) {
        return new Prism(BlockBox.of(
            new BlockPos(Math.min(x1, x2), world.getMinHeight(), Math.min(z1, z2)),
            new BlockPos(Math.max(x1, x2), world.getMaxHeight() - 1, Math.max(z1, z2))));
    }

    /**
     * Builds a prism from two corners, ignoring their Y and taking the extent from the world.
     */
    @NotNull
    public static Prism of(@NotNull World world, @NotNull BlockPos a, @NotNull BlockPos b) {
        return of(world, a.x(), a.z(), b.x(), b.z());
    }

    /**
     * Re-resolves the Y extent against a world's current height limits.
     */
    @NotNull
    public Prism resolvedFor(@NotNull World world) {
        return of(world, box.min().x(), box.min().z(), box.max().x(), box.max().z());
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return box.contains(x, y, z);
    }

    @Override
    @NotNull
    public BlockBox bounds() {
        return box;
    }

    @Override
    @NotNull
    public String typeId() {
        return TYPE_ID;
    }
}
