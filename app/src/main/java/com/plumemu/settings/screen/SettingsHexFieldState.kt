package com.plumemu.settings.screen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.game.KeyboardAsk
import com.plumemu.settings.ColorRefusal

/**
 * The state of a colour page's Color code field: whether it is being typed into, the keyboard asks,
 * the typed text, the error and the refusal the code met. [SettingsCustomColor] adds what a typed code does to the colour.
 */
internal abstract class HexFieldState {
    /** The hex code field has the keyboard. */
    var isEditingHex: Boolean by mutableStateOf(false)
        protected set

    /**
     * The asks for the hex keyboard (starting to type, A on the field, a tap on it): [hexKeyboardAsks]
     * counts them, so A brings Android's keyboard back after the system's Back closed it; while
     * [isTypingHex] Plumemu's tray is up ([HexTyping]).
     */
    val hexKeyboard = KeyboardAsk()

    val hexKeyboardAsks: Int get() = hexKeyboard.asks

    /**
     * The field as Plumemu's keyboard types into it, made at each start of editing with the
     * keyboard Settings › Controls › Keyboard named then; null before the first.
     */
    var hexTyping: HexTyping? by mutableStateOf(null)
        protected set

    /** The cursor is on "Use this colour" under the field (keyboard down); false: on the field. */
    var hexOnUse: Boolean by mutableStateOf(false)
        protected set

    /** A on the field or a tap on it: the cursor is on the field and its keyboard comes up (again). */
    fun askHexKeyboard() {
        hexOnUse = false
        hexKeyboard.ask()
    }

    /** The hex keyboard is wanted: asked for and the cursor has not left the field since. */
    val isTypingHex: Boolean get() = hexKeyboard.isTyping

    /** D-pad Down / Up between the field and "Use this colour"; neither opens the keyboard. */
    fun moveInHex(toUse: Boolean) {
        hexOnUse = toUse
        hexKeyboard.stop()
    }

    /** What the hex code field holds while it has the keyboard. */
    var hexText: String by mutableStateOf("")
        protected set

    /** The last typed hex code could not be read. */
    var hexError: Boolean by mutableStateOf(false)
        protected set

    /**
     * The typed code [hexRefusedText] was refused as unreadable and [ColorRefusal.nearest] is the
     * colour offered instead: "Use" on the row below the field takes it. Null otherwise.
     */
    var hexRefusal: ColorRefusal? by mutableStateOf(null)
        protected set

    protected var hexRefusedText: String = ""

    /** The refusal goes (the code changed, or editing stopped); the colour side clears what it said. */
    protected abstract fun clearHexRefusal()

    fun onHexTextChange(text: String) {
        hexText = text.take(HEX_TEXT_MAX)
        if (hexRefusal != null && hexText != hexRefusedText) clearHexRefusal()
    }

    fun stopEditingHex() {
        isEditingHex = false
        hexOnUse = false
        hexKeyboard.stop()
        hexError = false
        clearHexRefusal()
    }

    companion object {
        /** "#" and six digits. */
        const val HEX_TEXT_MAX = 7
    }
}
