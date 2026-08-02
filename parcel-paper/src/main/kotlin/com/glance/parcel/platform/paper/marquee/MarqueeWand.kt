package com.glance.parcel.platform.paper.marquee

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.NamespacedKey
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
) {

    private val tag = NamespacedKey(plugin, "marquee_wand")

    fun create(): ItemStack {
        val item = ItemStack(material)
        item.editMeta { meta ->
            meta.persistentDataContainer.set(tag, PersistentDataType.BYTE, 1)
            meta.displayName(
                mm.deserialize("<!italic><aqua>Marquee")
            )
            meta.lore(
                listOf(
                    line("<dark_gray>Parcel selection tool"),
                    Component.empty(),
                    line("<gray>Left click <dark_gray>- corner 1"),
                    line("<gray>Right click <dark_gray>- corner 2"),
                    line("<gray>Sneak + left <dark_gray>- <green>add<dark_gray> the marked box"),
                    line("<gray>Sneak + right <dark_gray>- <red>carve<dark_gray> the marked box"),
                    Component.empty(),
                    line("<dark_gray>Then"),
                    line("<gray>/mq save <name> <dark_gray>- keep it as a region"),
                    line("<gray>/mq apply <name> <dark_gray>- reshape an existing one"),
                    line("<gray>/mq undo <dark_gray>- drop the last part"),
                    line("<gray>/mq deselect <dark_gray>- start over"),
                    Component.empty(),
                    line("<dark_gray>Run <gray>/mq<dark_gray> for everything else"),
                )
            )
        }
        return item
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
