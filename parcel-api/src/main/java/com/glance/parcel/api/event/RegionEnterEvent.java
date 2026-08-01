package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player enters a region.
 *
 * <p>Parcel tracks region membership centrally, once per tick for all players, so consumers never
 * need their own containment loop.
 */
public class RegionEnterEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;

    public RegionEnterEvent(@NotNull Player player, @NotNull Region region) {
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
