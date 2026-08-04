package com.glance.parcel.platform.paper.menu

import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.platform.paper.command.Keys
import com.glance.parcel.platform.paper.MarkedRegions
import com.glance.parcel.platform.paper.command.Text
import com.glance.parcel.platform.paper.visual.panel.GlassPalette
import com.glance.parcel.platform.paper.visual.panel.PanelRenderer
import com.glance.parcel.platform.paper.visual.panel.PanelStyleDialog
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.util.UUID

/**
 * Browser for every saved region.
 *
 * Lives in Parcel rather than in consumers because it is inherently cross-consumer: a builder wants
 * to see every region in the world regardless of which plugin uses it, and only Parcel can answer
 * "who depends on this" via [RegionManager.usagesOf]. A consumer building its own browser could
 * only ever show its own slice.
 *
 * Consumers still own their *feature* menus - Motif listing its ambience zones and which region
 * each is bound to is a Motif concern. The test is whether the thing being listed is geometry or a
 * feature bound to geometry.
 *
 * Deliberately a plain Bukkit inventory, no GUI library, so an open-source jar carries no extra
 * dependency for one screen. Same call Cinematic Engine made for the same reason.
 */
internal class RegionBrowser(
    private val plugin: Plugin,
    private val regions: RegionManager,
    private val panels: PanelRenderer,
    private val styleDialog: PanelStyleDialog,
) : Listener {

    /**
     * Identified by its holder, never by title - a title can be spoofed by any other inventory.
     *
     * ⚠️ The field must NOT be called `inventory`: in Kotlin that clashes with `getInventory()` at
     * the JVM signature level and fails with a confusing error.
     */
    private class Holder(val page: Int, val keys: List<NamespacedKey>) : InventoryHolder {
        lateinit var view: Inventory
        override fun getInventory(): Inventory = view
    }

    /**
     * Whose delete is one click from happening, and on what.
     *
     * The confirm has to live in the menu rather than in chat: chat links cannot be clicked with an
     * inventory open, so routing the prompt out to chat meant closing the screen you were working
     * in. Armed against a specific key, so a stale arm can never delete a region you have since
     * scrolled to.
     */
    private val armed = HashMap<UUID, NamespacedKey>()

    fun open(player: Player, page: Int = 0) {
        val all = regions.all().sortedBy { it.key().toString() }
        if (all.isEmpty()) {
            // This is also the refresh path, so the menu may be open with the entry that was just
            // deleted still in it. Returning without closing left that stale item on screen,
            // clickable, for a region that no longer exists.
            if (player.openInventory.topInventory.holder is Holder) player.closeInventory()
            Text.error(player, "There are no regions yet. Build a selection and /parcel save one.")
            return
        }

        val pages = (all.size + PER_PAGE - 1) / PER_PAGE
        val clamped = page.coerceIn(0, pages - 1)
        val shown = all.drop(clamped * PER_PAGE).take(PER_PAGE)

        val holder = Holder(clamped, shown.map { it.key() })
        val inventory = plugin.server.createInventory(
            holder,
            ROWS * 9,
            mm.deserialize("<dark_gray>Regions <gray>(${clamped + 1}/$pages)"),
        )
        holder.view = inventory

        val pending = armed[player.uniqueId]
        // Read on every open and every refresh, so the menu always agrees with what the wand says.
        val marked = MarkedRegions.of(player)
        shown.forEachIndexed { index, region ->
            inventory.setItem(
                index,
                icon(region, armed = region.key() == pending, marked = region.key() == marked),
            )
        }

        if (clamped > 0) inventory.setItem(PREV_SLOT, nav("<gray>Previous page"))
        if (clamped < pages - 1) inventory.setItem(NEXT_SLOT, nav("<gray>Next page"))

        player.openInventory(inventory)
    }

    private fun icon(region: Region, armed: Boolean, marked: Boolean): ItemStack {
        val showing = panels.isShowing(region)
        val style = panels.styleFor(region)
        // Pane matching how the region renders, so the menu reads the same as the world.
        val material = runCatching {
            Material.valueOf(GlassPalette.nearest(style.colour).name + "_PANE")
        }.getOrElse { Material.WHITE_STAINED_GLASS_PANE }

        val box = region.bounds()
        val lore = mutableListOf(
            line("<dark_gray>${region.world().name}"),
            Component.empty(),
        )

        if (region.isEmpty()) {
            lore += line("<red>Empty <dark_gray>- no additive parts")
        } else {
            lore += line("<gray>Size <white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()}")
            lore += line("<gray>Parts <white>${region.parts().size}<dark_gray>  " +
                "<gray>Blocks <white>${box.volume()}")
            runCatching { region.mesh().size }
                .onSuccess { lore += line("<gray>Faces <white>$it") }
        }
        if (region.isTransient()) lore += line("<yellow>Transient <dark_gray>- not saved to disk")

        val usages = regions.usagesOf(region)
        if (usages.isNotEmpty()) {
            lore += Component.empty()
            lore += line("<gray>Used by")
            usages.forEach { lore += line("  <dark_gray>- <gray>$it") }
        }

        lore += Component.empty()
        // Marked but not drawn is a real state - something else, usually Motif, hid it out from
        // under the mark. The click that gets it back has to be the one offered, or the entry sends
        // you to unmark the very thing you are trying to see.
        lore += when {
            marked && showing -> line("<gray>Left <dark_gray>unmark it")
            marked -> line("<gray>Left <color:#e57373>show it again")
            else -> line("<gray>Left <dark_gray>mark it")
        }
        // Soft red rather than the usual grey: this is the one entry whose action changes meaning
        // depending on state, so it should not read identically in both.
        lore += if (showing) {
            line("<gray>Right <color:#e57373>hide")
        } else {
            line("<gray>Right <dark_gray>render")
        }
        lore += line("<gray>Shift-left <dark_gray>style")
        lore += if (armed) {
            // Plain red, not the soft red used to *start* a delete. This click is the deletion.
            line("<red>Shift-right again to delete")
        } else {
            line("<gray>Shift-right <dark_gray>delete")
        }

        return ItemStack(material).apply {
            editMeta { meta ->
                // Bold, and on the name rather than in the lore: which region you are working on
                // has to be answerable from the grid without reading anything.
                val label = if (marked) {
                    "<aqua>${Keys.display(region.key())} <green><b>Marked</b>"
                } else {
                    "<aqua>${Keys.display(region.key())}"
                }
                meta.displayName(mm.deserialize("<!italic>$label"))
                meta.lore(lore)
                // Glint marks what is currently drawn in the world, so a glance at the menu
                // answers "what have I got showing" without reading every entry's lore.
                meta.setEnchantmentGlintOverride(showing)
            }
        }
    }

    private fun nav(label: String) = ItemStack(Material.ARROW).apply {
        editMeta { it.displayName(mm.deserialize("<!italic>$label")) }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? Holder ?: return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot

        // Any click at all disarms. Only the branch below re-arms, so a pending delete never
        // survives navigating away, styling something, or clicking the same entry a different way.
        val wasArmed = armed.remove(player.uniqueId)

        when (slot) {
            PREV_SLOT -> return open(player, holder.page - 1)
            NEXT_SLOT -> return open(player, holder.page + 1)
        }

        val key = holder.keys.getOrNull(slot) ?: return
        val region = regions.get(key) ?: run {
            Text.error(player, "That region no longer exists.")
            return open(player, holder.page)
        }

        when {
            event.isShiftClick && event.isRightClick -> {
                // Nothing depends on it, so there is nothing to warn about and the confirm would be
                // friction that teaches people to click through warnings. Same rule the command
                // follows, kept in step deliberately.
                val needsConfirm = regions.usagesOf(region).isNotEmpty()

                if (wasArmed == key || !needsConfirm) {
                    // Still routed through the guarded command, so the snapshot for /parcel restore
                    // and the unmarking are not duplicated in a second code path.
                    player.performCommand("parcel delete ${Keys.display(key)} confirm")
                } else {
                    // Who uses it is already on the item, so arming just has to say what a second
                    // click will do.
                    armed[player.uniqueId] = key
                }
                open(player, holder.page)
            }

            event.isShiftClick -> {
                player.closeInventory()
                styleDialog.open(player, region)
            }

            event.isRightClick -> {
                if (panels.isShowing(region)) {
                    panels.hide(key)
                } else {
                    panels.show(region, viewer = player)
                }
                open(player, holder.page) // redraw so the lore reflects the new state
            }

            else -> {
                // Marking is what you almost always came here for - every other command then
                // defaults to it. A second click lets go of it again, so the same button both
                // takes and releases and there is no separate screen for the other half.
                // Unmark only when it is marked AND actually on screen. Marked but hidden means
                // something took the render away, and mark() re-renders unconditionally - so
                // falling through re-asserts the state rather than throwing the mark away too.
                if (MarkedRegions.of(player) == key && panels.isShowing(region)) {
                    player.performCommand("parcel unmark")
                    open(player, holder.page)
                    return
                }

                // Teleporting was the old behaviour and is now a consequence rather than the
                // action: if it is too far away to look at, marking it alone is useless.
                val far = region.world() != player.world || distanceTo(region, player) > GOTO_RANGE
                player.performCommand("parcel mark ${Keys.display(key)}")

                if (far) {
                    player.closeInventory()
                    player.performCommand("parcel goto ${Keys.display(key)}")
                } else {
                    // Still on screen, so redraw - the name and the glint both just changed.
                    open(player, holder.page)
                }
            }
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        armed.remove(event.player.uniqueId)
    }

    /**
     * Horizontal distance to the region's nearest edge, not to its centre.
     *
     * Centre distance would call a large region "far away" while you were stood inside it, and
     * teleport you out of the thing you were already looking at.
     */
    private fun distanceTo(region: Region, player: Player): Double {
        val box = region.bounds()
        val at = player.location
        val dx = maxOf(box.min().x() - at.x, 0.0, at.x - (box.max().x() + 1.0))
        val dz = maxOf(box.min().z() - at.z, 0.0, at.z - (box.max().z() + 1.0))
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    private fun line(raw: String) = mm.deserialize("<!italic>$raw")

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
        const val ROWS = 6
        const val PER_PAGE = 45
        const val PREV_SLOT = 48
        const val NEXT_SLOT = 50

        /**
         * Past this many blocks from the region's edge, marking also flies you there.
         *
         * Far enough that it never fires while you are working near something, close enough that
         * marking a region you cannot see does not leave you looking at nothing.
         */
        const val GOTO_RANGE = 100.0
    }
}
