package com.plumemu.launcher

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [LaunchAnimation] and a display: API 26+ only; below, the start is plain. A start on another display is not counted. */
@RunWith(RobolectricTestRunner::class)
class LaunchAnimationDisplayTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val intent = Intent("x.y.Z")
    private val seen = mutableListOf<Int?>()

    @After
    fun noHost() {
        LaunchAnimation.host = null
    }

    @Test
    @Config(sdk = [34])
    fun theHostGetsTheDisplayFromApi26() {
        LaunchAnimation.host = { _, display -> seen += display }
        LaunchAnimation.start(app, intent, displayId = 5)
        assertEquals(listOf<Int?>(5), seen)
    }

    @Test
    @Config(sdk = [23])
    fun belowApi26TheDisplayIsDropped() {
        LaunchAnimation.host = { _, display -> seen += display }
        LaunchAnimation.start(app, intent, displayId = 5)
        assertEquals(listOf<Int?>(null), seen)
        assertNull(LaunchAnimation.displayOptions(5))
    }

    @Test
    @Config(sdk = [34])
    fun aStartWithNoDisplayIsPlain() {
        LaunchAnimation.host = { _, display -> seen += display }
        LaunchAnimation.start(app, intent)
        assertEquals(listOf<Int?>(null), seen)
    }

    @Test
    @Config(sdk = [34])
    fun aStartOnAnotherDisplayIsNotCountedAsLeaving() {
        LaunchAnimation.host = { _, _ -> }
        val before = LaunchAnimation.starts
        LaunchAnimation.start(app, intent, displayId = 5)
        assertEquals("Plumemu stays on the first screen: no return motion later", before, LaunchAnimation.starts)
        LaunchAnimation.start(app, intent)
        assertEquals("a plain start leaves the library", before + 1, LaunchAnimation.starts)
    }

    @Test
    @Config(sdk = [23])
    fun belowApi26ADisplayStartIsAPlainOneAndCounted() {
        LaunchAnimation.host = { _, _ -> }
        val before = LaunchAnimation.starts
        LaunchAnimation.start(app, intent, displayId = 5)
        assertEquals("the app opens on the first screen: Plumemu leaves it", before + 1, LaunchAnimation.starts)
    }

    @Test
    @Config(sdk = [34])
    fun theOptionsNameTheDisplay() {
        val options = LaunchAnimation.displayOptions(5)!!
        assertEquals(5, options.getInt("android.activity.launchDisplayId", -1))
    }
}
