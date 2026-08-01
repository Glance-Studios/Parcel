package com.glance.parcel.api.region;

import com.glance.parcel.api.shape.Shape;
import org.jetbrains.annotations.NotNull;

/**
 * One shape plus what it does. A {@link Region} is an ordered list of these.
 */
public record Part(@NotNull Shape shape, @NotNull Op op) {

    @NotNull
    public static Part add(@NotNull Shape shape) {
        return new Part(shape, Op.ADD);
    }

    @NotNull
    public static Part subtract(@NotNull Shape shape) {
        return new Part(shape, Op.SUBTRACT);
    }
}
