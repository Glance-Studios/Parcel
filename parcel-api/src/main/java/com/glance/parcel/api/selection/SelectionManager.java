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
     * Convenience for the common consumer flow: take the player's selection and save it under the
     * given key.
     *
     * @throws IllegalStateException if the player has no selection, or it is empty
     */
    @NotNull
    Region promote(@NotNull Player player, @NotNull NamespacedKey key);
}
