package com.glance.parcel.platform.paper.region

import com.glance.parcel.api.math.BlockBox
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Part

/**
 * The whole of Parcel's geometry semantics, in two functions.
 *
 * Kept free of Bukkit so it can be unit tested directly, and so the marquee tool can preview an
 * uncommitted part list without building a Region first.
 */

/**
 * Ordered fold, last writer wins: walk the parts in order, and every time one covers this block the
 * result becomes whatever that part says. So a subtract after an add carves, and an add after that
 * fills the carve back in - no special cases, no ordering rules to remember beyond "later wins".
 */
internal fun List<Part>.evaluate(x: Int, y: Int, z: Int): Boolean {
    var inside = false
    for (part in this) {
        if (part.shape().contains(x, y, z)) {
            inside = part.op() == Op.ADD
        }
    }
    return inside
}

/**
 * Union of the ADD parts' bounds, or null if there are none.
 *
 * Subtractive parts are excluded deliberately: a subtraction can never make a region larger, so
 * including it would inflate the box every containment test rejects against.
 */
internal fun List<Part>.additiveBounds(): BlockBox? = this
    .asSequence()
    .filter { it.op() == Op.ADD }
    .map { it.shape().bounds() }
    .reduceOrNull { acc, box -> acc.union(box) }
