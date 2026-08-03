package com.glance.parcel.platform.paper

import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.api.render.RenderManager
import com.glance.parcel.platform.paper.visual.panel.PanelRenderer
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player

/**
 * Exposes [PanelRenderer] to other plugins as set-this-state rather than flip-it.
 *
 * Deliberately narrower than the renderer itself: styles, calibration and the follow task stay
 * internal, because a consumer that could reach those would be depending on decisions Parcel needs
 * to keep changing.
 */
internal class RenderManagerImpl(
    private val panels: PanelRenderer,
    private val regions: RegionManager,
) : RenderManager {

    override fun isRendering(region: NamespacedKey): Boolean {
        // Resolved through the manager rather than asked of the renderer directly, because
        // isShowing takes a Region and a key for a deleted region has nothing to look up.
        val resolved = regions.get(region) ?: return false
        return panels.isShowing(resolved)
    }

    override fun render(region: Region, viewer: Player?): Int =
        panels.show(region, viewer = viewer)

    override fun hide(region: NamespacedKey): Boolean {
        val resolved = regions.get(region)
        val wasShowing = resolved != null && panels.isShowing(resolved)
        // Hidden regardless of whether the region still resolves: a render outliving its region is
        // exactly the case where a caller most needs to be able to clear it.
        panels.hide(region)
        return wasShowing
    }

    override fun hideAll() {
        panels.hideAll()
    }
}
