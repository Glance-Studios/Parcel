package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player leaves a region.
 *
 * <p>Also fired when a player quits, or when a region they were inside is deleted, so a consumer
 * holding per-player state always gets a matching exit for every enter.
 */
public class RegionExitEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;

    public RegionExitEvent(@NotNull Player player, @NotNull Region region) {
        super(player);
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
