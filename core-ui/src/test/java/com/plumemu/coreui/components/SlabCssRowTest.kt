package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.exp

/**
 * A focused slab drawn at the boards' scale (1.5 px per dp, so a pixel is a board px), checked against its CSS composited by hand: the
 * `linear-gradient` at the top, middle and bottom (the bottom darkened by the inset `0 -10px 18px` black 0.20 on dark, `0 -4px 8px` brown 0.04 on
 * light), the border's top and side colours, and the drops making the page below darker than above.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabCssRowTest {
    private val density = 1.5f
    private val left = 60
    private val top = 120
    private val width = 630
    private val height = 70

    private class Shot(val page: IntArray, val px: (Int, Int) -> IntArray)

    private fun shot(settings: PlumThemeSettings): Shot {
        val c = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
        val page = Color(settings.backgroundColor.toArgb() or 0xFF000000.toInt())
        val image = ImageBitmap(760, 320)
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(760f, 320f)) {
            drawRect(page)
            val outline = Outline.Rounded(RoundRect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat(), 20f, 20f))
            drawSlabOutside(outline, c.slab, c.slabGlow, under = c.bg)
            drawSlabFill(outline, c.slab, onCard = false)
            drawSlabFace(outline, c)
        }
        val map = image.toPixelMap()
        return Shot(rgb(page)) { x, y -> rgb(map[x, y]) }
    }

    private fun rgb(c: Color) = IntArray(3) { (c.toArgb() shr (16 - 8 * it)) and 0xFF }

    /** The CSS gradient (stops at 0, 46 %, 100 %) at board row [y] of the slab, over a page of [page]: white alphas (dark) or opaque stops moved toward the page (light). */
    private fun cssFill(
        dark: Boolean,
        page: IntArray,
        y: Int,
    ): DoubleArray {
        val t = (y + 0.5) / height
        return if (dark) {
            val a = if (t <= 0.46) 0.17 + (0.07 - 0.17) * t / 0.46 else 0.07 + (0.045 - 0.07) * (t - 0.46) / 0.54
            DoubleArray(3) { page[it] + (255 - page[it]) * a }
        } else {
            // White moved 0.18 (top) to 0.55 (bottom) of the way toward the page.
            val toward = 0.18 + (0.55 - 0.18) * t
            DoubleArray(3) { 255 + (page[it] - 255) * toward }
        }
    }

    /** The standard normal CDF (Abramowitz and Stegun 7.1.26). */
    private fun phi(x: Double): Double {
        val z = abs(x) / Math.sqrt(2.0)
        val k = 1 / (1 + 0.3275911 * z)
        val erf = 1 - (((((1.061405429 * k - 1.453152027) * k) + 1.421413741) * k - 0.284496736) * k + 0.254829592) * k * exp(-z * z)
        return if (x >= 0) (1 + erf) / 2 else (1 - erf) / 2
    }

    private fun assertNear(
        what: String,
        expected: DoubleArray,
        actual: IntArray,
        tolerance: Double = 2.0,
    ) {
        for (ch in 0..2) assertEquals("$what channel $ch: ${actual.toList()} vs ${expected.map { Math.round(it) }}", expected[ch], actual[ch].toDouble(), tolerance)
    }

    private fun checkFill(
        settings: PlumThemeSettings,
        dark: Boolean,
        insetDrop: Double,
        insetSigma: Double,
        inset: DoubleArray,
    ) {
        val s = shot(settings)
        val x = left + width / 2
        for (row in listOf(5, 35)) assertNear("row $row", cssFill(dark, s.page, row), s.px(x, top + row))
        // Near the bottom the inset shadow shows: its hole is the padding box moved up, blurred; the colour is laid over the gradient.
        val row = height - 5
        val holeBottom = height - 1 + insetDrop
        val a = inset[3] * phi((row + 0.5 - holeBottom) / insetSigma)
        val under = cssFill(dark, s.page, row)
        assertNear("row $row", DoubleArray(3) { under[it] + (inset[it] - under[it]) * a }, s.px(x, top + row))
    }

    @Test
    fun graphiteFillIsTheCssGradientWithItsInsetShadow() = checkFill(PlumThemeSettings.Graphite, true, -10.0, 9.0, doubleArrayOf(0.0, 0.0, 0.0, 0.20))

    @Test
    fun paperFillIsTheCssGradientWithItsInsetShadow() = checkFill(PlumThemeSettings.Paper, false, -4.0, 4.0, doubleArrayOf(60.0, 40.0, 20.0, 0.04))

    @Test
    fun theBorderIsTheCssOnGraphite() {
        val s = shot(PlumThemeSettings.Graphite)
        val x = left + width / 2
        // The top px: the border's white 0.42 over the gradient's first row, under the inset `0 1px 1px` white (clipped to the padding box: not here).
        val topFill = cssFill(true, s.page, 0)
        assertNear("top border", DoubleArray(3) { topFill[it] + (255 - topFill[it]) * 0.42 }, s.px(x, top), 3.0)
        val mid = cssFill(true, s.page, height / 2)
        assertNear("side border", DoubleArray(3) { mid[it] + (255 - mid[it]) * 0.14 }, s.px(left, top + height / 2), 3.0)
    }

    @Test
    fun theDropShadowIsDarkerAndLongerBelowThanAbove() {
        for (settings in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            val s = shot(settings)
            val x = left + width / 2
            fun lum(p: IntArray) = p.sum()
            val page = lum(s.page)
            for (d in listOf(4, 12, 24)) {
                val below = lum(s.px(x, top + height + d))
                val above = lum(s.px(x, top - 1 - d))
                assertTrue("${settings.backgroundColor} $d px: below $below darker than above $above", below < above - 2)
                assertTrue("${settings.backgroundColor} $d px: below $below under the page $page", below < page)
            }
        }
    }
}
