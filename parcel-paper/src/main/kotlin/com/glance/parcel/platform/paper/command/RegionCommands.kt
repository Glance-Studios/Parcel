package com.glance.parcel.platform.paper.command

import com.glance.parcel.api.region.Op
import com.glance.parcel.api.region.Region
import com.glance.parcel.platform.paper.region.RegionManagerImpl
import com.glance.parcel.platform.paper.selection.SelectionManagerImpl
import com.glance.parcel.platform.paper.menu.RegionBrowser
import com.glance.parcel.platform.paper.visual.OutlineRenderer
import com.glance.parcel.platform.paper.visual.panel.DisplayMesh
import com.glance.parcel.platform.paper.visual.panel.PanelRenderer
import com.glance.parcel.platform.paper.visual.panel.PanelStyleDialog
import com.glance.parcel.platform.paper.visual.panel.StyleStore
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
    private val panels: PanelRenderer,
    private val styles: StyleStore,
    private val styleDialog: PanelStyleDialog,
    private val browser: RegionBrowser,
) {

    @Suggestions("region-keys")
    fun regionKeys(context: CommandContext<CommandSender>, input: String): List<String> =
        regions.all().map { Keys.display(it.key()) }

    @Command("parcel")
    @Permission(VIEW)
    fun help(sender: CommandSender) {
        Text.send(sender, "<gray>Region system. Commands:")
        Text.raw(
            sender,
            "  <aqua><click:run_command:'/parcel help'>/parcel help</click><gray> - " +
                "the full guide, as a book",
        )
        Text.raw(sender, "  <aqua>/parcel menu<gray> - browse every region")
        Text.raw(sender, "  <aqua>/parcel list [namespace]<gray> - saved regions, as text")
        Text.raw(sender, "  <aqua>/parcel info <name><gray> - parts, bounds and who uses it")
        Text.raw(sender, "  <aqua>/parcel create<gray> (or <aqua>save<gray>) <aqua><name><gray> - save your selection as a new region")
        Text.raw(sender, "  <aqua>/parcel load <name><gray> - pull a region into your selection to edit")
        Text.raw(sender, "  <aqua>/parcel apply <name><gray> - REPLACE a region's shape with your selection")
        Text.raw(sender, "  <aqua>/parcel append <name><gray> - ADD your selection to it, keeping what is there")
        Text.raw(sender, "  <dark_gray>create/save/apply also work on <aqua>/mq")
        Text.raw(sender, "  <aqua>/parcel undo <name><gray> - revert its last change")
        Text.raw(sender, "  <aqua>/parcel delete <name><gray> - remove a region")
        Text.raw(sender, "  <aqua>/parcel show <name><gray> - toggle its outline on or off")
        Text.raw(sender, "  <aqua>/parcel render <name><gray> - toggle solid panels on its surface")
        Text.raw(sender, "  <aqua>/parcel style <name><gray> - colour and opacity, with sliders")
        Text.raw(sender, "  <aqua>/parcel goto <name><gray> - teleport to it")
        Text.raw(sender, "  <aqua>/parcel reload<gray> - reload config and regions from disk")
        Text.raw(sender, "  <gray>Build a selection with <aqua>/marquee<gray>.")
    }

    @Command("parcel help|guide")
    @Permission(VIEW)
    fun helpBook(player: Player) = HelpBook.open(player)

    @Command("parcel menu|browse")
    @Permission(VIEW)
    fun menu(player: Player) = browser.open(player)

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
            val transient = if (region.isTransient()) " <yellow>*" else ""
            Text.raw(
                sender,
                "  <aqua>${Keys.display(region.key())}$transient " +
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
        if (region.isTransient()) {
            Text.raw(sender, "  <yellow>Transient <dark_gray>- in memory only, gone on restart")
        }

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

    /**
     * Saving spans both halves of the plugin - it is the end of a selection workflow and the start
     * of a region's life - so it is reachable from either root under either verb.
     *
     * ⚠️ These must be SEPARATE declarations, not `parcel|marquee|mq create|save`. Putting the
     * roots in one alternation makes marquee and mq aliases *of parcel*, while MarqueeCommands
     * already registers them as a root of their own - two root nodes claiming the same name, which
     * Cloud rejects with AmbiguousNodeException at load.
     */
    @Command("marquee|mq create|save <name>")
    @Permission(EDIT)
    fun createFromMarquee(player: Player, @Argument("name") name: String) = create(player, name)

    @Command("marquee|mq apply <name>")
    @Permission(EDIT)
    fun applyFromMarquee(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) = apply(player, name)

    @Command("parcel create|save <name>")
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

        // Render it straight away. Promoting clears the selection, so its outline vanishes at the
        // same moment - without this the shape you just spent time on disappears off the screen
        // and you have to ask for it back.
        val panelCount = panels.show(region, viewer = player)

        Text.send(
            player,
            "<gray>Created <aqua>${Keys.display(key)}<gray> from " +
                "<white>${region.parts().size}<gray> part(s). Your selection has been cleared.",
        )
        if (panelCount > 0) {
            Text.raw(
                player,
                "  <dark_gray>Rendering it now. <gray>/parcel render ${Keys.display(key)}" +
                    "<dark_gray> to toggle, <gray>/parcel style ${Keys.display(key)}<dark_gray> to recolour.",
            )
        }
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
        val result = runCatching { selection.applyTo(region) }
        if (result.isFailure) {
            Text.error(player, result.exceptionOrNull()?.message ?: "Could not apply that selection.")
            Text.raw(
                player,
                "  <gray>Try <aqua>/parcel append ${Keys.display(region.key())}<gray> to add to it " +
                    "instead of replacing it.",
            )
            return
        }

        finish(player, region, "Reshaped", usages)
    }

    @Command("parcel undo <name>")
    @Permission(EDIT)
    fun undoRegion(
        sender: CommandSender,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(sender, name) ?: return
        val before = region.parts().size

        if (!regions.undo(region.key())) {
            Text.error(sender, "${Keys.display(region.key())} has nothing left to undo.")
            return
        }

        Text.send(
            sender,
            "<gray>Reverted <aqua>${Keys.display(region.key())}<gray> " +
                "(<white>$before<gray> parts -> <white>${region.parts().size}<gray>). " +
                "<dark_gray>${region.historyDepth()} step(s) left.",
        )
        if (sender is Player && panels.isShowing(region)) panels.show(region, viewer = sender)
    }

    @Command("parcel load <name>")
    @Permission(EDIT)
    fun load(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) = loadImpl(player, name)

    @Command("marquee|mq load <name>")
    @Permission(EDIT)
    fun loadFromMarquee(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) = loadImpl(player, name)

    /**
     * The first half of the round trip that gives `apply` its purpose: pull a region into the
     * selection, edit it there, then apply it back. Without this, `apply` could only ever be
     * reached with a selection built from scratch, where replacing is rarely what is meant.
     */
    private fun loadImpl(player: Player, name: String) {
        val region = resolve(player, name) ?: return

        if (region.world() != player.world) {
            Text.error(
                player,
                "${Keys.display(region.key())} is in ${region.world().name}. " +
                    "Use /parcel goto ${Keys.display(region.key())} first.",
            )
            return
        }

        val existing = selections.of(player)
        if (existing != null && !existing.isEmpty()) {
            Text.error(player, "You already have a selection. Clear it first with /mq deselect.")
            return
        }

        val selection = selections.load(player, region)
        Text.send(
            player,
            "<gray>Loaded <aqua>${Keys.display(region.key())}<gray> into your selection " +
                "(<white>${selection.parts().size}<gray> part(s)).",
        )
        Text.raw(
            player,
            "  <dark_gray>Edit it, then <gray>/parcel apply ${Keys.display(region.key())}<dark_gray> to save.",
        )
    }

    @Command("parcel append <name>")
    @Permission(EDIT)
    fun append(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) = appendImpl(player, name)

    @Command("marquee|mq append <name>")
    @Permission(EDIT)
    fun appendFromMarquee(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) = appendImpl(player, name)

    private fun appendImpl(player: Player, name: String) {
        val region = resolve(player, name) ?: return

        val selection = selections.of(player)
        if (selection == null || selection.isEmpty()) {
            Text.error(player, "Your selection is empty. Build one with /marquee first.")
            return
        }

        val usages = regions.usagesOf(region)
        val before = region.parts().size
        selection.appendTo(region)

        finish(player, region, "Added ${region.parts().size - before} part(s) to", usages)
    }

    /**
     * Every write to a region ends the same way: the change is already committed and saved, so the
     * selection has done its job and goes, and the region is rendered in its place.
     *
     * Leaving the selection up after a write was actively misleading - its particles look exactly
     * like pending work, when in fact there is nothing left to save.
     */
    private fun finish(player: Player, region: Region, verb: String, usages: List<String>) {
        selections.clear(player)
        panels.show(region, viewer = player)

        Text.send(
            player,
            "<gray>$verb <aqua>${Keys.display(region.key())}<gray>. " +
                "<dark_gray>Saved, selection cleared.",
        )
        if (usages.isNotEmpty()) {
            Text.raw(player, "  <yellow>This region is shared. Also affects:")
            usages.forEach { Text.raw(player, "    <dark_gray>- <gray>$it") }
        }
        Text.raw(
            player,
            "  <dark_gray>Edit it again with <gray>/parcel load ${Keys.display(region.key())}",
        )
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

    @Command("parcel style <name>")
    @Permission(EDIT)
    fun style(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(player, name) ?: return
        styleDialog.open(player, region)
    }

    @Command("parcel style <name> clear")
    @Permission(EDIT)
    fun styleClear(
        player: Player,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(player, name) ?: return
        if (!styles.clear(region.key())) {
            Text.error(player, "${Keys.display(region.key())} has no stored style.")
            return
        }
        if (panels.isShowing(region)) panels.show(region, viewer = player)
        Text.send(player, "<gray>Cleared the stored style for <aqua>${Keys.display(region.key())}<gray>.")
    }

    @Command("parcel render <name>")
    @Permission(VIEW)
    fun render(
        sender: CommandSender,
        @Argument(value = "name", suggestions = "region-keys") name: String,
    ) {
        val region = resolve(sender, name) ?: return

        if (panels.isShowing(region)) {
            panels.hide(region.key())
            Text.send(sender, "<gray>Hid panels for <aqua>${Keys.display(region.key())}<gray>.")
            return
        }

        when (val count = panels.show(region, viewer = sender as? Player)) {
            -1 -> Text.error(sender, "${Keys.display(region.key())} is too large to render.")
            0 -> Text.error(sender, "${Keys.display(region.key())} has no shape to render.")
            else -> Text.send(
                sender,
                "<gray>Rendering <aqua>${Keys.display(region.key())}<gray> as " +
                    "<white>$count<gray> panel(s).",
            )
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

        val box = region.bounds()
        val world = region.world()
        val centreX = box.min().x() + box.sizeX() / 2
        val centreZ = box.min().z() + box.sizeZ() / 2

        // Above the top face and centred, so you arrive looking at the whole thing rather than
        // inside it. A flat region's top face is the build limit, though, which would drop you in
        // the sky miles from anything - so those go to ground level instead.
        val y = if (DisplayMesh.isCrossSection(region)) {
            world.getHighestBlockYAt(centreX, centreZ) + 3
        } else {
            box.max().y() + 3
        }.coerceIn(world.minHeight + 1, world.maxHeight - 2)
        val target = Location(
            world,
            centreX + 0.5,
            y.toDouble(),
            centreZ + 0.5,
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
