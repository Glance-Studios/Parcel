package com.glance.parcel.api.selection;

import com.glance.parcel.api.region.Region;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Access to players' in-progress marquee selections.
 */
public interface SelectionManager {

    /**
     * @return the player's current selection, or {@code null} if they have none
     */
    @Nullable
    Selection of(@NotNull Player player);

    /**
     * Gets or creates a selection for the player.
     */
    @NotNull
    Selection getOrCreate(@NotNull Player player);

    /**
     * Discards the player's selection.
     *
     * @return whether there was one to discard
     */
    boolean clear(@NotNull Player player);

    /**
     * Loads an existing region into the player's selection so it can be edited further.
     */
    @NotNull
    Selection load(@NotNull Player player, @NotNull Region region);

    /**
     * The common flow: save the player's selection as a region and clear it.
     *
     * <p>Clearing is the point - a Parcel selection accumulates parts, so leaving it populated after
     * a commit means the next region a builder draws silently inherits the previous one's shape.
     * Use {@link Selection#toRegion} directly if you genuinely want to keep it.
     *
     * @throws IllegalStateException if the player has no selection, or it is empty
     */
    @NotNull
    Region promote(@NotNull Player player, @NotNull NamespacedKey key);
}
