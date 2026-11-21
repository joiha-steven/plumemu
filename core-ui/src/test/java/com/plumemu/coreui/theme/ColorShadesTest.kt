package com.plumemu.coreui.theme

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorShadesTest {
    private val pages = BackgroundPreset.entries.map { it.color } + Rgb(0x161A24)

    private fun passesRole(
        role: ShadeRole,
        c: Rgb,
        page: Rgb,
    ) = when (role) {
        ShadeRole.Accent -> Readability.passes(c, page, ColorRole.Accent)
        ShadeRole.Text -> Readability.passes(c, page, ColorRole.Text)
        ShadeRole.Background -> Readability.backgroundAllowsText(c)
    }

    private fun lightness(c: Rgb) = Oklch.of(c).l

    private fun forEveryShadeList(check: (ShadeRole, Int, Rgb, List<Rgb>) -> Unit) {
        for (role in ShadeRole.entries) for (stop in 0 until ColorShades.stopCount(role)) for (page in pages) check(role, stop, page, ColorShades.shades(role, stop, page))
    }

    @Test
    fun everyShadePassesIsOrderedAndDistinct() =
        forEveryShadeList { role, stop, page, list ->
            val tag = "$role stop $stop on $page: $list"
            assertTrue(tag, list.size <= ColorShades.SHADE_COUNT)
            assertTrue("$tag unreadable", list.all { passesRole(role, it, page) })
            assertTrue("$tag order", list.zipWithNext().all { (a, b) -> lightness(a) > lightness(b) })
            assertEquals("$tag duplicates", list.size, list.toSet().size)
            assertEquals("$tag deterministic", list, ColorShades.shades(role, stop, page))
        }

    @Test
    fun accentOnBlackGivesFiveAtEveryStop() {
        for (stop in 0 until ColorShades.stopCount(ShadeRole.Accent)) assertEquals("stop $stop", 5, ColorShades.shades(ShadeRole.Accent, stop, Rgb(0x000000)).size)
    }

    @Test
    fun accentNearestShadeKeepsTheSetsMargin() {
        for (page in pages) for (stop in 0 until 24) {
            val list = ColorShades.shades(ShadeRole.Accent, stop, page)
            val nearest = if (TextMode.bestOn(page) == TextMode.Light) list.last() else list.first()
            assertTrue("$stop on $page", ColorMath.contrast(nearest, page) >= 3.45)
        }
    }

    @Test
    fun textFollowsThePageSide() {
        val paper = Rgb(0xECE8DF)
        val graphite = Rgb(0x111214)
        for (stop in 0 until 25) {
            assertTrue(ColorShades.shades(ShadeRole.Text, stop, paper).all { lightness(it) < lightness(paper) })
            assertTrue(ColorShades.shades(ShadeRole.Text, stop, graphite).all { lightness(it) > lightness(graphite) })
            assertTrue(ColorShades.shades(ShadeRole.Text, stop, paper).isNotEmpty())
            assertTrue(ColorShades.shades(ShadeRole.Text, stop, graphite).isNotEmpty())
        }
    }

    @Test
    fun aMiddleGreyPageHasNoText() {
        for (stop in 0 until 25) assertTrue(ColorShades.shades(ShadeRole.Text, stop, Rgb(0x777777)).isEmpty())
    }

    @Test
    fun backgroundIgnoresThePage() {
        for (stop in 0 until 25) assertEquals(ColorShades.shades(ShadeRole.Background, stop, Rgb(0)), ColorShades.shades(ShadeRole.Background, stop, Rgb(0xFFFFFF)))
    }

    @Test
    fun nearestStopRoundTripsEveryShade() =
        forEveryShadeList { role, stop, page, list ->
            // Two neighbouring hues can clip to the same pale colour; then either stop is right, so the colour must come back.
            list.forEach { c ->
                val (s2, i2) = ColorShades.nearestStopAndShade(role, c, page)
                assertEquals("$role $c on $page (stop $stop)", c, ColorShades.shades(role, s2, page)[i2])
                assertTrue("$role $c on $page: stop $s2 for $stop", s2 == stop || abs(s2 - stop) <= 1)
            }
        }

    @Test
    fun hueStopsStartAtRedAndIncreaseWithOneWrap() {
        val h = ColorShades.hueStops()
        assertEquals(24, h.size)
        assertTrue("red first: ${Math.toDegrees(h[0])}", Math.toDegrees(h[0]) in 20.0..40.0)
        assertTrue(h.all { it >= 0 && it < 2 * Math.PI })
        assertTrue("one wrap at most", h.zipWithNext().count { (a, b) -> b < a } <= 1)
    }

    private fun labDistance(
        a: Rgb,
        b: Rgb,
    ): Double {
        val x = Oklch.of(a)
        val y = Oklch.of(b)
        val da = x.c * Math.cos(x.h) - y.c * Math.cos(y.h)
        val db = x.c * Math.sin(x.h) - y.c * Math.sin(y.h)
        return Math.hypot(x.l - y.l, Math.hypot(da, db))
    }

    /** The old HSL-spaced strip's greens differed by 0.005 (stops 7 and 8); evenly spaced OKLCH hues keep every neighbour at 0.04 or more. */
    @Test
    fun neighbouringHuesOnTheStripAreClearlyDifferent() {
        val hues = (0 until ColorShades.HUE_COUNT).map { ColorShades.stripColor(ShadeRole.Accent, it) }
        val distances = hues.zipWithNext { a, b -> labDistance(a, b) }
        println("strip neighbour OKLab distance min ${distances.min()} max ${distances.max()}")
        assertTrue("closest neighbours ${distances.min()} at ${distances.indexOf(distances.min())}", distances.all { it >= 0.04 })
        val steps = ColorShades.hueStops().zipWithNext { a, b -> Math.toDegrees((b - a + 2 * Math.PI) % (2 * Math.PI)) }
        assertTrue("hue steps $steps", steps.all { Math.abs(it - 15.0) < 1e-6 })
    }

    @Test
    fun stopCountsAndStripColours() {
        assertEquals(25, ColorShades.stopCount(ShadeRole.Background))
        assertEquals(25, ColorShades.stopCount(ShadeRole.Text))
        assertEquals(24, ColorShades.stopCount(ShadeRole.Accent))
        assertEquals(25, (0 until 25).map { ColorShades.stripColor(ShadeRole.Text, it) }.toSet().size)
    }

    @Test
    fun noStripStopIsDarkerThanTheFloorAndYellowIsYellow() {
        for (role in ShadeRole.entries) for (stop in 0 until ColorShades.stopCount(role)) {
            val colour = ColorShades.stripColor(role, stop)
            val o = Oklch.of(colour)
            assertTrue("$role stop $stop $colour lightness ${o.l}", o.l >= ColorShades.STRIP_LIGHTNESS_FLOOR)
            if (!ColorShades.isGrey(role, stop)) {
                assertTrue("$role stop $stop $colour is washed out: chroma ${o.c}", o.c >= 0.10)
                assertTrue("$role stop $stop $colour is neon: chroma ${o.c}", o.c <= 0.19 + 0.01)
            } else {
                assertTrue("grey stop has colour: ${o.c}", o.c < 0.01)
            }
        }
        // Yellow (OKLCH hue about 110 degrees) is a light colour: olive is what a mid lightness turns it into.
        val yellow = ColorShades.hueStops().indices.minBy { Math.abs(Math.toDegrees(ColorShades.hueStops()[it]) - 110.0) }
        val y = Oklch.of(ColorShades.stripColor(ShadeRole.Accent, yellow))
        assertTrue("yellow stop lightness ${y.l}", y.l >= 0.88)
    }

    @Test
    fun shadesStayCheapOnTheSlowestPath() {
        val page = Rgb(0x161A24)
        repeat(50) { ColorShades.shades(ShadeRole.Text, 7, page) }
        val t = System.nanoTime()
        repeat(100) { ColorShades.shades(ShadeRole.Text, it % 25, page) }
        val ms = (System.nanoTime() - t) / 1e6 / 100
        println("shades(Text) mean $ms ms")
        assertTrue("mean $ms ms", ms < 5.0)
    }
}
