package com.plumemu.game

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.scraper.MediaRef
import java.io.File

/**
 * One ScreenScraper game in Fix box art's results: [id] is ScreenScraper's game id
 * (picked with `Scraper.byGameId`), [thumbnail] its box front for a small download. Names are for
 * the screen only, never for a log line.
 */
@Immutable
internal data class BoxArtHit(
    val id: String,
    val name: String?,
    val year: Int?,
    val publisher: String?,
    val systemName: String?,
    val thumbnail: MediaRef?,
)

/** Why ScreenScraper could not answer a search or a pick; each has its own plain line ([boxArtProblemText]). */
@Immutable
internal sealed interface BoxArtProblem {
    /** The account's requests for today are used up, until [untilMillis] (midnight in Paris). */
    data class DailyLimit(val untilMillis: Long) : BoxArtProblem

    /** ScreenScraper closed its API for now, until [untilMillis]. */
    data class ServerClosed(val untilMillis: Long) : BoxArtProblem

    /** ScreenScraper refused the password, or the stored account can no longer be used: sign in again. */
    data object SignInAgain : BoxArtProblem

    data object Offline : BoxArtProblem

    /** No unmetered network: nothing was asked (the person's own action needs Wi-Fi). */
    data object NoWifi : BoxArtProblem

    /** ScreenScraper (or its picture server) answered with error [code]. */
    data class ServerError(val code: Int) : BoxArtProblem

    /** This build cannot ask ScreenScraper (no developer credentials, or ScreenScraper turned it away). */
    data object NotAvailable : BoxArtProblem

    /** The library could not be read (a database error); logged where it happened. */
    data object NotRead : BoxArtProblem
}

/** What one search found. */
@Immutable
internal sealed interface BoxArtSearchOutcome {
    /** ScreenScraper's games in its own order; never empty (no game is [NothingFound]). */
    data class Found(val hits: List<BoxArtHit>) : BoxArtSearchOutcome

    data object NothingFound : BoxArtSearchOutcome

    /** The game is gone from the library, or is being named again (a rescan): nothing was asked. */
    data object GameNotReady : BoxArtSearchOutcome

    data class Failed(val problem: BoxArtProblem) : BoxArtSearchOutcome
}

/** What storing a pick or "No box art" did. */
@Immutable
internal sealed interface BoxArtStoreOutcome {
    data object Stored : BoxArtStoreOutcome

    /** The game is no longer in the library. */
    data object GameGone : BoxArtStoreOutcome

    /** The game is being named again (a rescan): its key may change, so nothing was stored. */
    data object NotNamedYet : BoxArtStoreOutcome

    /** The pictures could not be written in the media folder (no room, no permission). */
    data object StorageFailed : BoxArtStoreOutcome

    /** ScreenScraper no longer knows the picked game's id. */
    data object NotFound : BoxArtStoreOutcome

    /** The picked game has no box front that could be saved: nothing was stored. */
    data object NoBoxFront : BoxArtStoreOutcome

    data class Failed(val problem: BoxArtProblem) : BoxArtStoreOutcome
}

/** Where Fix box art is; each phase has one plain line ([fixBoxArtLine]) except [Results]. */
@Immutable
internal sealed interface FixBoxArtPhase {
    /** Opened, nothing asked yet: the field holds the game's title. */
    data object Idle : FixBoxArtPhase

    data class Searching(val query: String) : FixBoxArtPhase

    /** [FixBoxArtState.hits] holds the games. */
    data object Results : FixBoxArtPhase

    data class NothingFound(val query: String) : FixBoxArtPhase

    data object GameNotReady : FixBoxArtPhase

    data class SearchFailed(val problem: BoxArtProblem) : FixBoxArtPhase

    /** The pick (or No box art) is being fetched and stored. */
    data object Storing : FixBoxArtPhase

    /**
     * The pick's picture save has taken a while: it may be waiting for the background run's
     * current save.
     */
    data object WaitingForPictures : FixBoxArtPhase

    /** Stored: the controller shows the confirmation in the panel's place. */
    data object Stored : FixBoxArtPhase

    /** Nothing was stored for [hit] (null: No box art); the results stay, so another pick can be tried. */
    data class StoreFailed(
        val outcome: BoxArtStoreOutcome,
        val hit: BoxArtHit? = null,
    ) : FixBoxArtPhase
}

/**
 * Fix box art…: the field, pre-filled with the game's shown title, then the rows of
 * the panel column: Search (row [SEARCH_ROW]), ScreenScraper's games (rows 1 to [hits] size), and
 * "No box art" always last ([noBoxArtRow]). The keyboard follows the rules of every text field
 * (docs/conventions/input.md › Text fields): opening the panel never asks for it, A or a tap on the
 * field does, Down leaves the field and puts it away; its search key moves the cursor to Search
 * and searches (as A there). A on a game picks it, a tap on a row does what A does there. While a
 * pick is being stored ([isStoring]) nothing else is started; Back still leaves (and cancels what
 * has not been stored yet). Thumbnails land in [thumbnailOf] as they come. Main thread only; the
 * work that answers it is `FixBoxArtWork`.
 */
@Stable
internal class FixBoxArtState(
    initialQuery: String,
    /** Plumemu's name of the game's system, for "no game called … for <system>". */
    val systemName: String,
    override val keyboardKind: KeyboardChoice = KeyboardChoice.Plumemu,
    /** The clock the daily limit's end is read against; a test swaps it. */
    private val now: () -> Long = System::currentTimeMillis,
) : TypingState {
    var query: String by mutableStateOf(initialQuery)
        private set

    var phase: FixBoxArtPhase by mutableStateOf(FixBoxArtPhase.Idle)
        private set

    /** The last search's games (kept while a pick is stored or after it failed). */
    var hits: List<BoxArtHit> by mutableStateOf(emptyList())
        private set

    private val thumbnails = mutableStateMapOf<String, File>()

    override var inField: Boolean by mutableStateOf(true)
        private set

    override val keyboard = KeyboardAsk()

    /** The focused row; meaningful only when not [inField]. */
    var row: Int by mutableIntStateOf(SEARCH_ROW)
        private set

    override val cursor: Int get() = if (inField) -1 else row

    val noBoxArtRow: Int get() = hits.size + 1

    /** Today's requests are used up: Search cannot work until ScreenScraper resets, so the cursor never rests on it. */
    val isSearchBlocked: Boolean get() = !limitPassed && millisUntilSearchAllowed() != null

    /** Set when the limit's time passed with the panel open ([limitEnded]), so the screen draws Search usable again. */
    private var limitPassed by mutableStateOf(false)

    /** How long the daily limit still lasts, or null when it does not apply (another phase, or its time has passed). */
    fun millisUntilSearchAllowed(): Long? {
        val problem = (phase as? FixBoxArtPhase.SearchFailed)?.problem as? BoxArtProblem.DailyLimit ?: return null
        return (problem.untilMillis - now()).takeIf { it > 0 }
    }

    /** The panel's one delayed effect calls this when [millisUntilSearchAllowed] has run out. */
    fun limitEnded() {
        limitPassed = true
    }

    /** The first row the cursor can rest on: Search, or the row after it while Search is blocked. */
    private val firstRow: Int get() = if (isSearchBlocked) SEARCH_ROW + 1 else SEARCH_ROW

    /**
     * The row a pick or No box art was made on, from when it starts storing until the next search:
     * the panel draws what the store says under it, and it stays there while the cursor moves on.
     */
    var pickedRow: Int? by mutableStateOf(null)
        private set

    /** The game on [row], or null on Search and No box art. */
    fun hitAt(row: Int): BoxArtHit? = hits.getOrNull(row - 1)

    /** The thumbnail file of the game [hitId], once downloaded. */
    fun thumbnailOf(hitId: String): File? = thumbnails[hitId]

    val isStoring: Boolean
        get() = phase == FixBoxArtPhase.Storing || phase == FixBoxArtPhase.WaitingForPictures || phase == FixBoxArtPhase.Stored

    fun onQueryChange(text: String) {
        query = text
    }

    override val fieldText: String get() = query

    override fun replaceText(text: String) = onQueryChange(text)

    override val fieldKeys = FieldKeys(initialQuery)

    override fun focusField() {
        inField = true
        keyboard.ask()
        fieldKeys.shiftIfEmpty(query)
    }

    /** The search key: the keyboard goes away, the cursor goes to Search, and A (routed) searches. */
    override fun onKeyboardAction(): Action? {
        // A search that cannot work is not started. The press is not silent: the keyboard goes away and the cursor moves to the
        // first row that can act, so the daily limit's line shows. Nothing is handed back to run (that would be Search).
        if (isSearchBlocked) {
            inField = false
            keyboard.stop()
            row = firstRow
            return null
        }
        inField = false
        keyboard.stop()
        row = SEARCH_ROW
        return Action.Enter
    }

    override fun onAction(action: Action): TypingOutcome {
        ownKeyboardOutcome(action)?.let { return it }
        when (action) {
            is Action.Move -> return move(action)
            // A on the field opens the keyboard; A on a row does what the row says.
            Action.Enter, Action.Play -> if (inField) focusField() else return choose(row)
            Action.Search -> focusField()
            is Action.TapItem -> if (action.column == Column.Details) return tap(action.index)
            is Action.LongPressItem -> if (action.column == Column.Details) return tap(action.index)
            Action.Back, Action.Menu -> return TypingOutcome.NotHandled
            else -> Unit
        }
        return TypingOutcome.Handled
    }

    private fun tap(index: Int): TypingOutcome {
        if (index !in 0..noBoxArtRow || (index == SEARCH_ROW && isSearchBlocked)) return TypingOutcome.Handled
        inField = false
        keyboard.stop()
        row = index
        return choose(index)
    }

    private fun choose(index: Int): TypingOutcome {
        if (isStoring) return TypingOutcome.Handled
        val command = when {
            index == SEARCH_ROW -> {
                if (isSearchBlocked) return TypingOutcome.Handled
                val text = query.trim()
                if (text.isEmpty()) {
                    // Nothing to search for: back to the field, the keyboard down until A.
                    inField = true
                    return TypingOutcome.Handled
                }
                PanelCommand.SearchBoxArt(text)
            }
            index == noBoxArtRow -> PanelCommand.StoreNoBoxArt
            else -> hitAt(index)?.let { PanelCommand.PickBoxArt(it) } ?: return TypingOutcome.Handled
        }
        return TypingOutcome.Run(command)
    }

    private fun move(move: Action.Move): TypingOutcome {
        when (move.direction) {
            Direction.Down ->
                when {
                    inField -> {
                        inField = false
                        keyboard.stop()
                        row = firstRow
                    }
                    row < noBoxArtRow -> row += 1
                }
            // From Search a fresh press goes back to the field (keyboard down); a held one stops there.
            Direction.Up ->
                when {
                    inField -> Unit
                    row > firstRow -> row -= 1
                    !move.isRepeat -> inField = true
                }
            // Left is the panel's Back, except while typing.
            Direction.Left -> if (!inField) return TypingOutcome.NotHandled
            Direction.Right -> Unit
        }
        return TypingOutcome.Handled
    }

    // What the work reports (FixBoxArtWork, on the main thread).

    /** A search for [forQuery] started: the earlier games and thumbnails go. */
    fun searching(forQuery: String) {
        phase = FixBoxArtPhase.Searching(forQuery)
        limitPassed = false
        hits = emptyList()
        pickedRow = null
        thumbnails.clear()
        // Never left on a row that now means something else (a stray A must not store No box art).
        row = SEARCH_ROW
    }

    /** The answer for [forQuery]; one for an older search (a newer one started since) is dropped. */
    fun showSearch(
        forQuery: String,
        outcome: BoxArtSearchOutcome,
    ) {
        if (phase != FixBoxArtPhase.Searching(forQuery)) return
        val onNoBoxArt = !inField && row == noBoxArtRow
        when (outcome) {
            is BoxArtSearchOutcome.Found -> {
                hits = outcome.hits
                phase = if (outcome.hits.isEmpty()) FixBoxArtPhase.NothingFound(forQuery) else FixBoxArtPhase.Results
            }
            BoxArtSearchOutcome.NothingFound -> phase = FixBoxArtPhase.NothingFound(forQuery)
            BoxArtSearchOutcome.GameNotReady -> phase = FixBoxArtPhase.GameNotReady
            is BoxArtSearchOutcome.Failed -> phase = FixBoxArtPhase.SearchFailed(outcome.problem)
        }
        // "No box art" stays the last row: the cursor on it before the answer stays on it.
        if (onNoBoxArt) row = noBoxArtRow
        if (isSearchBlocked && row == SEARCH_ROW) row = firstRow
    }

    fun thumbnailReady(
        hitId: String,
        file: File,
    ) {
        if (hits.any { it.id == hitId }) thumbnails[hitId] = file
    }

    fun storing() {
        // A new store, perhaps on another row than a failed one.
        pickedRow = null
        markPicked()
        phase = FixBoxArtPhase.Storing
    }

    /** The pick is the row the cursor stands on (a store starts only from a game or No box art). */
    private fun markPicked() {
        if (pickedRow == null && !inField && row >= 1) pickedRow = row
    }

    /** The picture save is still going after a while; shown only while still storing. */
    fun waitingForPictures() {
        if (phase == FixBoxArtPhase.Storing) phase = FixBoxArtPhase.WaitingForPictures
    }

    fun stored(
        outcome: BoxArtStoreOutcome,
        hit: BoxArtHit? = null,
    ) {
        markPicked()
        phase = if (outcome == BoxArtStoreOutcome.Stored) FixBoxArtPhase.Stored else FixBoxArtPhase.StoreFailed(outcome, hit)
    }

    companion object {
        const val SEARCH_ROW = 0
    }
}

/** The plain line under the field for [state]'s phase; null while the games are listed. */
internal fun fixBoxArtLine(state: FixBoxArtState): UiText? =
    when (val phase = state.phase) {
        FixBoxArtPhase.Idle -> UiText.Res(R.string.game_fix_box_art_idle)
        is FixBoxArtPhase.Searching -> UiText.Res(R.string.game_fix_box_art_searching)
        FixBoxArtPhase.Results -> null
        is FixBoxArtPhase.NothingFound -> UiText.Res(R.string.game_fix_box_art_nothing_found, listOf(phase.query, state.systemName))
        FixBoxArtPhase.GameNotReady -> UiText.Res(R.string.game_fix_box_art_game_not_ready)
        is FixBoxArtPhase.SearchFailed -> boxArtProblemText(phase.problem)
        FixBoxArtPhase.Storing -> UiText.Res(R.string.game_fix_box_art_storing)
        FixBoxArtPhase.WaitingForPictures -> UiText.Res(R.string.game_fix_box_art_waiting)
        FixBoxArtPhase.Stored -> UiText.Res(R.string.game_fix_box_art_saved_title)
        is FixBoxArtPhase.StoreFailed -> storeFailedText(phase.outcome, phase.hit)
    }

private fun storeFailedText(
    outcome: BoxArtStoreOutcome,
    hit: BoxArtHit?,
): UiText =
    when (outcome) {
        BoxArtStoreOutcome.NoBoxFront ->
            UiText.Format(R.string.game_fix_box_art_no_box_front, listOf(hit?.let(::hitName) ?: UiText.Res(R.string.game_fix_box_art_untitled)))
        BoxArtStoreOutcome.GameGone -> UiText.Res(R.string.game_fix_box_art_game_gone)
        BoxArtStoreOutcome.NotNamedYet -> UiText.Res(R.string.game_fix_box_art_not_named_yet)
        BoxArtStoreOutcome.StorageFailed -> UiText.Res(R.string.game_fix_box_art_storage_failed)
        BoxArtStoreOutcome.NotFound -> UiText.Res(R.string.game_fix_box_art_not_found)
        is BoxArtStoreOutcome.Failed -> boxArtProblemText(outcome.problem)
        // Never shown as a failure (the confirmation replaces the panel).
        BoxArtStoreOutcome.Stored -> UiText.Res(R.string.game_fix_box_art_saved_title)
    }

internal fun boxArtProblemText(problem: BoxArtProblem): UiText =
    when (problem) {
        is BoxArtProblem.DailyLimit -> UiText.Format(R.string.game_fix_box_art_daily_limit, listOf(UiText.ClockTime(problem.untilMillis)))
        is BoxArtProblem.ServerClosed -> UiText.Format(R.string.game_fix_box_art_server_closed, listOf(UiText.ClockTime(problem.untilMillis)))
        BoxArtProblem.SignInAgain -> UiText.Res(R.string.game_fix_box_art_sign_in_again)
        BoxArtProblem.Offline -> UiText.Res(R.string.game_fix_box_art_offline)
        BoxArtProblem.NoWifi -> UiText.Res(R.string.game_fix_box_art_no_wifi)
        is BoxArtProblem.ServerError -> UiText.Res(R.string.game_fix_box_art_server_error, listOf(problem.code.toString()))
        BoxArtProblem.NotAvailable -> UiText.Res(R.string.game_fix_box_art_not_available)
        BoxArtProblem.NotRead -> UiText.Res(R.string.game_fix_box_art_not_read)
    }

/** A game's name in the results; ScreenScraper gives none for a few. */
internal fun hitName(hit: BoxArtHit): UiText = hit.name?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) } ?: UiText.Res(R.string.game_fix_box_art_untitled)

/** "1994 · Nintendo", or whichever of the two ScreenScraper gives; null when neither. */
internal fun hitNote(hit: BoxArtHit): UiText? {
    val year = hit.year?.toString()
    val publisher = hit.publisher?.takeIf { it.isNotBlank() }
    return when {
        year != null && publisher != null -> UiText.Res(R.string.game_fix_name_release, listOf(year, publisher))
        year != null -> UiText.Plain(year)
        publisher != null -> UiText.Plain(publisher)
        else -> null
    }
}

/**
 * The rows under the field as the panel draws them, in [FixBoxArtState]'s row order (a tap on
 * index i is row i): Search, each game (its name; year · publisher under it; ScreenScraper's
 * system name under the name), then No box art.
 */
internal fun fixBoxArtRows(state: FixBoxArtState): List<PanelRow> =
    buildList {
        add(PanelRow(BOX_ART_SEARCH_KEY, UiText.Res(R.string.game_fix_box_art_search), PanelCommand.SearchBoxArt(state.query.trim()), isEnabled = !state.isSearchBlocked))
        state.hits.forEach { hit ->
            add(
                PanelRow(
                    key = boxArtHitKey(hit),
                    label = hitName(hit),
                    command = PanelCommand.PickBoxArt(hit),
                    value = hit.systemName?.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) },
                    note = hitNote(hit),
                ),
            )
        }
        add(PanelRow(BOX_ART_NONE_KEY, UiText.Res(R.string.game_fix_box_art_no_box_art), PanelCommand.StoreNoBoxArt))
    }

internal fun boxArtHitKey(hit: BoxArtHit) = "box_art_hit_${hit.id}"

internal const val BOX_ART_SEARCH_KEY = "box_art_search"
internal const val BOX_ART_NONE_KEY = "box_art_none"

/** The confirmation shown in the panel's place once stored; OK (and B) return to the game menu. */
internal fun boxArtStoredContent(hit: BoxArtHit?): PanelContent {
    val (title, body) =
        if (hit == null) {
            UiText.Res(R.string.game_fix_box_art_none_title) to UiText.Res(R.string.game_fix_box_art_none_body)
        } else {
            UiText.Res(R.string.game_fix_box_art_saved_title) to UiText.Format(R.string.game_fix_box_art_saved_body, listOf(hitName(hit)))
        }
    return PanelContent(
        title = title,
        body = listOf(body),
        items = listOf(PanelItem.Row(PanelRow(BOX_ART_STORED_OK_KEY, UiText.Res(R.string.game_action_ok), PanelCommand.Back))),
    )
}

internal const val BOX_ART_STORED_OK_KEY = "box_art_ok"
