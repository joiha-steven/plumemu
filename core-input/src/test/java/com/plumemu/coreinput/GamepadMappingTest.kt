package com.plumemu.coreinput

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Every key core-input takes, under both layouts with and without the A/B swap. */
class GamepadMappingTest {
    private val xbox = InputSettings(ButtonLayout.Xbox, swapAB = false)
    private val xboxSwapped = InputSettings(ButtonLayout.Xbox, swapAB = true)
    private val nintendo = InputSettings(ButtonLayout.Nintendo, swapAB = false)
    private val nintendoSwapped = InputSettings(ButtonLayout.Nintendo, swapAB = true)
    private val all = listOf(xbox, xboxSwapped, nintendo, nintendoSwapped)

    /** Keys whose action does not depend on layout or swap. */
    private val fixed: Map<Int, Action> =
        mapOf(
            KeyEvent.KEYCODE_DPAD_UP to Action.Move(Direction.Up),
            KeyEvent.KEYCODE_DPAD_DOWN to Action.Move(Direction.Down),
            KeyEvent.KEYCODE_DPAD_LEFT to Action.Move(Direction.Left),
            KeyEvent.KEYCODE_DPAD_RIGHT to Action.Move(Direction.Right),
            KeyEvent.KEYCODE_DPAD_CENTER to Action.Enter,
            KeyEvent.KEYCODE_ENTER to Action.Enter,
            KeyEvent.KEYCODE_NUMPAD_ENTER to Action.Enter,
            KeyEvent.KEYCODE_BACK to Action.Back,
            KeyEvent.KEYCODE_ESCAPE to Action.Back,
            KeyEvent.KEYCODE_BUTTON_L1 to Action.PreviousSystem,
            KeyEvent.KEYCODE_BUTTON_R1 to Action.NextSystem,
            KeyEvent.KEYCODE_BUTTON_L2 to Action.JumpLetterPrevious,
            KeyEvent.KEYCODE_BUTTON_R2 to Action.JumpLetterNext,
            KeyEvent.KEYCODE_BUTTON_START to Action.Menu,
            KeyEvent.KEYCODE_BUTTON_SELECT to Action.ToggleView,
        )

    /** Face buttons (Android names them by position): settings -> key -> action. */
    private val faces: Map<InputSettings, Map<Int, Action?>> =
        mapOf(
            // X and Y follow the printed letters of the layout and never swap: on an
            // Xbox pad the west button is X, on a Nintendo-printed handheld the west button is Y.
            xbox to faceRow(a = Action.Enter, b = Action.Back, x = Action.Search, y = Action.ToggleFavorite),
            xboxSwapped to faceRow(a = Action.Back, b = Action.Enter, x = Action.Search, y = Action.ToggleFavorite),
            nintendo to faceRow(a = Action.Back, b = Action.Enter, x = Action.ToggleFavorite, y = Action.Search),
            nintendoSwapped to faceRow(a = Action.Enter, b = Action.Back, x = Action.ToggleFavorite, y = Action.Search),
        )

    private fun faceRow(
        a: Action,
        b: Action,
        x: Action,
        y: Action,
    ): Map<Int, Action?> =
        mapOf(
            KeyEvent.KEYCODE_BUTTON_A to a,
            KeyEvent.KEYCODE_BUTTON_B to b,
            KeyEvent.KEYCODE_BUTTON_X to x,
            KeyEvent.KEYCODE_BUTTON_Y to y,
        )

    private fun actionOf(
        keyCode: Int,
        settings: InputSettings,
    ): Action? {
        val control = GamepadMapping.controlFor(keyCode) ?: error("key $keyCode is not taken")
        return GamepadMapping.actionFor(control, settings)
    }

    @Test
    fun fixedKeysActTheSameUnderEveryLayoutAndSwap() {
        for (settings in all) {
            for ((key, expected) in fixed) {
                assertEquals("key $key with $settings", expected, actionOf(key, settings))
            }
        }
    }

    @Test
    fun faceButtonsFollowLayoutAndSwap() {
        for ((settings, row) in faces) {
            for ((key, expected) in row) {
                assertEquals("key $key with $settings", expected, actionOf(key, settings))
            }
        }
    }

    @Test
    fun theButtonLetteredAAlwaysConfirms() {
        for (settings in all) {
            for (control in listOf(GamepadControl.ButtonA, GamepadControl.ButtonB, GamepadControl.ButtonX, GamepadControl.ButtonY)) {
                val letter = GamepadMapping.letterOf(control, settings)
                val action = GamepadMapping.actionFor(control, settings)
                when (letter) {
                    FaceLetter.A -> assertEquals(Action.Enter, action)
                    FaceLetter.B -> assertEquals(Action.Back, action)
                    FaceLetter.X -> assertEquals(Action.Search, action)
                    FaceLetter.Y -> assertEquals(Action.ToggleFavorite, action)
                    null -> assertNull(action)
                }
            }
        }
    }

    @Test
    fun nintendoLettersTradeXAndYButSwapLeavesThem() {
        assertEquals(FaceLetter.X, GamepadMapping.letterOf(GamepadControl.ButtonX, xbox))
        assertEquals(FaceLetter.Y, GamepadMapping.letterOf(GamepadControl.ButtonX, nintendo))
        assertEquals(FaceLetter.X, GamepadMapping.letterOf(GamepadControl.ButtonY, nintendo))
        assertEquals(FaceLetter.X, GamepadMapping.letterOf(GamepadControl.ButtonX, xboxSwapped))
        assertEquals(FaceLetter.Y, GamepadMapping.letterOf(GamepadControl.ButtonX, nintendoSwapped))
    }

    @Test
    fun theDefaultIsXboxWithoutSwapLikeAndroidItself() {
        assertEquals(xbox.copy(holdToScrollFaster = true), InputSettings())
        assertEquals(Action.Enter, actionOf(KeyEvent.KEYCODE_BUTTON_A, InputSettings()))
    }

    @Test
    fun otherKeysAreLeftToAndroid() {
        val untouched =
            listOf(
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_POWER,
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_SPACE,
            )
        for (key in untouched) assertNull("key $key", GamepadMapping.controlFor(key))
    }
}
