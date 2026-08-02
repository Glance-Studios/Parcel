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

    private fun build(region: Region, style: PanelStyle): Dialog = Dialog.create { factory ->
        factory.empty()
            .base(
                DialogBase.builder(mm.deserialize("<aqua>${Keys.display(region.key())}"))
                    .canCloseWithEscape(true)
                    // Must close on click, and not only because a pausing dialog is required to:
                    // the dialog covers the screen, so previewing without closing would show you
                    // nothing. The reopen is offered as a clickable message instead.
                    .pause(false)
                    .afterAction(DialogBase.DialogAfterAction.CLOSE)
                    .body(
                        listOf(
                            DialogBody.plainMessage(
                                mm.deserialize(
                                    "<gray>Alpha applies to <white>text<gray> panels only. " +
                                        "Grid spacing applies to <white>wireframe<gray> only. " +
                                        "Block panels take their colour from the nearest glass."
                                )
                            )
                        )
                    )
                    .inputs(
                        listOf(
                            channel("red", "Red", style.red),
                            channel("green", "Green", style.green),
                            channel("blue", "Blue", style.blue),
                            channel("alpha", "Alpha", style.alpha),
                            DialogInput.numberRange(
                                "grid",
                                mm.deserialize("<gray>Grid spacing (wireframe)"),
                                0.25f,
                                8f,
                            ).initial(style.gridSpacing).step(0.25f).width(200).build(),
                            // The toggle exists because the hex field is pre-filled with the
                            // current colour, so "hex wins when non-empty" would mean the sliders
                            // never did anything. Which source to use has to be said explicitly.
                            DialogInput.bool(
                                "use_hex",
                                mm.deserialize("<gray>Use hex instead of sliders"),
                                false,
                                "true",
                                "false",
                            ),
                            DialogInput.text("hex", mm.deserialize("<gray>Hex or name"))
                                .initial(style.hex())
                                .maxLength(24)
                                .width(200)
                                .build(),
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
                            ).width(200).build(),
                        )
                    )
                    .build()
            )
            .type(
                DialogType.multiAction(
                    listOf(
                        button("<yellow>Preview", "Render with these values, without saving") { view, player ->
                            apply(player, region, read(view, style), persist = false)
                        },
                        button("<green>Save", "Store as this region's default") { view, player ->
                            apply(player, region, read(view, style), persist = true)
                        },
                    )
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
        )
    }

    private fun apply(player: Player, region: Region, style: PanelStyle, persist: Boolean) {
        // Callbacks land off the main thread and everything below touches entities or config.
        plugin.server.scheduler.runTask(plugin, Runnable {
            if (persist) styles.set(region.key(), style)
            panels.show(region, style)

            val what = if (persist) "Saved" else "Previewing"
            Text.send(
                player,
                "<gray>$what <aqua>${Keys.display(region.key())}<gray> as " +
                    "<white>${style.hex()}<gray> at alpha <white>${style.alpha}<gray>.",
            )

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
