package com.glance.parcel.platform.paper.marquee

import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Op
import com.glance.parcel.platform.paper.command.Text
import com.glance.parcel.platform.paper.selection.SelectionImpl
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.Plugin

/**
 * Turns wand clicks into selection edits.
 *
 * Every path here calls the same methods `/mq pos1`, `/mq add` and friends already call, so the wand
 * adds an input surface rather than a second implementation.
 *
 * Bindings follow WorldEdit's wand, so there is nothing new to learn:
 * - left click a **block** marks corner 1, right click a **block** marks corner 2
 * - sneak + click commits the marked box - left to add, right to carve - **pointing anywhere**
 *
 * The asymmetry is deliberate. Marking a corner needs a block because there is nothing else to
 * mark, but committing does not: you are normally stood inside the box you just outlined, with
 * nothing in reach, so requiring a block there would make the binding useless.
 *
 * Ray-tracing air clicks to mark distant corners was tried and removed - left clicks on air are
 * unreliable server-side, so the two buttons behaved differently at range.
 */
internal class MarqueeListener(
    private val plugin: Plugin,
    private val wand: MarqueeWand,
    private val selections: SelectionManagerImpl,
    private val debug: Boolean = false,
) : Listener {

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    fun onInteract(event: PlayerInteractEvent) {
        // Off-hand fires a second event for the same click, so drop that one - but only that one.
        // Testing `== HAND` instead silently swallows left-click-air, which arrives with a null
        // hand, and cost an afternoon of blaming Minecraft for not sending the packet.
        if (event.hand == EquipmentSlot.OFF_HAND) return
        if (!wand.isWand(event.item)) return

        val player = event.player
        if (!player.hasPermission(PERMISSION)) return

        if (debug) {
            plugin.logger.info(
                "marquee: action=${event.action} hand=${event.hand} " +
                    "sneaking=${player.isSneaking} block=${event.clickedBlock?.location?.toVector()}"
            )
        }

        // Claim the click regardless of outcome, so holding the wand never breaks or places blocks.
        event.isCancelled = true

        val left = when (event.action) {
            Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR -> true
            Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR -> false
            else -> return // PHYSICAL, i.e. pressure plates
        }

        val selection = selections.get(player)

        // Committing must work pointing at nothing. You are usually stood inside the box you just
        // marked, with no block in reach - requiring one to sneak-commit defeats the binding.
        if (player.isSneaking) {
            commit(player, selection, if (left) Op.ADD else Op.SUBTRACT)
            return
        }

        // Marking a corner does need a block, WorldEdit style - there is nothing to mark otherwise.
        val target = event.clickedBlock ?: return
        val pos = BlockPos(target.x, target.y, target.z)
        if (left) {
            selection.setCornerA(pos)
            Text.send(player, "<gray>Corner 1 at <white>${pos.x()}, ${pos.y()}, ${pos.z()}")
        } else {
            selection.setCornerB(pos)
            Text.send(player, "<gray>Corner 2 at <white>${pos.x()}, ${pos.y()}, ${pos.z()}")
        }
        wand.refresh(player)
    }

    private fun commit(player: Player, selection: SelectionImpl, op: Op) {
        val part = selection.commitPending(op)
        if (part == null) {
            Text.error(player, "Mark both corners first - left click, then right click.")
            return
        }

        val verb = if (op == Op.ADD) "<green>Added" else "<red>Carved"
        val box = part.shape().bounds()
        Text.send(
            player,
            "$verb <white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()}<gray>. " +
                "Selection now has <white>${selection.parts().size}<gray> part(s).",
        )
        // Committing consumes the corners, so the label has to drop back to "nothing marked".
        wand.refresh(player)
    }

    private companion object {
        const val PERMISSION = "parcel.edit"
    }
}
