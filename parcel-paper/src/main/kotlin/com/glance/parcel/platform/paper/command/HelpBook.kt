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
 * A wall of command lines does not explain Parcel, because the confusing parts are not the commands
 * - they are the concepts. A region is a *list of parts* rather than a shape; apply and append do
 * different things; flat regions are drawn as something other than what they are. Every one of
 * those has caused real confusion, so the book leads with them and lists commands afterwards.
 *
 * Opened virtually with [Player.openBook], so nothing is added to the inventory.
 */
internal object HelpBook {

    fun open(player: Player) {
        val book = ItemStack(Material.WRITTEN_BOOK)
        book.editMeta(BookMeta::class.java) { meta ->
            meta.title(mm.deserialize("Parcel"))
            meta.author(mm.deserialize("Glance Studios"))
            meta.pages(PAGES.map(mm::deserialize))
        }
        player.openBook(book)
    }

    private val mm: MiniMessage = MiniMessage.miniMessage()

    private val PAGES: List<String> = listOf(
        // 1 - what it is
        """
        <dark_gray><b>PARCEL</b>

        <reset>Regions of any shape,
        built by adding and
        cutting away boxes.

        <dark_gray>A region is not one
        shape. It is a list of
        parts, each either
        <dark_green>added<dark_gray> or <dark_red>carved<dark_gray>,
        applied in order.
        """.trimIndent(),

        // 2 - why that matters
        """
        <dark_gray><b>WHY PARTS</b>

        <reset>Because order is kept,
        nothing is destroyed.

        <dark_gray>Carve a hole, then add
        something back inside
        it. Later wins.

        Undo is exact - it
        just drops the last
        part.
        """.trimIndent(),

        // 3 - the wand
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

        // 4 - modes
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

        // 5 - seeing it
        """
        <dark_gray><b>SEEING IT</b>

        <reset>Your selection always
        draws itself.

        <dark_green>green<dark_gray> added part
        <dark_red>red<dark_gray> carved part
        <dark_aqua>cyan<dark_gray> the box you have
        marked but not yet
        committed

        <dark_gray>Nothing is saved yet.
        """.trimIndent(),

        // 6 - saving
        """
        <dark_gray><b>SAVING</b>

        <reset><click:suggest_command:'/mq save '><u>/mq save name</u></click>

        <dark_gray>Keeps it as a region
        and clears your
        selection.

        It renders straight
        away, so the outline
        is replaced by the
        real thing.
        """.trimIndent(),

        // 7 - the three verbs
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

        // 8 - the round trip
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

        // 9 - undo
        """
        <dark_gray><b>IF IT GOES WRONG</b>

        <reset><click:suggest_command:'/mq undo'><u>/mq undo</u></click>
        <dark_gray>drops the last part of
        your selection.

        <reset><click:suggest_command:'/parcel undo '><u>/parcel undo name</u></click>
        <dark_gray>reverts a saved region
        to how it was. Ten
        steps, kept on disk.
        """.trimIndent(),

        // 10 - rendering
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

        // 11 - flat rendering
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
        """.trimIndent(),

        // 12 - style
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

        // 13 - sharing
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

        // 14 - reference
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
