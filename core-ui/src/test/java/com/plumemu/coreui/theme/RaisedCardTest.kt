package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The raised card (`RaisedCard`): on every ready-made set it sits between the background and the focused slab, and the text on it stays readable. */
class RaisedCardTest {
    private fun lum(c: Rgb) = ColorMath.luminance(c)

    @Test
    fun theCardIsBetweenTheBackgroundAndTheSlabOnEverySet() {
        val failures = mutableListOf<String>()
        for (set in ColorSet.entries) {
            val colors = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor)
            val dark = TextMode.of(colors.text, colors.bg) == TextMode.Light
            val card = lum(RaisedCard.fill(colors.bg, colors.text, dark))
            val slab = lum(FocusSlab.fill(colors.bg, colors.text, dark))
            val bg = lum(colors.bg)
            val between = if (dark) bg < card && card < slab else slab > card && card > bg
            if (!between) failures += "$set: bg=$bg card=$card slab=$slab"
            // The text colours keep 4.5:1 on the card (the slab's own rule is the stricter one).
            val contrast = (maxOf(card, lum(colors.text)) + 0.05) / (minOf(card, lum(colors.text)) + 0.05)
            if (contrast < 4.5) failures += "$set: text on card $contrast"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun edgeAndShadowSharesAreTheBoards() {
        // The PANEL border: white .14, top side .22 (Graphite); rgba(60,40,20,.06), top #FFFFFF (Paper).
        val dark = CardLook.of(Rgb(0x111214), Rgb(0xECE8DE), dark = true)
        assertEquals(Color.White.copy(alpha = 0.14f), dark.edge)
        assertEquals(Color.White.copy(alpha = 0.22f), dark.edgeTop)
        val light = CardLook.of(Rgb(0xECE8DF), Rgb(0x1C1C1E), dark = false)
        assertEquals(Color(0xFF3C2814).copy(alpha = 0.06f), light.edge)
        assertEquals(Color.White, light.edgeTop)
        assertEquals(0.67f, CardDimens.edge.value, 0.01f)
        assertTrue("a light theme's shadow is fainter", RaisedCard.shadowAlpha(false) < RaisedCard.shadowAlpha(true))
    }

    private fun near(
        expected: Int,
        actual: Rgb,
    ) {
        for ((e, a) in listOf(expected shr 16 to actual.red, (expected shr 8 and 0xFF) to actual.green, (expected and 0xFF) to actual.blue)) {
            assertEquals("${Integer.toHexString(expected)} vs $actual", e.toDouble(), a.toDouble(), 2.0)
        }
    }

    /**
     * The PANEL gradient resolved over the page: Graphite rgba(44,46,52,.92) to rgba(26,27,31,.94) over #111214 = #2A2C31 to #191A1E
     * (a cool blue-grey, blue 7 above red at the top); Paper #FBFAF7 to #F3F0EA.
     */
    @Test
    fun theCardGradientIsTheBoardsPanel() {
        val bg = Rgb(0x111214)
        val text = Rgb(0xECE8DE)
        near(0x2A2C31, RaisedCard.top(bg, text, dark = true))
        near(0x191A1E, RaisedCard.bottom(bg, text, dark = true))
        val top = RaisedCard.top(bg, text, dark = true)
        assertTrue("cool: $top", top.blue - top.red >= 5)
        val pbg = Rgb(0xECE8DF)
        val ptext = Rgb(0x1C1C1E)
        near(0xFBFAF7, RaisedCard.top(pbg, ptext, dark = false))
        near(0xF3F0EA, RaisedCard.bottom(pbg, ptext, dark = false))
    }

    /** A custom dark purple page derives its own card: lighter than the page at the top, still purple, never the Graphite grey. */
    @Test
    fun aCustomPurpleThemeDerivesItsCard() {
        val bg = Rgb(0x1E1030)
        val top = RaisedCard.top(bg, Rgb(0xEDE6F5), dark = true)
        val bottom = RaisedCard.bottom(bg, Rgb(0xEDE6F5), dark = true)
        assertTrue("$top", top.blue > top.green && top.red > top.green)
        assertTrue("$top above $bottom above the page", lum(top) > lum(bottom) && lum(bottom) > lum(bg))
    }
}
