package com.glance.parcel.api.region;

import com.glance.parcel.api.shape.Shape;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Mutation of a {@link Region}, batched until {@link #commit()}.
 *
 * <p>Because a region is only ever a list of parts, undo is exact and needs no snapshotting - see
 * {@link #undo()}.
 */
public interface RegionEditor {

    @NotNull
    RegionEditor add(@NotNull Shape shape);

    @NotNull
    RegionEditor subtract(@NotNull Shape shape);

    @NotNull
    RegionEditor addPart(@NotNull Part part);

    /**
     * Removes the part at the given index.
     *
     * @throws IndexOutOfBoundsException if there is no such part
     */
    @NotNull
    RegionEditor removePart(int index);

    /**
     * Drops the most recently added part.
     *
     * @return the removed part, or empty if there was nothing to undo
     */
    @NotNull
    Optional<Part> undo();

    @NotNull
    RegionEditor clear();

    /**
     * Applies the pending changes to the region and invalidates its cached bounds and mesh.
     *
     * @return the edited region
     */
    @NotNull
    Region commit();
}
