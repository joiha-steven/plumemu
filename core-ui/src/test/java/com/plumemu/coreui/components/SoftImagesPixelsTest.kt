package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.plumemu.coreui.testing.ReferenceShots

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
        for ((size, hash) in listOf((496 to 93) to -361026559, (672 to 93) to 1029274369, (300 to 60) to -1218886559)) {
            val image = SoftImages.drops(size.first, size.second, size.second / 2f, near, far, glow, Color(0.05f, 0.06f, 0.1f, 1f))!!.image
            assertEquals("drops ${size.first}x${size.second}", hash, image.toPixelMap().buffer.contentHashCode())
        }
    }

    @Test
    fun theInsetShadowsKeepTheirPixels() {
        SoftImages.clear()
        for ((size, hash) in listOf((496 to 93) to 1857192737, (672 to 93) to 1745284897, (300 to 60) to 552608033)) {
            val image = SoftImages.inset(size.first, size.second, size.second / 2f, 2f, near, far)!!
            assertEquals("inset ${size.first}x${size.second}", hash, image.toPixelMap().buffer.contentHashCode())
        }
    }
}
