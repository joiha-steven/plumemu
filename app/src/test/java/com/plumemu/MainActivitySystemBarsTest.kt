package com.plumemu

import android.os.Looper
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.plumemu.coreui.testing.ReferenceShots
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The main screen hides the status and navigation bars, immersively, and hides them again on focus. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivitySystemBarsTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun barsVisible(): Pair<Boolean?, Boolean?> {
        val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        return insets?.isVisible(WindowInsetsCompat.Type.statusBars()) to insets?.isVisible(WindowInsetsCompat.Type.navigationBars())
    }

    @Test
    fun bothBarsAreHiddenAndComeBackOnlyBySwipe() {
        idle()
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        assertEquals(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE, insetsController.systemBarsBehavior)
        assertEquals("status, navigation", false to false, barsVisible())
    }

    @Test
    fun regainingFocusHidesThemAgain() {
        idle()
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).show(WindowInsetsCompat.Type.systemBars())
        idle()
        assertEquals("shown by someone else", true to true, barsVisible())
        activity.onWindowFocusChanged(true)
        idle()
        assertEquals(false to false, barsVisible())
    }
}
