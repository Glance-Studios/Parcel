package com.glance.parcel.api.storage;

import com.glance.parcel.api.region.Part;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Where saved regions live.
 *
 * <p>Parcel ships a YAML-backed implementation, which is enough for the config-sized data regions
 * actually are. Swap this to put regions in a database if a network needs them shared.
 *
 * <p>Implementations must be safe to call off the main thread, and must not touch the Bukkit API.
 * Records are deliberately world-name-and-parts rather than live {@link com.glance.parcel.api.region.Region}
 * objects so a repository never needs a loaded world.
 */
public interface RegionRepository {

    /**
     * A stored region, independent of whether its world is loaded.
     */
    record Record(@NotNull NamespacedKey key, @NotNull String world, @NotNull List<Part> parts) {
    }

    @NotNull
    CompletableFuture<Collection<Record>> loadAll();

    @NotNull
    CompletableFuture<Void> save(@NotNull Record record);

    @NotNull
    CompletableFuture<Void> delete(@NotNull NamespacedKey key);
}
