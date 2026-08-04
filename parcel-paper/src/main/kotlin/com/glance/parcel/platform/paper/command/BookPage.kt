package com.glance.parcel.platform.paper.command

/**
 * How much of a written book page is actually visible.
 *
 * A page shows a fixed number of lines and wraps long ones, and anything past the bottom is
 * **silently dropped** - no error, no ellipsis, it is simply not there. That is a bad failure mode
 * for a help book: the part that falls off is the end of the page, which is exactly where the
 * "here is what to do next" link goes.
 *
 * Minecraft measures the width in pixels against a variable-width font. Counting characters is an
 * approximation of that, deliberately a slightly pessimistic one - being told a page is close to
 * full when it has a few pixels spare costs a reworded line, while the opposite costs content
 * nobody ever sees.
 *
 * Pure, so the guard against overflowing is a unit test rather than someone opening every page.
 */
internal object BookPage {

    /** Lines a page displays before the rest is dropped. */
    const val MAX_LINES = 14

    /** Characters per line, as a stand-in for the real pixel width. */
    const val WIDTH = 19

    /** MiniMessage tags render as formatting, not as text, so they take no space. */
    private val TAG = Regex("<[^>]+>")

    /** How many lines [page] takes once tags are stripped and long lines wrap. */
    fun lines(page: String): Int = page.lines().sumOf { wrapped(TAG.replace(it, "").trimEnd()) }

    /** Blank lines still occupy one. */
    private fun wrapped(text: String): Int {
        if (text.isEmpty()) return 1

        var lines = 1
        var used = 0
        for (word in text.split(" ")) {
            // A word longer than the page wraps within itself rather than being pushed whole.
            val width = word.length
            when {
                used == 0 -> used = width
                used + 1 + width <= WIDTH -> used += 1 + width
                else -> {
                    lines++
                    used = width
                }
            }
            while (used > WIDTH) {
                lines++
                used -= WIDTH
            }
        }
        return lines
    }

    fun fits(page: String): Boolean = lines(page) <= MAX_LINES
}
