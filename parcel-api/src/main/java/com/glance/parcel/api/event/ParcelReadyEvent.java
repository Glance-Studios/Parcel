package com.glance.parcel.api.event;

import com.glance.parcel.api.ParcelAPI;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired once every saved region has loaded and the API is safe to query.
 *
 * <p>Consumers that read regions on startup should wait for this rather than querying in their own
 * {@code onEnable}, since region loading is asynchronous.
 */
public class ParcelReadyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final ParcelAPI api;

    public ParcelReadyEvent(@NotNull ParcelAPI api) {
        this.api = api;
    }

    @NotNull
    public ParcelAPI api() {
        return api;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
