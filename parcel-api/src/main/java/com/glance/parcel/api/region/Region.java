package com.glance.parcel.api.region;

import com.glance.parcel.api.math.BlockBox;
import com.glance.parcel.api.mesh.Quad;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A named volume, defined as an ordered list of {@link Part}s.
 *
 * <p>Membership is the fold over those parts: start outside, then for each part in order, if the
 * block falls inside that part's shape the result becomes {@code ADD ? inside : outside}. Last
 * writer wins, so a hole can be carved and then partly filled back in with no special cases.
 */
public interface Region {

    /**
     * @return this region's key, namespaced by whichever plugin created it
     */
    @NotNull
    NamespacedKey key();

    @NotNull
    World world();

    /**
     * @return the parts making up this region, in evaluation order
     */
    @NotNull
    @Unmodifiable
    List<Part> parts();

    /**
     * The union bounds of this region's additive parts, for cheap rejection before a full
     * containment test. Subtractive parts are excluded, since a subtraction can never make a
     * region larger.
     *
     * <p>Undefined when {@link #isEmpty()} - check that first.
     */
    @NotNull
    BlockBox bounds();

    /**
     * @return whether the given block is inside this region
     */
    boolean contains(int x, int y, int z);

    /**
     * Whether the given location is inside this region. The location is floored to a block, and a
     * location in another world is never contained.
     */
    boolean contains(@NotNull Location location);

    /**
     * The exposed surface of this region as merged rectangles, with interior faces removed.
     *
     * <p>Computed on demand and cached until the region changes. Cost is proportional to
     * {@link BlockBox#volume()}, so this is an edit-time operation - never call it per tick.
     */
    @NotNull
    @Unmodifiable
    List<Quad> mesh();

    /**
     * @return {@code true} if this region has no additive parts, and so can contain nothing
     */
    boolean isEmpty();

    /**
     * Opens an editor over this region. Changes apply on
     * {@link RegionEditor#commit()}.
     */
    @NotNull
    RegionEditor edit();
}
