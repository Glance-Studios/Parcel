package com.glance.parcel.platform.paper.visual.panel

/**
 * Which display entity a panel is made of.
 *
 * Both exist on purpose: [BLOCK] is exact and needs no calibration, so it is the ground truth to
 * measure [TEXT] against. Render the same region as both and tune the text constants until they
 * line up.
 */
internal enum class PanelPrimitive {

    /**
     * A stained-glass block display scaled thin.
     *
     * Real geometry, so it is two-sided for free and its scale maps 1:1 onto blocks. Limited to the
     * stained-glass palette, and its translucency comes from the block texture.
     */
    BLOCK,

    /**
     * A pair of text displays with an empty string and a translucent background.
     *
     * Arbitrary ARGB, so any colour at any alpha. Costs two entities per quad, because a text
     * display renders from one side only - the pair is front plus a copy rotated 180 degrees about
     * its own local Y, which is the technique from Shaded's `MirroredTextPanel`.
     */
    TEXT,
}
