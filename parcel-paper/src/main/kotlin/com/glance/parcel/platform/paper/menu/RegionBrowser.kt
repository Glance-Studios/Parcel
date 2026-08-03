package com.glance.parcel.platform.paper.menu

import com.glance.parcel.api.region.Region
import com.glance.parcel.api.region.RegionManager
import com.glance.parcel.platform.paper.command.Keys
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
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

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

    fun open(player: Player, page: Int = 0) {
        val all = regions.all().sortedBy { it.key().toString() }
        if (all.isEmpty()) {
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

        shown.forEachIndexed { index, region -> inventory.setItem(index, icon(region)) }

        if (clamped > 0) inventory.setItem(PREV_SLOT, nav("<gray>Previous page"))
        if (clamped < pages - 1) inventory.setItem(NEXT_SLOT, nav("<gray>Next page"))

        player.openInventory(inventory)
    }

    private fun icon(region: Region): ItemStack {
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
        lore += line("<gray>Left <dark_gray>teleport")
        // Soft red rather than the usual grey: this is the one entry whose action changes meaning
        // depending on state, so it should not read identically in both.
        lore += if (showing) {
            line("<gray>Right <color:#e57373>hide")
        } else {
            line("<gray>Right <dark_gray>render")
        }
        lore += line("<gray>Shift-left <dark_gray>style")
        lore += line("<gray>Shift-right <dark_gray>delete")

        return ItemStack(material).apply {
            editMeta { meta ->
                meta.displayName(mm.deserialize("<!italic><aqua>${Keys.display(region.key())}"))
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
                // Routed through the existing guarded command rather than deleting here, so the
                // usage warning and the confirm step are not duplicated in a second code path.
                player.closeInventory()
                player.performCommand("parcel delete ${Keys.display(key)}")
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
                player.closeInventory()
                player.performCommand("parcel goto ${Keys.display(key)}")
            }
        }
    }

    private fun line(raw: String) = mm.deserialize("<!italic>$raw")

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
        const val ROWS = 6
        const val PER_PAGE = 45
        const val PREV_SLOT = 48
        const val NEXT_SLOT = 50
    }
}
