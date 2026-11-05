package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.Rgb
import com.plumemu.coreui.theme.SlabLook
import com.plumemu.coreui.theme.SlabShadow

/**
 * The slab's shadow and glow against the V2 board's CSS (`0 3px 6px` and `0 16px 32px` shadows, `0 0 22px` glow at 0.30), measured on
 * Graphite and Paper from the board's pixels (1 board px = 1/1.5 dp): below the slab the shadows are about 0.49 dark at 2.5 px, 0.28 at 11.5 px,
 * 0.14 at 23.5 px and gone by 45 px (Paper the same times 0.45); the glow is about 0.15 at the edge, 0.07 at 7.5 px, 0.015 at 18 px and gone by 22 px.
 * Drawn here in black over white so a pixel reads as its alpha.
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

    /** The alpha at [px] board px below the slab's bottom edge, or [side] board px outside its left edge, centred on the row. */
    private fun alphaAt(
        glow: Color,
        shadow: Color,
        px: Float,
        side: Boolean = false,
        ring: Color = Color.Transparent,
    ): Float {
        val image = ImageBitmap(600, 500)
        val scope = CanvasDrawScope()
        scope.draw(Density(density), LayoutDirection.Ltr, Canvas(image), androidx.compose.ui.geometry.Size(600f, 500f)) {
            drawRect(Color.White)
            val rect = RoundRect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat(), 20f * density, 20f * density)
            drawSlabOutside(Outline.Rounded(rect), lookOf(shadow, ring), glow)
        }
        val map = image.toPixelMap()
        val x = if (side) left - (px / 1.5f * density).toInt() - 1 else left + width / 2
        val y = if (side) top + height / 2 else top + height + (px / 1.5f * density).toInt()
        return 1f - map[x, y].red
    }

    /** The Graphite slab's CSS drops in [shadow] (its alpha scales both), with [ring]; nothing else. */
    private fun lookOf(
        shadow: Color,
        ring: Color,
    ): SlabLook {
        val real = SlabLook.of(Rgb(0x111214), Rgb(0xEBE7DD), dark = true)
        fun scaled(s: SlabShadow?) = s?.let { SlabShadow(shadow.copy(alpha = shadow.alpha * it.color.alpha), it.drop, it.sigma) }
        return SlabLook(fill = real.fill, edge = real.edge, ring = ring, near = scaled(real.near), far = scaled(real.far))
    }

    @Test
    fun theShadowDropsAndFadesLikeTheBoards() {
        val shadow = Color.Black
        assertEquals(0.49f, alphaAt(Color.Transparent, shadow, 2.5f), 0.05f)
        assertEquals(0.28f, alphaAt(Color.Transparent, shadow, 11.5f), 0.04f)
        assertEquals(0.14f, alphaAt(Color.Transparent, shadow, 23.5f), 0.04f)
        assertTrue(alphaAt(Color.Transparent, shadow, 45f) < 0.03f)
    }

    /** The Graphite board below its slab, shadows plus the 1 px black ring at 0.4 (PartsDark): 0.67 at 1 px, 0.51 at 3, 0.33 at 10, 0.25 at 20, 0.12 at 30. */
    @Test
    fun theDarkShadowWithItsRingPeaksLikeTheBoard() {
        val ring = Color.Black.copy(alpha = 0.4f)
        assertEquals(0.67f, alphaAt(Color.Transparent, Color.Black, 0.7f, ring = ring), 0.07f)
        assertEquals(0.51f, alphaAt(Color.Transparent, Color.Black, 3f, ring = ring), 0.07f)
        assertEquals(0.33f, alphaAt(Color.Transparent, Color.Black, 10f, ring = ring), 0.07f)
        assertEquals(0.25f, alphaAt(Color.Transparent, Color.Black, 20f, ring = ring), 0.07f)
        assertEquals(0.12f, alphaAt(Color.Transparent, Color.Black, 30f, ring = ring), 0.07f)
    }

    @Test
    fun aLightThemeShadowIsThe45PercentOfIt() {
        val dark = alphaAt(Color.Transparent, Color.Black, 5.5f)
        val light = alphaAt(Color.Transparent, Color.Black.copy(alpha = 0.45f), 5.5f)
        assertEquals(0.18f, light, 0.04f)
        assertTrue("$light vs $dark", light < dark * 0.6f)
    }

    @Test
    fun theGlowIsFaintAndReachesAboutTwentyTwoBoardPixels() {
        val glow = Color.Black.copy(alpha = 0.30f)
        assertEquals(0.15f, alphaAt(glow, Color.Transparent, 0.5f, side = true), 0.04f)
        assertEquals(0.07f, alphaAt(glow, Color.Transparent, 7.5f, side = true), 0.03f)
        assertEquals(0.015f, alphaAt(glow, Color.Transparent, 18f, side = true), 0.02f)
        assertTrue(alphaAt(glow, Color.Transparent, 24f, side = true) < 0.015f)
    }
}
