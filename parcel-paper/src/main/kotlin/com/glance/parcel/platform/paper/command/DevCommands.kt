package com.glance.parcel.platform.paper.command

import com.glance.parcel.platform.paper.visual.panel.PanelCalibration
import com.glance.parcel.platform.paper.visual.panel.StepTarget
import org.bukkit.entity.Player
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission

/**
 * Development tooling, registered only when `dev-tools` is on.
 *
 * Kept in the main source tree rather than split into a dev-only artifact, on purpose. The panel
 * constants these commands measure are version-specific - the config records them as measured on
 * 1.21.11 - so the tool needs to stay trivially runnable when a version bump changes the font
 * metrics underneath them. A separate jar with a lookup seam would exist solely to serve one class,
 * which is the same kind of structure-for-a-dev-tool that argued against putting it in its own
 * module in the first place.
 *
 * It also documents where the constants came from: someone reading `text-base-width: 0.1250` can
 * find the thing that measured it sitting next to it, rather than taking the number on faith.
 *
 * With the flag off these commands are never registered, so they do not appear in tab completion or
 * help, and the calibration listener is never hooked up either.
 */
internal class DevCommands(
    private val calibration: PanelCalibration,
) {

    @Command("parcel calibrate")
    @Permission(ADMIN)
    fun calibrate(player: Player) = calibration.start(player)

    @Command("parcel calibrate done")
    @Permission(ADMIN)
    fun calibrateDone(player: Player) = calibration.finish(player)

    @Command("parcel calibrate step <tool> <amount>")
    @Permission(ADMIN)
    fun calibrateStep(
        player: Player,
        @Argument("tool") tool: StepTarget,
        @Argument("amount") amount: Double,
    ) {
        if (!calibration.setStep(player, tool, amount.toFloat())) {
            Text.error(player, "You are not calibrating.")
            return
        }
        Text.send(player, "<gray>${tool.name.lowercase()} step is now <white>$amount<gray>.")
    }

    @Command("parcel calibrate cancel")
    @Permission(ADMIN)
    fun calibrateCancel(player: Player) {
        if (!calibration.stop(player)) {
            Text.error(player, "You are not calibrating.")
            return
        }
        Text.send(player, "<gray>Calibration cancelled.")
    }

    private companion object {
        const val ADMIN = "parcel.admin"
    }
}
