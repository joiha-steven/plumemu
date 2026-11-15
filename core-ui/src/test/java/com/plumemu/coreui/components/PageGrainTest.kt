package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PageGrainTest {
    private val page = 0xFF161A24.toInt()

    @Test
    fun sameInputGivesSamePixels() {
        assertArrayEquals(pageGrainPixels(page, 4), pageGrainPixels(page, 4))
    }

    @Test
    fun meanIsThePageColourWithinOne() {
        for (amplitude in listOf(4, 8, 12)) {
            val pixels = pageGrainPixels(page, amplitude)
            for ((shift, base) in listOf(16 to 0x16, 8 to 0x1A, 0 to 0x24)) {
                val mean = pixels.sumOf { ((it shr shift) and 0xFF).toLong() }.toDouble() / pixels.size
                assertTrue("channel at $shift, amplitude $amplitude: mean $mean vs $base", abs(mean - base) <= 1.0)
            }
        }
    }

    @Test
    fun amplitudeIsRespectedAndNoiseIsMonochromeAndOpaque() {
        val pixels = pageGrainPixels(page, 8)
        var lowest = 0
        var highest = 0
        for (p in pixels) {
            assertEquals(0xFF, p ushr 24)
            val dr = ((p shr 16) and 0xFF) - 0x16
            assertEquals(dr, ((p shr 8) and 0xFF) - 0x1A)
            assertEquals(dr, (p and 0xFF) - 0x24)
            lowest = minOf(lowest, dr)
            highest = maxOf(highest, dr)
        }
        assertEquals(-8, lowest)
        assertEquals(8, highest)
    }

    @Test
    fun offAddsNothing() {
        assertTrue(pageGrainPixels(page, PageGrain.Off.amplitude).all { it == page })
        assertEquals(0, PageGrain.Off.amplitudeFor(Color.White))
    }

    @Test
    fun lightPagesGetMoreNoiseForTheSameLook() {
        assertEquals(4, PageGrain.Light.amplitudeFor(Color(0xFF161A24)))
        assertEquals(6, PageGrain.Light.amplitudeFor(Color(0xFFECEBE5)))
        assertEquals(12, PageGrain.Strong.amplitudeFor(Color(0xFFECEBE5)))
    }

    /** Printed so the report can quote it; the bound only catches a build that turns slow by an order of magnitude. */
    @Test
    fun buildingATileIsFast() {
        pageGrainPixels(page, 8) // warm the JIT as a first draw would not, then measure a cold-ish second one
        val start = System.nanoTime()
        pageGrainPixels(0xFFECEBE5.toInt(), 12)
        val ms = (System.nanoTime() - start) / 1_000_000.0
        println("PAGE_GRAIN_TILE_PIXELS_MS=$ms")
        assertTrue("tile pixels took $ms ms", ms < 100)
    }
}
