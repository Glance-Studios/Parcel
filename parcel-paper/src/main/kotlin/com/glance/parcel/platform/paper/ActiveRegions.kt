package com.glance.parcel.platform.paper

import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The region a player is currently working on.
 *
 * Creating one selects it. Everything you are likely to do next - render it, recolour it, pause its
 * plane, fly back to it - then refers to "the one I just made" rather than making you retype a name
 * you have only just invented.
 *
 * Deliberately a pointer and nothing more. It confers no lock and no ownership: two builders can
 * have the same region selected, and selecting one does not stop anyone else editing it. Regions
 * are shared geometry, and a selection that implied otherwise would be lying.
 *
 * Cleared by `/parcel deselect`, by deleting the region, and on quit.
 */
internal object ActiveRegions : Listener {

    private val active = ConcurrentHashMap<UUID, NamespacedKey>()

    fun set(player: Player, key: NamespacedKey) {
        active[player.uniqueId] = key
    }

    fun of(player: Player): NamespacedKey? = active[player.uniqueId]

    /** @return the key that was cleared, or null if nothing was selected */
    fun clear(player: Player): NamespacedKey? = active.remove(player.uniqueId)

    /** Drop this key for everyone - for when the region itself goes away. */
    fun forget(key: NamespacedKey) {
        active.entries.removeIf { it.value == key }
    }

    fun forget(player: Player) {
        active.remove(player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        forget(event.player)
    }
}
