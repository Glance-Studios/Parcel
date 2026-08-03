package com.glance.parcel.platform.paper.marquee

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import com.glance.parcel.platform.paper.MarkedRegions
import com.glance.parcel.platform.paper.command.Keys
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin

/**
 * The marquee wand item.
 *
 * Identified by a tag in its [org.bukkit.persistence.PersistentDataContainer], never by material or
 * display name - so a renamed item cannot impersonate it, and a builder holding an ordinary axe is
 * unaffected.
 *
 * Deliberately not a wooden axe: that is WorldEdit's wand, and on a server running both, every click
 * would be claimed twice.
 */
internal class MarqueeWand(
    private val plugin: Plugin,
    private val material: Material,
    private val selections: SelectionManagerImpl,
) {

    private val tag = NamespacedKey(plugin, "marquee_wand")

    fun create(): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.persistentDataContainer.set(tag, PersistentDataType.BYTE, 1)
            meta.displayName(mm.deserialize("<!italic><aqua>Marquee"))
            meta.lore(
                listOf(
                    line("<dark_gray>Parcel selection tool"),
                    Component.empty(),
                    line("<gray>Left click <dark_gray>- corner 1"),
                    line("<gray>Right click <dark_gray>- corner 2"),
                    line("<gray>Sneak + left <dark_gray>- <green>add<dark_gray> the marked box"),
                    line("<gray>Sneak + right <dark_gray>- <red>carve<dark_gray> the marked box"),
                    Component.empty(),
                    // Sits with the controls rather than under "everything else": mode decides
                    // whether the box has a top and bottom at all, so it is a thing you set before
                    // marking, not a thing you look up afterwards.
                    line("<gray>/mq mode <dark_gray>- <green>flat<dark_gray> footprint, or"),
                    line("<dark_gray>  <green>volume<dark_gray> using both corners' Y"),
                    Component.empty(),
                    line("<dark_gray>Save it as"),
                    line("<gray>/mq save <name> <dark_gray>- a new region"),
                    Component.empty(),
                    line("<dark_gray>Or change one that exists"),
                    line("<gray>/mq append <name> <dark_gray>- ADD this to it, e.g. a carve"),
                    line("<gray>/mq apply <name> <dark_gray>- REPLACE its shape with this"),
                    line("<gray>/mq load <name> <dark_gray>- copy it in here to edit"),
                    line("<dark_gray>  load, edit, apply is the round trip"),
                    Component.empty(),
                    line("<dark_gray>Fixing up"),
                    line("<gray>/mq undo <dark_gray>- drop the last part"),
                    line("<gray>/mq cancel <dark_gray>- unmark the corners, keep the parts"),
                    line("<gray>/mq deselect <dark_gray>- start over"),
                    Component.empty(),
                    line("<dark_gray>Saving clears the selection. Nothing is pending."),
                    line("<dark_gray>Run <gray>/mq<dark_gray> for everything else"),
                )
            )
        }
        return item
    }

    /**
     * Rewrite the label on every wand this player is carrying.
     *
     * The wand is the thing already in their hand, so it is the cheapest place to answer "what am I
     * doing right now" - no command, no glance at chat. It reads either the region they have marked,
     * or how far through selecting a box they are.
     *
     * Rewrites in place rather than replacing the stack, so it never disturbs the hotbar slot or
     * drops anything, and it is safe to call on every click.
     */
    fun refresh(player: Player) {
        val label = labelFor(player)
        for (item in player.inventory.contents) {
            if (!isWand(item)) continue
            item!!.editMeta { meta -> meta.displayName(mm.deserialize("<!italic>$label")) }
        }
    }

    private fun labelFor(player: Player): String {
        MarkedRegions.of(player)?.let { key ->
            return "<aqua>Marquee <dark_gray>- <gray>marked <white>${Keys.display(key)}"
        }

        val selection = selections.of(player)
        val a = selection?.pendingA()
        val b = selection?.pendingB()

        val state = when {
            // Both corners down: the size is what you actually want to know before committing.
            a != null && b != null -> {
                val x = kotlin.math.abs(a.x() - b.x()) + 1
                val y = kotlin.math.abs(a.y() - b.y()) + 1
                val z = kotlin.math.abs(a.z() - b.z()) + 1
                "<white>${x}x${y}x${z}"
            }

            a != null -> "<gray>corner 1 <dark_gray>set"
            b != null -> "<gray>corner 2 <dark_gray>set"
            else -> "<dark_gray>nothing marked"
        }
        return "<aqua>Marquee <dark_gray>- <gray>selecting <dark_gray>... $state"
    }

    fun isWand(item: ItemStack?): Boolean {
        val meta = item?.itemMeta ?: return false
        return meta.persistentDataContainer.has(tag, PersistentDataType.BYTE)
    }

    private fun line(raw: String) = mm.deserialize("<!italic>$raw")

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
    }
}
