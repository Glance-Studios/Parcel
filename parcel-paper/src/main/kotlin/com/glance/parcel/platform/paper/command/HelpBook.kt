package com.glance.parcel.platform.paper.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta

/**
 * The guide, as a book you can read in game.
 *
 * Two books rather than one long one. The first is the daily job - get the tool, pick a mode,
 * select, save, look at it - and it ends by offering the second. A builder who only ever draws
 * simple regions can stop after seven pages and still be right.
 *
 * The second holds the things that have actually caused confusion: a region is a *list of parts*
 * rather than a shape, apply and append do different things, and flat regions are drawn as
 * something other than what they are. Important, but not day one.
 *
 * Opened virtually with [Player.openBook], so nothing is added to the inventory.
 */
internal object HelpBook {

    /** The daily job, in seven pages. */
    fun open(player: Player) = show(player, "Parcel", EVERYDAY)

    /** Everything else, opened from the last page of the first book. */
    fun openShapes(player: Player) = show(player, "Parcel - Shapes", SHAPES)

    private fun show(player: Player, title: String, pages: List<String>) {
        val book = ItemStack(Material.WRITTEN_BOOK)
        book.editMeta(BookMeta::class.java) { meta ->
            meta.title(mm.deserialize(title))
            meta.author(mm.deserialize("Glance Studios"))
            meta.pages(pages.map(mm::deserialize))
        }
        player.openBook(book)
    }

    private val mm: MiniMessage = MiniMessage.miniMessage()

    private val EVERYDAY: List<String> = listOf(

        // ------------------------------------------------------------------ everyday
        // 1 - what it is
        """
        <dark_gray><b>PARCEL</b>

        <reset>Regions of any shape,
        drawn in game.

        <dark_gray>Mark two corners, add
        the box, save it. Other
        plugins then bind to
        the region by name.

        <dark_gray>The next few pages are
        all you need day to
        day.
        """.trimIndent(),

        // 2 - the wand
        """
        <dark_gray><b>THE MARQUEE</b>

        <reset><click:run_command:'/mq tool'><u>/mq tool</u></click>

        <dark_gray>Left click a block
        <reset>corner 1
        <dark_gray>Right click a block
        <reset>corner 2

        <dark_gray>Sneak + left
        <dark_green>add<dark_gray> the box
        Sneak + right
        <dark_red>carve<dark_gray> it away
        """.trimIndent(),

        // 3 - modes
        """
        <dark_gray><b>FLAT vs VOLUME</b>

        <reset><click:suggest_command:'/mq mode '><u>/mq mode</u></click>

        <dark_green>Flat<dark_gray> ignores Y. The
        region spans the whole
        world height - use it
        for areas of the map.

        <dark_green>Volume<dark_gray> uses both
        corners' Y. A box.
        """.trimIndent(),

        // 4 - seeing it
        """
        <dark_gray><b>SEEING IT</b>

        <reset>Your selection always
        draws itself.

        <dark_green>green<dark_gray> added part
        <dark_red>red<dark_gray> carved part
        <dark_aqua>cyan<dark_gray> box you have
        marked but not yet
        committed

        <dark_gray>Nothing is saved yet.
        """.trimIndent(),

        // 5 - saving
        """
        <dark_gray><b>SAVING</b>

        <reset><click:suggest_command:'/mq save '><u>/mq save name</u></click>

        <dark_gray>Keeps it as a region
        and clears your
        marquee.

        You are now working
        on it - it renders,
        and the wand says its
        name.
        """.trimIndent(),

        // 6 - looking at a saved one
        """
        <dark_gray><b>LOOKING AT ONE</b>

        <reset><click:run_command:'/parcel menu'><u>/parcel menu</u></click>
        <dark_gray>every region, with
        click actions.

        <reset><click:suggest_command:'/parcel render '><u>render name</u></click>
        <dark_gray>draw it
        <reset><click:suggest_command:'/parcel goto '><u>goto name</u></click>
        <dark_gray>fly to it
        <reset><click:suggest_command:'/parcel undo '><u>undo name</u></click>
        <dark_gray>put it back

        <reset><click:suggest_command:'/parcel mark '><u>mark name</u></click>
        <dark_gray>work on it again
        <reset><click:run_command:'/parcel unmark'><u>unmark</u></click>
        <dark_gray>stop, and hide it
        """.trimIndent(),

        // 7 - the way on
        """
        <dark_gray><b>MORE</b>

        <reset>That is the daily job.

        <dark_gray>There is a second book
        for shapes that are not
        just a box - carving,
        reshaping a saved
        region, colours, and
        why a flat region looks
        the way it does.

        <reset><click:run_command:'/parcel help more'><u>Open it</u></click>
        """.trimIndent(),
    )

    private val SHAPES: List<String> = listOf(
        // 1 - what this is
        """
        <dark_gray><b>SHAPES</b>

        <reset>The other half.

        <dark_gray>For regions that are
        not a single box, and
        for changing ones you
        already saved.

        <reset><click:run_command:'/parcel help'><u>Back to the basics</u></click>
        """.trimIndent(),

        // 8 - why parts
        """
        <dark_gray><b>WHY PARTS</b>

        <reset>A region is not one
        shape. It is a list of
        parts, each either
        <dark_green>added<dark_gray> or <dark_red>carved<dark_gray>,
        applied in order.

        <dark_gray>Because order is kept,
        nothing is destroyed.
        Carve a hole, then add
        something back inside
        it. Later wins.
        """.trimIndent(),

        // 9 - the three verbs
        """
        <dark_gray><b>CHANGING ONE</b>

        <reset>Three different things:

        <dark_green>append<dark_gray> adds your
        selection to it. Carve
        into a region this way.

        <dark_red>apply<dark_gray> REPLACES its
        shape entirely.

        <dark_aqua>load<dark_gray> copies it into
        your selection.
        """.trimIndent(),

        // 10 - the round trip
        """
        <dark_gray><b>THE ROUND TRIP</b>

        <reset>load, edit, apply.

        <dark_gray>Loading copies a
        region in so you can
        see and change it,
        then apply saves it
        back.

        Loading locks nothing.
        Walk away and the
        region is untouched.
        """.trimIndent(),

        // 11 - undo
        """
        <dark_gray><b>IF IT GOES WRONG</b>

        <reset><click:suggest_command:'/mq undo'><u>/mq undo</u></click>
        <dark_gray>drops the last part of
        your selection.

        <reset><click:suggest_command:'/parcel undo '><u>/parcel undo name</u></click>
        <dark_gray>reverts a saved region
        to how it was. Ten
        steps, kept on disk.

        <reset><click:suggest_command:'/parcel delete '><u>delete name</u></click>
        <dark_gray>removes it. Asks first
        only if something is
        using it.

        <reset><click:run_command:'/parcel restore'><u>/parcel restore</u></click>
        <dark_gray>puts the last deleted
        one back. One step,
        lost on restart.
        """.trimIndent(),

        // 12 - rendering
        """
        <dark_gray><b>RENDERING</b>

        <reset><click:suggest_command:'/parcel render '><u>/parcel render name</u></click>

        <dark_gray>Draws its surface as
        solid panels.

        Touching regions merge
        - two boxes side by
        side render as one
        shape, with no wall
        between them.
        """.trimIndent(),

        // 13 - flat rendering
        """
        <dark_gray><b>FLAT REGIONS</b>

        <reset>A flat region reaches
        from bedrock to sky, so
        drawing it truthfully
        would bury you.

        <dark_gray>Instead you get one
        plane at your feet,
        following you as you
        move. It shows the
        footprint, not the
        real extent.

        <reset><click:suggest_command:'/parcel follow '><u>follow name</u></click>
        <dark_gray>pins it where it is,
        so you can back off
        and look at it.
        """.trimIndent(),

        // 14 - style
        """
        <dark_gray><b>COLOUR</b>

        <reset><click:suggest_command:'/parcel style '><u>/parcel style name</u></click>

        <dark_gray>Sliders for colour and
        opacity, kept per
        region.

        <reset>text<dark_gray> any colour
        <reset>block<dark_gray> stained glass
        <reset>wireframe<dark_gray> a grid,
        no entities at all
        """.trimIndent(),

        // 15 - sharing
        """
        <dark_gray><b>SHARED</b>

        <reset>One region can be used
        by several plugins at
        once.

        <dark_gray>Editing it changes it
        for all of them. That
        is the point - the
        shape is defined once.

        <reset><click:run_command:'/parcel menu'><u>/parcel menu</u></click>
        <dark_gray>shows who uses what.
        """.trimIndent(),

        // 16 - reference
        """
        <dark_gray><b>EVERYTHING ELSE</b>

        <reset><click:run_command:'/parcel'><u>/parcel</u></click>
        <dark_gray>region commands

        <reset><click:run_command:'/mq'><u>/mq</u></click>
        <dark_gray>selection commands

        <reset><click:run_command:'/parcel menu'><u>/parcel menu</u></click>
        <dark_gray>browse every region

        <reset><click:suggest_command:'/parcel goto '><u>/parcel goto</u></click>
        <dark_gray>fly to one
        """.trimIndent(),
    )
}
