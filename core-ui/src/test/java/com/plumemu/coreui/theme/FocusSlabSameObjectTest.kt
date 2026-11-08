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

    /** Dark pages are lit by the gained dark steps; light pages by the three small light steps (the pane stays below white so the white rim shows). */
    @Test
    fun theFillStandsOffEveryPageByItsThemesSteps() {
        for ((bg, isDark) in backgrounds) {
            val (top, mid, bottom) = FocusSlab.stops(bg, isDark)
            val wantTop = if (isDark) FocusSlab.FILL_TOP_L * gain(bg) else FocusSlab.LIGHT_TOP_L
            val wantMid = if (isDark) FocusSlab.FILL_MID_L * gain(bg) else FocusSlab.LIGHT_MID_L
            val wantBottom = if (isDark) FocusSlab.FILL_BOTTOM_L * gain(bg) else FocusSlab.LIGHT_BOTTOM_L
            assertEquals("top on $bg", wantTop, step(top, bg), STEP_ROOM)
            assertEquals("mid on $bg", wantMid, step(mid, bg), STEP_ROOM)
            assertEquals("foot on $bg", wantBottom, step(bottom, bg), STEP_ROOM)
        }
    }

    @Test
    fun aMidTonePageIsLitByTheSameLightStepsAsAPaleOne() {
        for (bg in listOf(Rgb(0x8C93A0), Rgb(0xE8B84B))) {
            val (top, _, bottom) = FocusSlab.stops(bg, dark = false)
            assertEquals("top on $bg", FocusSlab.LIGHT_TOP_L, step(top, bg), STEP_ROOM)
            assertEquals("foot on $bg", FocusSlab.LIGHT_BOTTOM_L, step(bottom, bg), STEP_ROOM)
            assertTrue("top on $bg is lighter than the page", ColorMath.lightness(top) > ColorMath.lightness(bg))
        }
    }

    /** A shadow on every page, dark or light: black on dark, the brown tint on light; never a glow. */
    @Test
    fun theLiftIsAShadowOnEveryPage() {
        assertEquals(Rgb(0x000000), FocusSlab.liftInk(true))
        assertEquals(FocusSlab.shadowTint(false), FocusSlab.liftInk(false))
        for ((bg, isDark) in backgrounds) {
            val ink = FocusSlab.liftInk(isDark)
            val far = ColorMath.mix(bg, ink, FocusSlab.liftAlpha(bg, isDark, FocusSlab.LIFT_FAR_L).toDouble())
            assertEquals("far on $bg", FocusSlab.LIFT_FAR_L, step(far, bg), STEP_ROOM)
            assertTrue("the lift darkens $bg", ColorMath.lightness(far) < ColorMath.lightness(bg))
        }
    }

    /** The rim is white on every page: [FocusSlab.RIM_SIDE_L] above the fill on a dark page, white itself on a light one. */
    @Test
    fun theRimIsWhiteAndSitsItsStepAboveTheFillOnDarkPages() {
        for ((bg, isDark) in backgrounds) {
            val fill = FocusSlab.fill(bg, bg, isDark)
            assertEquals(Rgb(0xFFFFFF), FocusSlab.edgeInk(isDark))
            val rim = FocusSlab.edgeOver(fill, bg, isDark)
            if (isDark) {
                assertEquals("rim above the fill on $bg", FocusSlab.RIM_SIDE_L, ColorMath.lightness(rim) - ColorMath.lightness(fill), STEP_ROOM)
            } else {
                assertEquals("rim on light $bg is white", Rgb(0xFFFFFF), rim)
            }
        }
    }

    @Test
    fun theRimIsLitAtItsCornersAsFarAsWhiteGoes() {
        val fill = FocusSlab.fill(dark, dark, true)
        assertEquals(FocusSlab.RIM_LIT_L, step(ColorMath.mix(fill, Rgb(0xFFFFFF), FocusSlab.topAlpha(fill)), fill), STEP_ROOM)
        val paper = FocusSlab.fill(light, light, false)
        assertTrue("Paper's corners reach white or its L* step", FocusSlab.topAlpha(paper) > 0.5)
        val look = SlabLook.of(dark, Rgb(0xFFFFFF), true)
        assertTrue("dark sides weaker than the lit corners", look.edge.alpha < look.top.alpha)
        assertEquals("light sides are full white", 1f, SlabLook.of(light, Rgb(0x1C1C1E), false).edge.alpha, 0f)
    }

    /** White leaves a white page no fill to show: the white rim has nothing to show against, so only the shadow carries the slab. */
    @Test
    fun aWhitePageKeepsTheShadow() {
        val white = Rgb(0xFFFFFF)
        assertEquals("rim is white", white, FocusSlab.edgeOver(FocusSlab.fill(white, white, false), white, false))
        assertTrue("lift", FocusSlab.liftAlpha(white, false, FocusSlab.LIFT_FAR_L) > 0f)
    }

    private companion object {
        /** One 8-bit level moves L* by up to about this. */
        const val STEP_ROOM = 0.8
    }
}
