package com.glance.parcel.platform.paper.visual.panel

import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Styles are the other thing written to disk, and like regions a bad read is silent - you get the
 * wrong colour rather than an error, which nobody investigates.
 */
class StyleStoreTest {

    @TempDir
    lateinit var dir: File

    private fun store() = StyleStore(File(dir, "styles.yml"))

    private val key = NamespacedKey("parcel", "tavern")

    private val style = PanelStyle(
        primitive = PanelPrimitive.WIREFRAME,
        red = 12,
        green = 200,
        blue = 7,
        alpha = 128,
        gridSpacing = 2.5f,
        particleSize = 0.8f,
        follow = false,
    )

    @Test
    @DisplayName("a style survives a round trip through disk")
    fun roundTrip() {
        store().set(key, style)

        val reloaded = store().apply { load() }.get(key)
        assertEquals(style, reloaded)
    }

    @Test
    @DisplayName("follow survives as false, not silently defaulted back to true")
    fun falseFollowSurvives() {
        // Booleans that default true are the classic silent-loss case: a missing key and a stored
        // `false` both read as "not true" unless the write actually happened.
        store().set(key, style.copy(follow = false))
        assertFalse(store().apply { load() }.get(key)!!.follow)
    }

    @Test
    @DisplayName("an unknown primitive falls back rather than throwing")
    fun unknownPrimitiveFallsBack() {
        File(dir, "styles.yml").writeText(
            """
            parcel:tavern:
              primitive: HOLOGRAM
              red: 1
              green: 2
              blue: 3
              alpha: 4
            """.trimIndent()
        )

        val loaded = store().apply { load() }.get(key)
        assertEquals(PanelPrimitive.TEXT, loaded?.primitive)
        assertEquals(1, loaded?.red)
    }

    @Test
    @DisplayName("clearing removes it")
    fun clearRemoves() {
        val s = store()
        s.set(key, style)
        assertTrue(s.clear(key))
        assertNull(s.get(key))
        assertNull(store().apply { load() }.get(key), "and stays gone after a reload")
    }

    @Test
    @DisplayName("styles for different regions do not overwrite each other")
    fun keysAreIndependent() {
        val s = store()
        val other = NamespacedKey("motif", "tavern")
        s.set(key, style)
        s.set(other, style.copy(red = 255))

        val reloaded = store().apply { load() }
        assertEquals(12, reloaded.get(key)?.red)
        assertEquals(255, reloaded.get(other)?.red)
    }

    @Test
    @DisplayName("a missing file is empty, not an error")
    fun missingFileIsEmpty() {
        assertNull(store().apply { load() }.get(key))
    }
}
