package com.glance.parcel.api.mesh;

import org.bukkit.Axis;
import org.jetbrains.annotations.NotNull;

/**
 * Which way a meshed {@link Quad} faces.
 *
 * <p>Each face fixes which two axes are "in plane". {@link Quad#width()} runs along
 * {@link #widthAxis()} and {@link Quad#height()} along {@link #heightAxis()}.
 */
public enum Face {

    DOWN(Axis.Y, -1, Axis.X, Axis.Z),
    UP(Axis.Y, 1, Axis.X, Axis.Z),
    NORTH(Axis.Z, -1, Axis.X, Axis.Y),
    SOUTH(Axis.Z, 1, Axis.X, Axis.Y),
    WEST(Axis.X, -1, Axis.Z, Axis.Y),
    EAST(Axis.X, 1, Axis.Z, Axis.Y);

    private final Axis normalAxis;
    private final int direction;
    private final Axis widthAxis;
    private final Axis heightAxis;

    Face(Axis normalAxis, int direction, Axis widthAxis, Axis heightAxis) {
        this.normalAxis = normalAxis;
        this.direction = direction;
        this.widthAxis = widthAxis;
        this.heightAxis = heightAxis;
    }

    /**
     * @return the axis this face's normal runs along
     */
    @NotNull
    public Axis normalAxis() {
        return normalAxis;
    }

    /**
     * @return {@code +1} or {@code -1}, the direction along {@link #normalAxis()}
     */
    public int direction() {
        return direction;
    }

    @NotNull
    public Axis widthAxis() {
        return widthAxis;
    }

    @NotNull
    public Axis heightAxis() {
        return heightAxis;
    }

    @NotNull
    public Face opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
