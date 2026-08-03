package com.glance.parcel.platform.paper.command

import com.glance.parcel.api.math.BlockPos
import com.glance.parcel.api.region.Op
import com.glance.parcel.api.selection.SelectionMode
import com.glance.parcel.platform.paper.marquee.MarqueeWand
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission

/**
 * The marquee: building up a selection.
 *
 * Every one of these works from where the player is standing, with no wand needed. That is
 * deliberate for now - it makes the whole system usable and testable before any tool or visualiser
 * exists, and the wand will end up calling exactly these paths.
 */
internal class MarqueeCommands(
    private val selections: SelectionManagerImpl,
    private val wand: MarqueeWand,
) {

    @Command("marquee|mq wand|tool")
    @Permission(PERMISSION)
    fun wand(player: Player) {
        val leftover = player.inventory.addItem(wand.create())
        if (leftover.isEmpty()) {
            Text.send(player, "<gray>Have a marquee. <dark_gray>Left click, right click, sneak to commit.")
            // The wand's own lore carries the rest, but nobody hovers an item they were just
            // handed - so point at it once, and at the guide for anyone who wants the concepts.
            Text.send(
                player,
                "<dark_gray>Hover it for the full controls, or read " +
                    "<click:run_command:'/parcel help'><aqua><u>/parcel help</u></aqua></click><dark_gray>.",
            )
        } else {
            // Never silently drop it on the floor - a wand in a lava pit is a confusing failure.
            Text.error(player, "Your inventory is full.")
        }
    }

    @Command("marquee|mq")
    @Permission(PERMISSION)
    fun help(player: Player) {
        Text.send(player, "<gray>Selection tool. Commands:")
        Text.raw(
            player,
            "  <aqua><click:run_command:'/parcel help'>/parcel help</click><gray> - " +
                "the full guide, as a book",
        )
        Text.raw(player, "  <aqua>/mq wand<gray> (or <aqua>tool<gray>) - get the wand")
        Text.raw(player, "  <aqua>/mq pos1<gray> and <aqua>/mq pos2<gray> - mark the two corners")
        Text.raw(player, "  <aqua>/mq mode <flat|volume><gray> - flat ignores Y and spans world height")
        Text.raw(player, "  <aqua>/mq add<gray> / <aqua>/mq carve<gray> - commit the marked box, or cut it away")
        Text.raw(player, "  <aqua>/mq undo<gray> - remove the last committed part")
        Text.raw(player, "  <aqua>/mq cancel<gray> - unmark the corners, keep the parts")
        Text.raw(player, "  <aqua>/mq clear<gray> / <aqua>deselect<gray> - empty it / drop it entirely")
        Text.raw(player, "  <aqua>/mq info<gray> - what is selected right now")
        Text.raw(player, "  <aqua>/mq save<gray> (or <aqua>create<gray>) <aqua><name><gray> - save as a new region")
        Text.raw(player, "  <aqua>/mq load <name><gray> - pull a region in here to edit it")
        Text.raw(player, "  <aqua>/mq apply <name><gray> - replace a region's shape with this")
        Text.raw(player, "  <aqua>/mq append <name><gray> - add this to it, e.g. carving into it")
    }

    @Command("marquee|mq pos1")
    @Permission(PERMISSION)
    fun pos1(player: Player) {
        val pos = BlockPos.of(player.location)
        selections.get(player).setCornerA(pos)
        Text.send(player, "<gray>Corner 1 at <white>${pos.x()}, ${pos.y()}, ${pos.z()}")
    }

    @Command("marquee|mq pos2")
    @Permission(PERMISSION)
    fun pos2(player: Player) {
        val pos = BlockPos.of(player.location)
        selections.get(player).setCornerB(pos)
        Text.send(player, "<gray>Corner 2 at <white>${pos.x()}, ${pos.y()}, ${pos.z()}")
    }

    @Command("marquee|mq mode <mode>")
    @Permission(PERMISSION)
    fun mode(player: Player, @Argument("mode") mode: SelectionMode) {
        selections.get(player).setMode(mode)
        val explanation = when (mode) {
            SelectionMode.FLAT -> "footprint only, spanning the full world height"
            SelectionMode.VOLUME -> "a bounded box, using both corners' Y"
        }
        Text.send(player, "<gray>Mode is now <white>${mode.name.lowercase()}<gray> - $explanation.")
    }

    @Command("marquee|mq add")
    @Permission(PERMISSION)
    fun add(player: Player) = commit(player, Op.ADD)

    @Command("marquee|mq carve")
    @Permission(PERMISSION)
    fun carve(player: Player) = commit(player, Op.SUBTRACT)

    private fun commit(player: Player, op: Op) {
        val selection = selections.get(player)
        val part = selection.commitPending(op)
        if (part == null) {
            Text.error(player, "Mark both corners first with /mq pos1 and /mq pos2.")
            return
        }

        val verb = if (op == Op.ADD) "Added" else "Carved"
        val bounds = part.shape().bounds()
        Text.send(
            player,
            "<gray>$verb <white>${bounds.sizeX()}x${bounds.sizeY()}x${bounds.sizeZ()}<gray>. " +
                "Selection now has <white>${selection.parts().size}<gray> part(s).",
        )
    }

    @Command("marquee|mq undo")
    @Permission(PERMISSION)
    fun undo(player: Player) {
        val selection = selections.get(player)
        val removed = selection.undo()
        if (removed == null) {
            Text.error(player, "Nothing to undo.")
            return
        }
        Text.send(
            player,
            "<gray>Removed the last part. <white>${selection.parts().size}<gray> remaining.",
        )
    }

    @Command("marquee|mq clear")
    @Permission(PERMISSION)
    fun clear(player: Player) {
        selections.get(player).clearParts()
        Text.send(player, "<gray>Selection cleared.")
    }

    @Command("marquee|mq cancel")
    @Permission(PERMISSION)
    fun cancel(player: Player) {
        val selection = selections.get(player)
        if (!selection.clearPending()) {
            Text.error(player, "No corners marked.")
            return
        }
        Text.send(
            player,
            "<gray>Corners unmarked. <dark_gray>${selection.parts().size} committed part(s) kept.",
        )
    }

    @Command("marquee|mq deselect|reset")
    @Permission(PERMISSION)
    fun deselect(player: Player) {
        // Drops the selection object entirely rather than emptying it, so nothing is left to draw
        // and the next selection starts from a clean slate in whatever world you are then in.
        if (!selections.clear(player)) {
            Text.error(player, "You have no selection.")
            return
        }
        Text.send(player, "<gray>Deselected. Corners, parts and outline all gone.")
    }

    @Command("marquee|mq info")
    @Permission(PERMISSION)
    fun info(player: Player) {
        val selection = selections.get(player)

        Text.send(player, "<gray>Selection in <white>${selection.world().name}")
        Text.raw(player, "  <gray>Mode: <white>${selection.mode().name.lowercase()}")

        val a = selection.pendingA()
        val b = selection.pendingB()
        Text.raw(player, "  <gray>Corner 1: <white>${a?.let { "${it.x()}, ${it.y()}, ${it.z()}" } ?: "unset"}")
        Text.raw(player, "  <gray>Corner 2: <white>${b?.let { "${it.x()}, ${it.y()}, ${it.z()}" } ?: "unset"}")

        val parts = selection.parts()
        if (parts.isEmpty()) {
            Text.raw(player, "  <gray>No parts committed yet.")
            return
        }

        Text.raw(player, "  <gray>Parts (<white>${parts.size}<gray>):")
        parts.forEachIndexed { index, part ->
            val box = part.shape().bounds()
            val colour = if (part.op() == Op.ADD) "<green>" else "<red>"
            Text.raw(
                player,
                "    <dark_gray>${index + 1}. $colour${part.op().name.lowercase()} " +
                    "<gray>${part.shape().typeId()} " +
                    "<white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()} " +
                    "<dark_gray>at ${box.min().x()}, ${box.min().y()}, ${box.min().z()}",
            )
        }

        selection.bounds()?.let {
            Text.raw(player, "  <gray>Bounds: <white>${it.sizeX()}x${it.sizeY()}x${it.sizeZ()}<gray> (${it.volume()} blocks)")
        }
    }

    private companion object {
        const val PERMISSION = "parcel.edit"
    }
}
