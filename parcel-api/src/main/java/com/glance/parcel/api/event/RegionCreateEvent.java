package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a region is created. Informational - the region has no parts yet.
 */
public class RegionCreateEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;

    public RegionCreateEvent(@NotNull Region region) {
        this.region = region;
    }

    @NotNull
    public Region region() {
        return region;
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
