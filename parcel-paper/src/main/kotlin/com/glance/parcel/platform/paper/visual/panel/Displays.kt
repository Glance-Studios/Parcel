package com.glance.parcel.platform.paper.visual.panel

import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Display
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * The only place Parcel spawns a display entity.
 *
 * Everything Parcel draws is a **view**, never world content: outlines, panels, calibration props.
 * None of it should ever be written into a world save. Relying on each spawn site to remember
 * `isPersistent = false` works right up until someone adds a fifth one, and the failure is silent -
 * you find out when a server has thousands of orphaned displays in its region files.
 *
 * So spawning goes through here, and the flag is applied **after** the caller's setup, where it
 * cannot be overridden by accident.
 *
 * Every display is also tagged, which is what makes [sweep] able to find strays later.
 */
internal class Displays(private val plugin: Plugin) {

    private val tag = NamespacedKey(plugin, "display")

    fun <T : Display> spawn(location: Location, type: Class<T>, init: (T) -> Unit): T {
        val world = location.world ?: error("Cannot spawn a display at a location with no world")
        return world.spawn(location, type) { display ->
            init(display)
            // Last, and unconditionally: whatever the caller did, this holds.
            display.isPersistent = false
            display.persistentDataContainer.set(tag, PersistentDataType.BYTE, 1)
        }
    }

    /**
     * Removes any Parcel displays still standing in loaded worlds.
     *
     * Non-persistent entities do not survive a restart, so this is really about a plugin reload:
     * disable leaves the world loaded, and anything not cleaned up would linger with nothing left
     * to own it. Cheap insurance, and the tag makes it exact - it can only ever remove ours.
     *
     * @return how many were removed
     */
    fun sweep(): Int {
        var removed = 0
        for (world in plugin.server.worlds) {
            world.getEntitiesByClass(Display::class.java)
                .filter { it.persistentDataContainer.has(tag, PersistentDataType.BYTE) }
                .forEach {
                    it.remove()
                    removed++
                }
        }
        return removed
    }
}
