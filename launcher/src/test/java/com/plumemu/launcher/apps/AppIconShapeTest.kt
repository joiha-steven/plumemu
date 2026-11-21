package com.plumemu.launcher.apps

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every app icon wears the same rounded-square mask, legacy or adaptive. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppIconShapeTest {
    private val size = 100

    private fun alphas(b: Bitmap) = IntArray(size * size) { Color.alpha(b.getPixel(it % size, it / size)) }

    @Test
    fun aLegacySquareIconAndAnAdaptiveIconShareOneMask() {
        val legacy = AppIconShape.draw(ColorDrawable(Color.RED), size)
        val adaptive = AppIconShape.draw(AdaptiveIconDrawable(ColorDrawable(Color.BLUE), ColorDrawable(Color.GREEN)), size)

        assertEquals("masks identical", alphas(legacy).toList(), alphas(adaptive).toList())
        for (b in listOf(legacy, adaptive)) {
            for ((x, y) in listOf(0 to 0, size - 1 to 0, 0 to size - 1, size - 1 to size - 1)) {
                assertEquals("corner ($x,$y) outside the mask", 0, Color.alpha(b.getPixel(x, y)))
            }
            assertEquals("centre inside the mask", 255, Color.alpha(b.getPixel(size / 2, size / 2)))
            assertEquals("edge middle inside the mask", 255, Color.alpha(b.getPixel(size / 2, 1)))
        }
    }

    @Test
    fun aLegacyIconSitsInsetOnAFlatPlateOfItsEdgeColour() {
        val bitmap = AppIconShape.draw(ColorDrawable(Color.RED), size)
        assertEquals(Color.RED, bitmap.getPixel(size / 2, 2))
        assertEquals(Color.RED, bitmap.getPixel(size / 2, size / 2))
    }

    @Test
    fun theMaskCornerIsAboutAFifthOfTheSide() {
        val bitmap = AppIconShape.draw(ColorDrawable(Color.RED), size)
        // A 22 px radius leaves (5,5) outside the curve and (9,9) inside.
        assertTrue(Color.alpha(bitmap.getPixel(5, 5)) < 255)
        assertEquals(255, Color.alpha(bitmap.getPixel(9, 9)))
    }
}
