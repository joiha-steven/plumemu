package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seated parts' colours and layers against the kit boards' CSS (shared building blocks, buttons, controls, keys): every colour within
 * 2 per channel of the kit board's on Graphite and Paper, every layer's offset, blur and alpha as the CSS (dp = px / 1.5), each at its [Depth]:
 * highlights, insets and tones at RAISE, solid thicknesses and travel at THICKNESS, soft shadows at SHADOW (alpha) and SHADOW_REACH (offset, blur).
 */
class SeatLookTest {
    private val graphite = PlumComposeColors.from(PlumColors.derive(Rgb(0x111214), TextMode.Light)).collar.seat
    private val paper = PlumComposeColors.from(PlumColors.derive(Rgb(0xECE8DF), TextMode.Dark)).collar.seat

    private fun near(what: String, board: Int, ours: Color, tol: Int = 2) {
        val o = ours.toArgb()
        for (shift in intArrayOf(16, 8, 0)) {
            val d = kotlin.math.abs(((board shr shift) and 0xFF) - ((o shr shift) and 0xFF))
            assertTrue("%s: board #%06X, ours #%06X".format(what, board, o and 0xFFFFFF), d <= tol)
        }
    }

    private fun lighter(rgb: Int): Int {
        val c = Color(rgb or 0xFF000000.toInt())
        return Color(c.red + (1f - c.red) * 0.04f, c.green + (1f - c.green) * 0.04f, c.blue + (1f - c.blue) * 0.04f).toArgb() and 0xFFFFFF
    }

    private fun dp(what: String, board: Float, ours: Dp) = assertEquals(what, board, ours.value, 0.01f)

    /** A tone the board makes by mixing the page toward [board] (its own colour): at [Depth.RAISE] it is that share of the way. */
    private fun raised(page: Int, board: Int, t: Float = Depth.RAISE) = lerp(Color(page or 0xFF000000.toInt()), Color(board or 0xFF000000.toInt()), t).toArgb() and 0xFFFFFF

    /** A tone the board mixes from the page toward [to] by [t], drawn here toward [to] by [t]. */
    private fun mixed(page: Int, to: Int, t: Double) = ColorMath.mix(Rgb(page), Rgb(to), t).value

    /** [y], [blur], [alpha] are the board's; the expected ones follow the layer's kind: a soft shadow, a solid thickness, or a highlight / inset / ring. */
    private fun layer(what: String, s: CssShadow, y: Float, blur: Float, alpha: Float, inset: Boolean, spread: Float = 0f) {
        val soft = !inset && blur > 0f
        val thickness = !inset && blur == 0f && spread == 0f
        dp("$what y", if (soft) y * Depth.SHADOW_REACH else if (thickness) y * Depth.THICKNESS else y, s.y)
        dp("$what blur", if (soft) blur * Depth.SHADOW_REACH else blur, s.blur)
        dp("$what spread", spread, s.spread)
        assertEquals("$what alpha", if (thickness) alpha else alpha * if (soft) Depth.SHADOW else Depth.RAISE, s.color.alpha, 0.005f)
        assertEquals("$what inset", inset, s.inset)
    }

    /** The field well is the tone ladder's sunken step of the page (a design change from the boards' #0A0B0D and #E0DBD0). */
    private fun sunken(page: Int): Int = ToneLadder.level(Rgb(page), ToneLadder.SUNKEN).let { (it.red shl 16) or (it.green shl 8) or it.blue }

    @Test
    fun graphiteColoursAreTheBoards() {
        val g = graphite
        near("lip 0%", raised(0x111214, 0x3C3F45), g.lipStops[0])
        near("lip 45%", raised(0x111214, 0x26282D), g.lipStops[1])
        near("lip 100%", raised(0x111214, 0x17181B), g.lipStops[2])
        near("well", raised(0x111214, 0x0F1012), g.well)
        // The cap is nearly flat now (a design change, not Depth): its top is 0.10 of the way from the page (the board's was 0.12).
        near("cap top", raised(0x111214, 0x2A2C31, 0.10f / 0.12f), g.capStops[0])
        near("cap bottom", 0x232529, g.capStops[1])
        near("capDown top", 0x212327, g.capDownStops[0])
        near("capDown bottom", 0x1E2024, g.capDownStops[1])
        near("capOff", 0x18191C, g.capOff)
        near("side", 0x121316, g.side)
        near("field well", sunken(0x111214), g.fieldWell)
        // The word key is the letter cap a step (4 %) lighter.
        near("word key top", lighter(raised(0x111214, 0x2A2C31, 0.10f / 0.12f)), g.wordCap.rest.stops[0], 3)
        near("word key bottom", lighter(0x232529), g.wordCap.rest.stops[1], 3)
        assertEquals(1f, g.lipEllipseHeight)
    }

    @Test
    fun paperColoursAreTheBoards() {
        val p = paper
        near("lip 0%", raised(0xECE8DF, 0xFFFFFF), p.lipStops[0])
        near("lip 45%", raised(0xECE8DF, 0xF1EDE6), p.lipStops[1])
        near("lip 100%", raised(0xECE8DF, 0xE4DED3), p.lipStops[2])
        near("well", raised(0xECE8DF, 0xD7D0C3), p.well)
        // The cap is lighter at the bottom and less warm at the top than the board's (a design change, not Depth): 0.5 toward the warm white, 0.42 toward white.
        near("cap top", raised(0xECE8DF, 0xF7F4EE, 0.5f / 0.6f), p.capStops[0])
        near("cap bottom", mixed(0xECE8DF, 0xFFFFFF, 0.42), p.capStops[1])
        near("capDown top", 0xECE7DE, p.capDownStops[0])
        near("capOff", 0xE6E1D7, p.capOff)
        near("side", 0xD3CBBC, p.side)
        near("field well", sunken(0xECE8DF), p.fieldWell)
        assertEquals(0.9f, p.lipEllipseHeight)
    }

    @Test
    fun lipAndWellLayersAreTheCss() {
        val l = graphite.lipShadows
        assertEquals(6, l.size)
        layer("lip 1", l[0], 1.333f * Depth.LIP, 0.667f, 0.16f, true)
        layer("lip 2", l[1], -2f * Depth.LIP, 2.667f * Depth.LIP, 0.40f, true)
        layer("lip 3", l[2], 0f, 0f, 0.03f, true, 0.667f)
        layer("lip 4", l[3], -0.667f, 2f, 0.03f, false)
        layer("lip 5", l[4], 2f, 2.667f, 0.45f, false)
        layer("lip 6", l[5], 6.667f, 14.667f, 0.45f, false)
        val p = paper.lipShadows
        assertEquals(5, p.size)
        layer("paper lip 2", p[1], -2f * Depth.LIP, 2f * Depth.LIP, 0.08f, true)
        layer("paper lip 4", p[2], -1.333f, 2.667f, 0.8f, false)
        layer("paper lip 6", p[4], 6.667f, 14.667f, 0.14f, false)
        layer("well", graphite.wellShade, 0.667f, 1.333f, 0.6f, true)
        layer("paper well", paper.wellShade, 0.667f, 1.333f, 0.25f, true)
        layer("lit groove", graphite.focusShade, 0.667f, 1.333f, 0.35f, true)
        dp("collar glow", 14.667f * Depth.GLOW_REACH, CollarDimens.glowBlur)
        dp("groove glow", 8f * Depth.GLOW_REACH, CollarDimens.grooveGlowBlur)
    }

    @Test
    fun capsAreTheCss() {
        val c = graphite.buttonCap
        // The cap highlight is brighter (a design change): 0.5 on the board, at Depth.RAISE.
        layer("cap light", c.rest.shadows[0], 0.667f, 0f, 0.5f, true)
        layer("cap side", c.rest.shadows[1], 2f, 0f, 1f, false)
        layer("cap drop", c.rest.shadows[2], 2.667f, 3.333f, 0.45f, false)
        layer("cap down", c.down.shadows[0], 1.333f, 2f, 0.22f, true)
        dp("press travel", 2f * Depth.THICKNESS, c.travel)
        layer("paper cap drop", paper.buttonCap.rest.shadows[2], 2.667f, 3.333f, 0.20f, false)
        layer("off", graphite.offCap.rest.shadows[0], 0.667f, 1.333f, 0.18f, true)
        val a = graphite.buttonAccent(Color(0xFF3E7BE0))
        near("accentLight", 0x78A3E9, a.rest.stops[0])
        near("accent", 0x3E7BE0, a.rest.stops[1])
        near("accentDark", 0x264C8B, a.rest.shadows[1].color)
        layer("accent light", a.rest.shadows[0], 0.667f, 0f, 0.8f, true) // brighter than the board's 0.40: a design change
        layer("accent drop", a.rest.shadows[2], 2.667f, 3.333f, 0.35f, false)
        layer("accent press", a.down.shadows[0], 1.333f, 2.667f, 0.30f, true)
        val h = graphite.hintCap
        layer("hint side", h.rest.shadows[1], 1.333f, 0f, 1f, false)
        layer("hint drop", h.rest.shadows[2], 2f, 2.667f, 0.40f, false)
        layer("paper hint drop", paper.hintCap.rest.shadows[2], 2f, 2.667f, 0.18f, false)
        layer("hint pressed", h.down.shadows[0], 0.667f, 1.333f, 0.25f, true)
        dp("hint press", 1.333f * Depth.THICKNESS, h.travel)
        val k = graphite.keyCap
        assertEquals("letter key has no drop", 2, k.rest.shadows.size)
        layer("key light", k.rest.shadows[0], 0.667f, 0f, 0.14f, true)
        val s = graphite.swatch(Color(0xFFE5484D))
        layer("swatch light", s.rest.shadows[0], 0.667f, 0f, 0.35f, true)
        layer("swatch thickness", s.rest.shadows[1], 2f, 0f, 0.30f, false)
        layer("swatch drop", s.rest.shadows[3], 2.667f, 3.333f, 0.35f, false)
        layer("paper swatch drop", paper.swatch(Color(0xFFE5484D)).rest.shadows[3], 2.667f, 3.333f, 0.158f, false)
        val d = graphite.choiceDot(Color(0xFF3E7BE0))
        assertEquals("choice dot has no drop", 2, d.rest.shadows.size)
        layer("choice thickness", d.rest.shadows[1], 1.333f, 0f, 1f, false)
        layer("field well", graphite.fieldShade, 2f, 4f, 0.55f, true)
        layer("paper field well", paper.fieldShade, 2f, 4f, 0.248f, true)
    }

    @Test
    fun sizesAreTheSpecs() {
        val d = SwitchSliderDimens
        dp("switch track w", 40f, d.switchTrackWidth)
        dp("switch track h", 22.667f, d.switchTrackHeight)
        dp("switch knob", 18.667f, d.switchKnob)
        dp("switch lip", 4f, d.switchLip)
        dp("switch lip bottom", 4.667f, d.switchLipBottom)
        dp("switch ring", 1.333f, d.switchFocusRing)
        dp("slider track", 8f, d.sliderTrack)
        dp("slider lip top", 4.667f, d.sliderLipTop)
        dp("slider lip sides", 5.333f, d.sliderLip)
        dp("slider lip bottom", 5.333f, d.sliderLipBottom)
        dp("slider corner", 10.667f, d.sliderLipCorner)
        dp("slider knob", 20f, d.sliderKnob)
        dp("slider knob ring", 1.333f, d.sliderKnobRing)
        dp("fill glow", 6.667f * Depth.GLOW_REACH, d.fillGlowBlur)
        dp("knob glow", 9.333f * Depth.GLOW_REACH, d.knobGlowBlur)
        dp("segment padding", 17.333f, ChoiceDimens.segmentPaddingX)
        dp("segment gap", 2.667f, ChoiceDimens.segmentGap)
        dp("swatch corner", 6.667f, ChoiceDimens.swatchCorner)
        dp("choice gap", 10.667f, ChoiceDimens.choiceGap)
        dp("hint gap", 17.333f, PlumDimens.hintGapBoard)
        dp("hint key gap", 5.333f, PlumDimens.hintKeyGap)
    }

    @Test
    fun otherThemesDeriveTheirOwn() {
        val navy = PlumComposeColors.from(PlumColors.derive(Rgb(0x0C1424), TextMode.Light)).collar.seat
        val purple = PlumComposeColors.from(PlumColors.derive(Rgb(0x1A1024), TextMode.Light)).collar.seat
        for ((name, s) in listOf("navy" to navy, "purple" to purple)) {
            assertTrue("$name lip lighter at the top", s.lipStops[0].red > s.lipStops[2].red)
            assertTrue("$name cap differs from Graphite", s.capStops[0] != graphite.capStops[0])
            assertTrue("$name dark recipe", s.dark)
        }
        assertTrue("navy lip is blue", navy.lipStops[1].blue > navy.lipStops[1].red + 0.05f)
        assertTrue("purple lip is purple", purple.lipStops[1].red > purple.lipStops[1].green)
    }
}
