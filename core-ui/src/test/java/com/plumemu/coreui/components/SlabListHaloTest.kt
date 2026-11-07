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
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.SlabDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The games list's focused slab on Graphite (page #111214, accent #3E7BE0): its glow and lift against the Gaussians they are, per channel over the page,
 * within [DEPTH_TOLERANCE]. Each layer is the slab's box moved down by its drop and blurred by its sigma: the glow ([SlabDimens.glowSigma], the accent
 * at [PlumComposeColors.slabGlow]'s alpha) and the white lift ([FocusSlab.NEAR_DROP_DP], [FocusSlab.FAR_DROP_DP], strength from the look). Above and
 * at the sides the glow and the faint lift show, below the lift lightens the page the whole way down, and the glow's last visible pixel (2 of 255 on blue)
 * is where the model puts it, inside [SlabDimens.glowReach].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabListHaloTest {
    private val density = 1.5f

    private fun deltas(withGlow: Boolean = true): Map<String, Pair<IntArray, List<IntArray>>> {
        val s = PlumThemeSettings.Graphite
        val c = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))
        val page = Color(s.backgroundColor.toArgb() or 0xFF000000.toInt())
        val w = (520 * density).toInt()
        val h = (260 * density).toInt()
        val image = ImageBitmap(w, h)
        val left = 56f
        val top = 100f
        val right = left + 408f
        val bottom = top + 46.67f
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(page)
            val rect = RoundRect(left.dp.toPx(), top.dp.toPx(), right.dp.toPx(), bottom.dp.toPx(), 13.33f.dp.toPx(), 13.33f.dp.toPx())
            drawSlabOutside(Outline.Rounded(rect), c.slab, if (withGlow) c.slabGlow else Color.Transparent, under = c.bg)
        }
        val map = image.toPixelMap()
        val bg = IntArray(3) { (page.toArgb() shr (16 - 8 * it)) and 0xFF }
        fun at(
            x: Int,
            y: Int,
        ) = IntArray(3) { ((map[x, y].toArgb() shr (16 - 8 * it)) and 0xFF) - bg[it] }
        val cx = ((left + right) / 2 * density).toInt()
        val cy = ((top + bottom) / 2 * density).toInt()
        val t = (top * density).toInt()
        val b = (bottom * density).toInt()
        val l = (left * density).toInt()
        val r = (right * density).toInt()
        val steps = (24 * density).toInt()
        return mapOf(
            "top" to (at(cx, t - 1) to List(steps) { at(cx, t - 1 - it) }),
            "bottom" to (at(cx, b) to List(steps) { at(cx, b + it) }),
            "left" to (at(l - 1, cy) to List(steps) { at(l - 1 - it, cy) }),
            "right" to (at(r, cy) to List(steps) { at(r + it, cy) }),
        )
    }

    private val settings = PlumThemeSettings.Graphite
    private val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
    private val pagePx = intArrayOf(17, 18, 20)
    private val glow = colors.slabGlow

    /** The standard normal CDF (Abramowitz and Stegun 7.1.26). */
    private fun phi(x: Double): Double {
        val z = abs(x) / Math.sqrt(2.0)
        val k = 1 / (1 + 0.3275911 * z)
        val erf = 1 - (((((1.061405429 * k - 1.453152027) * k) + 1.421413741) * k - 0.284496736) * k + 0.254829592) * k * exp(-z * z)
        return if (x >= 0) (1 + erf) / 2 else (1 - erf) / 2
    }

    /** The slab's box in px: 408 x 46.67 dp. */
    private val boxW = 408 * density.toDouble()
    private val boxH = 46.67 * density.toDouble()

    /** A layer's share at ([x], [y]) px from the box's top left: the box moved down [drop] px and blurred by [sigma] px. */
    private fun cover(
        x: Double,
        y: Double,
        drop: Double,
        sigma: Double,
    ) = (phi((boxW - x) / sigma) - phi(-x / sigma)) * (phi((boxH + drop - y) / sigma) - phi((drop - y) / sigma))

    /** The page's delta at ([x], [y]) px from the box's top left, per channel: far lift, near lift, then glow, each composited over what is under it. */
    private fun model(
        x: Double,
        y: Double,
        withGlow: Boolean = true,
    ): DoubleArray {
        val v = DoubleArray(3) { pagePx[it].toDouble() }
        fun put(
            color: Color,
            drop: Double,
            sigma: Double,
        ) {
            val a = color.alpha * cover(x, y, drop, sigma)
            val ink = doubleArrayOf(color.red * 255.0, color.green * 255.0, color.blue * 255.0)
            for (ch in 0..2) v[ch] += (ink[ch] - v[ch]) * a
        }
        val far = colors.slab.far!!
        val near = colors.slab.near!!
        put(far.color, far.drop.value * density.toDouble(), far.sigma.value * density.toDouble())
        put(near.color, near.drop.value * density.toDouble(), near.sigma.value * density.toDouble())
        if (withGlow) put(glow, 0.0, SlabDimens.glowSigma.value * density.toDouble())
        return DoubleArray(3) { v[it] - pagePx[it] }
    }

    /** The model's pixel [k] outside the [side] of the box ([k] 0 is the pixel beside the edge), on the middle of that side. */
    private fun modelAt(
        side: String,
        k: Int,
        withGlow: Boolean = true,
    ): DoubleArray {
        val d = k + 0.5
        return when (side) {
            "top" -> model(boxW / 2, -d, withGlow)
            "bottom" -> model(boxW / 2, boxH + d, withGlow)
            "left" -> model(-d, boxH / 2, withGlow)
            else -> model(boxW + d, boxH / 2, withGlow)
        }
    }

    private fun near(
        side: String,
        line: List<IntArray>,
        upTo: Int = line.size - 1,
    ) {
        val off = mutableListOf<String>()
        for (k in 1..upTo) {
            val expected = modelAt(side, k)
            for (ch in 0..2) {
                if (abs(expected[ch] - line[k][ch]) > DEPTH_TOLERANCE) off += "px $k channel $ch: ${line[k].toList()} vs %.1f".format(expected[ch])
            }
        }
        assertTrue("$side: ${off.size} off by more than $DEPTH_TOLERANCE: ${off.joinToString("; ")}", off.isEmpty())
    }

    @Test
    fun theGlowMatchesItsGaussianAboveAndAtTheSides() {
        val d = deltas()
        near("top", d.getValue("top").second)
        near("left", d.getValue("left").second)
        near("right", d.getValue("right").second)
    }

    @Test
    fun belowTheSlabTheLiftLightensThePage() {
        val (_, line) = deltas().getValue("bottom")
        near("bottom", line)
        // The lift alone lightens the page the whole way down its near stretch.
        val (_, bare) = deltas(withGlow = false).getValue("bottom")
        for (i in 1 until (12 * density * Depth.SHADOW_REACH).toInt()) assertTrue("lighter than the page ${i / density} dp below: ${bare[i].toList()}", bare[i][0] > 0)
    }

    @Test
    fun theGlowEndsWhereItsGaussianDoes() {
        val d = deltas()
        // The last pixel whose blue is at least 2 over the page.
        fun reach(line: List<IntArray>) = line.indexOfLast { it[2] >= 2 }

        fun modelReach(side: String) = (1..60).lastOrNull { modelAt(side, it)[2] >= 1.5 } ?: 0
        assertEquals("top", modelReach("top").toDouble(), reach(d.getValue("top").second).toDouble(), 2.0)
        assertEquals("left", modelReach("left").toDouble(), reach(d.getValue("left").second).toDouble(), 1.0)
        assertEquals("right", modelReach("right").toDouble(), reach(d.getValue("right").second).toDouble(), 1.0)
    }

    @Test
    fun theGlowIsClippedNoCloserThanItsReach() {
        // The distance where the glow alone moves blue by under 2 of 255.
        val blue = glow.alpha * (glow.blue * 255.0 - pagePx[2])
        val under2 = (1..400).map { it / 10.0 }.first { blue * (1 - phi(it * density / (SlabDimens.glowSigma.value * density.toDouble()))) < 2.0 }
        assertTrue("the glow still shows past ${SlabDimens.glowReach.value} dp (to $under2 dp)", SlabDimens.glowReach.value >= under2)
    }

    private companion object {
        /** Ours against the Gaussian model: whole levels of rounding in the cached images, and the order the layers are composited in. */
        const val DEPTH_TOLERANCE = 3.0
    }
}
