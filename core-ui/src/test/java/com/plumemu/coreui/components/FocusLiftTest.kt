package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The focus of a cover or Apps tile (`FocusLift`): a 2 dp rim glued on the frame, concentric with its corner, near-white on a dark page
 * and the ink on a light one; a tight glow and a small depth shadow under it; a pivot that keeps the grown item inside the grid.
 * One px per dp; the frame is a 100 px square at (50, 50) with the cover frame's 10 px corner.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class FocusLiftTest {
    private val ink = Color(0xFF1C1C1E)
    private val frame = Rect(Offset(50f, 50f), Size(100f, 100f))
    private val corner get() = CollarDimens.coverFrameCorner.value

    private fun shot(draw: DrawScope.() -> Unit): PixelMap =
        ImageBitmap(200, 200).also { image ->
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(image), Size(200f, 200f), draw)
        }.toPixelMap()

    private fun rim(
        strength: Float = 1f,
        dark: Boolean = true,
    ) = shot { drawFocusRim(frame, corner, strength, dark, ink) }

    private fun under(
        strength: Float = 1f,
        dark: Boolean = true,
    ) = shot { drawFocusLiftUnder(frame, corner, strength, dark, ink) }

    @Test
    fun theTokensAreTheApprovedF3Values() {
        assertEquals(1.035f, FocusLift.SCALE, 0f)
        assertEquals(2.dp, FocusLift.rimWidth)
        assertEquals(0.96f, FocusLift.RIM_ALPHA, 0f)
        assertEquals(2.25.dp, FocusLift.glowSigma)
        assertEquals(10.dp, CollarDimens.coverFrameCorner)
        assertEquals(150, PlumMotion.FOCUS_GROW_MS)
        assertEquals(150, PlumMotion.FOCUS_LEAVE_MS)
    }

    @Test
    fun theRimStaysInsideHalfTheGridGap() {
        assertTrue("reach ${FocusLift.reach}", FocusLift.reach <= PlumDimens.gridGapDown / 2)
        assertTrue(FocusLift.reach <= PlumDimens.gridGapAcross / 2)
    }

    @Test
    fun theRimIsTwoWideGluedToTheFrameAndNothingCoversTheFrame() {
        val shot = rim()
        // Top middle: the rim covers 48..50, the frame (never drawn here) starts at 50.
        for (y in 48..49) assertEquals("rim at y=$y", FocusLift.RIM_ALPHA, shot[100, y].alpha, 0.02f)
        assertEquals("outside the rim", 0f, shot[100, 47].alpha, 0.001f)
        assertEquals("inside the frame", 0f, shot[100, 52].alpha, 0.001f)
        assertEquals("left edge as far", FocusLift.RIM_ALPHA, shot[48, 100].alpha, 0.02f)
        assertEquals(0f, shot[47, 100].alpha, 0.001f)
    }

    @Test
    fun theRimIsConcentricWithTheFrameCorner() {
        val shot = rim()
        // The frame's corner circle is centred at (60, 60): the rim covers radii 10..12 from it, along the diagonal too.
        val r = 1.41421356f

        fun at(radius: Float) = (60f - radius / r).toInt()
        assertEquals("on the diagonal", FocusLift.RIM_ALPHA, shot[at(11f), at(11f)].alpha, 0.1f)
        assertEquals("inside, on the frame", 0f, shot[at(9f), at(9f)].alpha, 0.001f)
        assertEquals("past the rim", 0f, shot[at(13.5f), at(13.5f)].alpha, 0.001f)
    }

    @Test
    fun theRimIsNearWhiteOnADarkPageAndTheInkOnALightOne() {
        val dark = rim(dark = true)[100, 49]
        assertTrue("near-white: $dark", dark.red > 0.99f && dark.green > 0.99f && dark.blue > 0.99f)
        val light = rim(dark = false)[100, 49]
        assertEquals(ink.red, light.red, 0.02f)
        assertEquals(ink.green, light.green, 0.02f)
        assertEquals(ink.blue, light.blue, 0.02f)
        assertEquals(FocusLift.RIM_ALPHA, light.alpha, 0.02f)
    }

    @Test
    fun theRimFadesWithTheStrengthAndEverythingIsGoneAtZero() {
        assertEquals(FocusLift.RIM_ALPHA / 2, rim(0.5f)[100, 49].alpha, 0.02f)
        val none = rim(0f)
        val noneUnder = under(0f)
        for (x in 0 until 200 step 7) {
            for (y in 0 until 200 step 7) {
                assertEquals(0f, none[x, y].alpha, 0f)
                assertEquals(0f, noneUnder[x, y].alpha, 0f)
            }
        }
    }

    @Test
    fun theGlowIsTightAndSoftAndTheShadowFallsBelowMore() {
        val shot = under()
        val near = shot[100, 46].alpha
        assertTrue("glow next to the rim: $near", near > 0.05f && near < 0.5f)
        assertTrue("fades with distance", shot[100, 42].alpha < near)
        assertEquals("gone above, past the glow", 0f, shot[100, 50 - 14].alpha, 0.01f)
        assertTrue("shadow below beyond the glow's reach: ${shot[100, 156].alpha}", shot[100, 156].alpha > shot[100, 44].alpha)
        val glow = shot[100, 47]
        assertTrue("white glow: $glow", glow.alpha > 0f && glow.red > 0.5f)
    }

    @Test
    fun theGlowAndShadowAreOneImageScaledByTheStrength() {
        val full = under(1f)[100, 46].alpha
        val half = under(0.5f)[100, 46].alpha
        assertEquals(full / 2, half, 0.01f)
    }

    @Test
    fun aLightPageGetsALighterShadowAndAnInkGlow() {
        val dark = under(dark = true)[100, 160].alpha
        val light = under(dark = false)[100, 160].alpha
        assertTrue("lighter on a light page: $light < $dark", light < dark)
        val glow = under(dark = false)[100, 47]
        assertTrue("ink glow, not white: ${glow.red}", glow.red < 0.5f)
    }

    @Test
    fun theLiftGrowsInwardAboutItsPivot() {
        val f = Rect(10f, 20f, 110f, 220f)
        assertEquals("last column: right edge", Offset(110f, 120f), FocusLift.pivot(f, atLeft = false, atRight = true, atTop = false))
        assertEquals("first column: left edge", Offset(10f, 120f), FocusLift.pivot(f, atLeft = true, atRight = false, atTop = false))
        assertEquals("top row: top edge", Offset(60f, 20f), FocusLift.pivot(f, atLeft = false, atRight = false, atTop = true))
        assertEquals("any other cell: centre", f.center, FocusLift.pivot(f, atLeft = false, atRight = false, atTop = false))
        assertEquals("a lone column keeps its centre", 60f, FocusLift.pivot(f, atLeft = true, atRight = true, atTop = false).x, 0f)
        // Scaled about the pivot, an edge on the pivot does not move and the opposite one moves out by the growth.
        val s = FocusLift.SCALE
        val p = FocusLift.pivot(f, atLeft = false, atRight = true, atTop = true)

        fun grown(
            v: Float,
            pivot: Float,
        ) = pivot + (v - pivot) * s
        assertEquals(f.right, grown(f.right, p.x), 0f)
        assertEquals(f.top, grown(f.top, p.y), 0f)
        assertTrue(grown(f.left, p.x) < f.left && grown(f.bottom, p.y) > f.bottom)
    }
}
