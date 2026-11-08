package com.plumemu

import android.os.Looper
import android.view.Choreographer
import androidx.core.view.ViewCompat
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import com.plumemu.secondscreen.StrayFocusCatcher
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * The state an earlier test can leave on the main Choreographer (a frame marked as scheduled that never comes) is
 * cleared by [unstickMainChoreographer], which [TestPlumApplication] runs at every test's start: without it MainActivity is never laid out, and the stray-focus catcher's
 * delayed start never runs (both seen as flaky MainActivity tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class StuckChoreographerTest {
    @After
    fun tearDown() = drainMainLooper()

    private fun leaveAFrameThatNeverComes() {
        Choreographer::class.java.getDeclaredField("mFrameScheduled").apply { isAccessible = true }
            .setBoolean(Choreographer.getInstance(), true)
    }

    @Test
    fun theMainScreenIsLaidOutAfterAStuckFrame() {
        leaveAFrameThatNeverComes()
        unstickMainChoreographer()
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull("the decor view has insets", ViewCompat.getRootWindowInsets(activity.window.decorView))
    }

    @Test
    fun theStrayFocusCatcherStartsAfterAStuckFrame() {
        leaveAFrameThatNeverComes()
        unstickMainChoreographer()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        controller.pause()
        controller.resume()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StrayFocusCatcher.DELAY_MS + 50))
        assertTrue("resumed without the controller: catching", controller.get().strayFocus.isCatching)
    }
}
