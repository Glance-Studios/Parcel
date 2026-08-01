package com.glance.parcel.platform.paper

import com.glance.parcel.api.ParcelAPI
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.api.selection.SelectionManager

internal class ParcelAPIImpl(
    private val regions: RegionManager,
    private val selections: SelectionManager,
) : ParcelAPI {

    override fun regions(): RegionManager = regions

    override fun selections(): SelectionManager = selections

    override fun apiVersion(): String = API_VERSION

    companion object {
        const val API_VERSION = "0.1.0"
    }
}
