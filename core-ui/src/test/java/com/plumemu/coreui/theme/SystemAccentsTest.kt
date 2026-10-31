package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Accent color › By system: the resolution of the system's colour on a background. */
class SystemAccentsTest {
    private val black = Rgb(0x000000)
    private val paper = BackgroundPreset.Paper.color
    private val colours = mapOf("dark" to Rgb(0x1B1B3A), "light" to Rgb(0xF4F3A0), "mid" to Rgb(0x7B5EA7), "red" to Rgb(0xE60012))

    @Test
    fun everySystemColourReadsAsAnAccentOnAnyBackground() {
        for (background in listOf(black, paper, Rgb(0xF2CD3C), Rgb(0x2A2B2E), Rgb(0x8C1D4E), Rgb(0xFFFFFF))) {
            val accents = SystemAccents(background, colours)
            for (id in colours.keys) {
                assertTrue("$id on ${background.value}", Readability.passes(accents.colorOf(id)!!, background, ColorRole.Accent))
            }
        }
    }

    @Test
    fun aColourThatAlreadyReadsIsKeptAndOneThatDoesNotMovesInLightnessOnly() {
        val onBlack = SystemAccents(black, colours)
        assertEquals(Rgb(0x7B5EA7), onBlack.colorOf("mid"))
        val moved = onBlack.colorOf("dark")!!
        assertTrue(moved != colours.getValue("dark"))
        assertEquals(ColorMath.toHsl(colours.getValue("dark")).first, ColorMath.toHsl(moved).first, 0.02)
    }

    @Test
    fun noSystemAndUnknownSystemsHaveNoColour() {
        val accents = SystemAccents(black, colours)
        assertNull(accents.colorOf(null))
        assertNull(accents.colorOf("no-such-system"))
    }

    @Test
    fun accentForUsesTheSystemColourOnlyForBySystemAndFallsBackOtherwise() {
        val accents = SystemAccents(black, colours)
        val fallback = AccentChoice.Preset(AccentPreset.Red)
        val bySystem = PlumThemeSettings(accent = AccentChoice.BySystem(fallback))
        assertEquals(Rgb(0x7B5EA7), bySystem.accentFor("mid", accents))
        assertEquals(bySystem.accentColor, bySystem.accentFor(null, accents))
        assertEquals(bySystem.accentColor, bySystem.accentFor("no-such-system", accents))
        assertEquals(AccentPreset.Red.colorFor(TextMode.Light), bySystem.accentColor)
        // A fixed accent ignores the system in view.
        val fixed = PlumThemeSettings(accent = fallback)
        assertEquals(fixed.accentColor, fixed.accentFor("mid", accents))
    }

    @Test
    fun aFallbackThatDoesNotReadMovesButStaysBySystem() {
        val onPaper = PlumThemeSettings(background = BackgroundChoice.Preset(BackgroundPreset.Paper), text = TextMode.Dark.color, accent = AccentChoice.BySystem(AccentChoice.Custom(Rgb(0xF4F3A0))))
        assertTrue(Readability.passes(onPaper.accentColor, paper, ColorRole.Accent))
        val settled = Readability.settle(paper, TextMode.Dark.color, onPaper.accent)
        assertTrue(settled.accent is AccentChoice.BySystem)
        assertTrue(settled.accentAdjusted)
    }

    @Test
    fun bySystemIsNoReadyMadeSetsAccentAndCannotFallBackToItself() {
        val plumemu = ColorSet.Plumemu
        val settings = PlumThemeSettings(accent = AccentChoice.BySystem(plumemu.accent))
        assertNull(ColorSet.matching(settings))
        assertThrows(IllegalArgumentException::class.java) { AccentChoice.BySystem(AccentChoice.BySystem(plumemu.accent)) }
    }
}
