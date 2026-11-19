package com.plumemu.home

import android.content.ComponentCallbacks2
import androidx.compose.ui.graphics.ImageBitmap
import androidx.test.core.app.ApplicationProvider
import com.plumemu.PlumApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The process's drawn box fronts are given back when Android asks for memory (a game in front, the screen off, memory low). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoxFrontImagesTrimTest {
    private val app = ApplicationProvider.getApplicationContext<PlumApplication>()
    private val images = SharedBoxFrontImages.of(app)

    @After
    fun emptyTheSharedCache() = images.clear()

    private fun holdOne() {
        images.getOrPut(BoxFrontKey(false, "Title", "Super Nintendo", "SNES", 1, 2, 3, 4, 5, 40f, 56f, 2f, 1f)) { ImageBitmap(4, 4) }
        assertTrue("one image held", images.bytes() > 0)
    }

    @Test
    fun theUiHiddenAndHigherLevelsEmptyTheCache() {
        for (level in listOf(
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
        )) {
            holdOne()
            app.onTrimMemory(level)
            assertEquals("level $level gives the images back", 0, images.bytes())
        }
    }

    @Test
    fun aModerateOrLowWarningWhileShownKeepsThem() {
        holdOne()
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE)
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
        assertTrue("still held", images.bytes() > 0)
    }

    @Test
    fun theHomeActivityStoppingEmptiesTheCacheEvenWithoutATrim() {
        SharedBoxFrontImages.releaseWhenStopped(app) { it is android.app.Activity }
        val home = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        holdOne()
        home.pause()
        assertTrue("paused only (Settings over the home is in the same activity): kept", images.bytes() > 0)
        home.stop()
        assertEquals("the home stopped: the images went", 0, images.bytes())
        home.destroy()
    }
}
