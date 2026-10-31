package com.plumemu.coreinput

import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * A hardware keyboard's keys while a screen takes text ([TextEntry.Field] or [TextEntry.Keyboard]).
 * A gamepad's keys never count, even the ones a keyboard shares with it: Enter is the field's
 * action only from a keyboard, and the arrows move the caret only from an alphabetic keyboard
 * (a handheld's D-pad can arrive as a keyboard device without the gamepad source, but never as an
 * alphabetic one). Every other control key (D-pad, face buttons, Back, Escape) stays a control.
 */
internal object TypedKeys {
    fun of(
        keyCode: Int,
        unicodeChar: Int,
        isFromGamepad: Boolean,
        isAlphabeticKeyboard: Boolean,
    ): TypedKey? {
        if (isFromGamepad) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> TypedKey.Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> TypedKey.ForwardDelete
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> TypedKey.Enter
            KeyEvent.KEYCODE_DPAD_LEFT -> if (isAlphabeticKeyboard) TypedKey.CaretLeft else null
            KeyEvent.KEYCODE_DPAD_RIGHT -> if (isAlphabeticKeyboard) TypedKey.CaretRight else null
            else -> if (GamepadMapping.controlFor(keyCode) != null) null else printable(unicodeChar)
        }
    }

    fun isFromGamepad(event: KeyEvent): Boolean =
        event.isFromSource(InputDevice.SOURCE_GAMEPAD) || event.isFromSource(InputDevice.SOURCE_JOYSTICK)

    fun isAlphabeticKeyboard(event: KeyEvent): Boolean = event.device?.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC

    /** The character a key produced, or null for none, a dead key (an accent waiting for its letter) or a control character (Tab). */
    private fun printable(unicodeChar: Int): TypedKey? {
        if (unicodeChar == 0 || unicodeChar and KeyCharacterMap.COMBINING_ACCENT != 0) return null
        val text = String(Character.toChars(unicodeChar))
        return if (text.any { Character.isISOControl(it) }) null else TypedKey.Text(text)
    }
}
