package com.plumemu.coreui.components

import com.plumemu.coreui.theme.ColorMath
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.TextMode
import com.plumemu.coreui.theme.toComposeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hint key caps in the ready-made sets' glyph colours, by printed letter. */
class KeyCapLookTest {
    private fun colorsOf(set: ColorSet): PlumComposeColors = PlumComposeColors.from(PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor))

    private fun themeOf(set: ColorSet) = PlumThemeSettings(background = set.background, text = set.textMode.color, accent = set.accent, buttonSet = set)

    private val white = Rgb(0xFFFFFF)
    private val dark = TextMode.Dark.color

    /** The ready-made colours by letter (docs/design/rules.md › Colour), with the letter colour on each. */
    private val expected: Map<ColorSet, Map<String, Pair<Int, Rgb>>> =
        mapOf(
            ColorSet.FrontLoader to mapOf("A" to (0xD22A35 to white), "B" to (0xD22A35 to white), "X" to (0x3A3B40 to white), "Y" to (0x3A3B40 to white)),
            ColorSet.SpiceCube to mapOf("A" to (0x2EA557 to dark), "B" to (0xD93A3E to white), "X" to (0xBDBCC8 to dark), "Y" to (0xBDBCC8 to dark)),
            ColorSet.Pocket to mapOf("A" to (0x8C1D4E to white), "B" to (0x8C1D4E to white), "X" to (0x8C1D4E to white), "Y" to (0x8C1D4E to white)),
            ColorSet.Lavender to mapOf("A" to (0x4E3591 to white), "B" to (0x4E3591 to white), "X" to (0xB2A5DB to dark), "Y" to (0xB2A5DB to dark)),
            ColorSet.RedAndGold to mapOf("A" to (0x9A1B2F to white), "B" to (0x9A1B2F to white), "X" to (0xC69A1E to dark), "Y" to (0xC69A1E to dark)),
            ColorSet.Ivory to mapOf("A" to (0xCF3A3A to white), "B" to (0xD9A400 to dark), "X" to (0x2F6FDB to white), "Y" to (0x2E9A47 to dark)),
            ColorSet.ColdGrey to mapOf("A" to (0x3E6FD8 to white), "B" to (0x2E9A47 to dark), "X" to (0xD9A400 to dark), "Y" to (0xD9A400 to dark)),
            ColorSet.Banana to mapOf("A" to (0xC8302B to white), "B" to (0x0E7C7C to white), "X" to (0x926B00 to white), "Y" to (0x926B00 to white)),
        )

    @Test
    fun aBXAndYTakeTheirPrintedLettersColourInTheSetsThatHaveThem() {
        for ((set, letters) in expected) {
            val buttons = requireNotNull(set.buttons) { "$set has glyph colours" }
            val colors = colorsOf(set)
            for ((letter, want) in letters) {
                val look = keyCapLook(letter, buttons, colors)
                assertEquals("$set $letter fill", Rgb(want.first).toComposeColor(), look.fill)
                assertEquals("$set $letter letter", want.second.toComposeColor(), look.letter)
            }
        }
    }

    @Test
    fun everyOtherKeyCapKeepsTheUsualKeyFaceAndText() {
        for (set in expected.keys) {
            val colors = colorsOf(set)
            for (cap in listOf("START", "SELECT", "L1 R1", "L1", "R1", "↑↓", "a", "AB", "")) {
                val look = keyCapLook(cap, set.buttons, colors)
                assertNull("$set '$cap' has no fill", look.fill)
                assertEquals("$set '$cap' text", colors.text, look.letter)
            }
        }
    }

    @Test
    fun setsWithoutGlyphColoursAndCustomColoursKeepTheUsualGlyphs() {
        for (set in ColorSet.entries.filter { it !in expected }) {
            assertNull("$set carries no glyph colours", set.buttons)
            val colors = colorsOf(set)
            for (cap in listOf("A", "B", "X", "Y")) {
                val look = keyCapLook(cap, themeOf(set).buttonSet?.buttons, colors)
                assertNull("$set $cap", look.fill)
                assertEquals(colors.text, look.letter)
            }
        }
        // Ivory with one colour changed by hand is Custom, but its buttons stay.
        val edited = themeOf(ColorSet.Ivory).copy(text = Rgb(0x000000))
        assertNull(ColorSet.matching(edited))
        assertEquals(ColorSet.Ivory.buttons, edited.buttonSet?.buttons)
        // No button set (Custom colours picked from nothing): the usual glyphs.
        assertNull(keyCapLook("A", edited.copy(buttonSet = null).buttonSet?.buttons, colorsOf(ColorSet.Ivory)).fill)
    }

    /** "Confirm with B" swaps what the letters do, not their colours: the cap that reads B is B's colour. */
    @Test
    fun theColourFollowsTheLetterNotTheRole() {
        val buttons = ColorSet.Ivory.buttons!!
        val colors = colorsOf(ColorSet.Ivory)
        val confirmWithA = keyCapLook("A", buttons, colors)
        val confirmWithB = keyCapLook("B", buttons, colors)
        assertEquals(buttons.a.fill.toComposeColor(), confirmWithA.fill)
        assertEquals(buttons.b.fill.toComposeColor(), confirmWithB.fill)
    }

    @Test
    fun everyGlyphsLetterHasAtLeast3To1OnItsFill() {
        for (set in ColorSet.entries) {
            val buttons = set.buttons ?: continue
            for (letter in listOf("A", "B", "X", "Y")) {
                val glyph = buttons.forKeyCap(letter)!!
                val contrast = ColorMath.contrast(glyph.letter, glyph.fill)
                assertTrue("$set $letter: letter on fill %.2f:1".format(contrast), contrast >= 3.0)
            }
        }
    }
}
