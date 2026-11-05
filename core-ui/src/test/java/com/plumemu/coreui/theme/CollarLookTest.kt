package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The collar's colours derive for every colour set (and Graphite, Paper): the groove sinks under the lip, the lip stands off the background. */
class CollarLookTest {
    private fun rgb(color: Color) = Rgb(color.toArgb() and 0xFFFFFF)

    private fun themes(): List<Pair<String, PlumColors>> =
        ColorSet.entries.map { it.name to PlumColors.derive(it.backgroundColor, it.textMode, it.accentColor) } +
            listOf("Black" to PlumColors.derive(Rgb(0x000000), TextMode.Light, Rgb(0x4C8DFF)), "White" to PlumColors.derive(Rgb(0xFFFFFF), TextMode.Dark, Rgb(0xC8372B))) +
            listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper).map { (name, s) -> name to PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor) }

    private fun maxChannelGap(
        a: Rgb,
        b: Rgb,
    ) = maxOf(kotlin.math.abs(a.red - b.red), kotlin.math.abs(a.green - b.green), kotlin.math.abs(a.blue - b.blue))

    @Test
    fun theGrooveIsDarkerThanTheLipInEverySet() {
        for ((name, tokens) in themes()) {
            val look = PlumComposeColors.from(tokens).collar
            assertTrue("$name: groove darker than the lip's bottom band", ColorMath.luminance(rgb(look.groove)) < ColorMath.luminance(rgb(look.lipBottom)))
            assertTrue("$name: groove darker than the lip's body", ColorMath.luminance(rgb(look.groove)) < ColorMath.luminance(rgb(look.lipMid)))
            val top = ColorMath.luminance(rgb(look.lipTop))
            val mid = ColorMath.luminance(rgb(look.lipMid))
            val bottom = ColorMath.luminance(rgb(look.lipBottom))
            // Light at the top, dark at the bottom; a white background has no room above it, so top and body may be equal there.
            assertTrue("$name: lip tones go light to dark (top >= body >= bottom)", top >= mid && mid >= bottom)
            assertTrue("$name: the lip's top is strictly lighter than its bottom", top > bottom)
        }
    }

    @Test
    fun theLipStandsOffTheBackgroundByAtLeast6Of255() {
        // Either the lip's body or its bottom band (a white background has no room above it) must differ from the background,
        // and the groove must differ from the lip's body, in some channel by MIN_GAP.
        for ((name, tokens) in themes()) {
            val look = PlumComposeColors.from(tokens).collar
            val bg = tokens.bg
            val lip = maxOf(maxChannelGap(rgb(look.lipMid), bg), maxChannelGap(rgb(look.lipBottom), bg), maxChannelGap(rgb(look.lipTop), bg))
            assertTrue("$name: lip vs background gap $lip < $MIN_GAP", lip >= MIN_GAP)
            // The lip always sits between the background and the groove, so the groove is held apart from the lip (a black background has no room below it).
            val groove = minOf(maxChannelGap(rgb(look.groove), rgb(look.lipBottom)), maxChannelGap(rgb(look.groove), rgb(look.lipMid)))
            assertTrue("$name: groove vs lip gap $groove < $MIN_GAP", groove >= MIN_GAP)
        }
    }

    @Test
    fun theFocusedGrooveIsTheAccentAndTheOffFaceSitsBelowTheKey() {
        for ((name, tokens) in themes()) {
            val colors = PlumComposeColors.from(tokens)
            assertEquals("$name: focused groove is the accent", rgb(colors.accent), rgb(com.plumemu.coreui.components.grooveColor(colors.collar, 1f, null, colors.accent)))
            assertEquals("$name: resting groove", rgb(colors.collar.ring), rgb(com.plumemu.coreui.components.grooveColor(colors.collar, 0f, null, colors.accent)))
            assertEquals("$name: error wins over focus", rgb(colors.danger), rgb(com.plumemu.coreui.components.grooveColor(colors.collar, 1f, colors.danger, colors.accent)))
            val off = rgb(colors.keyOff.face)
            val key = rgb(colors.collar.cap)
            val groove = rgb(colors.collar.groove)
            val toGroove = ColorMath.luminance(groove)
            assertTrue("$name: off face is between the key and the groove", ColorMath.luminance(off).let { it in (minOf(toGroove, ColorMath.luminance(key)) - LUMA_SLACK)..(maxOf(toGroove, ColorMath.luminance(key)) + LUMA_SLACK) })
            assertTrue("$name: off has no edge and no shadow", colors.keyOff.shadow.alpha == 0f && colors.keyOff.edge == colors.keyOff.face)
        }
    }

    @Test
    fun theCapIsLighterThanTheGrooveAndOnLightThemesThanTheLip() {
        for ((name, tokens) in themes()) {
            val colors = PlumComposeColors.from(tokens)
            val look = colors.collar
            val cap = ColorMath.luminance(rgb(look.cap))
            assertTrue("$name: cap lighter than the groove", cap > ColorMath.luminance(rgb(look.groove)))
            if (TextMode.of(tokens.text, tokens.bg) == TextMode.Dark) {
                assertTrue("$name: on a light theme the cap is not darker than the lip body", cap >= ColorMath.luminance(rgb(look.lipMid)))
            }
            assertEquals("$name: collarCap is the cap", rgb(look.cap), rgb(colors.collarCap.faceTop))
        }
    }

    private companion object {
        const val MIN_GAP = 6

        // Compose's lerp blends in Oklab, so the middle colour's luminance is only about between the two.
        const val LUMA_SLACK = 0.01
    }
}
