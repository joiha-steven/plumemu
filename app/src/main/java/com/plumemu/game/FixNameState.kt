package com.plumemu.game

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.keyboard.KeyboardChoice

/** What a panel with a text field made of an action. */
internal sealed interface TypingOutcome {
    /** Done inside the field or its list. */
    data object Handled : TypingOutcome

    /** Not the field's: the panel's own handling (Back, START close) applies. */
    data object NotHandled : TypingOutcome

    data class Run(val command: PanelCommand) : TypingOutcome
}

/**
 * Whether a text field's Android keyboard is wanted: A or a tap on the field asks for it
 * ([ask]); leaving the field or the IME's own action ends it ([stop]). [asks] counts the asks, so
 * one brings the keyboard back even when the system's Back closed it with the cursor still on the
 * field (the app is not told). Opening a panel never asks (same as the sign-in
 * form's `isTyping` / `keyboardAsks`). Main thread only.
 */
@Stable
internal class KeyboardAsk {
    var isTyping: Boolean by mutableStateOf(false)
        private set

    var asks: Int by mutableIntStateOf(0)
        private set

    fun ask() {
        isTyping = true
        asks++
    }

    fun stop() {
        isTyping = false
    }
}

/**
 * A panel's text field and the rows under it, for both inputs: the cursor is on
 * the field ([inField]) or on a row. The keyboard is up only after A or a tap on the field asked
 * for it ([keyboardUp]). With Android's keyboard D-pad Down leaves the field and hides it, Up from
 * the first row goes back to the field with the keyboard down, so the keyboard never traps a
 * gamepad. With Plumemu's ([keyboardKind]) the D-pad moves on the keys while the tray is up and the
 * hide key, B on an empty field, Back or START put it away ([ownKeyboardOutcome], TypingKeys.kt).
 * Main thread only.
 */
internal interface TypingState {
    val inField: Boolean

    val keyboard: KeyboardAsk

    /** Which keyboard the field types with (Settings › Controls › Keyboard); Plumemu's goes through [fieldKeys]. */
    val keyboardKind: KeyboardChoice

    /** The field's text as typed so far. */
    val fieldText: String

    /** Plumemu's keyboard edits the field through this (Android's text field has its own change callback). */
    fun replaceText(text: String)

    /** The caret and keys of Plumemu's keyboard on this field ([ownKeyboardOutcome]); unused with Android's. */
    val fieldKeys: FieldKeys

    /** The cursor is on the field and the person asked to type there. */
    val keyboardUp: Boolean get() = inField && keyboard.isTyping

    /**
     * The IME's own action key (search, done): what the panel does with it. Returns the action to
     * route like a button press, or null when the state handled it.
     */
    fun onKeyboardAction(): Action?

    /** Where the cursor is: -1 on the field, else the row. A change is a move (for its sound). */
    val cursor: Int

    fun focusField()

    fun onAction(action: Action): TypingOutcome
}

/**
 * Fix name…: the field, pre-filled with the shown name, and the titles of the system's
 * list ranked for it ([TitleFinder], answered off the main thread by `FollowTitleResults`). A
 * pick (A on a row, or a tap) runs [PanelCommand.PickTitle]. The panel opens before the list is
 * built (the first time per system): [finder] is null until [listReady], and the results area says
 * "Reading the names…".
 */
@Stable
internal class FixNameState(
    initialQuery: String,
    finder: TitleFinder?,
    override val keyboardKind: KeyboardChoice = KeyboardChoice.Plumemu,
) : TypingState {
    var finder: TitleFinder? by mutableStateOf(finder)
        private set

    val isLoading: Boolean get() = finder == null

    fun listReady(list: TitleFinder) {
        finder = list
    }

    var query: String by mutableStateOf(initialQuery)
        private set

    var results: List<TitleChoice> by mutableStateOf(emptyList())
        private set

    /** Whether [results] answer [query] (the "no match" line waits for it). */
    var isAnswered: Boolean by mutableStateOf(false)
        private set

    override var inField: Boolean by mutableStateOf(true)
        private set

    override val keyboard = KeyboardAsk()

    override val cursor: Int get() = if (inField) -1 else row

    /** The focused result; meaningful only when not [inField]. */
    var row: Int by mutableIntStateOf(0)
        private set

    fun onQueryChange(text: String) {
        if (text == query) return
        query = text
        isAnswered = false
    }

    override val fieldText: String get() = query

    override fun replaceText(text: String) = onQueryChange(text)

    override val fieldKeys = FieldKeys(initialQuery)

    override fun focusField() {
        inField = true
        keyboard.ask()
        fieldKeys.shiftIfEmpty(query)
    }

    /** The search key: the keyboard goes away and the cursor to the results, when there are any. */
    override fun onKeyboardAction(): Action? {
        toResults()
        return null
    }

    fun showResults(
        forQuery: String,
        hits: List<TitleChoice>,
    ) {
        if (forQuery != query) return
        results = hits
        isAnswered = true
        if (hits.isEmpty()) {
            inField = true
            row = 0
        } else {
            row = row.coerceIn(0, hits.lastIndex)
        }
    }

    /** Whether there is a result to go to with Down, or to pick with A. */
    val canChoose: Boolean get() = results.isNotEmpty()

    override fun onAction(action: Action): TypingOutcome {
        ownKeyboardOutcome(action)?.let { return it }
        when (action) {
            is Action.Move -> return move(action)
            Action.Enter, Action.Play -> {
                val focused = results.getOrNull(row)
                when {
                    // A on the field opens the keyboard; D-pad Down goes to the results.
                    inField -> focusField()
                    focused != null -> return TypingOutcome.Run(PanelCommand.PickTitle(focused))
                }
            }
            Action.Search -> focusField()
            is Action.TapItem -> pickAt(action.column, action.index)?.let { return it }
            is Action.LongPressItem -> pickAt(action.column, action.index)?.let { return it }
            Action.Back, Action.Menu -> return TypingOutcome.NotHandled
            else -> Unit
        }
        return TypingOutcome.Handled
    }

    /** A tap on a result picks it at once, like every panel row. */
    private fun pickAt(
        column: Column,
        index: Int,
    ): TypingOutcome? = results.getOrNull(index)?.takeIf { column == Column.Details }?.let { TypingOutcome.Run(PanelCommand.PickTitle(it)) }

    private fun toResults() {
        if (results.isEmpty()) return
        row = 0
        inField = false
        keyboard.stop()
    }

    private fun move(move: Action.Move): TypingOutcome {
        when (move.direction) {
            Direction.Down ->
                when {
                    inField -> toResults()
                    row < results.lastIndex -> row += 1
                }
            // From the first result a fresh press goes back to the field; a held one stops there.
            Direction.Up ->
                when {
                    inField -> Unit
                    row > 0 -> row -= 1
                    !move.isRepeat -> inField = true
                }
            // Left is the panel's Back, except while typing.
            Direction.Left -> if (!inField) return TypingOutcome.NotHandled
            Direction.Right -> Unit
        }
        return TypingOutcome.Handled
    }
}

/**
 * Use my own name: the field, pre-filled with the shown name, then one Save row
 * (row 0 of the panel column). A, the keyboard's Done or a tap on Save stores the trimmed name;
 * an empty name or only spaces is refused with a plain line ([isRefused]) and the cursor goes
 * back to the field.
 */
@Stable
internal class OwnNameState(
    initialName: String,
    override val keyboardKind: KeyboardChoice = KeyboardChoice.Plumemu,
) : TypingState {
    var text: String by mutableStateOf(initialName)
        private set

    override var inField: Boolean by mutableStateOf(true)
        private set

    override val keyboard = KeyboardAsk()

    override val cursor: Int get() = if (inField) -1 else 0

    var isRefused: Boolean by mutableStateOf(false)
        private set

    fun onTextChange(value: String) {
        text = value
        isRefused = false
    }

    override val fieldText: String get() = text

    override fun replaceText(text: String) = onTextChange(text)

    override val fieldKeys = FieldKeys(initialName)

    override fun focusField() {
        inField = true
        keyboard.ask()
        fieldKeys.shiftIfEmpty(text)
    }

    /** Done: the keyboard goes away, the cursor goes to Save and A (routed) saves. */
    override fun onKeyboardAction(): Action? {
        inField = false
        keyboard.stop()
        return Action.Enter
    }

    /** Save: the command for a name, or the refusal for a blank one. */
    fun submit(): TypingOutcome {
        val name = text.trim()
        if (name.isEmpty()) {
            isRefused = true
            inField = true
            keyboard.stop()
            return TypingOutcome.Handled
        }
        return TypingOutcome.Run(PanelCommand.SaveOwnName(name))
    }

    override fun onAction(action: Action): TypingOutcome {
        ownKeyboardOutcome(action)?.let { return it }
        when (action) {
            is Action.Move ->
                when (action.direction) {
                    Direction.Down -> {
                        inField = false
                        keyboard.stop()
                    }
                    Direction.Up -> if (!action.isRepeat) inField = true
                    Direction.Left -> if (!inField) return TypingOutcome.NotHandled
                    Direction.Right -> Unit
                }
            // A on the field opens the keyboard; A on Save saves.
            Action.Enter, Action.Play -> if (inField) focusField() else return submit()
            Action.Search -> focusField()
            is Action.TapItem -> if (action.column == Column.Details && action.index == SAVE_ROW) return submit()
            Action.Back, Action.Menu -> return TypingOutcome.NotHandled
            else -> Unit
        }
        return TypingOutcome.Handled
    }

    companion object {
        const val SAVE_ROW = 0
    }
}
