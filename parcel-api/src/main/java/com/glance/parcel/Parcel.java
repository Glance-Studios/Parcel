package com.glance.parcel;

import com.glance.parcel.api.ParcelAPI;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Static entry point to the Parcel region system.
 *
 * <p>Parcel registers its {@link ParcelAPI} implementation with Bukkit's
 * {@link org.bukkit.plugin.ServicesManager} during enable. Consumers should depend on the Parcel
 * plugin (so load order is guaranteed) and, if they query on startup, wait for
 * {@link com.glance.parcel.api.event.ParcelReadyEvent} before touching saved regions.
 *
 * <pre>{@code
 * Region region = Parcel.api().regions().get(new NamespacedKey(this, "tavern"));
 * }</pre>
 */
public final class Parcel {

    private Parcel() {
    }

    /**
     * @return the live API instance
     * @throws IllegalStateException if Parcel is not loaded
     */
    @NotNull
    public static ParcelAPI api() {
        ParcelAPI api = apiOrNull();
        if (api == null) {
            throw new IllegalStateException(
                "Parcel is not loaded. Declare it as a dependency in your plugin descriptor.");
        }
        return api;
    }

    /**
     * @return the live API instance, or {@code null} if Parcel is not loaded
     */
    @Nullable
    public static ParcelAPI apiOrNull() {
        return Bukkit.getServicesManager().load(ParcelAPI.class);
    }

    /**
     * @return whether Parcel is loaded and its API is available
     */
    public static boolean isAvailable() {
        return apiOrNull() != null;
    }
}
