package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** (The slider's glow plus its collar shadow measures 13.5 dp in the slider_glow shot, the board's 13.3.) How far the light-theme focus glows reach (to 2 of 255 on Paper, where the accent is 174 away in red) against the board's measured reach. */
class LightGlowReachTest {
    /** Distance (dp) at which an accent of [peak] over a [sigma] dp blur drops below 2.5 of 255 on Paper. */
    private fun reach(
        sigma: Float,
        peak: Float,
    ): Float {
        var d = 0f
        while (peak * 174f * FocusHalo.softEdge(d, sigma) >= 2.5f) d += 0.1f
        return d
    }

    @Test
    fun sliderGlowReachesAbout12DpAlone() = assertEquals(12.5f, reach(CollarDimens.sliderLightGlowSigma.value, FocusHalo.LIGHT_PART_PEAK.toFloat()), 1f)

    @Test
    fun coverHazeReachesTheBoardsTwentyDp() = assertEquals(20f, reach(CollarDimens.coverLightGlowSigma.value, CollarDimens.COVER_LIGHT_GLOW_ALPHA), 1.5f)

    @Test
    fun coverHazePeakIsTheBoardsThirtyFive() = assertEquals(35f, 0.5f * CollarDimens.COVER_LIGHT_GLOW_ALPHA * 174f, 2f)
}
