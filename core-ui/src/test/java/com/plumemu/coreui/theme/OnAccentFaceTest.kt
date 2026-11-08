package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A label on an accent cap is white, as on the kit boards, while white reaches 3:1 on the cap's middle (the accent 15 % toward white);
 * otherwise the readable `onAccent`. Every other text keeps the 4.5:1 floor (`onAccent` itself is unchanged).
 */
class OnAccentFaceTest {
    private val white = Rgb(0xFFFFFF)

    private fun face(s: PlumThemeSettings) = PlumColors.derive(s.backgroundColor, s.textColor, s.accentColor)

    private fun whiteOnMiddle(accent: Rgb) = ColorMath.contrast(white, ColorMath.mix(accent, white, PlumColors.ACCENT_FACE_MIDDLE))

    @Test
    fun graphiteAndPaperPresetsAreWhite() {
        for (s in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            val c = face(s)
            assertEquals("${c.accent}", white, c.onAccentFace)
            assertEquals("white on the default blue #3A7CEB itself (WCAG: 1.05 / (0.2132 + 0.05))", 3.99, ColorMath.contrast(white, c.accent), 0.01)
        }
    }

    @Test
    fun aPaleCustomAccentFallsBackToTheReadableColour() {
        val c = PlumColors.derive(Rgb(0x111214), TextMode.Light.color, Rgb(0xF2E27A))
        assertTrue("white is under 3:1 on pale yellow", whiteOnMiddle(c.accent) < 3.0)
        assertEquals(c.onAccent, c.onAccentFace)
        assertTrue("and it is dark", c.onAccentFace != white)
    }

    @Test
    fun whiteOnlyWhereItReachesThreeToOne() {
        val accents = AccentPreset.entries.flatMap { p -> TextMode.entries.map { p.colorFor(it) } } + ColorSet.entries.map { it.accentColor }
        for (a in accents) {
            val c = PlumColors.derive(Rgb(0x111214), TextMode.Light.color, a)
            if (c.onAccentFace == white) assertTrue("$a: white at ${whiteOnMiddle(a)}", whiteOnMiddle(a) >= PlumColors.ON_ACCENT_FACE_MIN)
            else assertEquals("$a falls back", c.onAccent, c.onAccentFace)
        }
        // Every preset accent of a light theme (dark text) keeps the board's white.
        for (p in AccentPreset.entries.filter { it != AccentPreset.Mono }) {
            val a = p.colorFor(TextMode.Dark)
            assertTrue("$p on a light theme: ${whiteOnMiddle(a)}", whiteOnMiddle(a) >= PlumColors.ON_ACCENT_FACE_MIN)
        }
    }
}
