package com.glance.parcel.api.math;

import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable block coordinate.
 *
 * <p>Parcel is block-aligned throughout: a selection is always whole blocks, which is what makes
 * voxelising a region exact rather than an approximation.
 */
public record BlockPos(int x, int y, int z) {

    /**
     * Floors a continuous location to the block containing it.
     */
    @NotNull
    public static BlockPos of(@NotNull Location location) {
        return new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /**
     * Component-wise minimum.
     */
    @NotNull
    public BlockPos min(@NotNull BlockPos other) {
        return new BlockPos(
            Math.min(x, other.x),
            Math.min(y, other.y),
            Math.min(z, other.z));
    }

    /**
     * Component-wise maximum.
     */
    @NotNull
    public BlockPos max(@NotNull BlockPos other) {
        return new BlockPos(
            Math.max(x, other.x),
            Math.max(y, other.y),
            Math.max(z, other.z));
    }

    /**
     * @return the centre of this block, as a location in the given world
     */
    @NotNull
    public Location toLocation(@NotNull World world) {
        return new Location(world, x + 0.5, y + 0.5, z + 0.5);
    }

    @NotNull
    public BlockPos offset(int dx, int dy, int dz) {
        return new BlockPos(x + dx, y + dy, z + dz);
    }
}
