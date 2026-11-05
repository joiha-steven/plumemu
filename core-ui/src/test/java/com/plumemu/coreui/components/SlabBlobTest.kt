package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumColors
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import com.plumemu.coreui.theme.SlabLook
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Paper's focused slab has the slab's own glow only (the accent at 0.3, sigma 7 dp), no floor blob: the halo is a few dp wide on a 2.25-density screen as on Graphite. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabBlobTest {
    private val density = 2f
    private val left = 300
    private val top = 300
    private val width = 842 // 421 dp
    private val height = 96 // 48 dp

    private fun colours(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))

    private val paper = colours(PlumThemeSettings.Paper)

    /** Accent alpha of the pixel [dx], [dy] dp from the slab's left-centre (dx) or centre column (dy from its top, negative above, positive past the bottom). */
    private fun alpha(
        c: PlumComposeColors,
        below: Float? = null,
        above: Float? = null,
        beside: Float? = null,
    ): Float {
        val image = ImageBitmap(1800, 1000)
        val page = Color.White
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), androidx.compose.ui.geometry.Size(1800f, 1000f)) {
            drawRect(page)
            val rect = RoundRect(left.toFloat(), top.toFloat(), (left + width).toFloat(), (top + height).toFloat(), 26f, 26f)
            drawSlabOutside(Outline.Rounded(rect), SlabLook(c.slab.fill, c.slab.edge), c.slabGlow, blob = c.slabBlob)
        }
        val map = image.toPixelMap()
        val cx = left + width / 2
        val (x, y) =
            when {
                below != null -> cx to top + height + (below * density).toInt()
                above != null -> cx to top - (above * density).toInt()
                else -> left - (beside!! * density).toInt() to top + height / 2
            }
        return pixelAlpha(map[x, y].red, c)
    }

    /** White over accent: red = 1 - a (1 - accent.red), so a = (1 - red) / (1 - accent.red). */
    private fun pixelAlpha(red: Float, c: PlumComposeColors) = (1f - red) / (1f - c.accent.red)

    @Test
    fun paperHaloIsTightAroundTheSlab() {
        // The slab's own glow only: gone within the glow's reach (13 dp) of the slab, not a cloud over the rows beside it.
        assertEquals(0f, alpha(paper, below = 30f), 0.03f)
        assertEquals(0f, alpha(paper, above = 30f), 0.03f)
        assertEquals(0f, alpha(paper, beside = 30f), 0.03f)
    }

    @Test
    fun noThemeDrawsAFloorBlob() {
        assertEquals(0f, colours(PlumThemeSettings.Graphite).slabBlob.alpha, 0f)
        assertEquals(0f, paper.slabBlob.alpha, 0f)
    }
}
