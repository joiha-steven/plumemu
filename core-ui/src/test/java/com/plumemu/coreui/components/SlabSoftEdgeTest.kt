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
import com.plumemu.coreui.theme.FocusSlab
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.SlabLook
import com.plumemu.coreui.theme.SlabShadow
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
 * The slab's lift and glow as drawn, against the Gaussians they are: each layer is the slab's rounded box moved down by its drop ([FocusSlab.NEAR_DROP_DP],
 * [FocusSlab.FAR_DROP_DP]) and blurred by its sigma, at the strength the look carries ([FocusSlab.liftAlpha]); the glow is the box blurred by
 * [SlabDimens.glowSigma]. The lift is a shadow on every page: black on a dark one, brown on a light one, so its share is read as how far a pixel
 * moved from the page toward the layer's ink. The measurements are taken on the row's middle, where the profile is that of a straight edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabSoftEdgeTest {
    private val density = 2f
    private val left = 100
    private val top = 100
    private val width = 300
    private val height = 92 // 46 dp, a Settings row

    private class Theme(val settings: PlumThemeSettings, val dark: Boolean, val page: Color = Color(settings.backgroundColor.toArgb() or 0xFF000000.toInt())) {
        val colors = PlumComposeColors.from(PlumColors.derive(settings.backgroundColor, settings.textMode, settings.accentColor))
    }

    private val graphite = Theme(PlumThemeSettings.Graphite, dark = true)
    private val paper = Theme(PlumThemeSettings.Paper, dark = false)

    /** A white page, where a black glow reads as its own alpha to the level. */
    private val white = Theme(PlumThemeSettings.Paper, dark = false, page = Color.White)

    private fun draw(
        theme: Theme,
        look: SlabLook,
        glow: Color,
    ) = ImageBitmap(600, 500).also { image ->
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(600f, 500f)) {
            drawRect(theme.page)
            val rect = RoundRect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat(), 20f * density, 20f * density)
            drawSlabOutside(Outline.Rounded(rect), look, glow, under = theme.page)
        }
    }

    /** The standard normal CDF (Abramowitz and Stegun 7.1.26). */
    private fun phi(x: Double): Double {
        val z = abs(x) / Math.sqrt(2.0)
        val k = 1 / (1 + 0.3275911 * z)
        val erf = 1 - (((((1.061405429 * k - 1.453152027) * k) + 1.421413741) * k - 0.284496736) * k + 0.254829592) * k * exp(-z * z)
        return if (x >= 0) (1 + erf) / 2 else (1 - erf) / 2
    }

    /** A layer of [alpha] moved [drop] px down and blurred by [sigma] px, at [u] px below the slab's top edge (negative: above it), on the row's middle. */
    private fun layerAt(
        alpha: Float,
        drop: Double,
        sigma: Double,
        u: Double,
    ) = alpha * (phi((height + drop - u) / sigma) - phi((drop - u) / sigma))

    /** The near and the far layer composited, [dp] below the slab's bottom edge (or above its top edge when [above]), by the Gaussian model. */
    private fun expectedLift(
        look: SlabLook,
        dp: Float,
        above: Boolean,
    ): Double {
        val px = Math.round(dp * density)
        val u = if (above) -(px + 0.5) else height + px + 0.5
        val near = layerAt(look.near!!.color.alpha, FocusSlab.NEAR_DROP_DP * density.toDouble(), FocusSlab.NEAR_SIGMA_DP * density.toDouble(), u)
        val far = layerAt(look.far!!.color.alpha, FocusSlab.FAR_DROP_DP * density.toDouble(), FocusSlab.FAR_SIGMA_DP * density.toDouble(), u)
        return 1 - (1 - near) * (1 - far)
    }

    /** How far the pixel at ([x], [y]) moved from the page toward [ink], as a share of the way, read on the channel where they differ most. */
    private fun share(
        image: ImageBitmap,
        theme: Theme,
        ink: Color,
        x: Int,
        y: Int,
    ): Double {
        val px = image.toPixelMap()[x, y]
        val channels: List<(Color) -> Float> = listOf({ it.red }, { it.green }, { it.blue })
        val read = channels.maxByOrNull { abs(it(ink) - it(theme.page)) }!!
        return ((read(px) - read(theme.page)) / (read(ink) - read(theme.page))).toDouble()
    }

    private fun lift(
        theme: Theme,
        dp: Float,
        above: Boolean,
        image: ImageBitmap = draw(theme, theme.colors.slab, Color.Transparent),
    ): Double {
        val px = Math.round(dp * density)
        val y = if (above) top - px - 1 else top + height + px
        return share(image, theme, theme.colors.slab.near!!.color, left + width / 2, y)
    }

    @Test
    fun theLiftDropsAndFadesAsTwoGaussians() {
        for (theme in listOf(graphite, paper)) {
            val image = draw(theme, theme.colors.slab, Color.Transparent)
            for (dp in listOf(0.5f, 2f, 4f, 7f, 12f)) {
                for (above in listOf(false, true)) {
                    assertEquals("${theme.settings.backgroundColor} $dp dp ${if (above) "above" else "below"}", expectedLift(theme.colors.slab, dp, above), lift(theme, dp, above, image), maxOf(LIFT_TOLERANCE, oneLevel(theme)))
                }
            }
        }
    }

    @Test
    fun theLiftIsAShadowOnADarkPageAndOnALightOne() {
        for ((theme, ink) in listOf(graphite to 0f, paper to 0.2f)) {
            val look = theme.colors.slab
            assertEquals(ink, look.near!!.color.red, 0.15f)
            val image = draw(theme, look, Color.Transparent)
            val below = image.toPixelMap()[left + width / 2, top + height + 2].red
            assertEquals("${theme.settings.backgroundColor}: below the slab the page moves toward the ink", true, (below - theme.page.red) * (look.near.color.red - theme.page.red) > 0)
        }
    }

    /** One 8-bit step of the channel [share] reads, as a share: the smallest difference a pixel can show (a black shadow on a near-black page has few). */
    private fun oneLevel(theme: Theme): Double {
        val ink = theme.colors.slab.near!!.color
        val span = listOf(abs(ink.red - theme.page.red), abs(ink.green - theme.page.green), abs(ink.blue - theme.page.blue)).max()
        return 1.0 / (255.0 * span)
    }

    @Test
    fun theLiftIsLongerAndStrongerBelowThanAbove() {
        for (theme in listOf(graphite, paper)) {
            val image = draw(theme, theme.colors.slab, Color.Transparent)
            // At 3.5 dp the model's gap is two levels and Linux rounds the blur one level apart, so the gap must be at least one whole level:
            // a symmetric lift (no drop) gives none.
            val level = oneLevel(theme)
            for (dp in listOf(0.5f, 2f, 3.5f)) {
                val below = lift(theme, dp, above = false, image = image)
                val above = lift(theme, dp, above = true, image = image)
                assertTrue("${theme.settings.backgroundColor} $dp dp: below $below stronger than above $above by ${below - above}, one level is $level", below - above > level * 0.9)
            }
        }
    }

    @Test
    fun theLiftEndsWithinItsReach() {
        for (theme in listOf(graphite, paper)) {
            val far = FocusSlab.FAR_DROP_DP + 3 * FocusSlab.FAR_SIGMA_DP
            assertTrue("${theme.settings.backgroundColor}: ${lift(theme, far, above = false)} left $far dp below", lift(theme, far, above = false) < 0.01)
        }
    }

    @Test
    fun theLiftStrengthIsTheLiftAlphaOfItsSteps() {
        for (theme in listOf(graphite, paper)) {
            val bg = Rgb(theme.settings.backgroundColor.toArgb() and 0xFFFFFF)
            val look = theme.colors.slab
            assertEquals(FocusSlab.liftAlpha(bg, theme.dark, FocusSlab.LIFT_NEAR_L), look.near!!.color.alpha, 0.003f)
            assertEquals(FocusSlab.liftAlpha(bg, theme.dark, FocusSlab.LIFT_FAR_L), look.far!!.color.alpha, 0.003f)
            assertEquals(look.near.drop.value, FocusSlab.NEAR_DROP_DP, 0.001f)
            assertEquals(look.far.sigma.value, FocusSlab.FAR_SIGMA_DP, 0.001f)
        }
    }

    /** The slab's own drops left out (alpha 0), so the glow alone is on the page. */
    private fun glowOnly(theme: Theme): SlabLook {
        val real = theme.colors.slab
        fun none(s: SlabShadow?) = s?.let { SlabShadow(it.color.copy(alpha = 0f), it.drop, it.sigma) }
        return SlabLook(fill = real.fill, edge = real.edge, near = none(real.near), far = none(real.far))
    }

    /** The share of [glow] [dp] left of the slab's left edge, on the row's middle. */
    private fun glowShare(
        theme: Theme,
        glow: Color,
        dp: Float,
    ): Double {
        val px = Math.round(dp * density)
        return share(draw(theme, glowOnly(theme), glow), theme, glow.copy(alpha = 1f), left - px - 1, top + height / 2) / glow.alpha
    }

    @Test
    fun theGlowIsAGaussianOfTheSlabsBox() {
        val glow = Color.Black.copy(alpha = 0.30f)
        val sigma = SlabDimens.glowSigma.value * density.toDouble()
        for (dp in listOf(0.5f, 2f, 4f, 6f)) {
            val d = Math.round(dp * density) + 0.5
            // Outside the left edge the box is a half plane; the row's height is the vertical factor.
            val expected = (1 - phi(d / sigma)) * (phi(height / 2.0 / sigma) - phi(-height / 2.0 / sigma))
            assertEquals("$dp dp", expected, glowShare(white, glow, dp), GLOW_TOLERANCE)
        }
    }

    @Test
    fun theGlowEndsWithinItsReach() {
        for (theme in listOf(graphite, paper)) {
            val glow = theme.colors.slabGlow
            val ink = glow.copy(alpha = 1f)
            val px = Math.round(SlabDimens.glowReach.value * density)
            val moved = share(draw(theme, glowOnly(theme), glow), theme, ink, left - px - 1, top + height / 2)
            // The reach is where the glow is under 2 of 255 on the channel it moves most.
            val levels = moved * 255.0 * listOf(ink.red - theme.page.red, ink.green - theme.page.green, ink.blue - theme.page.blue).maxOf { abs(it) }
            assertTrue("${theme.settings.backgroundColor}: the glow still moves the page by $levels levels at its reach", levels < 2.0)
        }
    }

    private companion object {
        /** One 8-bit level is a share of 1 / 176 or finer; the rest is the cached image's own rounding. */
        const val LIFT_TOLERANCE = 0.012

        const val GLOW_TOLERANCE = 0.03
    }
}
