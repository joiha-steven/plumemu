package com.plumemu.coreui.theme

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The cover's frame is the same glass on a dark, a mid-tone and a light page: lightness steps above the page, a crest, an edge. */
class CoverGlassFrameTest {
    private val red = Rgb(0x771F19)
    private val graphite = Rgb(0x111214)
    private val paper = Rgb(0xECE8DF)

    private fun seat(page: Rgb, dark: Boolean) = SeatLook.of(page, if (dark) Rgb(0xF2F0EA) else Rgb(0x1C1C1E), dark).coverGlass

    private fun lightness(argb: Int) = ColorMath.lightness(Rgb(argb and 0xFFFFFF))

    @Test
    fun onADarkOrMidTonePageTheFrameIsTheRecipesStepsAboveThePage() {
        for (page in listOf(red, graphite)) {
            val stops = seat(page, dark = true).lipStops.map { lightness(it.toArgb()) - ColorMath.lightness(page) }
            assertEquals(Depth.COVER_FRAME_TOP_L, stops[0], 0.6)
            assertEquals(Depth.COVER_FRAME_MID_L, stops[1], 0.6)
            assertEquals(Depth.COVER_FRAME_BOTTOM_L, stops[2], 0.6)
        }
    }

    @Test
    fun aNearWhitePageLeavesLittleRoomSoTheEdgeIsBrownAndTheFrameWhite() {
        val glass = seat(paper, dark = false)
        assertTrue(lightness(glass.lipStops[0].toArgb()) > ColorMath.lightness(paper))
        val edge = glass.lipShadows[1]
        assertTrue("the edge is an inset ring", edge.inset)
        assertEquals(CollarLook.SHADE_BROWN.toComposeColor().red, edge.color.red, 0.01f)
    }

    @Test
    fun onlyTheLipChanges() {
        val look = SeatLook.of(red, Rgb(0xF2F0EA), true)
        assertEquals(look.well, look.coverGlass.well)
        assertEquals(look.capStops, look.coverGlass.capStops)
        assertTrue("its soft drops stay", look.coverGlass.lipShadows.count { !it.inset } == look.lipShadows.count { !it.inset })
    }
}
