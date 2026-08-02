package com.glance.parcel.platform.paper.visual.panel

/**
 * Which calibration tool a step-size change applies to.
 *
 * They are set independently because they work at different magnitudes - scale moves in multiples
 * of a small base quad, offsets in fractions of a block - so one shared step is always too coarse
 * for one of them and too fine for the other.
 */
internal enum class StepTarget {
    SCALE,
    MOVE,
    BOTH,
}
