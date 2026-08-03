package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.region.Region
import com.glance.parcel.platform.paper.command.Keys
import com.glance.parcel.platform.paper.command.Text
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.dialog.DialogResponseView
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import net.kyori.adventure.text.event.ClickCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.input.DialogInput
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin

/**
 * A slider-based editor for a region's panel colour.
 *
 * Colour is four continuous channels, which sliders express far better than a command line. Uses
 * `customClick` callbacks rather than command templates, so the values arrive as a typed response
 * instead of being stringified through a command and parsed back.
 *
 * ⚠️ Dialogs are submit-based: sliders do not fire as you drag, only when a button is pressed. So
 * "live preview" is really re-render-and-reopen, giving a one-click edit-see-edit loop.
 *
 * ⚠️ Callbacks arrive **off the main thread**. Anything touching entities has to hop back.
 */
internal class PanelStyleDialog(
    private val plugin: Plugin,
    private val styles: StyleStore,
    private val panels: PanelRenderer,
    private val defaultStyle: () -> PanelStyle,
) {

    fun open(player: Player, region: Region) {
        player.showDialog(build(region, styles.get(region.key()) ?: defaultStyle()))
    }

    /**
     * Edit the style every region without one of its own uses.
     *
     * The same dialog with no region: nothing in it is region-specific, and a second editor for the
     * same eight values would be two places to keep in step.
     */
    fun openDefault(player: Player) {
        player.showDialog(build(null, defaultStyle()))
    }

    private fun build(region: Region?, style: PanelStyle): Dialog = Dialog.create { factory ->
        factory.empty()
            .base(
                DialogBase.builder(
                    mm.deserialize(
                        if (region == null) "<light_purple>Default style"
                        else "<aqua>${Keys.display(region.key())}"
                    )
                )
                    .canCloseWithEscape(true)
                    // Must close on click, and not only because a pausing dialog is required to:
                    // the dialog covers the screen, so previewing without closing would show you
                    // nothing. The reopen is offered as a clickable message instead.
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(
                        buildList {
                            if (region == null) {
                                add(
                                    DialogBody.plainMessage(
                                        mm.deserialize(
                                            "<gray>Every region uses this unless it was given a " +
                                                "style of its own."
                                        )
                                    )
                                )
                            }
                            add(
                                DialogBody.plainMessage(
                                    mm.deserialize(
                                        "<gray>Alpha applies to <white>text<gray> panels only. " +
                                            "Grid spacing applies to <white>wireframe<gray> only. " +
                                            "Block panels take their colour from the nearest glass."
                                    )
                                )
                            )
                        }
                    )
                    .inputs(
                        buildList {
                            add(channel("red", "Red", style.red))
                            add(channel("green", "Green", style.green))
                            add(channel("blue", "Blue", style.blue))
                            add(channel("alpha", "Alpha", style.alpha))
                            add(
                                DialogInput.numberRange(
                                    "grid",
                                    mm.deserialize("<gray>Grid spacing (wireframe)"),
                                    0.25f,
                                    8f,
                                ).initial(style.gridSpacing).step(0.25f).width(200).build()
                            )

                            // Only a cross-section has a plane to raise. On a volume region this
                            // slider would move nothing, and a control that does nothing is worse
                            // than an absent one - it invites you to go looking for the effect.
                            // The default belongs to no one region, so there it always shows and
                            // says which regions it can reach.
                            if (region == null || DisplayMesh.isCrossSection(region)) {
                                val label =
                                    if (region == null) "Plane height (flat regions)"
                                    else "Plane height (blocks)"
                                add(
                                    DialogInput.numberRange(
                                        "height",
                                        mm.deserialize("<gray>$label"),
                                        -32f,
                                        32f,
                                    ).initial(style.heightOffset.toFloat()).step(1f).width(200).build()
                                )
                            }
                            // The toggle exists because the hex field is pre-filled with the
                            // current colour, so "hex wins when non-empty" would mean the sliders
                            // never did anything. Which source to use has to be said explicitly.
                            add(
                                DialogInput.bool(
                                    "follow",
                                    mm.deserialize("<gray>Follow player height (flat only)"),
                                    style.follow,
                                    "true",
                                    "false",
                                )
                            )
                            add(
                                DialogInput.bool(
                                    "use_hex",
                                    mm.deserialize("<gray>Use hex instead of sliders"),
                                    false,
                                    "true",
                                    "false",
                                )
                            )
                            add(
                                DialogInput.text("hex", mm.deserialize("<gray>Hex or name"))
                                    .initial(style.hex())
                                    .maxLength(24)
                                    .width(200)
                                    .build()
                            )
                            add(
                                DialogInput.singleOption(
                                    "primitive",
                                    mm.deserialize("<gray>Primitive"),
                                    PanelPrimitive.entries.map {
                                        SingleOptionDialogInput.OptionEntry.create(
                                            it.name,
                                            Component.text(it.name.lowercase()),
                                            it == style.primitive,
                                        )
                                    },
                                ).width(200).build()
                            )
                        }
                    )
                    .build()
            )
            .type(
                DialogType.multiAction(
                    buildList {
                        // Preview needs something to draw on. Editing the default has no target,
                        // and saving it redraws every inheriting region on screen anyway, so there
                        // is nothing a preview would show that saving does not.
                        if (region != null) {
                            add(
                                button(
                                    "<yellow>Preview",
                                    "Render with these values, without saving",
                                ) { view, viewer ->
                                    apply(viewer, region, read(view, style), persist = false)
                                }
                            )
                        }
                        add(
                            button(
                                "<green>Save",
                                if (region == null) "Store as the default for every region"
                                else "Store as this region's default",
                            ) { view, viewer ->
                                apply(viewer, region, read(view, style), persist = true)
                            }
                        )
                    }
                ).build()
            )
    }

    private fun channel(key: String, label: String, initial: Int) =
        DialogInput.numberRange(key, mm.deserialize("<gray>$label"), 0f, 255f)
            .initial(initial.toFloat())
            .step(1f)
            .width(200)
            .build()

    private fun button(
        label: String,
        tooltip: String,
        handler: (DialogResponseView, Player) -> Unit,
    ): ActionButton = ActionButton.builder(mm.deserialize("<!italic>$label"))
        .tooltip(mm.deserialize("<gray>$tooltip"))
        .width(150)
        .action(
            // Explicit SAM: Kotlin otherwise resolves this to the customClick(Key, ...) overload
            // and complains that a lambda is not a Key.
            DialogAction.customClick(
                DialogActionCallback { view, audience ->
                    (audience as? Player)?.let { handler(view, it) }
                },
                ClickCallback.Options.builder().build(),
            )
        )
        .build()

    /**
     * Sliders drive the colour unless the hex toggle is on.
     *
     * Alpha always comes from its slider - a hex colour has no opacity to give.
     */
    private fun read(view: DialogResponseView, fallback: PanelStyle): PanelStyle {
        val primitive = runCatching {
            PanelPrimitive.valueOf(view.getText("primitive") ?: fallback.primitive.name)
        }.getOrElse { fallback.primitive }

        val typed = if (view.getBoolean("use_hex") == true) {
            view.getText("hex")?.let(PanelStyle::parseColour)
        } else {
            null
        }

        return PanelStyle(
            primitive = primitive,
            red = typed?.red ?: view.getFloat("red")?.toInt() ?: fallback.red,
            green = typed?.green ?: view.getFloat("green")?.toInt() ?: fallback.green,
            blue = typed?.blue ?: view.getFloat("blue")?.toInt() ?: fallback.blue,
            alpha = view.getFloat("alpha")?.toInt() ?: fallback.alpha,
            gridSpacing = view.getFloat("grid") ?: fallback.gridSpacing,
            particleSize = fallback.particleSize,
            follow = view.getBoolean("follow") ?: fallback.follow,
            heightOffset = view.getFloat("height")?.toInt() ?: fallback.heightOffset,
        )
    }

    private fun apply(player: Player, region: Region?, style: PanelStyle, persist: Boolean) {
        // Callbacks land off the main thread and everything below touches entities or config.
        plugin.server.scheduler.runTask(plugin, Runnable {
            val colour = "<white>${style.hex()}<gray> at alpha <white>${style.alpha}<gray>."

            if (region == null) {
                styles.setDefault(style)
                // Anything on screen inheriting it should change now, not on its next toggle.
                val redrawn = panels.refreshInherited()
                Text.send(player, "<gray>Saved the <light_purple>default style<gray> as $colour")
                Text.raw(
                    player,
                    if (redrawn > 0) "  <dark_gray>Redrew $redrawn shown region(s) using it."
                    else "  <dark_gray>Regions with a style of their own are unaffected.",
                )
                return@Runnable
            }

            if (persist) styles.set(region.key(), style)
            panels.show(region, style, viewer = player)

            val what = if (persist) "Saved" else "Previewing"
            Text.send(player, "<gray>$what <aqua>${Keys.display(region.key())}<gray> as $colour")

            if (!persist) {
                // One click back into the editor, since the dialog had to close to let you look.
                val reopen = "/parcel style ${Keys.display(region.key())}"
                player.sendMessage(
                    mm.deserialize(
                        "  <dark_gray>[<aqua><click:run_command:'$reopen'>Adjust again</click><dark_gray>]" +
                            "  <dark_gray>or run <gray>$reopen"
                    )
                )
            }
        })
    }

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
    }
}
