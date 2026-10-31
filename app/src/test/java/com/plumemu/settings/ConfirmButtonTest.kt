package com.plumemu.settings

import android.view.InputDevice
import android.view.KeyEvent
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.ControlsSettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.testing.ReferenceShots
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Real key events through core-input's router with the stored confirm button. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class ConfirmButtonTest {
    private fun TestScope.actionsFor(controls: ControlsSettings, vararg keyCodes: Int): List<Action> {
        val router = InputRouter(backgroundScope, controls.toInputSettings(), clock = { 0L }, onDebug = {}, onStalled = {})
        val received = mutableListOf<Action>()
        backgroundScope.launch { router.actions.collect { received += it } }
        runCurrent()
        for (code in keyCodes) {
            router.onKeyEvent(key(KeyEvent.ACTION_DOWN, code))
            router.onKeyEvent(key(KeyEvent.ACTION_UP, code))
        }
        runCurrent()
        return received
    }

    private fun key(action: Int, code: Int) = KeyEvent(0L, 0L, action, code, 0, 0, 0, 0, 0, InputDevice.SOURCE_GAMEPAD)

    @Test
    fun byDefaultButtonAConfirmsAndButtonBGoesBack() = runTest {
        val actions = actionsFor(ControlsSettings(), KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(listOf(Action.Enter, Action.Back), actions)
    }

    @Test
    fun thorSendsByLetterSoThePrintedAConfirmsOutOfTheBox() = runTest {
        // Release APK on the AYN Thor: the button printed A sends KEYCODE_BUTTON_A,
        // printed B sends BUTTON_B, X sends BUTTON_X, Y sends BUTTON_Y. A confirms, B goes back.
        val actions = actionsFor(
            PlumSettings.Defaults.controls,
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        )
        assertEquals(listOf(Action.Enter, Action.Back, Action.Search, Action.ToggleFavorite), actions)
    }

    @Test
    fun swapAAndBTurnsItRound() = runTest {
        val actions = actionsFor(ControlsSettings(ConfirmButtonSetting.Bottom), KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(listOf(Action.Back, Action.Enter), actions)
    }

    @Test
    fun dpadCenterEnterBackAndEscapeIgnoreTheSwap() = runTest {
        for (controls in listOf(ControlsSettings(), ControlsSettings(ConfirmButtonSetting.Bottom))) {
            val actions = actionsFor(
                controls,
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE,
            )
            assertEquals("with ${controls.confirmButton}", listOf(Action.Enter, Action.Enter, Action.Back, Action.Back), actions)
        }
    }

    @Test
    fun xAndYStayWherePrintedWhicheverButtonConfirms() = runTest {
        // Only A and B swap. The Thor sends by letter: BUTTON_X is X (search), BUTTON_Y is Y (favorite).
        for (controls in listOf(ControlsSettings(), ControlsSettings(ConfirmButtonSetting.Bottom))) {
            val actions = actionsFor(controls, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y)
            assertEquals("with ${controls.confirmButton}", listOf(Action.Search, Action.ToggleFavorite), actions)
        }
    }
}
