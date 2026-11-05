package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seated parts' colours and layers against the kit boards' CSS (shared building blocks, buttons, controls, keys): every colour within
 * 2 per channel of the kit board's on Graphite and Paper, every layer's offset, blur and alpha as the CSS (dp = px / 1.5).
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

    private fun dp(what: String, board: Float, ours: Dp) = assertEquals(what, board, ours.value, 0.01f)

    private fun layer(what: String, s: CssShadow, y: Float, blur: Float, alpha: Float, inset: Boolean, spread: Float = 0f) {
        dp("$what y", y, s.y)
        dp("$what blur", blur, s.blur)
        dp("$what spread", spread, s.spread)
        assertEquals("$what alpha", alpha, s.color.alpha, 0.005f)
        assertEquals("$what inset", inset, s.inset)
    }

    @Test
    fun graphiteColoursAreTheBoards() {
        val g = graphite
        near("lip 0%", 0x3C3F45, g.lipStops[0])
        near("lip 45%", 0x26282D, g.lipStops[1])
        near("lip 100%", 0x17181B, g.lipStops[2])
        near("well", 0x0F1012, g.well)
        near("cap top", 0x2A2C31, g.capStops[0])
        near("cap bottom", 0x232529, g.capStops[1])
        near("capDown top", 0x212327, g.capDownStops[0])
        near("capDown bottom", 0x1E2024, g.capDownStops[1])
        near("capOff", 0x18191C, g.capOff)
        near("side", 0x121316, g.side)
        near("field well", 0x0A0B0D, g.fieldWell)
        near("word key top", 0x33353B, g.wordCap.rest.stops[0], 3)
        near("word key bottom", 0x2B2D32, g.wordCap.rest.stops[1], 3)
        assertEquals(1f, g.lipEllipseHeight)
    }

    @Test
    fun paperColoursAreTheBoards() {
        val p = paper
        near("lip 0%", 0xFFFFFF, p.lipStops[0])
        near("lip 45%", 0xF1EDE6, p.lipStops[1])
        near("lip 100%", 0xE4DED3, p.lipStops[2])
        near("well", 0xD7D0C3, p.well)
        near("cap top", 0xF7F4EE, p.capStops[0])
        near("cap bottom", 0xEFEBE3, p.capStops[1])
        near("capDown top", 0xECE7DE, p.capDownStops[0])
        near("capOff", 0xE6E1D7, p.capOff)
        near("side", 0xD3CBBC, p.side)
        near("field well", 0xE0DBD0, p.fieldWell)
        assertEquals(0.9f, p.lipEllipseHeight)
    }

    @Test
    fun lipAndWellLayersAreTheCss() {
        val l = graphite.lipShadows
        assertEquals(6, l.size)
        layer("lip 1", l[0], 1.333f, 0.667f, 0.16f, true)
        layer("lip 2", l[1], -2f, 2.667f, 0.40f, true)
        layer("lip 3", l[2], 0f, 0f, 0.03f, true, 0.667f)
        layer("lip 4", l[3], -0.667f, 2f, 0.03f, false)
        layer("lip 5", l[4], 2f, 2.667f, 0.45f, false)
        layer("lip 6", l[5], 6.667f, 14.667f, 0.45f, false)
        val p = paper.lipShadows
        assertEquals(5, p.size)
        layer("paper lip 2", p[1], -2f, 2f, 0.08f, true)
        layer("paper lip 4", p[2], -1.333f, 2.667f, 0.8f, false)
        layer("paper lip 6", p[4], 6.667f, 14.667f, 0.14f, false)
        layer("well", graphite.wellShade, 0.667f, 1.333f, 0.6f, true)
        layer("paper well", paper.wellShade, 0.667f, 1.333f, 0.25f, true)
        layer("lit groove", graphite.focusShade, 0.667f, 1.333f, 0.35f, true)
        dp("collar glow", 14.667f, CollarDimens.glowBlur)
        dp("groove glow", 8f, CollarDimens.grooveGlowBlur)
    }

    @Test
    fun capsAreTheCss() {
        val c = graphite.buttonCap
        layer("cap light", c.rest.shadows[0], 0.667f, 0f, 0.16f, true)
        layer("cap side", c.rest.shadows[1], 2f, 0f, 1f, false)
        layer("cap drop", c.rest.shadows[2], 2.667f, 3.333f, 0.45f, false)
        layer("cap down", c.down.shadows[0], 1.333f, 2f, 0.22f, true)
        dp("press travel", 2f, c.travel)
        layer("paper cap drop", paper.buttonCap.rest.shadows[2], 2.667f, 3.333f, 0.20f, false)
        layer("off", graphite.offCap.rest.shadows[0], 0.667f, 1.333f, 0.18f, true)
        val a = graphite.buttonAccent(Color(0xFF3E7BE0))
        near("accentLight", 0x78A3E9, a.rest.stops[0])
        near("accent", 0x3E7BE0, a.rest.stops[1])
        near("accentDark", 0x264C8B, a.rest.shadows[1].color)
        layer("accent light", a.rest.shadows[0], 0.667f, 0f, 0.40f, true)
        layer("accent drop", a.rest.shadows[2], 2.667f, 3.333f, 0.35f, false)
        layer("accent press", a.down.shadows[0], 1.333f, 2.667f, 0.30f, true)
        val h = graphite.hintCap
        layer("hint side", h.rest.shadows[1], 1.333f, 0f, 1f, false)
        layer("hint drop", h.rest.shadows[2], 2f, 2.667f, 0.40f, false)
        layer("paper hint drop", paper.hintCap.rest.shadows[2], 2f, 2.667f, 0.18f, false)
        layer("hint pressed", h.down.shadows[0], 0.667f, 1.333f, 0.25f, true)
        dp("hint press", 1.333f, h.travel)
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
        dp("fill glow", 6.667f, d.fillGlowBlur)
        dp("knob glow", 9.333f, d.knobGlowBlur)
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
