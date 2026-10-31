package com.plumemu.coreinput

import androidx.compose.runtime.Immutable

/**
 * What the person asked for, whichever way they asked: a gamepad button or a touch. Screens
 * collect these and decide by context what each means (the table with every binding is in
 * core-input/README.md; the source is). Names stay generic: the main
 * screen reads [NextSystem] as "next system", Settings reads it as "next section".
 */
@Immutable
sealed interface Action {
    /**
     * D-pad, left stick or hat. [isRepeat] is true for the moves that hold-to-repeat sends after
     * the first one, so a list can wrap on a fresh press but stop at its end while held.
     */
    data class Move(
        val direction: Direction,
        val isRepeat: Boolean = false,
    ) : Action

    /** Confirm: A (after layout and swap), D-pad centre, Enter. Opens a column; on a game it plays. */
    data object Enter : Action

    /** B (after layout and swap), Back, Escape, the system back gesture, or the "B Back" hint. */
    data object Back : Action

    /** R1. Main screen: next system. Settings: next section. */
    data object NextSystem : Action

    /** L1. Main screen: previous system. Settings: previous section. */
    data object PreviousSystem : Action

    /** R2 (key or trigger axis): jump to the next first letter. */
    data object JumpLetterNext : Action

    /** L2 (key or trigger axis): jump to the previous first letter. */
    data object JumpLetterPrevious : Action

    /** Play the focused game. From touch: a second tap on the focused game, or the "A Play" hint. */
    data object Play : Action

    /** START. On a game: the game menu. On the system column the main screen reads it as Settings. */
    data object Menu : Action

    /** SELECT, or the "SELECT" hint: list or grid for the focused system. */
    data object ToggleView : Action

    /** The "START Settings" hint. */
    data object OpenSettings : Action

    /** Y, or the "Y Favorite" / "Y Unfavorite" hint: on a game, add it to Favorites or take it out. */
    data object ToggleFavorite : Action

    /** X, or the "X Search" hint: open search (the main screen's systems and games columns). */
    data object Search : Action

    /**
     * A tap on the item at [index] in [column]. The screen's focus state decides: focus it, or,
     * when it is already focused, act on it (a game plays).
     */
    data class TapItem(
        val column: Column,
        val index: Int,
    ) : Action

    /** A long press on the item at [index] in [column]: focus it and open its menu. */
    data class LongPressItem(
        val column: Column,
        val index: Int,
    ) : Action

    /**
     * The B button (after the confirm swap) while a screen types with Plumemu's own keyboard
     * ([InputRouter.textEntry] is [TextEntry.Keyboard]), or the "B Delete" hint: delete the
     * character before the caret. Held, it repeats like a move ([isRepeat] after the first), so a
     * screen can close on a fresh press with nothing left to delete and never on a held one. The
     * system's Back and Escape stay [Back].
     */
    data class Erase(
        val isRepeat: Boolean = false,
    ) : Action

    /** A hardware keyboard's key while a screen takes text ([InputRouter.textEntry] is not [TextEntry.None]). */
    data class Typed(
        val key: TypedKey,
    ) : Action
}

/** What a hardware keyboard (Bluetooth, USB) typed into a screen that takes text. */
@Immutable
sealed interface TypedKey {
    /** A printable character (letters, digits, symbols, space) as the keyboard produced it. */
    data class Text(
        val text: String,
    ) : TypedKey

    /** Backspace: the character before the caret. */
    data object Backspace : TypedKey

    /** Delete (forward delete): the character after the caret. */
    data object ForwardDelete : TypedKey

    /** The arrow keys of a hardware keyboard move the caret, not the cursor. */
    data object CaretLeft : TypedKey

    data object CaretRight : TypedKey

    /** Enter: the field's action. */
    data object Enter : TypedKey
}

/**
 * What a screen that takes text asks of [InputRouter] while it shows; [None] everywhere else, so
 * every other screen gets exactly the actions it always had.
 */
enum class TextEntry {
    /** No text field takes keys: letters are left to Android, B is [Action.Back]. */
    None,

    /** A field with Plumemu's keyboard put away: a hardware keyboard types into it ([Action.Typed]). */
    Field,

    /** Plumemu's keyboard is up: also the B button erases ([Action.Erase]) and repeats while held. */
    Keyboard,
}

enum class Direction { Up, Down, Left, Right }

/**
 * The three columns of the strip. Settings uses the same strip and reads them as sections,
 * options and panel.
 */
enum class Column { Systems, Games, Details }
