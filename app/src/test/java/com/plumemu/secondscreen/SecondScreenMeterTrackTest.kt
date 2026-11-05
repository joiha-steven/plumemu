package com.plumemu.secondscreen

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.EntryCardDimens
import com.plumemu.coreui.theme.SecondScreenGameCardDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * An empty meter track against the V2 boards, row by row as the browser renders them at 1.5 px per dp (the boards' `sunk`: its
 * `inset 0 3px 6px` shade over the `inset 0 -1px 0` lit line): Paper and Graphite, the game card's 8 px track and the system card's
 * 6 px one. The shade has a fixed size, so the thinner track is darker; one `meterTrack` token serves both. Tolerance 2 per channel.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SecondScreenMeterTrackTest {
    private val density = Density(1.5f)

    private fun rows(
        background: Rgb,
        set: ColorSet,
        height: androidx.compose.ui.unit.Dp,
    ): List<Int> {
        val colors = PlumComposeColors.from(PlumColors.derive(background, set.textMode, set.accentColor))
        val h = with(density) { height.roundToPx() }
        val bitmap = ImageBitmap(200, h)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, Canvas(bitmap), androidx.compose.ui.geometry.Size(200f, h.toFloat())) {
            drawMeter(colors, 0f)
        }
        val map = bitmap.toPixelMap()
        // The board's rows were read 12 px in from the track's right end, past the shade along the end.
        return (0 until h).map { map[200 - 12, it].toArgb() and 0xFFFFFF }
    }

    private fun assertBoard(
        what: String,
        board: List<Int>,
        ours: List<Int>,
    ) {
        assertEquals("$what: rows", board.size, ours.size)
        for ((row, pair) in board.zip(ours).withIndex()) {
            for (shift in listOf(16, 8, 0)) {
                val b = (pair.first shr shift) and 0xFF
                val o = (pair.second shr shift) and 0xFF
                assertTrue("$what: row $row is #%06X, board #%06X".format(pair.second, pair.first), kotlin.math.abs(o - b) <= 2)
            }
        }
    }

    @Test
    fun paperGameCardTrackMatchesTheBoard() =
        assertBoard(
            "paper game card",
            listOf(0xB9B0A3, 0xBDB5A8, 0xC2BBAE, 0xC9C2B5, 0xCEC7BC, 0xD2CCBF, 0xD5CEC3, 0xD6CFC5),
            rows(ColorSet.Paper.backgroundColor, ColorSet.Paper, SecondScreenGameCardDimens.meterHeight),
        )

    @Test
    fun paperSystemCardTrackMatchesTheBoard() =
        assertBoard(
            "paper system card",
            listOf(0xB9B0A3, 0xBCB4A8, 0xC1BAAD, 0xC7C0B3, 0xCBC4B8, 0xCDC7BC),
            rows(ColorSet.Paper.backgroundColor, ColorSet.Paper, EntryCardDimens.meterHeight),
        )

    @Test
    fun graphiteGameCardTrackMatchesTheBoard() =
        assertBoard(
            "graphite game card",
            listOf(0x040506, 0x050506, 0x060607, 0x060708, 0x070809, 0x08080A, 0x08090B, 0x121315),
            rows(GRAPHITE, ColorSet.Plumemu, SecondScreenGameCardDimens.meterHeight),
        )

    @Test
    fun graphiteSystemCardTrackMatchesTheBoard() =
        assertBoard(
            "graphite system card",
            listOf(0x040506, 0x050506, 0x050607, 0x060708, 0x070709, 0x101112),
            rows(GRAPHITE, ColorSet.Plumemu, EntryCardDimens.meterHeight),
        )

    private companion object {
        /** The Graphite background preset, which the boards and the shots use. */
        val GRAPHITE = Rgb(0x111214)
    }
}
