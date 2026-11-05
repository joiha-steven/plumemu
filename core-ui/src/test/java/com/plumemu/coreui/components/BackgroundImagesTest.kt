package com.plumemu.coreui.components

import androidx.compose.ui.graphics.Color
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Soft shades and CSS shadow images of a new size: a stand-in is drawn while the exact one is built off the draw thread, bounded, and idle when done. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class BackgroundImagesTest {
    @After
    fun inlineAgain() {
        ImageBuilding.inBackground = false
    }

    private fun untilIdle() {
        val end = System.nanoTime() + 20_000_000_000L
        while (BackgroundBuilds.waiting > 0 && System.nanoTime() < end) Thread.sleep(5)
        assertEquals("background builds did not finish", 0, BackgroundBuilds.waiting)
    }

    @Test
    fun aShadowOfANewSizeDrawsTheLastOneUntilItsOwnLands() {
        ImageBuilding.inBackground = true
        val color = Color(0f, 0f, 0f, 0.41f)
        val first = SoftImages.shadow(60, 40, 7.3f, color, 5f)
        val standIn = SoftImages.shadow(70, 45, 7.3f, color, 5f)
        assertSame("the draw must not build the new size itself", first, standIn)
        untilIdle()
        val exact = SoftImages.shadow(70, 45, 7.3f, color, 5f)
        assertNotSame(first, exact)
        assertEquals("built for its own box", 70, exact.w)
        assertEquals(45, exact.h)
    }

    @Test
    fun aShadowOfAVeryDifferentShapeIsBuiltAtOnce() {
        ImageBuilding.inBackground = true
        val color = Color(0f, 0f, 0f, 0.42f)
        SoftImages.shadow(60, 40, 7.4f, color, 5f)
        val wide = SoftImages.shadow(300, 40, 7.4f, color, 5f)
        assertEquals("built for its own box", 300, wide.w)
        assertEquals("nothing is queued for it", 0, BackgroundBuilds.waiting)
    }

    @Test
    fun withBackgroundBuildingOffTheExactShadowIsBuiltInTheDraw() {
        val color = Color(0f, 0f, 0f, 0.43f)
        SoftImages.shadow(60, 40, 7.5f, color, 5f)
        val exact = SoftImages.shadow(66, 44, 7.5f, color, 5f)
        assertEquals("built for its own box", 66, exact.w)
    }

    @Test
    fun noWorkIsLeftOnceTheImagesAreBuilt() {
        ImageBuilding.inBackground = true
        val color = Color(0f, 0f, 0f, 0.44f)
        SoftImages.shadow(60, 40, 7.6f, color, 5f)
        SoftImages.shadow(70, 45, 7.6f, color, 5f)
        untilIdle()
        repeat(5) { SoftImages.shadow(70, 45, 7.6f, color, 5f) }
        assertEquals(0, BackgroundBuilds.waiting)
    }

    @Test
    fun theShadowCacheStaysWithinItsBound() {
        val color = Color(0f, 0f, 0f, 0.45f)
        for (i in 0 until 80) SoftImages.shadow(40 + i, 30, 7.7f, color, 5f)
        assertTrue("kept ${SoftImages.keptCount}", SoftImages.keptCount <= 24)
    }

    @Test
    fun aCssImageOfANewSizeIsAStandInUntilTheExactOneLands() {
        ImageBuilding.inBackground = true
        // Boxes too small to be sliced: each size has its own image.
        val layers = listOf(CssImages.Layer(Color(0.1f, 0.2f, 0.3f, 0.46f), 0f, 6.1f, 0f, false))
        val first = CssImages.shown(30, 36, 6f, layers)!!
        val standIn = CssImages.shown(33, 40, 6f, layers)!!
        assertSame(first, standIn)
        untilIdle()
        val exact = CssImages.shown(33, 40, 6f, layers)!!
        assertNotSame(first, exact)
        assertEquals(33, exact.w)
        assertEquals(40, exact.h)
        repeat(5) { assertSame(exact, CssImages.shown(33, 40, 6f, layers)) }
        assertEquals(0, BackgroundBuilds.waiting)
    }

    @Test
    fun bigBoxesOfOneLookShareOneSmallCssImage() {
        val layers = listOf(CssImages.Layer(Color(0.1f, 0.2f, 0.3f, 0.48f), 0f, 6.1f, 0f, false))
        val one = CssImages.shown(300, 420, 6f, layers)!!
        assertSame("another big size draws the same image, sliced", one, CssImages.shown(340, 380, 6f, layers))
        assertTrue("kept small: ${one.placed.image.width} x ${one.placed.image.height}", one.placed.image.width < 300 && one.placed.image.height < 380)
    }

    @Test
    fun aBuildThatRunsOutOfMemoryIsDroppedAndTheWorkerGoesOn() {
        assertTrue(BackgroundBuilds.submit { throw OutOfMemoryError("test") })
        untilIdle()
        ImageBuilding.inBackground = true
        val color = Color(0f, 0f, 0f, 0.47f)
        SoftImages.shadow(60, 40, 7.8f, color, 5f)
        SoftImages.shadow(70, 45, 7.8f, color, 5f)
        untilIdle()
        assertEquals(70, SoftImages.shadow(70, 45, 7.8f, color, 5f).w)
    }

    @Test
    fun standsInOnlyForASimilarSize() {
        assertTrue(standsIn(100, 100, 120, 90))
        assertTrue(standsIn(100, 100, 200, 100))
        assertFalse(standsIn(100, 100, 260, 100))
        assertFalse(standsIn(100, 100, 100, 39))
        assertFalse(standsIn(0, 100, 10, 100))
    }
}
