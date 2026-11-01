package com.plumemu.home

import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.theme.POSTER_PALETTE
import com.plumemu.coreui.theme.posterLookIndex
import com.plumemu.coreui.theme.posterTitleKey
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The poster palette: contrast of every look, the pick of a look from a title (golden values equal the Python reference). */
class PosterPaletteTest {
    // The palette and hash live in core-ui (`theme/PosterPalette.kt`, where the theme guard allows colour literals); the cover that uses them is in this module.

    private fun contrast(
        a: Color,
        b: Color,
    ): Double {
        val x = a.luminance().toDouble() + 0.05
        val y = b.luminance().toDouble() + 0.05
        return if (x > y) x / y else y / x
    }

    @Test
    fun hasTwentyThreeLooks() = assertEquals(23, POSTER_PALETTE.size)

    @Test
    fun inkPassesFourPointFiveAgainstEveryColour() {
        POSTER_PALETTE.forEachIndexed { index, look ->
            for ((name, colour) in listOf("top" to look.top, "bottom" to look.bottom, "band" to look.band)) {
                val ratio = contrast(look.ink, colour)
                assertTrue("look $index ink against $name is $ratio", ratio >= 4.5)
            }
        }
    }

    @Test
    fun golden() {
        val expected =
            mapOf(
                "Super Mario World" to 20,
                "Chrono Trigger" to 11,
                "Mega Man 2" to 19,
                "Mega Man 3" to 15,
                // Final Fantasy VI in katakana, then Vietnamese, accents and a stroked d.
                PosterEdgeCases.JAPANESE to 3,
                PosterEdgeCases.VIETNAMESE to 7,
                pokemon("Crystal") to 10,
                // U+20BB7 is outside the BMP (a surrogate pair): the hash steps over whole code points. Expected value computed with the Python reference (poly of the normalised title).
                text(0x47, 0x61, 0x6D, 0x65, 0x20, 0x20BB7, 0x20, 0x51, 0x75, 0x65, 0x73, 0x74) to 11,
                text(0x110, 0x1ea5, 0x74, 0x20, 0x52, 0x1eeb, 0x6e, 0x67) to 19,
            )
        for ((title, index) in expected) assertEquals(title, index, posterLookIndex(title))
    }

    @Test
    fun titlesDifferingInOneCharacterGetDifferentLooks() {
        val pairs =
            listOf("Mega Man 2" to "Mega Man 3", "Final Fantasy IV" to "Final Fantasy V", "Sonic 1" to "Sonic 2", "Tetris" to "Tetrix", "Zelda" to "Zeldb")
        for ((a, b) in pairs) assertNotEquals("$a / $b", posterLookIndex(a), posterLookIndex(b))
    }

    @Test
    fun keyIgnoresCaseAccentsPunctuationAndSpaces() {
        assertEquals(posterTitleKey("pokemon"), posterTitleKey(pokemon("").trim()))
        assertEquals("supermarioworld", posterTitleKey("Super Mario World!"))
        assertEquals("supermarioworld", posterTitleKey("  super-mario: world "))
        assertEquals(posterLookIndex("Zelda II"), posterLookIndex("zelda ii."))
    }

    @Test
    fun caseDoesNotChangeCjkTitles() {
        for (title in listOf(PosterEdgeCases.JAPANESE, text(0x5E7B, 0x60F3, 0x6C34, 0x6EF8), text(0xD55C, 0xAE00))) {
            assertEquals(title, title.uppercase(Locale.ROOT).lowercase(Locale.ROOT).uppercase(Locale.ROOT), title.uppercase(Locale.ROOT))
            assertEquals(posterLookIndex(title), posterLookIndex(title.uppercase(Locale.ROOT)))
            assertEquals(posterTitleKey(title), posterTitleKey(title.uppercase(Locale.ROOT)))
        }
    }

    @Test
    fun emptyTitleStillPicksALook() = assertEquals(0, posterLookIndex(""))
}
