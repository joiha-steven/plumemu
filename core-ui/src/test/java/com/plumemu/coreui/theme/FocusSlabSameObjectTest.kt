package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The slab is one object on every background: each layer's step in perceived lightness, whatever the colour set. */
class FocusSlabSameObjectTest {
    private val light = Rgb(0xECE8DF)
    private val dark = Rgb(0x111214)

    /** Presets, and custom backgrounds on either side of the middle. */
    private val backgrounds = listOf(dark to true, light to false, Rgb(0x1238B0) to true, Rgb(0x5A5F6B) to true, Rgb(0x8C93A0) to false, Rgb(0xE8B84B) to false)

    private fun step(
        a: Rgb,
        b: Rgb,
    ) = abs(ColorMath.lightness(a) - ColorMath.lightness(b))

    private fun gain(bg: Rgb) = 1 + FocusSlab.DARK_GAIN * (1 - ColorMath.lightness(bg) / 100)

    @Test
    fun theFillStandsOffGraphiteAndPaperByTheSameStep() {
        for ((bg, isDark) in listOf(dark to true, Rgb(0x1238B0) to true, Rgb(0x5A5F6B) to true, light to false)) {
            val (top, _, bottom) = FocusSlab.stops(bg, isDark)
            assertEquals("top on $bg", FocusSlab.FILL_TOP_L * (if (isDark) gain(bg) else 1.0), step(top, bg), STEP_ROOM)
            assertEquals("foot on $bg", FocusSlab.FILL_BOTTOM_L * (if (isDark) gain(bg) else 1.0), step(bottom, bg), STEP_ROOM)
        }
    }

    @Test
    fun aMidTonePageIsFrostedAtLeastAsMuch() {
        for (bg in listOf(Rgb(0x8C93A0), Rgb(0xE8B84B))) {
            val (top, _, bottom) = FocusSlab.stops(bg, dark = false)
            assertTrue("top on $bg", step(top, bg) >= FocusSlab.FILL_TOP_L)
            assertTrue("foot on $bg", step(bottom, bg) >= FocusSlab.FILL_BOTTOM_L)
        }
    }

    @Test
    fun theLiftMovesThePageByTheSameStepEitherWay() {
        for ((bg, isDark) in backgrounds) {
            val ink = FocusSlab.liftInk(isDark)
            val far = ColorMath.mix(bg, ink, FocusSlab.liftAlpha(bg, isDark, FocusSlab.LIFT_FAR_L).toDouble())
            assertEquals("far on $bg", FocusSlab.LIFT_FAR_L, step(far, bg), STEP_ROOM)
            assertTrue("a dark page is lit, a light one shaded", (ColorMath.lightness(far) > ColorMath.lightness(bg)) == isDark)
        }
    }

    @Test
    fun theRimSitsTheSameStepOffThePage() {
        for ((bg, isDark) in backgrounds) {
            val fill = FocusSlab.fill(bg, bg, isDark)
            assertEquals("rim on $bg", FocusSlab.RIM_SIDE_L, step(FocusSlab.edgeOver(fill, bg, isDark), bg), STEP_ROOM)
        }
    }

    @Test
    fun theRimIsLitAtItsCornersAsFarAsWhiteGoes() {
        val fill = FocusSlab.fill(dark, dark, true)
        assertEquals(FocusSlab.RIM_LIT_L, step(ColorMath.mix(fill, Rgb(0xFFFFFF), FocusSlab.topAlpha(fill)), fill), STEP_ROOM)
        val paper = FocusSlab.fill(light, light, false)
        assertEquals("Paper's corners are as white as it gets", 1.0, FocusSlab.topAlpha(paper), 0.0)
        val look = SlabLook.of(dark, Rgb(0xFFFFFF), true)
        assertTrue("sides far weaker than the lit corners", look.edge.alpha < look.top.alpha / 2)
    }

    /** White leaves a white page no fill to show: the rim and the lift still mark the slab. */
    @Test
    fun aWhitePageKeepsTheRimAndTheLift() {
        val white = Rgb(0xFFFFFF)
        val fill = FocusSlab.fill(white, white, false)
        assertEquals("rim", FocusSlab.RIM_SIDE_L, step(FocusSlab.edgeOver(fill, white, false), white), STEP_ROOM)
        assertTrue("lift", FocusSlab.liftAlpha(white, false, FocusSlab.LIFT_FAR_L) > 0f)
    }

    private companion object {
        /** One 8-bit level moves L* by up to about this. */
        const val STEP_ROOM = 0.8
    }
}
