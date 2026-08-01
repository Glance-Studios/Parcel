package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a region's parts change.
 *
 * <p>Essential when regions are shared: several plugins may reference the same region by key, and an
 * edit made through any of them - or through the marquee tool - changes the shape underneath all of
 * them at once. Anything holding derived state (a cached mesh, a spawned visualiser, per-player
 * bookkeeping) must invalidate it here.
 *
 * <p>Membership fixes itself: the tracker re-evaluates on its next pass, so players who are now
 * inside or outside get their enter and exit events without any work from the consumer.
 */
public class RegionModifyEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;

    public RegionModifyEvent(@NotNull Region region) {
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
