package com.plumemu.coreinput

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Android entry points with real KeyEvents and MotionEvents: what is taken, what is left. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class InputRouterAndroidEventsTest {
    private fun key(
        action: Int,
        code: Int,
        repeat: Int = 0,
    ) = KeyEvent(0L, 0L, action, code, repeat, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    private fun motion(
        source: Int,
        vararg axes: Pair<Int, Float>,
    ): MotionEvent {
        val properties = MotionEvent.PointerProperties().apply { id = 0 }
        val coords = MotionEvent.PointerCoords().apply { axes.forEach { (axis, value) -> setAxisValue(axis, value) } }
        return MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_MOVE, 1, arrayOf(properties), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, source, 0)
    }

    @Test
    fun gamepadKeysAreTakenOnDownAndUpOtherKeysAreLeft() =
        runTest {
            val job = Job(coroutineContext[Job])
            val router = InputRouter(CoroutineScope(coroutineContext + job)) { error("dropped $it") }
            val received = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.actions.collect { received += it } }

            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A, repeat = 1)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_Y)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_Y)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
            assertTrue(router.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
            assertFalse(router.onKeyEvent(key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))
            assertFalse(router.onKeyEvent(key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP)))

            // Y is taken and sends its action once (Favorite); the key repeat of A is ignored.
            assertEquals(listOf(Action.Enter, Action.ToggleFavorite, Action.Back), received)
            job.cancel()
        }

    @Test
    fun aKeyboardsRightArrowIsTheSameRightAsTheDpad() =
        runTest {
            val job = Job(coroutineContext[Job])
            val router = InputRouter(CoroutineScope(coroutineContext + job)) { error("dropped $it") }
            val received = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.actions.collect { received += it } }
            val arrow = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, 0, 0, 0, 0, InputDevice.SOURCE_KEYBOARD)
            assertTrue(router.onKeyEvent(arrow))
            assertEquals(listOf<Action>(Action.Move(Direction.Right)), received)
            job.cancel()
        }

    @Test
    fun joystickHatStickAndTriggersBecomeActionsAndAreTaken() =
        runTest {
            val job = Job(coroutineContext[Job])
            val router = InputRouter(CoroutineScope(coroutineContext + job)) { error("dropped $it") }
            val received = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.actions.collect { received += it } }
            val joystick = InputDevice.SOURCE_JOYSTICK

            assertTrue(router.onGenericMotionEvent(motion(joystick, MotionEvent.AXIS_HAT_Y to -1f)))
            assertTrue(router.onGenericMotionEvent(motion(joystick)))
            assertTrue(router.onGenericMotionEvent(motion(joystick, MotionEvent.AXIS_X to 0.9f)))
            assertTrue(router.onGenericMotionEvent(motion(joystick)))
            assertTrue(router.onGenericMotionEvent(motion(joystick, MotionEvent.AXIS_BRAKE to 1f)))
            assertTrue(router.onGenericMotionEvent(motion(joystick)))
            assertTrue(router.onGenericMotionEvent(motion(joystick, MotionEvent.AXIS_GAS to 1f)))
            assertTrue(router.onGenericMotionEvent(motion(joystick)))
            assertFalse("touch is not the router's", router.onGenericMotionEvent(motion(InputDevice.SOURCE_TOUCHSCREEN)))

            assertEquals(
                listOf(Action.Move(Direction.Up), Action.Move(Direction.Right), Action.JumpLetterPrevious, Action.JumpLetterNext),
                received,
            )
            assertFalse(router.isRepeating)
            job.cancel()
        }
}
