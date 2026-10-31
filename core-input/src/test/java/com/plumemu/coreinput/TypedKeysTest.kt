package com.plumemu.coreinput

import android.view.InputDevice
import android.view.KeyEvent
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A hardware keyboard (Bluetooth, USB) while a screen takes text: real KeyEvents into the router.
 * Letters, digits, symbols and space are typed; Backspace, Delete and Enter are the field's; the
 * arrows move the caret only from an alphabetic keyboard; a gamepad's keys are never typed; with no
 * screen taking text the keys are left to Android as before.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class TypedKeysTest {
    private fun keyboard(
        action: Int,
        code: Int,
        meta: Int = 0,
        repeat: Int = 0,
    ) = KeyEvent(0L, 0L, action, code, repeat, meta, KEYBOARD_DEVICE, 0, 0, InputDevice.SOURCE_KEYBOARD)

    private fun gamepad(
        action: Int,
        code: Int,
    ) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD)

    private fun withRouter(block: (InputRouter, List<Action>) -> Unit) =
        runTest {
            val job = Job(coroutineContext[Job])
            val router = InputRouter(CoroutineScope(coroutineContext + job), onDebug = {}) { error("dropped $it") }
            val received = mutableListOf<Action>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { router.actions.collect { received += it } }
            block(router, received)
            job.cancel()
        }

    private fun InputRouter.type(
        code: Int,
        meta: Int = 0,
    ): Boolean = onKeyEvent(keyboard(KeyEvent.ACTION_DOWN, code, meta)).also { onKeyEvent(keyboard(KeyEvent.ACTION_UP, code, meta)) }

    @Test
    fun lettersDigitsSymbolsAndSpaceAreTypedOnAField() =
        withRouter { router, received ->
            router.textEntry = TextEntry.Field
            assertTrue(router.type(KeyEvent.KEYCODE_M))
            router.type(KeyEvent.KEYCODE_A, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            router.type(KeyEvent.KEYCODE_3)
            router.type(KeyEvent.KEYCODE_SPACE)
            router.type(KeyEvent.KEYCODE_MINUS)
            router.type(KeyEvent.KEYCODE_1, KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON)
            assertEquals(listOf("m", "A", "3", " ", "-", "!").map { Action.Typed(TypedKey.Text(it)) }, received)
        }

    @Test
    fun backspaceDeleteAndEnterAreTheFieldsAndAHeldKeyRepeatsAsOnAnyKeyboard() =
        withRouter { router, received ->
            router.textEntry = TextEntry.Keyboard
            router.type(KeyEvent.KEYCODE_DEL)
            router.onKeyEvent(keyboard(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            router.onKeyEvent(keyboard(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, repeat = 1))
            router.onKeyEvent(keyboard(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
            router.type(KeyEvent.KEYCODE_FORWARD_DEL)
            router.type(KeyEvent.KEYCODE_ENTER)
            router.type(KeyEvent.KEYCODE_NUMPAD_ENTER)
            assertEquals(
                listOf(TypedKey.Backspace, TypedKey.Backspace, TypedKey.Backspace, TypedKey.ForwardDelete, TypedKey.Enter, TypedKey.Enter).map { Action.Typed(it) },
                received,
            )
        }

    @Test
    fun escapeStaysBackAndNoTextEntryLeavesLettersToAndroid() =
        withRouter { router, received ->
            assertFalse("no screen takes text: the letter is Android's", router.type(KeyEvent.KEYCODE_M))
            assertTrue(router.type(KeyEvent.KEYCODE_ENTER))
            router.textEntry = TextEntry.Keyboard
            router.type(KeyEvent.KEYCODE_ESCAPE)
            router.type(KeyEvent.KEYCODE_TAB)
            assertEquals("Enter confirms with no field; Escape is Back; Tab types nothing", listOf(Action.Enter, Action.Back), received)
        }

    @Test
    fun aGamepadsKeysAreNeverTyped() =
        withRouter { router, received ->
            router.textEntry = TextEntry.Keyboard
            for (code in listOf(KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_ENTER)) {
                router.onKeyEvent(gamepad(KeyEvent.ACTION_DOWN, code))
                router.onKeyEvent(gamepad(KeyEvent.ACTION_UP, code))
            }
            assertEquals(listOf(Action.Enter, Action.Move(Direction.Left), Action.Enter), received)
        }

    @Test
    fun theArrowsMoveTheCaretOnlyFromAnAlphabeticKeyboard() {
        assertEquals(TypedKey.CaretLeft, TypedKeys.of(KeyEvent.KEYCODE_DPAD_LEFT, 0, isFromGamepad = false, isAlphabeticKeyboard = true))
        assertEquals(TypedKey.CaretRight, TypedKeys.of(KeyEvent.KEYCODE_DPAD_RIGHT, 0, isFromGamepad = false, isAlphabeticKeyboard = true))
        assertNull("a handheld's D-pad sent as keys", TypedKeys.of(KeyEvent.KEYCODE_DPAD_LEFT, 0, isFromGamepad = false, isAlphabeticKeyboard = false))
        assertNull("up and down stay moves", TypedKeys.of(KeyEvent.KEYCODE_DPAD_UP, 0, isFromGamepad = false, isAlphabeticKeyboard = true))
        assertNull("a dead key waits for its letter", TypedKeys.of(KeyEvent.KEYCODE_GRAVE, 0x60 or android.view.KeyCharacterMap.COMBINING_ACCENT, isFromGamepad = false, isAlphabeticKeyboard = true))
        assertNull(TypedKeys.of(KeyEvent.KEYCODE_BUTTON_START, 0, isFromGamepad = false, isAlphabeticKeyboard = true))
    }

    @Test
    fun anArrowHeldBeforeTheFieldTookKeysIsReleasedByItsKeyUp() =
        withRouter { router, received ->
            router.onKey(KeyEvent.KEYCODE_DPAD_LEFT, isDown = true, repeatCount = 0)
            assertTrue(router.isRepeating)
            router.textEntry = TextEntry.Field
            router.onTyped(TypedKey.CaretLeft, isDown = false, keyCode = KeyEvent.KEYCODE_DPAD_LEFT)
            assertFalse("released, not repeating forever", router.isRepeating)
            assertEquals(listOf(Action.Move(Direction.Left)), received)
        }

    private companion object {
        /** The virtual keyboard's device: its key map types like a full keyboard. */
        const val KEYBOARD_DEVICE = -1
    }
}
