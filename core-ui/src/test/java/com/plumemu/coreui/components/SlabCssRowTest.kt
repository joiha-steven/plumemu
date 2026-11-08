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
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.FocusSlab
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.SlabLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A focused slab drawn at 1.5 px per dp, checked against what its look says, composited by hand: the glass gradient at the top, middle and bottom
 * ([SlabLook.stops] at 0, [FocusSlab.MID_AT] and 1; no inset shadow marks the bottom), the thin rim lit at two corners ([SlabLook.top]) and weak between
 * them ([SlabLook.edge]), and the lift (light on a dark page, shade on a light one) longer and stronger below the slab than above it.
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

    private class Shot(val page: IntArray, val look: SlabLook, val px: (Int, Int) -> IntArray)

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
        return Shot(rgb(page), c.slab) { x, y -> rgb(map[x, y]) }
    }

    private fun rgb(c: Color) = IntArray(3) { (c.toArgb() shr (16 - 8 * it)) and 0xFF }

    /** The look's gradient (stops at 0, [FocusSlab.MID_AT], 1) at board row [y] of the slab: opaque stops, so the page does not show through. */
    private fun gradientAt(
        look: SlabLook,
        y: Int,
    ): DoubleArray {
        val t = (y + 0.5) / height
        val mid = FocusSlab.MID_AT.toDouble()
        val (from, to, share) = if (t <= mid) Triple(look.stops[0], look.stops[1], t / mid) else Triple(look.stops[1], look.stops[2], (t - mid) / (1 - mid))
        return doubleArrayOf(from.red + (to.red - from.red) * share, from.green + (to.green - from.green) * share, from.blue + (to.blue - from.blue) * share).map { it * 255.0 }.toDoubleArray()
    }

    private fun assertNear(
        what: String,
        expected: DoubleArray,
        actual: IntArray,
        tolerance: Double = 2.0,
    ) {
        for (ch in 0..2) assertEquals("$what channel $ch: ${actual.toList()} vs ${expected.map { Math.round(it) }}", expected[ch], actual[ch].toDouble(), tolerance)
    }

    private fun checkFill(settings: PlumThemeSettings) {
        val s = shot(settings)
        val x = left + width / 2
        // The foot included: glass has no inset shadow darkening it.
        for (row in listOf(5, 35, height - 5)) assertNear("row $row", gradientAt(s.look, row), s.px(x, top + row))
    }

    @Test
    fun graphiteFillIsTheLooksGradient() = checkFill(PlumThemeSettings.Graphite)

    @Test
    fun paperFillIsTheLooksGradient() = checkFill(PlumThemeSettings.Paper)

    /** The rim's ink over the fill at [t] along the diagonal (0 at the top left, 1 at the bottom right): [SlabLook.top] at the lit corners to [SlabLook.edge] a sixth of the way in. */
    private fun rimOver(
        look: SlabLook,
        fill: DoubleArray,
        t: Double,
    ): DoubleArray {
        val share = (minOf(t, 1 - t).coerceIn(0.0, RIM_FADE) / RIM_FADE)
        // Android gradients blend premultiplied: the lit corner's strong white outweighs the side's faint brown near it.
        fun premul(of: (Color) -> Float) = of(look.top) * look.top.alpha + (of(look.edge) * look.edge.alpha - of(look.top) * look.top.alpha) * share
        val alpha = look.top.alpha + (look.edge.alpha - look.top.alpha) * share
        return DoubleArray(3) { fill[it] * (1 - alpha) + 255.0 * premul { c -> arrayOf(c.red, c.green, c.blue)[it] } }
    }

    private fun rimAt(
        s: Shot,
        x: Int,
        y: Int,
        row: Int,
    ) {
        val t = ((x + 0.5 - left) * width + (y + 0.5 - top) * height) / (width.toDouble() * width + height.toDouble() * height)
        assertNear("rim at $x,$y (t %.3f)".format(t), rimOver(s.look, gradientAt(s.look, row), t), s.px(x, y), 3.0)
    }

    @Test
    fun theRimIsLitAtTwoCornersAndWeakBetweenOnGraphite() = checkRim(PlumThemeSettings.Graphite)

    @Test
    fun theRimIsLitAtTwoCornersAndWeakBetweenOnPaper() = checkRim(PlumThemeSettings.Paper)

    private fun checkRim(settings: PlumThemeSettings) {
        val s = shot(settings)
        // Dark: the corners are RIM_LIT_L above the fill, the sides RIM_SIDE_L, so the corners are stronger. Light: the sides are full white already.
        if (settings.backgroundColor == PlumThemeSettings.Graphite.backgroundColor) {
            assertTrue("lit corners are stronger than the sides", s.look.top.alpha > s.look.edge.alpha)
        } else {
            assertEquals("light sides are white itself", 1f, s.look.edge.alpha, 0f)
        }
        // Between the lit corners: the top edge's middle and the bottom edge's middle are the faint rim.
        rimAt(s, left + width / 2, top, 0)
        rimAt(s, left + width / 2, top + height - 1, height - 1)
        // Near the top left and bottom right, where the rim is lit.
        rimAt(s, left + 40, top, 0)
        rimAt(s, left + width - 40, top + height - 1, height - 1)
        // A wide row's two ends are at the diagonal's ends: the sides are lit.
        rimAt(s, left, top + height / 2, height / 2)
        rimAt(s, left + width - 1, top + height / 2, height / 2)
    }

    /** The lift moves the page toward its ink: lighter on a dark page, darker on a light one. This is how far it moved, in the direction the ink goes. */
    private fun lifted(
        look: SlabLook,
        page: Int,
        p: IntArray,
    ): Int {
        val ink = look.near!!.color
        val darker = ink.red < 0.5f
        return if (darker) page - p.sum() else p.sum() - page
    }

    @Test
    fun theLiftIsStrongerAndLongerBelowThanAbove() {
        for (settings in listOf(PlumThemeSettings.Graphite, PlumThemeSettings.Paper)) {
            val s = shot(settings)
            val x = left + width / 2
            val page = s.page.sum()
            // The distances of the board (4, 12 and 24 px) at the drops' reach.
            for (d in listOf(4, 12, 24).map { (it * Depth.SHADOW_REACH).toInt() }) {
                val below = lifted(s.look, page, s.px(x, top + height + d))
                val above = lifted(s.look, page, s.px(x, top - 1 - d))
                assertTrue("${settings.backgroundColor} $d px: lift below $below stronger than above $above", below > above + 2)
                // Within the glow's reach the accent light outweighs the thin lift; past it the page below is moved toward the lift's ink.
                if (d > SlabDimens.glowReach.value * density) assertTrue("${settings.backgroundColor} $d px: page below moved $below", below > 0)
            }
        }
    }

    private companion object {
        /** The share of the rim's diagonal, from each lit corner, over which its light fades out (`SlabLook.rim`). */
        const val RIM_FADE = 0.16
    }
}
