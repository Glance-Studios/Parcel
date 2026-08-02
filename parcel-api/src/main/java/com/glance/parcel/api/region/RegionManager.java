package com.glance.parcel.api.region;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Lookup and storage of saved regions.
 *
 * <p>Regions are keyed by {@link NamespacedKey}, so consumers namespace their own regions and
 * cannot collide with each other - a Motif ambience region is {@code motif:tavern_hum}.
 */
public interface RegionManager {

    @Nullable
    Region get(@NotNull NamespacedKey key);

    @NotNull
    @Unmodifiable
    Collection<Region> all();

    /**
     * @return every region in the given namespace, e.g. all of one plugin's regions
     */
    @NotNull
    @Unmodifiable
    Collection<Region> inNamespace(@NotNull String namespace);

    /**
     * Every region containing the given location.
     *
     * <p>Cheap enough for interactive use - candidates are rejected on their bounds before the
     * full part fold runs.
     */
    @NotNull
    @Unmodifiable
    Collection<Region> at(@NotNull Location location);

    /**
     * Creates an empty region and registers it. Use {@link Region#edit()} to give it parts.
     *
     * <p>Persistent: it is written to storage on every edit and reloaded on the next start.
     *
     * @throws IllegalArgumentException if a region with this key already exists
     */
    @NotNull
    Region create(@NotNull NamespacedKey key, @NotNull World world);

    /**
     * Creates an empty region that is never written to storage.
     *
     * <p>Identical to {@link #create} in every other respect - it is indexed, tracked, meshable and
     * visible to {@link #at} - but it vanishes on restart and is unaffected by a reload. For
     * geometry that is generated rather than authored.
     *
     * @throws IllegalArgumentException if a region with this key already exists
     * @see Region#isTransient()
     */
    @NotNull
    Region createTransient(@NotNull NamespacedKey key, @NotNull World world);

    /**
     * Removes a region.
     *
     * <p>Fires a cancellable {@link com.glance.parcel.api.event.RegionDeleteEvent} first, since
     * regions are shared and a deletion can break consumers unrelated to whoever triggered it.
     * Consider showing {@link #usagesOf} before calling this.
     *
     * @return whether a region with this key existed and was removed
     */
    boolean delete(@NotNull NamespacedKey key);

    /**
     * Reverts a region to its previous shape.
     *
     * <p>Fires {@link com.glance.parcel.api.event.RegionModifyEvent} like any other edit. Calling
     * it repeatedly walks back through history rather than flipping between two shapes; there is
     * no redo.
     *
     * @return whether there was anything to undo
     * @see Region#historyDepth()
     */
    boolean undo(@NotNull NamespacedKey key);

    /**
     * Asks every plugin what it is using this region for.
     *
     * <p>Fires {@link com.glance.parcel.api.event.RegionUsageQueryEvent} and collects the answers,
     * so the result is always current - there is no registry to go stale. Intended for confirmation
     * prompts before a destructive change.
     *
     * @return human-readable usage descriptions, empty if nothing claims this region
     */
    @NotNull
    @Unmodifiable
    List<String> usagesOf(@NotNull Region region);

    /**
     * Persists a region through the configured repository. Completes off the main thread.
     */
    @NotNull
    CompletableFuture<Void> save(@NotNull Region region);

    /**
     * Persists every loaded region.
     */
    @NotNull
    CompletableFuture<Void> saveAll();
}
