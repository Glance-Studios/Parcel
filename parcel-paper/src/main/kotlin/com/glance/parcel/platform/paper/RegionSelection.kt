package com.glance.parcel.platform.paper

import com.glance.parcel.api.region.Region
import com.glance.parcel.platform.paper.marquee.MarqueeWand
import com.glance.parcel.platform.paper.visual.panel.PanelRenderer
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

/**
 * Selecting and deselecting a region, and everything that has to happen alongside it.
 *
 * Exists because "selected" is not one fact. It is a pointer, a render, and the label on the wand,
 * and there are several ways in - creating a region, selecting one by name, deleting the one you
 * had. When each caller did its own bookkeeping they drifted: deselect hid the panels but select
 * did not show them, and nothing updated the wand at all.
 *
 * So every route goes through here, and the three move together.
 */
internal class RegionSelection(
    private val panels: PanelRenderer,
    private val wand: MarqueeWand,
) {

    /**
     * Select a region, rendering it if it was not already drawn.
     *
     * Rendering is deliberately not conditional on it being off - `show` replaces any existing
     * render, and re-showing a region already drawn is how a stale one gets refreshed.
     *
     * @return panels spawned, so callers do not have to render a second time to find out
     */
    fun select(player: Player, region: Region): Int {
        ActiveRegions.set(player, region.key())
        val panels = panels.show(region, viewer = player)
        wand.refresh(player)
        return panels
    }

    /**
     * Clear the selection, hiding whatever it was drawing.
     *
     * @return the key that was cleared, or null if nothing was selected
     */
    fun deselect(player: Player): NamespacedKey? {
        val key = ActiveRegions.clear(player) ?: return null
        panels.hide(key)
        wand.refresh(player)
        return key
    }

    /** The region went away. Anyone working on it stops, and their panels come down with it. */
    fun forget(key: NamespacedKey) {
        ActiveRegions.forget(key)
        panels.hide(key)
    }

    /** Nudge the wand label after a corner is marked or a part committed. */
    fun refreshWand(player: Player) = wand.refresh(player)
}
