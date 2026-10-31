package com.plumemu.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyboardDirection
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardModel
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.KeyboardState
import com.plumemu.coreui.components.keyboard.ShiftMode
import com.plumemu.keyboard.CaretText
import com.plumemu.keyboard.KeyboardChoice

/**
 * A field's part of Plumemu's keyboard: where typing goes in the
 * field ([caret], at the end of the pre-filled text to begin with) and the [keys] (by default letters
 * with shift: titles have capitals; Settings' hex field starts on the digits without shift, step
 * 2b-1). Kept while the field is open, so the tray comes back on the page and key it was left on.
 * Main thread only.
 */
@Stable
internal class FieldKeys(
    startText: String,
    hasShift: Boolean = true,
    startPage: KeyboardPage = KeyboardPage.Letters,
    /** An empty field's next letter is a capital ([shiftIfEmpty]); not for the sign-in form, whose name and password are typed as they are. */
    private val capitalizesFirst: Boolean = true,
) {
    var caret: Int by mutableIntStateOf(startText.length)

    val keys = KeyboardState(KeyboardModel(hasShift = hasShift, page = startPage))

    /** At the start of an empty field the next letter is a capital, as on a phone (shift once, unless locked). */
    fun shiftIfEmpty(text: String) {
        if (capitalizesFirst && text.isEmpty() && keys.shift == ShiftMode.Off) keys.toggleShift()
    }
}

/** The tray is up: Plumemu's keyboard, the cursor on the field and the person asked to type. */
internal val TypingState.isTrayUp: Boolean get() = keyboardKind == KeyboardChoice.Plumemu && keyboardUp

/**
 * What the input router must send for this field now (docs/conventions/input.md): with Plumemu's
 * keyboard, B erasing and typed keys while the tray is up, typed keys with the cursor on the field
 * and the tray down; nothing else (Android's keyboard, or the cursor on a row).
 */
internal val TypingState.textEntry: TextEntry
    get() =
        when {
            keyboardKind != KeyboardChoice.Plumemu || !inField -> TextEntry.None
            keyboard.isTyping -> TextEntry.Keyboard
            else -> TextEntry.Field
        }

/** A tap on the field's line at character [offset]: typing, with the caret there. */
internal fun TypingState.focusFieldAt(offset: Int) {
    fieldKeys.caret = offset.coerceIn(0, fieldText.length)
    focusField()
}

/**
 * An action for a field typed with Plumemu's keyboard, before the state's own handling: the button
 * map while the tray is up, a hardware keyboard with the tray
 * down. Null when the state's own rules apply (Android's keyboard, the cursor on a row, the tray down,
 * a tap on a row).
 */
internal fun TypingState.ownKeyboardOutcome(action: Action): TypingOutcome? {
    if (keyboardKind != KeyboardChoice.Plumemu || !inField) return null
    if (!keyboard.isTyping) {
        return when (action) {
            is Action.Typed -> typed(action.key)
            // The router had not yet heard the tray went down: never erase or leave on it.
            is Action.Erase -> TypingOutcome.Handled
            else -> null
        }
    }
    when (action) {
        // Up or Down out of the keys goes nowhere: the hide key, Back or START leave the tray.
        is Action.Move -> fieldKeys.keys.move(action.direction.onKeys(), action.isRepeat)
        Action.Enter, Action.Play -> fieldKeys.keys.press()?.let { return onKeyboardEvent(it) }
        is Action.Erase ->
            when {
                fieldText.isNotEmpty() -> edit { it.deleteBefore() }
                // A fresh B with nothing to delete puts the tray away; a held one only stops.
                !action.isRepeat -> keyboard.stop()
            }
        // The system's Back, Escape or the gesture put the tray away; the cursor stays on the field.
        Action.Back -> keyboard.stop()
        Action.Search -> edit { it.insert(" ") }
        Action.ToggleFavorite -> fieldKeys.keys.togglePage()
        Action.ToggleView -> fieldKeys.keys.toggleShift()
        Action.Menu -> return keyboardActionKey()
        Action.PreviousSystem -> edit { it.moveBy(-1) }
        Action.NextSystem -> edit { it.moveBy(1) }
        Action.JumpLetterPrevious -> edit { it.toStart() }
        Action.JumpLetterNext -> edit { it.toEnd() }
        is Action.Typed -> return typed(action.key)
        // A tap on a row does what it does with the tray down.
        is Action.TapItem, is Action.LongPressItem -> return null
        Action.OpenSettings -> Unit
    }
    return TypingOutcome.Handled
}

/** A key of Plumemu's keyboard was pressed (A on the focused key) or tapped. */
internal fun TypingState.onKeyboardEvent(event: KeyboardEvent): TypingOutcome {
    when (event) {
        is KeyboardEvent.Type -> edit { it.insert(event.char.toString()) }
        KeyboardEvent.Space -> edit { it.insert(" ") }
        KeyboardEvent.Delete -> edit { it.deleteBefore() }
        KeyboardEvent.Action -> return keyboardActionKey()
        KeyboardEvent.Hide -> keyboard.stop()
    }
    return TypingOutcome.Handled
}

/**
 * The action key (START, the tray's key, a hardware Enter): exactly what the field's Android IME
 * action does ([TypingState.onKeyboardAction]), once; an action it hands back is handled here as a
 * routed press would be (Use my own name saves, Fix box art searches).
 */
private fun TypingState.keyboardActionKey(): TypingOutcome = onKeyboardAction()?.let(::onAction) ?: TypingOutcome.Handled

private fun TypingState.typed(key: TypedKey): TypingOutcome {
    val next = CaretText(fieldText, fieldKeys.caret).typed(key) ?: return keyboardActionKey()
    applyEdit(next)
    return TypingOutcome.Handled
}

private fun TypingState.edit(change: (CaretText) -> CaretText) = applyEdit(change(CaretText(fieldText, fieldKeys.caret)))

private fun TypingState.applyEdit(next: CaretText) {
    replaceText(next.text)
    // A field with a length limit (the hex code) may keep less than was typed: the caret stays inside it.
    fieldKeys.caret = next.caret.coerceAtMost(fieldText.length)
    if (keyboard.isTyping) fieldKeys.shiftIfEmpty(next.text)
}

private fun Direction.onKeys(): KeyboardDirection =
    when (this) {
        Direction.Up -> KeyboardDirection.Up
        Direction.Down -> KeyboardDirection.Down
        Direction.Left -> KeyboardDirection.Left
        Direction.Right -> KeyboardDirection.Right
    }
