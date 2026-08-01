package com.glance.parcel.api.event;

import com.glance.parcel.api.region.Region;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks, on demand, "who is using this region?".
 *
 * <p>Deliberately a question rather than a registry. A registry of usages would need registering,
 * unregistering, and cleaning up after reloads, and would be quietly wrong whenever a consumer
 * forgot a step. Asking at the moment the answer is needed cannot go stale.
 *
 * <p>Listeners answer by calling {@link #addUsage(String)} with something a human can act on:
 *
 * <pre>{@code
 * @EventHandler
 * public void onQuery(RegionUsageQueryEvent event) {
 *     motifs.boundTo(event.region().key())
 *         .forEach(m -> event.addUsage("Motif: ambience '" + m.id() + "'"));
 * }
 * }</pre>
 */
public class RegionUsageQueryEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Region region;
    private final List<String> usages = new ArrayList<>();

    public RegionUsageQueryEvent(@NotNull Region region) {
        this.region = region;
    }

    @NotNull
    public Region region() {
        return region;
    }

    /**
     * Declares that the calling plugin depends on this region.
     *
     * @param description human-readable, shown in confirmation prompts
     */
    public void addUsage(@NotNull String description) {
        usages.add(description);
    }

    /**
     * @return every declared usage, empty if nothing depends on this region
     */
    @NotNull
    @Unmodifiable
    public List<String> usages() {
        return List.copyOf(usages);
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
