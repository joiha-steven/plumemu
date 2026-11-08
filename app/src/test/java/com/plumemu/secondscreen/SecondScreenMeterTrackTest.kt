package com.plumemu.secondscreen

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.BackgroundPreset
import com.plumemu.coreui.theme.ColorSet
import com.plumemu.coreui.theme.MeterTrackToken
import com.plumemu.coreui.theme.ToneLadder
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
 * An empty meter track against the V2 boards' shade (the tone ladder moved the well: it is the page's sunken step, not the boards' own
 * well colour, so the boards' rows are kept as shade strengths and laid over the new well), row by row as the browser renders them at 1.5 px per dp (the boards' `sunk`: its
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

    /**
     * The rows the code must draw: the boards' shade layers kept, laid over the tone ladder's sunken well instead of the boards' own well.
     * Each board row is `shade over old well` (the shade colour [shade] at a per-row strength) with the lit foot line on the last row; the
     * strength is read back from the board row and the old well, then applied to the new well, so only the well moved. Tolerance 2 per channel.
     */
    private fun assertOverTheSunkenWell(
        what: String,
        board: List<Int>,
        page: Rgb,
        oldWell: Rgb,
        dark: Boolean,
        ours: List<Int>,
    ) {
        assertEquals("$what: rows", board.size, ours.size)
        val newWell = ToneLadder.level(page, ToneLadder.SUNKEN)
        val foot = MeterTrackToken.BOTTOM_LINE.toDouble()
        for ((row, pair) in board.zip(ours).withIndex()) {
            for (shift in listOf(16, 8, 0)) {
                val shadeColour = if (dark) 0.0 else ((WARM_SHADOW.value shr shift) and 0xFF).toDouble()
                val wellOld = ((oldWell.value shr shift) and 0xFF).toDouble()
                val wellNew = ((newWell.value shr shift) and 0xFF).toDouble()
                var v = ((pair.first shr shift) and 0xFF).toDouble()
                val last = row == board.lastIndex
                if (last) v = (v - 255 * foot) / (1 - foot)
                var want = shadeColour + (v - shadeColour) * (wellNew - shadeColour) / (wellOld - shadeColour)
                if (last) want = want * (1 - foot) + 255 * foot
                val o = (pair.second shr shift) and 0xFF
                assertTrue(
                    "$what: row $row channel ${shift / 8}: wanted ${"%.1f".format(want)} (board shade over the sunken well #%06X), got $o".format(newWell.value),
                    kotlin.math.abs(o - Math.round(want).toInt()) <= 2,
                )
            }
        }
    }

    private fun mixed(
        from: Rgb,
        to: Rgb,
        amount: Double,
    ) = Rgb(
        listOf(16, 8, 0).fold(0) { acc, shift ->
            val a = (from.value shr shift) and 0xFF
            val b = (to.value shr shift) and 0xFF
            (acc shl 8) or Math.round(a + (b - a) * amount).toInt()
        },
    )

    /** The boards' old wells: Paper's page 7 % toward the warm shadow, Graphite's 40 % toward black. */
    private val paperOldWell = mixed(BackgroundPreset.Paper.color, WARM_SHADOW, 0.07)
    private val graphiteOldWell = mixed(GRAPHITE, Rgb(0x000000), 0.40)

    @Test
    fun paperGameCardTrackIsTheBoardsShadeOverTheSunkenWell() =
        assertOverTheSunkenWell(
            "paper game card",
            listOf(0xB9B0A3, 0xBDB5A8, 0xC2BBAE, 0xC9C2B5, 0xCEC7BC, 0xD2CCBF, 0xD5CEC3, 0xD6CFC5),
            BackgroundPreset.Paper.color,
            paperOldWell,
            dark = false,
            rows(BackgroundPreset.Paper.color, ColorSet.Daylight, SecondScreenGameCardDimens.meterHeight),
        )

    @Test
    fun paperSystemCardTrackIsTheBoardsShadeOverTheSunkenWell() =
        assertOverTheSunkenWell(
            "paper system card",
            listOf(0xB9B0A3, 0xBCB4A8, 0xC1BAAD, 0xC7C0B3, 0xCBC4B8, 0xCDC7BC),
            BackgroundPreset.Paper.color,
            paperOldWell,
            dark = false,
            rows(BackgroundPreset.Paper.color, ColorSet.Daylight, EntryCardDimens.meterHeight),
        )

    @Test
    fun graphiteGameCardTrackIsTheBoardsShadeOverTheSunkenWell() =
        assertOverTheSunkenWell(
            "graphite game card",
            listOf(0x040506, 0x050506, 0x060607, 0x060708, 0x070809, 0x08080A, 0x08090B, 0x121315),
            GRAPHITE,
            graphiteOldWell,
            dark = true,
            rows(GRAPHITE, ColorSet.Plumemu, SecondScreenGameCardDimens.meterHeight),
        )

    @Test
    fun graphiteSystemCardTrackIsTheBoardsShadeOverTheSunkenWell() =
        assertOverTheSunkenWell(
            "graphite system card",
            listOf(0x040506, 0x050506, 0x050607, 0x060708, 0x070709, 0x101112),
            GRAPHITE,
            graphiteOldWell,
            dark = true,
            rows(GRAPHITE, ColorSet.Plumemu, EntryCardDimens.meterHeight),
        )

    private companion object {
        /** The Graphite background preset, which the boards and the shots use. */
        val GRAPHITE = Rgb(0x111214)

        /** The boards' warm inset-shadow colour on light pages. */
        val WARM_SHADOW = Rgb(0x3C2814)
    }
}
