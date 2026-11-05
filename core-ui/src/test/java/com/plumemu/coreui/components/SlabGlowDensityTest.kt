package com.plumemu.coreui.components

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
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
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.SlabLook
import com.plumemu.coreui.theme.toComposeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The slab's glow is sized in dp: how far it reaches past the slab (in dp) must not change with the screen's density (the Thor is 2.25).
 * Without the blob (Graphite, and any list that draws no floor glow) it is gone within [SlabDimens.glowReach] of the edge; with the Paper
 * blob the light at a given dp distance is the same at every density.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SlabGlowDensityTest {
    private val densities = listOf(1f, 1.5f, 2.25f, 3f)
    private val slabWidthDp = 421f
    private val slabHeightDp = 46.67f
    private val padDp = 140f

    private fun colours(s: PlumThemeSettings) = PlumComposeColors.from(PlumColors.derive(s.backgroundColor, s.textMode, s.accentColor))

    /** Alpha of the accent [belowDp] under the slab's bottom edge (negative: inside) at the slab's centre column, on a page of [page]. */
    private fun alphaBelow(
        c: PlumComposeColors,
        density: Float,
        belowDp: Float,
        blob: Boolean,
        page: Color,
    ): Float {
        val w = ((slabWidthDp + 2 * padDp) * density).toInt()
        val h = ((slabHeightDp + 2 * padDp) * density).toInt()
        val image = ImageBitmap(w, h)
        val left = padDp * density
        val top = padDp * density
        CanvasDrawScope().draw(Density(density), LayoutDirection.Ltr, Canvas(image), Size(w.toFloat(), h.toFloat())) {
            drawRect(page)
            val rect = RoundRect(left, top, left + slabWidthDp * density, top + slabHeightDp * density, 13f * density, 13f * density)
            drawSlabOutside(Outline.Rounded(rect), SlabLook(c.slab.fill, c.slab.edge), c.focusGlow, blob = if (blob) c.slabBlob else Color.Transparent)
        }
        val x = (left + slabWidthDp * density / 2).toInt()
        val y = (top + (slabHeightDp + belowDp) * density).toInt().coerceIn(0, h - 1)
        val red = image.toPixelMap()[x, y].red
        return (page.red - red) / (page.red - c.accent.red)
    }

    @Test
    fun glowWithoutBlobIsGoneWithinItsReachAtEveryDensity() {
        for ((name, settings) in listOf("Graphite" to PlumThemeSettings.Graphite, "Paper" to PlumThemeSettings.Paper)) {
            val c = colours(settings)
            val page = settings.backgroundColor.toComposeColor()
            for (d in densities) {
                // The drops are dark; look sideways-free at the centre column well below the glow's reach plus the far drop.
                val far = alphaBelow(c, d, SlabDimens.glowReach.value + 30f, blob = false, page = page)
                assertTrue("$name density $d: accent still $far at ${SlabDimens.glowReach.value + 30f} dp below the slab", far < 0.02f)
            }
        }
    }

    @Test
    fun paperBlobReachesTheSameDpAtEveryDensity() {
        val c = colours(PlumThemeSettings.Paper)
        val base = alphaBelow(c, 1f, 61f, blob = true, page = Color.White)
        for (d in densities) {
            assertEquals("density $d", base, alphaBelow(c, d, 61f, blob = true, page = Color.White), 0.03f)
        }
    }
}
