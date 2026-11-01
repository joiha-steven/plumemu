package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The halo's colour and strength (decision 0011, point 1) against the approved mockup's `halo()` maths. */
class FocusHaloTest {
    private val graphite = BackgroundPreset.Graphite.color
    private val paper = BackgroundPreset.Paper.color

    private fun look(hex: String) = FocusHalo.look(Rgb.fromHexOrNull(hex)!!, graphite)

    @Test
    fun defaultGreenKeepsItsColourAndAlmostFullStrength() {
        val halo = look("#7ED093")!!
        assertEquals("#7ED093", halo.colour.toHex())
        assertEquals(0.30, halo.peak, 0.01)
    }

    @Test
    fun aDarkAccentHasItsLightnessLiftedIntoTheClamp() {
        val halo = look("#3B2F8F")!!
        val (hue, _, lightness) = ColorMath.toHsl(halo.colour)
        val (accentHue, _, _) = ColorMath.toHsl(Rgb.fromHexOrNull("#3B2F8F")!!)
        assertEquals(FocusHalo.LIGHTNESS_MIN, lightness, 0.01)
        assertEquals(accentHue, hue, 0.01)
        assertEquals(FocusHalo.PEAK, halo.peak, 0.001)
    }

    @Test
    fun aBrightAccentGetsALowerAlpha() {
        val bright = look("#E6F23A")!!
        assertTrue("peak ${bright.peak}", bright.peak < look("#7ED093")!!.peak)
        assertEquals(FocusHalo.PEAK * FocusHalo.BRIGHT_FACTOR, bright.peak, 0.01)
        val (_, _, lightness) = ColorMath.toHsl(bright.colour)
        assertTrue(lightness <= FocusHalo.LIGHTNESS_MAX + 0.01)
    }

    @Test
    fun paperDrawsNoHalo() {
        assertNull(FocusHalo.look(Rgb.fromHexOrNull("#7ED093")!!, paper))
        assertNotNull(look("#7ED093"))
    }

    @Test
    fun theBandsFadeFromThePeakToNothing() {
        val steps = PlumDimens.FOCUS_HALO_STEPS
        val alphas = (0 until steps).map { FocusHalo.bandAlpha(0.32f, it, steps) }
        assertTrue(alphas.zipWithNext().all { (a, b) -> a > b })
        assertTrue(alphas.first() < 0.32f && alphas.first() > 0.25f)
        assertTrue(alphas.last() < 0.01f)
    }
}
