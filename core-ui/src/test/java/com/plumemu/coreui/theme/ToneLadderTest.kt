package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The tone ladder: every step is the page moved in L*, keeping its OKLCH hue. */
class ToneLadderTest {
    private val pages = mapOf("dark" to Rgb(0x161A24), "light" to Rgb(0xECEBE5), "coloured" to Rgb(0xF2CF43))
    private val steps = mapOf("SUNKEN" to ToneLadder.SUNKEN, "SURFACE" to ToneLadder.SURFACE, "RAISED" to ToneLadder.RAISED, "TOP" to ToneLadder.TOP)

    private fun hueGap(
        a: Double,
        b: Double,
    ): Double {
        val d = ((Math.toDegrees(a - b) % 360) + 360) % 360
        return minOf(d, 360 - d)
    }

    @Test
    fun eachStepLandsWithinATenthOfAnL3OfItsTarget() {
        for ((pageName, page) in pages) {
            for ((stepName, step) in steps) {
                val target = (ColorMath.lightness(page) + step).coerceIn(0.0, 100.0)
                val got = ColorMath.lightness(ToneLadder.level(page, step))
                assertEquals("$pageName $stepName L*", target, got, 0.3)
            }
        }
    }

    @Test
    fun theStepsKeepThePagesOklchHueWhereItHasColour() {
        for ((pageName, page) in pages) {
            val own = Oklch.of(page)
            if (own.c <= 0.02) continue
            for ((stepName, step) in steps) {
                val moved = Oklch.of(ToneLadder.level(page, step))
                if (moved.c <= 0.02) continue
                val gap = hueGap(own.h, moved.h)
                // A pale chroma (the dark page's is 0.021) cannot hold 3 degrees in 8-bit channels: one level moves OKLab by about 0.003, 8 degrees at 0.02.
                val allowed = if (own.c >= 0.04) 3.0 else 5.0
                assertTrue("$pageName $stepName hue moved %.1f degrees, allowed %.0f".format(gap, allowed), gap <= allowed)
            }
        }
    }

    @Test
    fun theStepsGoTheWayTheirSignSays() {
        for ((pageName, page) in pages) {
            assertTrue("$pageName sunken is darker", ColorMath.lightness(ToneLadder.level(page, ToneLadder.SUNKEN)) < ColorMath.lightness(page))
            assertTrue("$pageName surface is lighter", ColorMath.lightness(ToneLadder.level(page, ToneLadder.SURFACE)) > ColorMath.lightness(page))
        }
    }

    @Test
    fun theLineGoesTowardTheText() {
        for ((pageName, page) in pages) {
            // The text a page takes is the one that reads on it: light text lifts the line, dark text sinks it.
            val text = TextMode.bestOn(page)
            val sign = if (text == TextMode.Light) 1 else -1
            val line = ToneLadder.line(page, text.color)
            assertEquals("$pageName line toward its text", ColorMath.lightness(page) + sign * ToneLadder.LINE, ColorMath.lightness(line), 0.5) // 8-bit steps on a dark page are about 0.4 L*
        }
    }
}
