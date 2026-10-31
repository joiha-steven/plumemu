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
            ColorSet.Super to mapOf("A" to (0xC8302B to white), "B" to (0xD9A400 to dark), "X" to (0x2B54B5 to white), "Y" to (0x2E8B4A to white)),
            ColorSet.Charcoal to mapOf("A" to (0x3E6FD8 to white), "B" to (0x2E9A47 to dark), "X" to (0xD9A400 to dark), "Y" to (0xD9A400 to dark)),
            ColorSet.Ivory to mapOf("A" to (0xCF3A3A to white), "B" to (0xD9A400 to dark), "X" to (0x2F6FDB to white), "Y" to (0x2E9A47 to dark)),
            ColorSet.Banana to mapOf("A" to (0xC8302B to white), "B" to (0x0E7C7C to white), "X" to (0x926B00 to white), "Y" to (0x926B00 to white)),
            ColorSet.Pocket to mapOf("A" to (0x8C1D4E to white), "B" to (0x8C1D4E to white), "X" to (0x8C1D4E to white), "Y" to (0x8C1D4E to white)),
        )

    @Test
    fun aBXAndYTakeTheirPrintedLettersColourInTheSetsThatHaveThem() {
        for ((set, letters) in expected) {
            val buttons = requireNotNull(set.buttons) { "$set has glyph colours" }
            val colors = colorsOf(set)
            for ((letter, want) in letters) {
                val look = keyCapLook(letter, buttons, colors)
                assertEquals("$set $letter fill", Rgb(want.first).toComposeColor(), look.fill)
                val melts = ColorMath.contrast(Rgb(want.first), set.backgroundColor) < 3.0
                val ring = if (melts) colors.chip else look.fill
                assertEquals("$set $letter ring: the fill's colour (the chip ring where the fill melts into the background)", ring, look.edge)
                assertEquals("$set $letter letter", want.second.toComposeColor(), look.letter)
            }
        }
    }

    @Test
    fun everyOtherKeyCapKeepsTheUsualRingAndText() {
        for (set in expected.keys) {
            val colors = colorsOf(set)
            for (cap in listOf("START", "SELECT", "L1 R1", "L1", "R1", "↑↓", "a", "AB", "")) {
                val look = keyCapLook(cap, set.buttons, colors)
                assertNull("$set '$cap' has no fill", look.fill)
                assertEquals("$set '$cap' ring", colors.chip, look.edge)
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
                assertEquals(colors.chip, look.edge)
                assertEquals(colors.text, look.letter)
            }
        }
        // Super with one colour changed by hand is Custom, but its buttons stay.
        val edited = themeOf(ColorSet.Super).copy(text = Rgb(0x000000))
        assertNull(ColorSet.matching(edited))
        assertEquals(ColorSet.Super.buttons, edited.buttonSet?.buttons)
        // No button set (Custom colours picked from nothing): the usual glyphs.
        assertNull(keyCapLook("A", edited.copy(buttonSet = null).buttonSet?.buttons, colorsOf(ColorSet.Super)).fill)
    }

    /** A fill under 3:1 on the background gets the `chip` ring; one at or above keeps its own colour as ring. */
    @Test
    fun aCapUnder3To1OnTheBackgroundGetsTheChipRing() {
        val buttons = ColorSet.Ivory.buttons!!
        // Ivory's yellow B on Banana's yellow, and Ivory's own background.
        val onBanana = colorsOf(ColorSet.Banana)
        assertTrue(ColorMath.contrast(buttons.b.fill, ColorSet.Banana.backgroundColor) < 3.0)
        val melting = keyCapLook("B", buttons, onBanana)
        assertEquals(buttons.b.fill.toComposeColor(), melting.fill)
        assertEquals(onBanana.chip, melting.edge)
        // The same set's blue X on Banana is far enough from it: no ring change.
        assertTrue(ColorMath.contrast(buttons.x.fill, ColorSet.Banana.backgroundColor) >= 3.0)
        assertEquals(buttons.x.fill.toComposeColor(), keyCapLook("X", buttons, onBanana).edge)
        // On Black every Ivory fill that reaches 3:1 keeps its own ring; the ones that do not get chip.
        val black = PlumComposeColors.from(PlumColors.derive(Rgb(0x000000), TextMode.Light, ColorSet.Plumemu.accentColor))
        for (letter in listOf("A", "B", "X", "Y")) {
            val fill = buttons.forKeyCap(letter)!!.fill
            val want = if (ColorMath.contrast(fill, Rgb(0x000000)) < 3.0) black.chip else fill.toComposeColor()
            assertEquals(letter, want, keyCapLook(letter, buttons, black).edge)
        }
    }

    /** "Confirm with B" swaps what the letters do, not their colours: the cap that reads B is B's colour. */
    @Test
    fun theColourFollowsTheLetterNotTheRole() {
        val buttons = ColorSet.Super.buttons!!
        val colors = colorsOf(ColorSet.Super)
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
