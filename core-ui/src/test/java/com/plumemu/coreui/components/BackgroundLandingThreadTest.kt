package com.plumemu.coreui.components

import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Collections

/**
 * A landed background image tells the draws that used a stand-in from the main thread, never from the build thread: a snapshot write
 * on the low-priority build thread queues work on Compose's UI dispatcher under its lock, and a build thread stopped there holds the main thread up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class BackgroundLandingThreadTest {
    @Test
    fun aLandingIsWrittenOnTheMainThread() {
        val writers = Collections.synchronizedList(mutableListOf<String>())
        val handle = Snapshot.registerGlobalWriteObserver { writers += Thread.currentThread().name }
        try {
            repeat(BUILDS) { assertTrue(BackgroundBuilds.submit { ImageBitmap(2, 2) }) }
            val end = System.nanoTime() + 20_000_000_000L
            while (BackgroundBuilds.waiting > 0 && System.nanoTime() < end) Thread.sleep(5)
            assertEquals("background builds did not finish", 0, BackgroundBuilds.waiting)
            shadowOf(Looper.getMainLooper()).idle()
        } finally {
            handle.dispose()
        }
        assertTrue("no landing was written", writers.isNotEmpty())
        assertEquals("a landing was written off the main thread: $writers", emptyList<String>(), writers.filter { it == BUILD_THREAD })
    }

    private companion object {
        const val BUILDS = 20
        const val BUILD_THREAD = "plum-image-builds"
    }
}
