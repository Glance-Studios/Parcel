package com.glance.parcel.platform.paper.visual.panel

import com.glance.parcel.api.mesh.Face
import com.glance.parcel.platform.paper.command.Text
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.Plugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID

/**
 * Interactive measurement of a text display's base quad size.
 *
 * The scale a text panel needs is `coveredBlocks / baseSize`, and `baseSize` cannot be derived - a
 * text display's background is sized by its glyphs and padding. So measure it instead: put a text
 * panel next to a block display of known exactly-one-block size, scale the text one by hand until
 * they match, and read the answer off as `base = 1 / scale`.
 *
 * A development tool, not a player-facing feature.
 */
internal class PanelCalibration(
    private val plugin: Plugin,
) : Listener {

    private val scaleTag = NamespacedKey(plugin, "panel_scaler")
    private val moveTag = NamespacedKey(plugin, "panel_mover")
    private val sessions = HashMap<UUID, Session>()

    private class Session(
        val panel: TextDisplay,
        val reference: BlockDisplay,
        val blockLocation: Location,
        val previousBlock: org.bukkit.block.data.BlockData,
        val facing: Quaternionf,
        var scaleX: Float,
        var scaleY: Float,
        var offsetX: Float,
        var offsetY: Float,
        // Separate, because the two want very different magnitudes: scale is measured in whole
        // multiples of a small base quad, offsets in fractions of a block.
        var scaleStep: Float,
        var moveStep: Float,
    )

    /**
     * Two tools, not one with modes.
     *
     * Scaling a text display moves its quad as well as resizing it, so size and position fight each
     * other and you converge by alternating. Cycling a mode between every adjustment makes that
     * miserable; holding a different item does not. Each tool spends all four click combinations on
     * direct actions, so nothing is modal.
     */
    fun scaleTool(): ItemStack = ItemStack(Material.BLAZE_ROD).apply {
        editMeta { meta ->
            meta.persistentDataContainer.set(scaleTag, PersistentDataType.BYTE, 1)
            meta.displayName(mm.deserialize("<!italic><light_purple>Panel Scaler"))
            meta.lore(
                listOf(
                    line("<dark_gray>Size the panel to the block face"),
                    Component.empty(),
                    line("<gray>Left click <dark_gray>- wider"),
                    line("<gray>Right click <dark_gray>- narrower"),
                    line("<gray>Sneak + left <dark_gray>- taller"),
                    line("<gray>Sneak + right <dark_gray>- shorter"),
                    Component.empty(),
                    line("<gray>/parcel calibrate step scale <n>"),
                )
            )
        }
    }

    fun moveTool(): ItemStack = ItemStack(Material.STICK).apply {
        editMeta { meta ->
            meta.persistentDataContainer.set(moveTag, PersistentDataType.BYTE, 1)
            meta.displayName(mm.deserialize("<!italic><light_purple>Panel Nudger"))
            meta.lore(
                listOf(
                    line("<dark_gray>Line the panel's corner up with the block's"),
                    Component.empty(),
                    line("<gray>Left click <dark_gray>- right"),
                    line("<gray>Right click <dark_gray>- left"),
                    line("<gray>Sneak + left <dark_gray>- up"),
                    line("<gray>Sneak + right <dark_gray>- down"),
                    Component.empty(),
                    line("<gray>/parcel calibrate step move <n> <dark_gray>- finer or coarser"),
                    line("<gray>/parcel calibrate done <dark_gray>- read the answer"),
                )
            )
        }
    }

    fun start(player: Player) {
        stop(player)

        val world = player.world

        // Snap to a cardinal direction, so everything sits square on the block grid. A panel at an
        // arbitrary yaw could never line up with a real block, and lining up with a real block is
        // the entire point of the exercise.
        val face = cardinalFacing(player.location.yaw)
        val facing = Panels.rotationFor(face)

        // The block whose face we are measuring against: three blocks ahead, at eye level.
        val blockLocation = blockAhead(player, face)
        val previousBlock = blockLocation.block.blockData
        blockLocation.block.type = Material.WHITE_CONCRETE

        // Dead centre of whichever face of that block points back at the player, nudged out of the
        // surface so it does not z-fight with it.
        val panelAt = faceCentre(blockLocation, face, 0.02)

        val panel = world.spawn(panelAt, TextDisplay::class.java) { d ->
            d.text(Component.text(" "))
            d.backgroundColor = Color.fromARGB(200, 85, 200, 255)
            d.billboard = Display.Billboard.FIXED
            d.isSeeThrough = false
            d.isShadowed = false
            d.transformation = Transformation(
                Vector3f(0f, 0f, 0f), facing, Vector3f(1f, 1f, 1f), Quaternionf(),
            )
            common(d)
        }

        // A second, display-based one-block square two along, so the text panel can be compared
        // against both a real block and a display of known exact size.
        val sideways = sidewaysOffset(face, 2.0)
        val reference = world.spawn(
            faceCentre(blockLocation.clone().add(sideways), face, 0.02),
            BlockDisplay::class.java,
        ) { d ->
            d.block = Material.LIME_CONCRETE.createBlockData()
            d.transformation = Transformation(
                Vector3f(-0.5f, -0.5f, 0f), facing, Vector3f(1f, 1f, 0.02f), Quaternionf(),
            )
            common(d)
        }

        sessions[player.uniqueId] = Session(
            panel, reference, blockLocation, previousBlock, facing,
            scaleX = 1f, scaleY = 1f, offsetX = 0f, offsetY = 0f,
            scaleStep = 0.5f, moveStep = 0.05f,
        )
        player.inventory.addItem(scaleTool(), moveTool())

        Text.send(player, "<gray>Calibrating against the white block.")
        Text.raw(player, "  <light_purple>Nudger<gray> to line up a corner, <light_purple>Scaler<gray> to reach the far one.")
        Text.raw(
            player,
            "  <dark_gray>They interact, so alternate. " +
                "<gray>/parcel calibrate step <scale|move|both> <n><dark_gray> to refine.",
        )
        report(player, sessions.getValue(player.uniqueId))
    }

    /** @return whether a session was actually running */
    fun stop(player: Player): Boolean = sessions.remove(player.uniqueId)?.also(::tearDown) != null

    /** Puts back whatever block we replaced - calibration must not leave a mark on the world. */
    private fun tearDown(session: Session) {
        session.panel.remove()
        session.reference.remove()
        session.blockLocation.block.blockData = session.previousBlock
    }

    /** Reports the measured base size and removes the props. */
    fun finish(player: Player) {
        val session = sessions[player.uniqueId] ?: run {
            Text.error(player, "You are not calibrating. Run /parcel calibrate first.")
            return
        }

        // At the scale that makes the quad one block, one unit of scale buys 1/scale blocks.
        val baseWidth = 1f / session.scaleX
        val baseHeight = 1f / session.scaleY

        // The offset needed at this scale, expressed per unit of scale - because the correction
        // grows with the panel. The renderer multiplies it back up by whatever scale it uses.
        val anchorX = -session.offsetX / session.scaleX
        val anchorY = -session.offsetY / session.scaleY
        stop(player)

        val values = listOf(
            "text-base-width" to baseWidth,
            "text-base-height" to baseHeight,
            "text-anchor-x" to anchorX,
            "text-anchor-y" to anchorY,
        )

        Text.send(player, "<gray>Measured. Put these under <white>panels<gray> in config.yml:")
        values.forEach { (name, value) ->
            Text.raw(player, "  <aqua>$name: <white>${"%.4f".format(value)}")
        }
        plugin.logger.info(
            "Panel calibration: " + values.joinToString(" ") { "${it.first}=${"%.4f".format(it.second)}" }
        )
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand == EquipmentSlot.OFF_HAND) return
        val pdc = event.item?.itemMeta?.persistentDataContainer ?: return
        val scaling = pdc.has(scaleTag, PersistentDataType.BYTE)
        if (!scaling && !pdc.has(moveTag, PersistentDataType.BYTE)) return

        val player = event.player
        val session = sessions[player.uniqueId] ?: return
        event.isCancelled = true

        val left = when (event.action) {
            Action.LEFT_CLICK_BLOCK, Action.LEFT_CLICK_AIR -> true
            Action.RIGHT_CLICK_BLOCK, Action.RIGHT_CLICK_AIR -> false
            else -> return
        }

        val step = if (scaling) session.scaleStep else session.moveStep
        val delta = if (left) step else -step
        val vertical = player.isSneaking

        if (scaling) {
            if (vertical) {
                session.scaleY = (session.scaleY + delta).coerceAtLeast(0.01f)
            } else {
                session.scaleX = (session.scaleX + delta).coerceAtLeast(0.01f)
            }
        } else {
            if (vertical) session.offsetY += delta else session.offsetX += delta
        }

        apply(session)
        report(player, session)
    }

    fun setStep(player: Player, target: StepTarget, step: Float): Boolean {
        val session = sessions[player.uniqueId] ?: return false
        val clamped = step.coerceIn(0.001f, 5f)
        if (target != StepTarget.MOVE) session.scaleStep = clamped
        if (target != StepTarget.SCALE) session.moveStep = clamped
        report(player, session)
        return true
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        stop(event.player)
    }

    fun stopAll() {
        sessions.keys.toList().forEach { uuid -> sessions.remove(uuid)?.let(::tearDown) }
    }

    private fun apply(session: Session) {
        session.panel.transformation = Transformation(
            // Translation is applied in WORLD space, after the rotation, so a local offset has to
            // be rotated into the face's frame first - otherwise "left" means world +X no matter
            // which way the panel is turned.
            session.facing.transform(Vector3f(session.offsetX, session.offsetY, 0f)),
            session.facing,
            Vector3f(session.scaleX, session.scaleY, 1f),
            Quaternionf(),
        )
    }

    private fun report(player: Player, session: Session) {
        player.sendActionBar(
            mm.deserialize(
                "<gray>scale <white>${"%.2f".format(session.scaleX)}x${"%.2f".format(session.scaleY)}<gray>  " +
                    "offset <white>${"%.2f".format(session.offsetX)},${"%.2f".format(session.offsetY)}<gray>  " +
                    "steps <white>s${session.scaleStep}<gray>/<white>m${session.moveStep}<gray>  " +
                    "base <aqua>${"%.3f".format(1f / session.scaleX)}<gray>/" +
                    "<aqua>${"%.3f".format(1f / session.scaleY)}"
            )
        )
    }

    /**
     * Which way the calibration panel should face, snapped to a cardinal.
     *
     * It faces back at the player, so it is the opposite of the way they are looking: yaw 0 is
     * looking south, and the panel then wants to be a north face.
     */
    private fun cardinalFacing(yaw: Float): Face {
        val normalised = ((yaw % 360f) + 360f) % 360f
        return when {
            normalised < 45f || normalised >= 315f -> Face.NORTH
            normalised < 135f -> Face.EAST
            normalised < 225f -> Face.SOUTH
            else -> Face.WEST
        }
    }

    /** The direction the player is looking, given the face pointing back at them. */
    private fun lookDirection(face: Face): Triple<Int, Int, Int> = when (face) {
        Face.NORTH -> Triple(0, 0, 1)
        Face.SOUTH -> Triple(0, 0, -1)
        Face.EAST -> Triple(-1, 0, 0)
        Face.WEST -> Triple(1, 0, 0)
        else -> Triple(0, 0, 1)
    }

    private fun blockAhead(player: Player, face: Face): Location {
        val (dx, _, dz) = lookDirection(face)
        val eye = player.eyeLocation
        return Location(
            player.world,
            (eye.blockX + dx * 3).toDouble(),
            eye.blockY.toDouble(),
            (eye.blockZ + dz * 3).toDouble(),
        )
    }

    /**
     * Dead centre of one face of a block, pushed [out] blocks along its normal so a panel sitting
     * there does not z-fight with the surface.
     *
     * Yaw and pitch are left at zero deliberately - the entity's own rotation composes with the
     * transformation's, so all orientation has to come from the quaternion or nothing lines up.
     */
    private fun faceCentre(block: Location, face: Face, out: Double): Location {
        val bx = block.blockX.toDouble()
        val by = block.blockY.toDouble()
        val bz = block.blockZ.toDouble()
        return when (face) {
            Face.NORTH -> Location(block.world, bx + 0.5, by + 0.5, bz - out)
            Face.SOUTH -> Location(block.world, bx + 0.5, by + 0.5, bz + 1 + out)
            Face.EAST -> Location(block.world, bx + 1 + out, by + 0.5, bz + 0.5)
            Face.WEST -> Location(block.world, bx - out, by + 0.5, bz + 0.5)
            else -> Location(block.world, bx + 0.5, by + 1 + out, bz + 0.5)
        }
    }

    /** Perpendicular to the face, in the horizontal plane. */
    private fun sidewaysOffset(face: Face, distance: Double): org.bukkit.util.Vector = when (face) {
        Face.NORTH, Face.SOUTH -> org.bukkit.util.Vector(distance, 0.0, 0.0)
        else -> org.bukkit.util.Vector(0.0, 0.0, distance)
    }

    private fun common(display: Display) {
        display.brightness = Display.Brightness(15, 15)
        display.viewRange = 4f
        display.displayWidth = 8f
        display.displayHeight = 8f
        display.isPersistent = false
    }

    private fun line(raw: String) = mm.deserialize("<!italic>$raw")

    private companion object {
        val mm: MiniMessage = MiniMessage.miniMessage()
    }
}
