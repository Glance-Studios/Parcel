package com.glance.parcel.api.region;

/**
 * What a {@link Part} does to the region it belongs to.
 */
public enum Op {

    /**
     * Blocks covered by this part become part of the region.
     */
    ADD,

    /**
     * Blocks covered by this part are removed from the region.
     */
    SUBTRACT
}
