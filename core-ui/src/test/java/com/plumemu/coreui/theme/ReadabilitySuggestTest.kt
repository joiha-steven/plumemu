package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** `Readability.suggest`: the colour offered when one is refused (design/rules.md > Suggesting a colour). */
class ReadabilitySuggestTest {
    private val lightPage = Rgb(0xECEBE5)
    private val darkPage = Rgb(0x161A24)
    private val yellowPage = Rgb(0xF2CF43)

    private fun hueGap(
        a: Double,
        b: Double,
    ): Double {
        val d = ((Math.toDegrees(a - b) % 360) + 360) % 360
        return minOf(d, 360 - d)
    }

    @Test
    fun aPassingColourComesBackUnchanged() {
        val accent = Rgb(0x2A64D8)
        assertTrue(Readability.passes(accent, lightPage, ColorRole.Accent))
        assertEquals(accent, Readability.suggest(accent, lightPage, ColorRole.Accent))
        val text = Rgb(0x1C1C1E)
        assertEquals(text, Readability.suggest(text, lightPage, ColorRole.Text))
    }

    @Test
    fun anAccentSuggestionReachesThreeAndAHalfToOne() {
        for ((colour, page) in listOf(Rgb(0xB0B0B0) to lightPage, Rgb(0x303848) to darkPage, Rgb(0xFFD400) to lightPage, Rgb(0xE8A0B0) to Rgb(0xFDE3D3))) {
            val got = Readability.suggest(colour, page, ColorRole.Accent)
            val ratio = ColorMath.contrast(got, page)
            assertTrue("$colour on $page gives $got at %.2f:1".format(ratio), ratio >= 3.5)
            assertTrue(Readability.passes(got, page, ColorRole.Accent))
        }
    }

    @Test
    fun aDarkYellowKeepsItsColourInsteadOfTurningOlive() {
        val start = Oklch.of(Rgb(0xFFD400))
        val got = Oklch.of(Readability.suggest(Rgb(0xFFD400), lightPage, ColorRole.Accent))
        val kept = got.c / start.c
        val turned = hueGap(start.h, got.h)
        assertTrue("kept %.0f%% of the chroma, turned %.0f degrees".format(kept * 100, turned), kept >= 0.85 || turned <= 40.0 + HUE_ROUNDING)
    }

    /** Yellow and orange turn toward red (marigold, rust), lime toward green: never yellow into green. */
    @Test
    fun aMuddyHueTurnsAwayFromYellow() {
        fun degrees(c: Rgb) = ((Math.toDegrees(Oklch.of(c).h) % 360) + 360) % 360
        for (colour in listOf(Rgb(0xFFD400), Rgb(0xFFB000))) {
            val got = Readability.suggest(colour, lightPage, ColorRole.Accent)
            assertTrue("${'$'}colour went to ${'$'}got", degrees(got) <= degrees(colour) + 1)
        }
        val lime = Rgb(0xC8F000)
        val got = Readability.suggest(lime, lightPage, ColorRole.Accent)
        assertTrue("${'$'}lime went to ${'$'}got", degrees(got) >= degrees(lime) - 1)
    }

    @Test
    fun aClashingAccentOnAColouredPageIsOfferedNearTheOppositeHue() {
        val clash = Rgb(0xF0C040)
        assertTrue("the page's own hue is refused", !Readability.passes(clash, yellowPage, ColorRole.Accent))
        val got = Readability.suggest(clash, yellowPage, ColorRole.Accent)
        val gap = hueGap(Oklch.of(yellowPage).h, Oklch.of(got).h)
        assertTrue("hue is %.0f degrees from the page's, wanted near 180".format(gap), gap >= 100.0)
        assertTrue(ColorMath.contrast(got, yellowPage) >= 3.5)
    }

    @Test
    fun aTextSuggestionIsQuietAndReadable() {
        for ((colour, page) in listOf(Rgb(0x8A8F99) to lightPage, Rgb(0xFF3030) to lightPage, Rgb(0x5050A0) to darkPage, Rgb(0xC0A020) to yellowPage)) {
            val got = Readability.suggest(colour, page, ColorRole.Text)
            val chroma = Oklch.of(got).c
            assertTrue("$colour on $page gives $got with chroma %.3f".format(chroma), chroma <= 0.04 + 0.005)
            assertTrue("$colour on $page gives $got which does not pass as text", Readability.passes(got, page, ColorRole.Text))
        }
    }

    private companion object {
        /** A hue read back from an 8-bit colour lands a fraction of a degree off the turn asked for. */
        const val HUE_ROUNDING = 1.0
    }
}
