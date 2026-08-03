package com.glance.parcel.api.render;

import com.glance.parcel.api.region.Region;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shows and hides the visual representation of a region - the panels or wireframe it was styled
 * with.
 *
 * <p>Exists because a consumer that wants to show a region alongside its own visuals had no way to
 * do it except dispatching {@code /parcel render}, which is a <em>toggle</em>. Without a way to ask
 * what is currently rendering, a plugin can only flip, and any flip it did not make itself puts the
 * two out of step. This lets callers set the state they want instead of guessing at it.
 *
 * <p>Rendering is <b>per region, not per player</b>. Panels are real display entities in the world,
 * so a region is either rendered or it is not; the {@code viewer} argument decides who can see it
 * and where a cross-section plane is placed, not whether a second player asking gets their own copy.
 */
public interface RenderManager {

    /** {@link #render} returned this because the mesh was refused as too large. */
    int TOO_LARGE = -1;

    /** {@link #render} returned this because the region has no shape to draw. */
    int EMPTY = 0;

    /**
     * @return whether this region is currently rendered, as panels or as a wireframe
     */
    boolean isRendering(@NotNull NamespacedKey region);

    /**
     * Render a region, replacing any existing render of it.
     *
     * @param viewer whoever asked. A cross-section plane spawns at their height rather than on the
     *               ground, so it appears where they are looking; and if the server is configured
     *               for viewer-only renders, they are the one who sees it. May be null for a
     *               console-initiated render.
     * @return the number of panels spawned, or {@link #TOO_LARGE} / {@link #EMPTY}
     */
    int render(@NotNull Region region, @Nullable Player viewer);

    /**
     * @return true if a render existed and was removed
     */
    boolean hide(@NotNull NamespacedKey region);

    /**
     * Remove every render.
     *
     * <p>Note this hides renders started by anyone, including by Parcel's own commands.
     */
    void hideAll();
}
