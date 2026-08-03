package com.glance.parcel.platform.paper

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The region a player is currently working on - the one they have *marked*.
 *
 * Deliberately not called a selection. The marquee already owns that word for the transient corners
 * and parts a builder is assembling, and having two kinds of "selected" - one you are drawing, one
 * you have saved - was a collision waiting to be misread. You select with the tool; you mark a
 * region.
 *
 * Creating one marks it. Everything you are likely to do next - render it, recolour it, pause its
 * plane, fly back to it - then refers to "the one I just made" rather than making you retype a name
 * you have only just invented.
 *
 * Deliberately a pointer and nothing more. It confers no lock and no ownership: two builders can
 * have the same region selected, and selecting one does not stop anyone else editing it. Regions
 * are shared geometry, and a selection that implied otherwise would be lying.
 *
 * Cleared by `/parcel unmark`, by deleting the region, and on quit.
 */
internal object MarkedRegions : Listener {

    private val active = ConcurrentHashMap<UUID, NamespacedKey>()

    fun set(player: Player, key: NamespacedKey) {
        active[player.uniqueId] = key
    }

    fun of(player: Player): NamespacedKey? = active[player.uniqueId]

    /** @return the key that was cleared, or null if nothing was selected */
    fun clear(player: Player): NamespacedKey? = active.remove(player.uniqueId)

    /**
     * Drop this key for everyone - for when the region itself goes away.
     *
     * @return who had it marked, so their wands can be relabelled. Without this the label kept
     *   naming a region that no longer existed.
     */
    fun forget(key: NamespacedKey): Set<UUID> {
        val affected = active.entries.filter { it.value == key }.map { it.key }.toSet()
        affected.forEach(active::remove)
        return affected
    }

    fun forget(player: Player) {
        active.remove(player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        forget(event.player)
    }
}
