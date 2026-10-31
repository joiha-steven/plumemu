package com.plumemu.home.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.TypedKey
import com.plumemu.coreui.components.keyboard.KeyboardDirection
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardModel
import com.plumemu.coreui.components.keyboard.KeyboardMove
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.coreui.components.keyboard.KeyboardState
import com.plumemu.game.KeyboardAsk
import com.plumemu.keyboard.CaretText
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome
import com.plumemu.home.HomeContent
import com.plumemu.coreinput.strip.PushRightArm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/** What search asks the main screen to do with a result. */
internal sealed interface SearchEffect {
    data class Play(val hit: SearchHit) : SearchEffect

    data class OpenMenu(val hit: SearchHit) : SearchEffect

    data class ToggleFavorite(val hit: SearchHit) : SearchEffect
}

/**
 * Search (X): the query in the top bar and the results in the
 * games column. The strip's focus is not touched while it is open, so B returns exactly where the
 * person was. Main thread only.
 *
 * With Plumemu's keyboard ([KeyboardChoice.Plumemu]) there are
 * three states: **typing** ([inField] and [isTrayUp]: the keyboard tray shows and the cursor is on
 * its keys, [keys]), **results** (not [inField]: a result focused, the tray down) and the **field**
 * with the tray down ([inField], not [isTrayUp]: no results to go to). The query has a [caret].
 *
 * With Android's ([KeyboardChoice.Android]) the cursor is on the field ([inField], the Android
 * keyboard up) or on a result ([row]); D-pad Down leaves the field for the results and hides the
 * keyboard, Up from the first result goes back, so the keyboard never traps a gamepad.
 */
@Stable
internal class SearchState(
    val keyboardKind: KeyboardChoice = KeyboardChoice.Plumemu,
) {
    /**
     * Settings › Controls › Push right to play, set by the screen: a first fresh Right on a result
     * arms it, a second plays it, as A does. Any other input disarms ([pushRightArm]).
     */
    var pushRightPlays: Boolean = false
    internal var pushRightArm = PushRightArm()

    /** Times the arming out so the hint bar stops saying "Again to play"; set by the screen. */
    var scope: CoroutineScope? = null
    private var armedByThisAction = false

    /** Whether the focused result is armed (the hint bar says "Again to play"). Compose state. */
    val isPushRightArmed: Boolean get() = pushRightArm.armedKey == hitKey(row)

    /** The result at [index] by its game id and system, never by its row (results change while Search is open). */
    private fun hitKey(index: Int): String? = results.getOrNull(index)?.let { "${it.systemId}/${it.row.id}" }

    private fun rightOnResult(
        move: Action.Move,
        focused: SearchHit,
    ): SearchEffect? {
        if (move.isRepeat) return null
        val key = hitKey(row) ?: return null
        if (pushRightArm.isArmedFor(key)) {
            pushRightArm.disarm()
            return SearchEffect.Play(focused)
        }
        pushRightArm.arm(key, scope)
        armedByThisAction = true
        return null
    }

    var isOpen: Boolean by mutableStateOf(false)
        private set

    var query: String by mutableStateOf("")
        private set

    /** Where typing goes in [query]: 0 before the first character, `query.length` after the last. */
    var caret: Int by mutableIntStateOf(0)
        private set

    var results: List<SearchHit> by mutableStateOf(emptyList())
        private set

    /** The focused result; meaningful only when not [inField]. */
    var row: Int by mutableIntStateOf(0)
        private set

    var inField: Boolean by mutableStateOf(true)
        private set

    /** Plumemu's keyboard tray shows (typing); always false with Android's keyboard. */
    var isTrayUp: Boolean by mutableStateOf(false)
        private set

    /** The keys' focus and page; a fresh one, on the letters with q focused, each time Search opens. */
    var keys: KeyboardState by mutableStateOf(newKeys())
        private set

    val usesOwnKeyboard: Boolean get() = keyboardKind == KeyboardChoice.Plumemu

    /** Asks for Android's keyboard: entering Search, A or a tap on the field (docs/conventions/input.md). */
    val keyboard = KeyboardAsk()

    /** The cursor is on the field and the person asked to type there with Android's keyboard (entering Search counts). */
    val keyboardUp: Boolean get() = !usesOwnKeyboard && inField && keyboard.isTyping

    /** What the input router must do for Search now: typed keys on the field, and B erasing while the tray is up. */
    val textEntry: TextEntry
        get() =
            when {
                !isOpen || !usesOwnKeyboard || !inField -> TextEntry.None
                isTrayUp -> TextEntry.Keyboard
                else -> TextEntry.Field
            }

    /** Whether [results] answer the current [query] (the "no match" line waits for it). */
    var isAnswered: Boolean by mutableStateOf(true)
        private set

    /**
     * Told when Search closes after a search (a query typed): its length, the results shown and
     * whether one was played or its menu opened. Never the words (`search_used`). Set by the screen.
     */
    var onSearchUsed: (queryLength: Int, results: Int, openedResult: Boolean) -> Unit = { _, _, _ -> }

    /** A result was played or its menu opened during this search. */
    private var hasOpenedResult = false

    /** The screen played a result or opened its menu ([SearchEffect.Play], [SearchEffect.OpenMenu]). */
    fun markResultOpened() {
        hasOpenedResult = true
    }

    fun open() {
        hasOpenedResult = false
        query = ""
        caret = 0
        results = emptyList()
        row = 0
        inField = true
        if (usesOwnKeyboard) {
            keys = newKeys()
            isTrayUp = true
        } else {
            keyboard.ask()
        }
        isAnswered = true
        isOpen = true
    }

    fun close() {
        if (isOpen && query.isNotBlank()) onSearchUsed(query.trim().length, results.size, hasOpenedResult)
        hasOpenedResult = false
        isOpen = false
        isTrayUp = false
        query = ""
        caret = 0
        results = emptyList()
    }

    /** The whole query at once (Android's field, tests); the caret goes to its end. */
    fun onQueryChange(text: String) {
        caret = text.length
        takeQuery(text)
    }

    /** The field was tapped (or X pressed again): back to typing. */
    fun focusField() {
        inField = true
        if (usesOwnKeyboard) isTrayUp = true else keyboard.ask()
    }

    /** A tap on the query line at character [offset]: typing, with the caret there. */
    fun focusQueryAt(offset: Int) {
        caret = offset.coerceIn(0, query.length)
        focusField()
    }

    internal fun showResults(
        forQuery: String,
        hits: List<SearchHit>,
    ) {
        if (forQuery != query) return
        results = hits
        isAnswered = true
        if (hits.isEmpty()) {
            // Nothing left to focus: back on the field (with Plumemu's keyboard, the tray stays as it was).
            inField = true
            row = 0
        } else {
            row = row.coerceIn(0, hits.lastIndex)
        }
        // The armed game is no longer the one at the focused row: the second Right must not play another.
        if (pushRightArm.armedKey != hitKey(row)) pushRightArm.disarm()
    }

    /** One action while search is open; null when the action needs nothing from the screen. */
    fun onAction(action: Action): SearchEffect? =
        feel.act {
            armedByThisAction = false
            val effect = if (usesOwnKeyboard) handle(action) else handleWithAndroidKeyboard(action)
            // Anything but the arming Right, or a held one, puts the arming away.
            val isHeldRight = action is Action.Move && action.direction == Direction.Right && action.isRepeat
            if (!armedByThisAction && !isHeldRight) pushRightArm.disarm()
            effect
        }

    /**
     * A key of Plumemu's keyboard was pressed (A on the focused key) or tapped. Typing makes no
     * sound; only moves do.
     */
    fun onKeyboardEvent(event: KeyboardEvent) {
        when (event) {
            is KeyboardEvent.Type -> insert(event.char.toString())
            KeyboardEvent.Space -> insert(" ")
            KeyboardEvent.Delete -> deleteBeforeCaret()
            KeyboardEvent.Action -> toResults()
            KeyboardEvent.Hide -> hideTray()
        }
    }

    /** Says what each action did, for the sounds; a [SearchEffect] is reported by who runs it. */
    val feel = Feel()

    /** Plumemu's keyboard: the button map in). */
    private fun handle(action: Action): SearchEffect? {
        val cursor = Triple(row, inField, isTrayUp)
        val effect =
            when {
                !inField -> onResult(action)
                isTrayUp -> onKeys(action)
                else -> onFieldWithTrayDown(action)
            }
        if (isOpen && cursor.first to cursor.second != (row to inField)) feel.report(Outcome.Moved)
        return effect
    }

    private fun onKeys(action: Action): SearchEffect? {
        when (action) {
            is Action.Move -> moveOnKeys(action)
            Action.Enter, Action.Play -> keys.press()?.let(::onKeyboardEvent)
            is Action.Erase ->
                when {
                    query.isNotEmpty() -> deleteBeforeCaret()
                    // A fresh B with nothing to delete closes Search; a held one only stops.
                    !action.isRepeat -> closeGoingBack()
                }
            // The system's Back (or Escape, or the gesture) puts the keyboard away.
            Action.Back -> hideTray()
            Action.Search -> insert(" ")
            Action.ToggleFavorite -> keys.togglePage()
            Action.Menu -> toResults()
            Action.PreviousSystem -> moveCaret(-1)
            Action.NextSystem -> moveCaret(1)
            Action.JumpLetterPrevious -> edit { it.toStart() }
            Action.JumpLetterNext -> edit { it.toEnd() }
            is Action.Typed -> onTyped(action.key)
            is Action.TapItem -> if (action.column == Column.Games) tapResult(action.index)
            is Action.LongPressItem -> return longPress(action)
            // No shift in Search (it ignores case); Settings belongs to the strip.
            Action.ToggleView, Action.OpenSettings -> Unit
        }
        return null
    }

    private fun onFieldWithTrayDown(action: Action): SearchEffect? {
        when (action) {
            Action.Enter, Action.Play, Action.Search -> focusField()
            Action.Back -> closeGoingBack()
            is Action.Erase -> if (!action.isRepeat) closeGoingBack()
            is Action.Move -> if (action.direction == Direction.Down) toResults()
            Action.Menu -> toResults()
            is Action.Typed -> onTyped(action.key)
            is Action.TapItem -> if (action.column == Column.Games) tapResult(action.index)
            is Action.LongPressItem -> return longPress(action)
            Action.NextSystem, Action.PreviousSystem, Action.JumpLetterNext, Action.JumpLetterPrevious,
            Action.ToggleView, Action.OpenSettings, Action.ToggleFavorite,
            -> Unit
        }
        return null
    }

    private fun onResult(action: Action): SearchEffect? {
        val focused = results.getOrNull(row)
        when (action) {
            is Action.Move ->
                when (action.direction) {
                    Direction.Down -> if (row < results.lastIndex) row += 1
                    // From the first result a fresh press goes back to typing; a held one stops there.
                    Direction.Up ->
                        when {
                            row > 0 -> row -= 1
                            !action.isRepeat -> focusField()
                        }
                    Direction.Right -> if (pushRightPlays && focused != null) return rightOnResult(action, focused)
                    Direction.Left -> Unit
                }
            Action.Enter, Action.Play -> if (focused != null) return SearchEffect.Play(focused)
            Action.Back -> closeGoingBack()
            // Only if the router had not yet heard that the tray went down: the person meant B.
            is Action.Erase -> if (!action.isRepeat) closeGoingBack()
            Action.Menu -> if (focused != null) return SearchEffect.OpenMenu(focused)
            Action.ToggleFavorite -> if (focused != null) return SearchEffect.ToggleFavorite(focused)
            Action.Search -> focusField()
            is Action.TapItem -> if (action.column == Column.Games) return tap(action.index)
            is Action.LongPressItem -> return longPress(action)
            is Action.Typed, Action.NextSystem, Action.PreviousSystem, Action.JumpLetterNext, Action.JumpLetterPrevious,
            Action.ToggleView, Action.OpenSettings,
            -> Unit
        }
        return null
    }

    /** Up from the keys' top row goes to the results (when there are some); Down from the bottom row does nothing. */
    private fun moveOnKeys(move: Action.Move) {
        val direction =
            when (move.direction) {
                Direction.Up -> KeyboardDirection.Up
                Direction.Down -> KeyboardDirection.Down
                Direction.Left -> KeyboardDirection.Left
                Direction.Right -> KeyboardDirection.Right
            }
        when (keys.move(direction, move.isRepeat)) {
            KeyboardMove.Moved -> feel.report(Outcome.Moved)
            KeyboardMove.LeftAbove -> toResults()
            KeyboardMove.Stayed, KeyboardMove.LeftBelow -> Unit
        }
    }

    /** A hardware keyboard's key: an edit, or Enter (to the results, as the action key). */
    private fun onTyped(key: TypedKey) {
        CaretText(query, caret).typed(key)?.let(::setQuery) ?: toResults()
    }

    private fun insert(text: String) = edit { it.insert(text) }

    private fun deleteBeforeCaret() = edit { it.deleteBefore() }

    /** One character left or right; the caret stops at the query's ends. */
    private fun moveCaret(step: Int) = edit { it.moveBy(step) }

    private fun edit(change: (CaretText) -> CaretText) = setQuery(change(CaretText(query, caret)))

    private fun setQuery(next: CaretText) {
        caret = next.caret
        takeQuery(next.text)
    }

    /**
     * A new query waits for the index's answer, which comes off the main thread, so a press can come
     * first. A blank query finds nothing (`SearchIndex.find`) and is answered here at once: otherwise
     * START, Up or Back right after erasing to empty went to the old query's results still listed.
     */
    private fun takeQuery(text: String) {
        if (text == query) return
        query = text
        isAnswered = false
        if (text.isBlank()) showResults(text, emptyList())
    }

    /** The tray goes down: to the first result when there are some, else the field stays with the tray down. */
    private fun hideTray() {
        if (results.isNotEmpty()) focusRow(0) else isTrayUp = false
    }

    private fun closeGoingBack() {
        close()
        feel.report(Outcome.WentBack)
    }

    /** A tap on a result while typing: it is focused and the tray goes down (a second tap plays it). */
    private fun tapResult(index: Int) {
        if (results.getOrNull(index) != null) focusRow(index)
    }

    private fun longPress(action: Action.LongPressItem): SearchEffect? {
        if (action.column != Column.Games) return null
        val hit = results.getOrNull(action.index) ?: return null
        focusRow(action.index)
        return SearchEffect.OpenMenu(hit)
    }

    /** Android's keyboard: the field and the results as before Plumemu had its own keyboard. */
    private fun handleWithAndroidKeyboard(action: Action): SearchEffect? {
        val focused = if (inField) null else results.getOrNull(row)
        val cursor = row to inField
        when (action) {
            is Action.Move ->
                if (pushRightPlays && action.direction == Direction.Right && focused != null) return rightOnResult(action, focused) else move(action)
            // A on the field opens the keyboard (D-pad Down goes to the results).
            Action.Enter, Action.Play -> if (focused != null) return SearchEffect.Play(focused) else focusField()
            Action.Back -> closeGoingBack()
            Action.Menu -> if (focused != null) return SearchEffect.OpenMenu(focused)
            Action.ToggleFavorite -> if (focused != null) return SearchEffect.ToggleFavorite(focused)
            Action.Search -> focusField()
            is Action.TapItem -> if (action.column == Column.Games) return tap(action.index)
            is Action.LongPressItem -> longPress(action)?.let { return it }
            // Systems, letters, view and Settings belong to the strip, which waits underneath; the
            // router sends no erase or typed key with Android's keyboard (textEntry stays None).
            Action.NextSystem, Action.PreviousSystem, Action.JumpLetterNext, Action.JumpLetterPrevious,
            Action.ToggleView, Action.OpenSettings, is Action.Erase, is Action.Typed,
            -> Unit
        }
        if (isOpen && cursor != (row to inField)) feel.report(Outcome.Moved)
        return null
    }

    /** From the field to the first result (A, Enter, the keyboard's search key, D-pad Down, START). */
    fun toResults() {
        if (results.isNotEmpty()) focusRow(0)
    }

    private fun move(move: Action.Move) {
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
            Direction.Left, Direction.Right -> Unit
        }
    }

    private fun tap(index: Int): SearchEffect? {
        val hit = results.getOrNull(index) ?: return null
        if (!inField && row == index) return SearchEffect.Play(hit)
        focusRow(index)
        return null
    }

    private fun focusRow(index: Int) {
        row = index
        inField = false
        isTrayUp = false
        keyboard.stop()
    }

    private companion object {
        /** Search ignores case: no shift (its place stays empty), the letters first. */
        fun newKeys(): KeyboardState = KeyboardState(KeyboardModel(hasShift = false, page = KeyboardPage.Letters))
    }
}

/**
 * Keeps [search]'s results answering its query over [content], off the main thread. Nothing runs
 * while search is closed; the index is built once per content while it is open.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Composable
internal fun FollowSearchResults(
    search: SearchState,
    content: HomeContent,
) {
    if (!search.isOpen) return
    val current by rememberUpdatedState(content)
    LaunchedEffect(search) {
        val indexes = snapshotFlow { current }.mapLatest { withContext(Dispatchers.Default) { SearchIndex.of(it) } }
        combine(indexes, snapshotFlow { search.query }) { index, query -> index to query }
            .mapLatest { (index, query) -> query to withContext(Dispatchers.Default) { index.find(query) } }
            .collect { (query, hits) -> search.showResults(query, hits) }
    }
}

@Composable
internal fun rememberSearchState(keyboard: KeyboardChoice = KeyboardChoice.Plumemu): SearchState = remember(keyboard) { SearchState(keyboard) }
