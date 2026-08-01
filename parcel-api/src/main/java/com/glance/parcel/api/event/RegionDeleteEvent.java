package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired before a region is removed. Cancellable.
 *
 * <p>Because regions are shared by key, deleting one can break consumers that had nothing to do with
 * the deletion. A consumer that cannot function without a given region may cancel here; one that can
 * degrade gracefully should instead just drop its reference.
 *
 * <p>To find out who would be affected <em>before</em> deleting, fire a {@link RegionUsageQueryEvent}
 * and show the answers.
 */
public class RegionDeleteEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private boolean cancelled;

    public RegionDeleteEvent(@NotNull Region region) {
        this.region = region;
    }

    @NotNull
    public Region region() {
        return region;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
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
