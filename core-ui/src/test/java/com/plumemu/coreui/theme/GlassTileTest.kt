package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The second screen's glass tile, `line` and accent-light tokens against the V2 boards' values. */
class GlassTileTest {
    private fun colorsOf(set: ColorSet) = PlumColors.derive(set.backgroundColor, set.textMode, set.accentColor)

    private fun isDark(colors: PlumColors) = TextMode.of(colors.text, colors.bg) == TextMode.Light

    private fun assertNear(
        what: String,
        expected: Int,
        actual: Color,
        slack: Int = 2,
    ) {
        val argb = actual.toArgb()
        val channels = listOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
        val want = listOf((expected shr 16) and 0xFF, (expected shr 8) and 0xFF, expected and 0xFF)
        for (i in 0..2) assertTrue("$what: channel $i is ${channels[i]}, board ${want[i]}", kotlin.math.abs(channels[i] - want[i]) <= slack)
    }

    @Test
    fun graphiteGlassIsTheBoardsWhiteAtItsThreeAlphas() {
        val colors = colorsOf(ColorSet.Plumemu)
        val glass = GlassLook.of(colors.bg, isDark(colors))
        assertEquals(0.17f, glass.top.alpha, 0.003f)
        assertEquals(0.07f, glass.mid.alpha, 0.003f)
        assertEquals(0.045f, glass.foot.alpha, 0.003f)
        assertEquals(0.14f, glass.edge.alpha, 0.003f)
        assertEquals(0.42f, glass.edgeTop.alpha, 0.003f)
        assertEquals(0xFFFFFF, glass.top.toArgb() and 0xFFFFFF)
        // The board's `inset 0 1px 1px rgba(255,255,255,0.30)` and `inset 0 -10px 18px rgba(0,0,0,0.20)`.
        assertEquals(0.30f, glass.highlight.alpha, 0.003f)
        assertEquals(0.20f, glass.shade.alpha, 0.003f)
        assertEquals(0x000000, glass.shade.toArgb() and 0xFFFFFF)
    }

    @Test
    fun paperGlassRunsFromWhiteToTheBoardsFoot() {
        val colors = colorsOf(ColorSet.Paper)
        val glass = GlassLook.of(colors.bg, isDark(colors))
        assertNear("top", 0xFFFFFF, glass.top, slack = 0)
        assertNear("foot", 0xFAF8F3, glass.foot, slack = 0)
        // The board's `border: 1px solid rgba(60,40,20,0.06); border-top-color: #FFFFFF`.
        assertEquals(0.06f, glass.edge.alpha, 0.003f)
        assertEquals(0x3C2814, glass.edge.toArgb() and 0xFFFFFF)
        assertEquals(Color.White, glass.edgeTop)
        assertEquals(1f, glass.foot.alpha, 0f)
        // The board's `inset 0 -4px 8px rgba(60,40,20,0.04)`, and no lit line.
        assertEquals(0.04f, glass.shade.alpha, 0.003f)
        assertEquals(0x3C2814, glass.shade.toArgb() and 0xFFFFFF)
        assertEquals(0f, glass.highlight.alpha, 0f)
    }

    @Test
    fun lineIsTheTextAtTheBoardsShare() {
        val graphite = colorsOf(ColorSet.Plumemu)
        val paper = colorsOf(ColorSet.Paper)
        val dark = LineToken.of(graphite.text, dark = true)
        val light = LineToken.of(paper.text, dark = false)
        assertEquals(0.10f, dark.alpha, 0.003f)
        assertEquals(0.12f, light.alpha, 0.003f)
        assertEquals(graphite.text.toComposeColor().toArgb() and 0xFFFFFF, dark.toArgb() and 0xFFFFFF)
        assertEquals(paper.text.toComposeColor().toArgb() and 0xFFFFFF, light.toArgb() and 0xFFFFFF)
    }

    @Test
    fun everySetHasAGlassBetweenItsBackgroundAndWhite() {
        val failures = mutableListOf<String>()
        for (set in ColorSet.entries) {
            val colors = colorsOf(set)
            val glass = GlassLook.of(colors.bg, isDark(colors))
            if (glass.top.alpha < glass.foot.alpha) failures += "$set: top fainter than foot"
            if (!isDark(colors) && ColorMath.luminance(Rgb(glass.foot.toArgb() and 0xFFFFFF)) < ColorMath.luminance(colors.bg)) failures += "$set: foot darker than the page"
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun meterTrackWellIsTheBoardsSunkColour() {
        // The boards' `sunk`: #E0DBD0 on Paper, #0A0B0D on Graphite (ours sits on the theme's #0E0F11). The inset shadow over it is drawn
        // by the meter and pinned by the shot measurements (game card 2, system card 2 on Paper; see the work log).
        assertNear("paper well", 0xE0DBD0, MeterTrackToken.of(colorsOf(ColorSet.Paper).bg, dark = false), slack = 2)
        assertNear("graphite well", 0x0A0B0D, MeterTrackToken.of(Rgb(0x0E0F11), dark = true), slack = 3)
    }

    @Test
    fun paperPlayedColumnMatchesTheBoardOverItsTile() {
        // The board's `muted` #9A958B at opacity 0.55 over the tile where the bars stand (#FBFBF8 in our shot); the rendered board is 199,195,188
        // on the game card and 199,195,187 on Recent. Tolerance 2 per channel.
        val paper = colorsOf(ColorSet.Paper)
        val onTile = PlayedBarToken.of(paper.bg, dark = false).compositeOver(Color(0xFFFBFBF8))
        assertNear("paper played column, game card", 0xC7C3BC, onTile, slack = 2)
        assertNear("paper played column, Recent", 0xC7C3BB, onTile, slack = 2)
        assertEquals(0.55f, PlayedBarToken.of(paper.bg, dark = false).alpha, 0.003f)
        assertNear("paper played column itself", 0x9A958B, PlayedBarToken.of(paper.bg, dark = false).copy(alpha = 1f), slack = 1)
    }

    @Test
    fun graphitePlayedColumnMatchesTheBoardOverItsTile() {
        // The board's `muted` #6F737B at opacity 0.55: 75,77,84 on the game card and 75,77,83 on Recent, over the glass beside the bars
        // (32,34,38 and 32,33,35 in the rendered board, the same in our shot). Tolerance 3.
        // On the Graphite background preset, which the shots and the boards use.
        val token = PlayedBarToken.of(Rgb(0x111214), dark = true)
        assertNear("graphite played column itself", 0x6F737B, token.copy(alpha = 1f), slack = 1)
        assertNear("graphite played column, game card", 0x4B4D54, token.compositeOver(Color(0xFF202226)), slack = 3)
        assertNear("graphite played column, Recent", 0x4B4D53, token.compositeOver(Color(0xFF202123)), slack = 3)
    }

    @Test
    fun accentLightIsTheBoardsLift() {
        // The boards' `lift`: each channel + (255 - c) * 0.3, rgb(120,163,233) for the blue #3E7BE0 (a meter's far end, the latest bar's head).
        // SeatLookTest checks the colour itself on the accent cap.
        assertEquals(0.3f, ACCENT_LIGHT_SHARE, 0f)
    }
}
