package com.glance.parcel.api.shape;

import com.glance.parcel.api.math.BlockBox;
import com.glance.parcel.api.math.BlockPos;
import org.jetbrains.annotations.NotNull;

/**
 * A fully bounded box. The "3D" authoring mode.
 */
public record Cuboid(@NotNull BlockBox box) implements Shape {

    public static final String TYPE_ID = "cuboid";

    @NotNull
    public static Cuboid of(@NotNull BlockPos a, @NotNull BlockPos b) {
        return new Cuboid(BlockBox.of(a, b));
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
