package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coreui.testing.ReferenceShots
import kotlin.math.abs

/**
 * The shadow images of a slab are built pixel for pixel by [SoftImages.drops] and [SoftImages.inset]. Their loops were made faster (rows of a
 * corner added over whole x runs, no per-pixel closures) without changing one bit: these hashes were taken from the images before that change.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class SoftImagesPixelsTest {
    private val near = SoftImages.Inset(Color(0.1f, 0.2f, 0.7f, 0.35f), 6f, 12f)
    private val far = SoftImages.Inset(Color(0.0f, 0.0f, 0.1f, 0.3f), 20f, 40f)
    private val glow = SoftImages.Inset(Color(0.3f, 0.5f, 1f, 0.4f), 0f, 30f)

    @Test
    fun theDropsKeepTheirPixels() {
        SoftImages.clear()
        val under = Color(0.05f, 0.06f, 0.1f, 1f)
        for ((size, hash) in listOf((496 to 93) to -361026559, (672 to 93) to 1029274369, (300 to 60) to -1218886559)) {
            val exact = SoftImages.dropsBuild(size.first, size.second, size.second / 2f, near, far, glow, under, sliced = false)!!
            assertEquals("drops ${size.first}x${size.second}", hash, exact.image.toPixelMap().buffer.contentHashCode())
            // What a box of that width draws, sliced from the image its look shares, is the exact image within two levels of each channel.
            val placed = SoftImages.drops(size.first, size.second, size.second / 2f, near, far, glow, under)!!
            val drawn = drawnPixels(placed, exact.image.width)
            exact.image.toPixelMap().buffer.forEachIndexed { p, pixel ->
                val other = drawn[p]
                assertTrue("alpha of pixel $p of ${size.first}", abs((pixel ushr 24) - (other ushr 24)) <= 1)
                for (shift in intArrayOf(0, 8, 16)) {
                    // Over the page colour, as the image is seen: the stored colour absorbs the rounding of its alpha against that colour.
                    val page = 255 * when (shift) {
                        16 -> under.red
                        8 -> under.green
                        else -> under.blue
                    }
                    assertTrue("channel of pixel $p of ${size.first}", abs(over(pixel, shift, page) - over(other, shift, page)) <= 2f)
                }
            }
        }
    }

    @Test
    fun aBoxExactlyAsWideAsTheSharedImageLeavesItSlicedForWiderOnes() {
        SoftImages.clear()
        val near = SoftImages.Inset(Color.Black.copy(alpha = 0.3f), 2f, 4f)
        val far = SoftImages.Inset(Color.Black.copy(alpha = 0.2f), 6f, 10f)
        fun drops(width: Int) = checkNotNull(SoftImages.drops(width, 60, 10f, near, far, null))
        val wide = drops(1000)
        val shared = wide.image.width + 2 * wide.dx
        SoftImages.clear()
        drops(shared)
        assertTrue("a wider box is drawn sliced, not stretched", drops(1000).keep >= 0)
    }

    @Test
    fun theInsetShadowsKeepTheirPixels() {
        SoftImages.clear()
        for ((size, hash) in listOf((496 to 93) to 1857192737, (672 to 93) to 1745284897, (300 to 60) to 552608033)) {
            val placed = SoftImages.inset(size.first, size.second, size.second / 2f, 2f, near, far)!!
            assertEquals("inset ${size.first}x${size.second}", hash, drawnPixels(placed, size.first).contentHashCode())
        }
    }

    private fun over(
        pixel: Int,
        shift: Int,
        page: Float,
    ): Float {
        val alpha = (pixel ushr 24) / 255f
        return (pixel ushr shift and 0xFF) * alpha + page * (1f - alpha)
    }

    /** The image's pixels as a box [width] px wide draws them: a shared image's middle column repeated ([drawSliced]). */
    private fun drawnPixels(
        placed: SoftImages.Placed,
        width: Int,
    ): IntArray {
        val map = placed.image.toPixelMap()
        val h = map.height
        val keep = placed.keep
        return IntArray(width * h) { p ->
            val x = p % width
            val from = if (keep < 0 || x < keep) x else if (x >= width - keep) map.width - (width - x) else keep
            map.buffer[(p / width) * map.width + from]
        }
    }
}
