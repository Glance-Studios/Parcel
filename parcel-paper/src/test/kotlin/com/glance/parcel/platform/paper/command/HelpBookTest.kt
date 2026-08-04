package com.glance.parcel.platform.paper.command

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * A written book page drops whatever does not fit, without complaint.
 *
 * So an overlong page loses its last lines - which is where the next step and the link to the other
 * book live. None of that is visible while writing the page. It looked fine right up until somebody
 * asked why the book never said how to reach book two.
 */
class HelpBookTest {

    @Test
    @DisplayName("every page fits on a page")
    fun everyPageFits() {
        val tooLong = HelpBook.BOOKS.flatMap { (title, pages) ->
            pages.withIndex()
                .filter { !BookPage.fits(it.value) }
                .map { "$title page ${it.index + 1}: ${BookPage.lines(it.value)} lines" }
        }

        assertTrue(
            tooLong.isEmpty(),
            "over ${BookPage.MAX_LINES} lines, so the end is silently dropped:\n" +
                tooLong.joinToString("\n"),
        )
    }
}
