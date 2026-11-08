package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawn box front is a saturated, mid-dark field with white ink on every page: the title keeps 4.5:1 and the code 3:1 on the
 * field's lightest part with the big letter and the grain at their strongest, for any system colour and hue turn; and the field
 * stands off every page (Graphite, Paper, the colour sets) as a picture does.
 */
class BoxFrontContrastTest {
    /** The sample systems, the lightest, darkest and most saturated colours the platform data has, and every accent a colour set or page has. */
    private val bases: List<Color> =
        (SystemAccents.SAMPLE + listOf(Rgb(0xC8C8D0), Rgb(0xF4C20D), Rgb(0x3DDC84), Rgb(0x6B4A2E), Rgb(0x1D4E9E), Rgb(0xFF0000), Rgb(0xFFFF00), Rgb(0xFFFFFF), Rgb(0x000000)) +
            ColorSet.entries.map { it.accentColor } + listOf(PlumThemeSettings.Graphite.accentColor, PlumThemeSettings.Paper.accentColor)).map { it.toComposeColor() }

    private fun rgb(c: Color) = Rgb(c.toArgb() and 0xFFFFFF)

    private fun lightestField(look: BoxFieldLook): Rgb = boxLightestPixel(ColorMath.mix(rgb(look.top), rgb(look.mark), BOX_MARK_ALPHA.toDouble()))

    private val titles = (0 until 60).map { "Title $it" }

    @Test
    fun titleAndCodeReadOnTheFieldForAnySystemColourAndTurn() {
        for (base in bases) {
            for (title in titles) {
                val look = boxFieldFor(base, title)
                val field = lightestField(look)
                val ratio = ColorMath.contrast(rgb(look.title), field)
                assertTrue("base ${rgb(base)} $title: title $ratio", ratio >= 4.5)
                val code = ColorMath.mix(field, rgb(look.title), BOX_CODE_ALPHA.toDouble())
                val codeRatio = ColorMath.contrast(code, field)
                assertTrue("base ${rgb(base)} $title: code $codeRatio", codeRatio >= 3.0)
            }
        }
    }

    /** The titled tile has no big letter: its white title keeps 4.5:1 and its year (the code's white at 80 %) 3:1 on the field's lightest pixel. */
    @Test
    fun tileTitleAndYearReadOnTheFieldsLightestPoint() {
        for (base in bases) {
            for (title in titles) {
                val look = boxFieldFor(base, title)
                val field = boxLightestPixel(rgb(look.top))
                val ratio = ColorMath.contrast(rgb(look.title), field)
                assertTrue("base ${rgb(base)} $title: tile title $ratio", ratio >= 4.5)
                val year = ColorMath.mix(field, rgb(look.title), BOX_CODE_ALPHA.toDouble())
                val yearRatio = ColorMath.contrast(year, field)
                assertTrue("base ${rgb(base)} $title: tile year $yearRatio", yearRatio >= 3.0)
            }
        }
    }

    @Test
    fun theFieldIsSaturatedAndLightToDark() {
        for (base in bases) {
            val look = boxFieldFor(base, "Zelda")
            val top = ColorMath.toHsl(rgb(look.top))
            val bottom = ColorMath.toHsl(rgb(look.bottom))
            val chromatic = ColorMath.toHsl(rgb(base)).second >= 0.12
            if (chromatic) assertTrue("base ${rgb(base)} saturation ${top.second}", top.second >= 0.5)
            assertTrue("base ${rgb(base)}: bottom ${bottom.third} darker than top ${top.third}", bottom.third < top.third - 0.08)
            assertTrue("base ${rgb(base)}: mid dark ${ColorMath.luminance(rgb(look.top))}", ColorMath.luminance(rgb(look.top)) <= 0.2)
        }
    }

    @Test
    fun theFieldStandsOffEveryPage() {
        val pages = listOf(PlumThemeSettings.Graphite.backgroundColor, PlumThemeSettings.Paper.backgroundColor) + ColorSet.entries.map { it.backgroundColor }
        // A light page shows a dark card plainly (2.5:1); a dark page keeps a mid-dark card apart by lightness (1.5:1).
        for (base in bases) {
            val top = rgb(boxFieldFor(base, "Zelda").top)
            for (page in pages) {
                val ratio = ColorMath.contrast(top, page)
                val light = ColorMath.luminance(page) > 0.35
                assertTrue("base ${rgb(base)} on page $page: $ratio", ratio >= if (light) 2.5 else 1.5)
            }
        }
    }

    @Test
    fun theHueTurnStaysWithinTheSpread() {
        val turns = (0 until 300).map { boxHueTurn("Title $it") }
        assertTrue(turns.all { it in -BOX_HUE_SPREAD..BOX_HUE_SPREAD })
        assertTrue("turns differ: ${turns.toSet().size}", turns.toSet().size > 20)
        assertTrue(boxHueTurn("Zelda") == boxHueTurn("zelda"))
    }
}
