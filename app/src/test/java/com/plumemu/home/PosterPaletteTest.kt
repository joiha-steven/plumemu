package com.plumemu.home

import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.theme.BOX_BAND_BOTTOM
import com.plumemu.coreui.theme.BOX_BAND_TEXT
import com.plumemu.coreui.theme.BOX_BAND_TOP
import com.plumemu.coreui.theme.POSTER_PALETTE
import com.plumemu.coreui.theme.boxCapsAlpha
import com.plumemu.coreui.theme.posterLookIndex
import com.plumemu.coreui.theme.posterTitleKey
import androidx.compose.ui.graphics.luminance
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The palette of the drawn box front: contrast of every look, the pick of a look from a title. The cover does not follow the theme, so Graphite and Paper see the same colours. */
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

    /** The hash is part of the look, so it never changes silently: the same title always wears the same field. */
    @Test
    fun golden() {
        val expected = mapOf("Super Mario World" to 3, "Chrono Trigger" to 5, "Mega Man 2" to 8, "Mega Man 3" to 16, PosterEdgeCases.VIETNAMESE to 19)
        for ((title, index) in expected) assertEquals(title, index, posterLookIndex(title))
    }

    /** One character apart gives unrelated looks: neighbours in a list seldom share a field (23 looks: about 1 pair in 23 by chance). */
    @Test
    fun neighboursRarelyShareAField() {
        val titles = (1..300).map { "Mega Man $it" } + (1..300).map { "Game " + ('a' + it % 26) + (it / 26) }
        val same = titles.zipWithNext().count { (a, b) -> posterLookIndex(a) == posterLookIndex(b) }
        assertTrue("$same of ${titles.size - 1} neighbours share a field", same <= titles.size / 12)
        assertEquals("every look is reachable", POSTER_PALETTE.size, titles.map { posterLookIndex(it) }.toSet().size)
    }

    @Test
    fun titlesDifferingInOneCharacterGetDifferentLooks() {
        val pairs = listOf("Mega Man 2" to "Mega Man 3", "Sonic 1" to "Sonic 2")
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
    fun emptyTitleStillPicksALook() = assertTrue(posterLookIndex("") in POSTER_PALETTE.indices)

    /** The small line is the ink at 0.8 (more where that is too faint), and the band text sits on the band's two greys: both pass 4.5:1 on every field. */
    @Test
    fun smallLineAndBandTextAreReadable() {
        for ((index, look) in POSTER_PALETTE.withIndex()) {
            for (field in listOf(look.top, look.bottom, look.band)) {
                val a = boxCapsAlpha(look)
                val blended = Color(look.ink.red * a + field.red * (1 - a), look.ink.green * a + field.green * (1 - a), look.ink.blue * a + field.blue * (1 - a))
                assertTrue("look $index small line on $field is ${contrast(blended, field)}", contrast(blended, field) >= 4.5)
            }
        }
        for (band in listOf(BOX_BAND_TOP, BOX_BAND_BOTTOM)) assertTrue(contrast(BOX_BAND_TEXT, band) >= 4.5)
    }
}
