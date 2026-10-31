package com.plumemu

import android.content.Intent
import android.os.Looper
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Which Home intents reset the main screen, in the order Android really delivers them: it never
 * delivers a new intent to a RESUMED activity (it pauses first), and the lifecycle is no help.
 * A press over Plumemu arrives with the window still focused; the return from a game arrives with
 * `FLAG_ACTIVITY_BROUGHT_TO_FRONT` and no focus, and keeps focus and scroll (singleTask's purpose).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityHomePressTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private var presses = 0
    private val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    init {
        activity.lifecycleScope.launch { activity.homePresses.presses.collect { presses++ } }
        idle()
    }

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    @Test
    fun homeOverPlumemuIsAPress() {
        controller.windowFocusChanged(true)
        controller.pause()
        controller.newIntent(home)
        controller.resume()
        idle()
        assertEquals(1, presses)
    }

    @Test
    fun homeFromAGameIsNotAPress() {
        controller.windowFocusChanged(false)
        controller.pause().stop()
        controller.restart().start()
        controller.newIntent(Intent(home).addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT))
        controller.resume()
        idle()
        assertEquals(0, presses)
    }

    @Test
    fun homeWhileADialogOrTheShadeHasTheFocusIsNotAPress() {
        controller.windowFocusChanged(false)
        controller.pause()
        controller.newIntent(home)
        controller.resume()
        idle()
        assertEquals(0, presses)
    }

    @Test
    fun anIntentWithoutTheHomeCategoryIsNotAPress() {
        controller.windowFocusChanged(true)
        controller.pause()
        controller.newIntent(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        controller.resume()
        idle()
        assertEquals(0, presses)
    }

    @Test
    fun theDecisionNeedsFocusAndNoBroughtToFront() {
        val brought = Intent(home).addFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)
        assertTrue(isHomePressInFront(home, hasWindowFocus = true))
        assertFalse(isHomePressInFront(home, hasWindowFocus = false))
        assertFalse(isHomePressInFront(brought, hasWindowFocus = true))
        assertFalse(isHomePressInFront(null, hasWindowFocus = true))
    }
}
