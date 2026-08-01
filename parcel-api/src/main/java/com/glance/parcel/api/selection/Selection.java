package com.glance.parcel.api.selection;

import com.glance.parcel.api.math.BlockBox;
import com.glance.parcel.api.math.BlockPos;
import com.glance.parcel.api.region.Part;
import com.glance.parcel.api.region.Region;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * A player's in-progress selection, built with the marquee tool.
 *
 * <p>This is the handoff point for other plugins: a builder draws a shape in game, then a consumer
 * turns it into one of its own regions without ever implementing selection itself.
 *
 * <pre>{@code
 * Selection sel = Parcel.api().selections().of(player);
 * if (sel != null) {
 *     Region region = sel.toRegion(new NamespacedKey(this, "tavern"));
 * }
 * }</pre>
 */
public interface Selection {

    @NotNull
    World world();

    /**
     * @return the parts committed so far, in order
     */
    @NotNull
    @Unmodifiable
    List<Part> parts();

    /**
     * The first corner of the not-yet-committed shape, if one has been marked.
     */
    @Nullable
    BlockPos pendingA();

    /**
     * The second corner of the not-yet-committed shape, if one has been marked.
     */
    @Nullable
    BlockPos pendingB();

    /**
     * @return the mode the next commit will use
     */
    @NotNull
    SelectionMode mode();

    /**
     * @return the union bounds of the committed parts, or {@code null} if there are none
     */
    @Nullable
    BlockBox bounds();

    /**
     * @return {@code true} if nothing has been committed yet
     */
    boolean isEmpty();

    /**
     * Materialises this selection as a saved region under the given key.
     *
     * <p>The selection is left intact; clear it explicitly if that is what you want.
     *
     * @throws IllegalStateException    if the selection is empty
     * @throws IllegalArgumentException if a region already exists under this key
     */
    @NotNull
    Region toRegion(@NotNull NamespacedKey key);
}
