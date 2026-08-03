package com.glance.parcel.platform.paper

import com.glance.parcel.api.region.Part
import com.glance.parcel.api.region.Region
import org.bukkit.NamespacedKey
import org.bukkit.World

/**
 * One step of undo for deletion.
 *
 * Deleting a region unlinks its file and wipes its ten-step history, so before this there was
 * nothing between a mistyped name and losing a shape someone spent real time carving. This keeps
 * the last one deleted, so that mistake costs a command rather than an afternoon.
 *
 * **Transient on purpose.** It lives in memory and does not survive a restart. A deletion that is
 * still recoverable an hour later is really a soft-delete, and a soft-delete that nothing ever
 * empties is a growing pile of files nobody knows about. This is a safety net for the seconds after
 * a slip, and it says so when it offers itself.
 *
 * One slot, server-wide rather than per player: deletion is global - regions are shared geometry -
 * so the undo should be too. Whoever notices first can put it back.
 */
internal object DeletedRegions {

    /** Enough to rebuild the region. Parts are immutable, so holding them is safe. */
    data class Snapshot(val key: NamespacedKey, val world: World, val parts: List<Part>)

    private var last: Snapshot? = null

    /** Called immediately before the region goes, while it can still be read. */
    fun remember(region: Region) {
        last = Snapshot(region.key(), region.world(), region.parts().toList())
    }

    /** What is currently recoverable, without consuming it. */
    fun peek(): Snapshot? = last

    /**
     * Take the snapshot, clearing it.
     *
     * Consumed rather than kept so restoring twice cannot silently recreate a region someone
     * deliberately deleted again afterwards.
     */
    fun take(): Snapshot? {
        val snapshot = last
        last = null
        return snapshot
    }

    /** The region came back some other way, so there is nothing to restore. */
    fun forget(key: NamespacedKey) {
        if (last?.key == key) last = null
    }
}
