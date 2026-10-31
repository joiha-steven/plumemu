package com.plumemu

import android.os.Looper
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.home.drainMainLooper
import com.plumemu.secondscreen.StrayFocusCatcher
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * `MainActivity` hands its controller events to `StrayFocusCatcher`: losing the controller while resumed, or resuming without it, lets the
 * bottom window catch the focus; onPause and getting it back stop that. Needs no content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityStrayFocusTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()

    @After
    fun tearDown() {
        drainMainLooper()
    }

    private fun waitPastTheDelay() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(StrayFocusCatcher.DELAY_MS + 50))

    @Test
    fun losingTheControllerWhileResumedCatchesUntilPaused() {
        activity.onTopResumedActivityChanged(true)
        activity.onTopResumedActivityChanged(false)
        waitPastTheDelay()
        assertTrue(activity.strayFocus.isCatching)
        controller.pause()
        assertFalse("paused: never focusable over a game", activity.strayFocus.isCatching)
    }

    @Test
    fun gettingTheControllerBackStopsCatching() {
        activity.onTopResumedActivityChanged(false)
        waitPastTheDelay()
        activity.onTopResumedActivityChanged(true)
        assertFalse(activity.strayFocus.isCatching)
    }

    @Test
    fun resumingWithoutTheControllerCatches() {
        controller.pause()
        controller.resume()
        waitPastTheDelay()
        assertTrue("back from a game while the bottom display holds the controller", activity.strayFocus.isCatching)
    }
}
