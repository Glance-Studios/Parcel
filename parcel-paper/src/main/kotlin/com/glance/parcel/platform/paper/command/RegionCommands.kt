package com.glance.parcel.platform.paper.command

import com.glance.parcel.api.region.Op
import com.glance.parcel.platform.paper.region.RegionManagerImpl
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import com.glance.parcel.platform.paper.visual.OutlineRenderer
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import java.util.logging.Level

/**
 * Managing saved regions.
 *
 * Regions are shared geometry referenced by key, so these commands are about the region itself, not
 * about what any plugin does with it. `/parcel delete` asks other plugins what they are using a
 * region for before it lets you remove it.
 */
internal class RegionCommands(
    private val plugin: Plugin,
    private val regions: RegionManagerImpl,
    private val selections: SelectionManagerImpl,
    private val outlines: OutlineRenderer,
) {

    @Suggestions("region-keys")
    fun regionKeys(context: CommandContext<CommandSender>, input: String): List<String> =
        regions.all().map { Keys.display(it.key()) }

    @Command("parcel")
    @Permission(VIEW)
    fun help(sender: CommandSender) {
        Text.send(sender, "<gray>Region system. Commands:")
        Text.raw(sender, "  <aqua>/parcel list [namespace]<gray> - saved regions")
        Text.raw(sender, "  <aqua>/parcel info <name><gray> - parts, bounds and who uses it")
        Text.raw(sender, "  <aqua>/parcel create <name><gray> - save your selection as a new region")
        Text.raw(sender, "  <aqua>/parcel apply <name><gray> - reshape an existing region to your selection")
        Text.raw(sender, "  <aqua>/parcel delete <name><gray> - remove a region")
        Text.raw(sender, "  <aqua>/parcel show <name><gray> - toggle its outline on or off")
        Text.raw(sender, "  <aqua>/parcel goto <name><gray> - teleport to it")
        Text.raw(sender, "  <aqua>/parcel reload<gray> - reload config and regions from disk")
        Text.raw(sender, "  <gray>Build a selection with <aqua>/marquee<gray>.")
    }

    @Command("parcel list [namespace]")
    @Permission(VIEW)
    fun list(sender: CommandSender, @Argument("namespace") namespace: String?) {
        val found = if (namespace == null) regions.all() else regions.inNamespace(namespace.lowercase())

        if (found.isEmpty()) {
            Text.send(sender, "<gray>No regions" + (namespace?.let { " in <white>$it" } ?: "") + ".")
            return
        }

        Text.send(sender, "<gray>Regions (<white>${found.size}<gray>):")
        found.sortedBy { it.key().toString() }.forEach { region ->
            val box = region.bounds()
            val size = if (region.isEmpty()) "<dark_gray>empty" else
                "<white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()}"
            Text.raw(
                sender,
                "  <aqua>${Keys.display(region.key())} " +
                    "<dark_gray>${region.world().name} " +
                    "$size <dark_gray>(${region.parts().size} parts)",
            )
        }
    }

    @Command("parcel info <name>")
    @Permission(VIEW)
    fun info(
        sender: CommandSender,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(sender, name) ?: return

        Text.send(sender, "<gray>Region <aqua>${Keys.display(region.key())}")
        Text.raw(sender, "  <gray>World: <white>${region.world().name}")

        if (region.isEmpty()) {
            Text.raw(sender, "  <dark_gray>No additive parts - this region contains nothing.")
        } else {
            val box = region.bounds()
            Text.raw(
                sender,
                "  <gray>Bounds: <white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()}<gray> " +
                    "(${box.volume()} blocks) <dark_gray>at ${box.min().x()}, ${box.min().y()}, ${box.min().z()}",
            )
            // Meshing is O(volume) and refuses past its cap, so never let it kill the command.
            runCatching { region.mesh().size }
                .onSuccess { Text.raw(sender, "  <gray>Mesh: <white>$it<gray> quads") }
                .onFailure { Text.raw(sender, "  <dark_gray>Mesh: too large to compute") }
        }

        val parts = region.parts()
        if (parts.isNotEmpty()) {
            Text.raw(sender, "  <gray>Parts (<white>${parts.size}<gray>):")
            parts.forEachIndexed { index, part ->
                val box = part.shape().bounds()
                val colour = if (part.op() == Op.ADD) "<green>" else "<red>"
                Text.raw(
                    sender,
                    "    <dark_gray>${index + 1}. $colour${part.op().name.lowercase()} " +
                        "<gray>${part.shape().typeId()} " +
                        "<white>${box.sizeX()}x${box.sizeY()}x${box.sizeZ()}",
                )
            }
        }

        val usages = regions.usagesOf(region)
        if (usages.isEmpty()) {
            Text.raw(sender, "  <dark_gray>Nothing is using this region.")
        } else {
            Text.raw(sender, "  <gray>Used by:")
            usages.forEach { Text.raw(sender, "    <dark_gray>- <gray>$it") }
        }
    }

    @Command("parcel create <name>")
    @Permission(EDIT)
    fun create(player: Player, @Argument("name") name: String) {
        val key = Keys.parse(name) ?: run {
            Text.error(player, "'$name' is not a valid region name.")
            return
        }
        if (regions.get(key) != null) {
            Text.error(player, "${Keys.display(key)} already exists. Use /parcel apply to reshape it.")
            return
        }

        val selection = selections.of(player)
        if (selection == null || selection.isEmpty()) {
            Text.error(player, "Your selection is empty. Build one with /marquee first.")
            return
        }

        val region = selections.promote(player, key)
        Text.send(
            player,
            "<gray>Created <aqua>${Keys.display(key)}<gray> from " +
                "<white>${region.parts().size}<gray> part(s). Your selection has been cleared.",
        )
    }

    @Command("parcel apply <name>")
    @Permission(EDIT)
    fun apply(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(player, name) ?: return

        val selection = selections.of(player)
        if (selection == null || selection.isEmpty()) {
            Text.error(player, "Your selection is empty. Build one with /marquee first.")
            return
        }

        val usages = regions.usagesOf(region)
        selection.applyTo(region)

        Text.send(player, "<gray>Reshaped <aqua>${Keys.display(region.key())}<gray>.")
        if (usages.isNotEmpty()) {
            Text.raw(player, "  <yellow>This region is shared. Also affects:")
            usages.forEach { Text.raw(player, "    <dark_gray>- <gray>$it") }
        }
    }

    @Command("parcel delete <name>")
    @Permission(EDIT)
    fun deletePrompt(
        sender: CommandSender,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(sender, name) ?: return
        val usages = regions.usagesOf(region)

        if (usages.isEmpty()) {
            Text.send(sender, "<gray>Nothing is using <aqua>${Keys.display(region.key())}<gray>.")
        } else {
            Text.send(sender, "<yellow>${Keys.display(region.key())} is in use by:")
            usages.forEach { Text.raw(sender, "  <dark_gray>- <gray>$it") }
        }
        Text.raw(sender, "  <gray>Run <red>/parcel delete ${Keys.display(region.key())} confirm<gray> to remove it.")
    }

    @Command("parcel delete <name> confirm")
    @Permission(EDIT)
    fun deleteConfirm(
        sender: CommandSender,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(sender, name) ?: return

        if (!regions.delete(region.key())) {
            Text.error(sender, "Deletion of ${Keys.display(region.key())} was cancelled by another plugin.")
            return
        }
        Text.send(sender, "<gray>Deleted <aqua>${Keys.display(region.key())}<gray>.")
    }

    @Command("parcel show <name>")
    @Permission(VIEW)
    fun show(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(player, name) ?: return

        val shown = outlines.toggleWatch(player, region)
        if (!shown) {
            Text.send(player, "<gray>Hid <aqua>${Keys.display(region.key())}<gray>.")
            return
        }

        Text.send(player, "<gray>Showing <aqua>${Keys.display(region.key())}<gray>.")
        if (region.world() != player.world) {
            Text.raw(player, "  <yellow>It is in ${region.world().name} - /parcel goto to fly there.")
        }
    }

    @Command("parcel goto <name>")
    @Permission(EDIT)
    fun goto(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(player, name) ?: return
        if (region.isEmpty()) {
            Text.error(player, "${Keys.display(region.key())} has no shape to go to.")
            return
        }

        // Above the top face and centred, so you arrive looking at the whole thing rather than
        // inside it. Prisms span the world height, so clamp into something survivable.
        val box = region.bounds()
        val world = region.world()
        val y = (box.max().y() + 3).coerceIn(world.minHeight + 1, world.maxHeight - 2)
        val target = Location(
            world,
            box.min().x() + box.sizeX() / 2.0,
            y.toDouble(),
            box.min().z() + box.sizeZ() / 2.0,
        ).apply { pitch = 45f }

        player.teleport(target)
        Text.send(
            player,
            "<gray>Teleported to <aqua>${Keys.display(region.key())}<gray> " +
                "<dark_gray>(${target.blockX}, ${target.blockY}, ${target.blockZ})",
        )
    }

    @Command("parcel reload")
    @Permission(ADMIN)
    fun reload(sender: CommandSender) {
        regions.reload().whenComplete { count, error ->
            // Loading finishes on a repository thread. Hop back before touching Bukkit, and log as
            // well as reply - a console or RCON sender may be gone by the time this lands, and a
            // reload that reports nothing at all is indistinguishable from one that hung.
            plugin.server.scheduler.runTask(plugin, Runnable {
                if (error != null) {
                    plugin.logger.log(Level.SEVERE, "Region reload failed", error)
                    Text.error(sender, "Reload failed, see console.")
                    return@Runnable
                }
                plugin.logger.info("Reloaded $count region(s) from disk")
                Text.send(sender, "<gray>Reloaded <white>$count<gray> region(s) from disk.")
            })
        }
    }

    private fun resolve(sender: CommandSender, name: String) =
        Keys.parse(name)?.let(regions::get)
            ?: run {
                Text.error(sender, "No region called '$name'.")
                null
            }

    private companion object {
        const val VIEW = "parcel.view"
        const val EDIT = "parcel.edit"
        const val ADMIN = "parcel.admin"
    }
}
