package com.glance.parcel.api.region;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
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
     * @throws IllegalArgumentException if a region with this key already exists
     */
    @NotNull
    Region create(@NotNull NamespacedKey key, @NotNull World world);

    /**
     * @return whether a region with this key existed and was removed
     */
    boolean delete(@NotNull NamespacedKey key);

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
