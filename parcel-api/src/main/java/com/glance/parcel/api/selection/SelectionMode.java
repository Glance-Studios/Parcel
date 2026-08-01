package com.glance.parcel.api.selection;

/**
 * Which kind of shape the marquee tool produces on its next commit.
 */
public enum SelectionMode {

    /**
     * A horizontal footprint spanning the full world height. Y is ignored when marking corners.
     */
    FLAT,

    /**
     * A fully bounded box. Both corners' Y values are used.
     */
    VOLUME
}
