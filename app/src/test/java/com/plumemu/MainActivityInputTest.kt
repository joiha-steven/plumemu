package com.plumemu

import android.os.Looper
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.lifecycle.lifecycleScope
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
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
import java.time.Duration

/** The Activity hands keys, joystick moves and the back gesture to core-input and keeps the rest. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class MainActivityInputTest {
    private val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    private val activity = controller.get()
    private val received = mutableListOf<Action>()

    init {
        activity.lifecycleScope.launch { activity.input.actions.collect { received += it } }
        idle()
    }

    private fun idle(by: Duration = Duration.ZERO) = shadowOf(Looper.getMainLooper()).idleFor(by)

    private fun key(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    @Test
    fun gamepadKeysBecomeActionsAndAreTaken() {
        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue(activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
        assertTrue("B is taken so Android does not turn it into Back", activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_B)))
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_B))
        idle()
        // The Thor sends by letter: BUTTON_A (printed A) confirms by default, BUTTON_B goes back.
        assertEquals(listOf(Action.Enter, Action.Back), received)
    }

    @Test
    fun volumeKeysAreLeftToAndroid() {
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP))
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP))
        idle()
        assertTrue(received.isEmpty())
    }

    @Test
    fun theStickIsTakenAndMoves() {
        val properties = MotionEvent.PointerProperties().apply { id = 0 }
        val pushed = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_Y, 1f) }
        val event =
            MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(pushed), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_JOYSTICK, 0)
        assertTrue(activity.dispatchGenericMotionEvent(event))
        activity.onWindowFocusChanged(false)
        idle()
        assertEquals(listOf(Action.Move(Direction.Down)), received)
    }

    @Test
    fun theBackGestureSendsBack() {
        activity.onBackPressedDispatcher.onBackPressed()
        idle()
        assertEquals(listOf(Action.Back), received)
        assertFalse("Back on the home screen never closes it", activity.isFinishing)
    }

    @Test
    fun losingWindowFocusStopsAHeldDirection() {
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        idle(Duration.ofMillis(100))
        activity.onWindowFocusChanged(false)
        idle(Duration.ofSeconds(3))
        assertEquals(listOf(Action.Move(Direction.Down)), received)
    }

    @Test
    fun aHeldDirectionRepeatsOnTheMainThreadUntilReleased() {
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_DOWN))
        idle(Duration.ofMillis(500))
        activity.dispatchKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_DOWN))
        val count = received.size
        idle(Duration.ofSeconds(3))
        assertTrue("repeated while held: $received", count > 1)
        assertEquals(count, received.size)
    }
}
