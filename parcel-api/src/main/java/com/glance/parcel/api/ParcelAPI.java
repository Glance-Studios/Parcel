package com.glance.parcel.api;

import com.glance.parcel.api.region.RegionManager;
import com.glance.parcel.api.selection.SelectionManager;
import org.jetbrains.annotations.NotNull;

/**
 * The Parcel service, registered with Bukkit's ServicesManager.
 *
 * @see com.glance.parcel.Parcel#api()
 */
public interface ParcelAPI {

    /**
     * @return saved region storage and lookup
     */
    @NotNull
    RegionManager regions();

    /**
     * @return in-progress player selections made with the marquee tool
     */
    @NotNull
    SelectionManager selections();

    /**
     * @return the API version, for consumers that want to guard against drift
     */
    @NotNull
    String apiVersion();
}
