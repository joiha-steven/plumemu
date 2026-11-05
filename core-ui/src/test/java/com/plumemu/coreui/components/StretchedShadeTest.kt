package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A big box's soft shade is made from a small one with its middle stretched ([StretchedPixels]): it must be the shade made pixel by
 * pixel, so drawn covers do not change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class StretchedShadeTest {
    @Test
    fun theMiddleRepeatsAndTheEndsAreCopied() {
        // 5 x 5 small image, keep 2 -> 8 x 7.
        val small = IntArray(25) { it }
        val big = StretchedPixels.expand(small, 5, 5, 8, 7, 2)
        assertEquals(56, big.size)
        assertArrayEquals(intArrayOf(0, 1, 2, 2, 2, 2, 3, 4), big.copyOfRange(0, 8))
        // Rows 2 to 4 of the big one repeat small row 2; the last row is small row 4.
        assertArrayEquals(intArrayOf(10, 11, 12, 12, 12, 12, 13, 14), big.copyOfRange(16, 24))
        assertArrayEquals(intArrayOf(20, 21, 22, 22, 22, 22, 23, 24), big.copyOfRange(48, 56))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aSmallImageTooSmallToKeepFromIsRefused() {
        StretchedPixels.expand(IntArray(9), 3, 3, 8, 8, 2)
    }

    @Test
    fun aBigShadeEqualsTheOneMadePixelByPixel() {
        for ((w, h, sigma, corner) in listOf(Case(260, 340, 2f, 14f), Case(300, 120, 4f, 20f), Case(90, 400, 1.5f, 8f))) {
            val color = Color(0f, 0f, 0f, 0.4f)
            val shade = SoftImages.shadow(w, h, sigma, color, corner)
            val step = SoftImages.step(sigma)
            val margin = (3f * sigma).toInt()
            val bw = (w + 2 * margin + step - 1) / step
            val bh = (h + 2 * margin + step - 1) / step
            assertEquals(bw, SoftImages.cells(w, sigma))
            assertTrue("a big shade is kept small (${shade.width} x ${shade.height})", shade.width < bw && shade.height < bh)
            // Drawn as the screen draws it (its middle repeated), one cell a pixel.
            val drawn = ImageBitmap(bw, bh)
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(drawn), Size(bw.toFloat(), bh.toFloat())) {
                with(shade) { draw(IntOffset.Zero, IntSize(bw, bh), 1, BlendMode.Src) }
            }
            val image = drawn.asAndroidBitmap()
            val extraX = bw * step - w
            val extraY = bh * step - h
            val xs = FloatArray(bw) { i -> (i + 0.5f) * step - extraX / 2f }
            val ys = FloatArray(bh) { j -> (j + 0.5f) * step - extraY / 2f }
            val v = SoftImages.blurred(w.toFloat(), h.toFloat(), corner.coerceAtMost(minOf(w, h) / 2f), sigma, xs, ys, 1f)
            var worst = 0
            for (y in 0 until bh) {
                for (x in 0 until bw) {
                    val expected = (255f * 0.4f * v[y * bw + x]).roundToInt().coerceIn(0, 255)
                    worst = maxOf(worst, abs(expected - (image.getPixel(x, y) ushr 24)))
                }
            }
            assertTrue("shade ${w}x$h differs from the direct one by up to $worst levels", worst <= 1)
        }
    }

    private data class Case(val w: Int, val h: Int, val sigma: Float, val corner: Float)
}
