package com.plumemu.home

import com.plumemu.keyboard.PasteBoard
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.staticCompositionLocalOf
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.hint
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.FittingHintBar
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.TitledHintBar
import com.plumemu.coreui.components.TopBar
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.components.keyboard.KeyboardPage
import com.plumemu.game.FixBoxArtState
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.TypingScreen
import com.plumemu.game.isTrayUp
import com.plumemu.game.typingActionWord
import com.plumemu.home.search.SearchState
import com.plumemu.settings.ButtonLetters
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.settings.LocalShoulderKeys
import com.plumemu.settings.ShoulderKeys

/**
 * Top bar: where you are on the left ("18 systems · 1,284 games" with systems focused,
 * "Super Nintendo · 248 games" with games focused), clock and battery on the right.
 */
@Composable
internal fun HomeTopBar(
    content: HomeContent,
    state: StripState,
    clock: () -> String?,
    battery: () -> String?,
    wifi: () -> Boolean = { false },
    charging: () -> Boolean = { false },
    /** A tap on the update pill: Settings, on the update. */
    onSettings: () -> Unit = {},
    /** True while a game panel's keyboard tray is up (the bar keeps its base height). */
    typing: () -> Boolean = { false },
) {
    // The pill is read here, not by the screen: an update found recomposes this bar only.
    val updateName by LocalUpdateFound.current.collectAsState()
    // Only what the bar shows: a row move changes the focus but neither of these.
    val column by remember(state) { derivedStateOf { state.focus.column } }
    val shownEntry by remember(state, content) { derivedStateOf { content.shownEntry(state.focus) } }
    val entry = shownEntry
    val numbers = rememberCountFormat()
    val isAsking = content.systemCount == 0 && (column == Column.Systems || entry == null)
    val (lead, count) =
        if (isAsking) {
            // Nothing to count yet: the empty state below is the invitation.
            stringResource(R.string.app_name) to null
        } else if (column == Column.Systems || entry == null) {
            pluralStringResource(R.plurals.home_systems_count, content.systemCount, numbers.format(content.systemCount)) to
                pluralStringResource(R.plurals.home_games_count, content.gameCount, numbers.format(content.gameCount))
        } else {
            // The folders page counts collections (New collection… is not one); a folder's games are "n games".
            val countRes =
                when {
                    entry.isFolderList -> R.plurals.collections_count
                    entry.kind == EntryKind.Apps -> R.plurals.home_apps_count
                    else -> R.plurals.home_games_count
                }
            entryName(entry) to pluralStringResource(countRes, entry.shownCount, numbers.format(entry.shownCount))
        }
    val shown = rememberShownNotice()
    val notice = shown.notice
    // After a game: "+3 achievements · Super Metroid", a few seconds, in the notice's place (it wins while it shows).
    val afterGame = rememberShownAchievements()
    val unlocked = afterGame.line
    val unlockedLead = unlocked?.let { pluralStringResource(R.plurals.home_notice_achievements, it.count, numbers.format(it.count)) }
    val shownUndo = rememberShownUndo()
    val undo = shownUndo.line
    val noticeLead = undo?.first ?: unlockedLead ?: notice?.let { noticeLead(it, numbers) }
    val noticeTail = if (undo != null) undo.second else if (unlocked != null) unlocked.gameTitle else notice?.let { noticeTail(it, content, numbers) }
    TopBar(
        place =
            if (noticeLead != null) {
                if (noticeTail != null) stringResource(R.string.home_place, noticeLead, noticeTail) else noticeLead
            } else if (count != null) {
                stringResource(R.string.home_place, lead, count)
            } else {
                lead
            },
        emphasis = noticeLead ?: lead,
        clock = clock,
        battery = battery,
        wifi = wifi,
        wifiDescription = stringResource(R.string.wifi_connected),
        charging = charging,
        chargingDescription = stringResource(R.string.battery_charging),
        modifier = Modifier.testTag(HomeTags.TOP_BAR),
        placeAlpha = if (undo != null) shownUndo.alpha else if (unlocked != null) afterGame.alpha else shown.alpha,
        pill = updateName?.let { stringResource(R.string.home_update_pill, it) },
        onPill = onSettings,
        typing = typing,
    )
}

/** The name shown for an entry: the system's own, or Recent / Favorites / Apps from strings.xml. */
@Composable
internal fun entryName(entry: HomeEntry): String =
    when (entry.kind) {
        EntryKind.Recent -> stringResource(R.string.home_entry_recent)
        EntryKind.Favorites -> stringResource(R.string.home_entry_favorites)
        EntryKind.Apps -> stringResource(R.string.home_entry_apps)
        EntryKind.Collections -> if (entry.isFolder) folderTitle(entry.id, entry.name) else stringResource(R.string.collections_entry)
        EntryKind.System -> entry.name.orEmpty()
    }

/**
 * The three-character code shown in the peek for System codes: the system's own, or REC / FAV / APP from strings.xml.
 * A system the platform data does not know gets the first three letters of its name.
 */
@Composable
internal fun entryCode(entry: HomeEntry): String =
    when (entry.kind) {
        EntryKind.Recent -> stringResource(R.string.home_code_recent)
        EntryKind.Favorites -> stringResource(R.string.home_code_favorites)
        EntryKind.Apps -> stringResource(R.string.home_code_apps)
        EntryKind.Collections -> stringResource(R.string.home_code_collections)
        EntryKind.System -> entry.code.ifEmpty { entry.name.orEmpty().filter { it.isLetterOrDigit() }.take(CODE_LENGTH).uppercase() }
    }

private const val CODE_LENGTH = 3

/**
 * The version name of the update found, null while none waits (`HomeCallbacks.updateFound`, provided by `MainScreen`): the
 * top bar shows the "Update 0.1.1" pill and START's hint reads "Update", both opening Settings on the update.
 */
internal val LocalUpdateFound = staticCompositionLocalOf<StateFlow<String?>> { MutableStateFlow(null) }

/** One hint: its key cap, its label and the action a tap sends (the same as the button). */
@Immutable
internal data class HintSpec(
    @StringRes val key: Int,
    @StringRes val label: Int,
    val action: Action,
    /** A tap cycles the grid size (Small, Medium, Large, Small) instead of sending [action]; the keys stop at the ends. */
    val cyclesGridSize: Boolean = false,
    /** What a tap sends. It differs from [action] only for "Switch list" under Switch list with L1 / R1, where [action] stays L2 / R2's name so the hints keep their order and test tags. */
    val tapAction: Action = action,
)

/** "Y Favorite", or "Y Unfavorite" on a game that is one. */
internal fun favoriteHint(
    letters: ButtonLetters,
    isFavorite: Boolean,
): HintSpec = HintSpec(letters.favorite, if (isFavorite) R.string.home_hint_unfavorite else R.string.home_hint_favorite, Action.ToggleFavorite)

/**
 * Search: on the field "A Type" (opens the keyboard; D-pad Down goes to the results) and "B Back"; on a result
 * "A Play", "B Back", "Y Favorite" or "Y Unfavorite", "START Menu", and with Plumemu's keyboard ([xTypes])
 * "X Type" after B (X brings the keyboard back).
 */
internal fun searchHints(
    inField: Boolean,
    focusedIsFavorite: Boolean,
    letters: ButtonLetters = ButtonLetters.Default,
    xTypes: Boolean = false,
    pushRightPlays: Boolean = false,
    pushRightArmed: Boolean = false,
): List<HintSpec> {
    val back = HintSpec(letters.back, R.string.home_hint_back, Action.Back)
    return if (inField) {
        listOf(HintSpec(letters.confirm, R.string.account_hint_type, Action.Enter), back)
    } else {
        listOfNotNull(
            HintSpec(letters.confirm, R.string.home_hint_play, Action.Play),
            if (pushRightPlays) HintSpec(R.string.home_key_right, pushRightLabel(false, pushRightArmed), Action.Play) else null,
            back,
            if (xTypes) HintSpec(letters.search, R.string.account_hint_type, Action.Search) else null,
            favoriteHint(letters, focusedIsFavorite),
            HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu),
        )
    }
}

/**
 * Search while Plumemu's keyboard is up: "A Type" (the focused key), "B Delete",
 * "X Space", "START Search" (to the results), "Y 123" ("Y ABC" off the letters, [onLetters]) and
 * "L1 R1 Cursor"; a tap on the last moves the caret one character right, as R1 does.
 */
internal fun searchTypingHints(
    onLetters: Boolean,
    letters: ButtonLetters = ButtonLetters.Default,
    canPaste: Boolean = false,
): List<HintSpec> = typingHints(onLetters, R.string.home_hint_search, hasShift = false, letters, canPaste)

/**
 * Any field while Plumemu's keyboard is up: "A Type", "B Delete", "X Space", "START <[actionWord]>",
 * "R3 Paste" while the clipboard holds text ([canPaste]; every other button is taken while typing),
 * "Y 123" / "Y ABC", "SELECT Shift" when the keys have shift ([hasShift]: not Search) and "L1 R1 Cursor".
 * Short of room they give way in [TYPING_HINT_DROP_ORDER]: L1 R1, SELECT, Y.
 */
internal fun typingHints(
    onLetters: Boolean,
    @StringRes actionWord: Int,
    hasShift: Boolean,
    letters: ButtonLetters = ButtonLetters.Default,
    canPaste: Boolean = false,
): List<HintSpec> =
    listOfNotNull(
        HintSpec(letters.confirm, R.string.account_hint_type, Action.Enter),
        HintSpec(letters.back, R.string.home_hint_delete, Action.Erase()),
        HintSpec(letters.search, R.string.home_hint_space, Action.Search),
        HintSpec(R.string.home_key_start, actionWord, Action.Menu),
        if (canPaste) HintSpec(R.string.home_key_r3, R.string.keyboard_paste, Action.Paste) else null,
        HintSpec(letters.favorite, if (onLetters) R.string.home_hint_symbols else R.string.home_hint_letters, Action.ToggleFavorite),
        if (hasShift) HintSpec(R.string.home_key_select, R.string.home_hint_shift, Action.ToggleView) else null,
        HintSpec(R.string.home_key_l1_r1, R.string.home_hint_cursor, Action.NextSystem),
    )

/**
 * Inside the game panel: "A Type" while the cursor is on a text field (A opens its keyboard), else
 * "A Select" where there is something to choose; always "B Back".
 */
internal fun panelHints(
    panel: OpenPanel,
    letters: ButtonLetters = ButtonLetters.Default,
): List<HintSpec> {
    val back = HintSpec(letters.back, R.string.home_hint_back, Action.Back)
    val onField = (panel.screen as? TypingScreen)?.typing?.inField == true
    return when {
        onField -> listOf(HintSpec(letters.confirm, R.string.account_hint_type, Action.Enter), back)
        panelCanChoose(panel) -> listOf(HintSpec(letters.confirm, panelChooseLabel(panel), Action.Enter), back)
        else -> listOf(back)
    }
}

/** What A says in [panel] off a text field: "Search" on Fix box art's Search row, else "Select". */
internal fun panelChooseLabel(panel: OpenPanel): Int =
    when (val screen = panel.screen) {
        is PanelScreen.FixBoxArt -> if (screen.typing.row == FixBoxArtState.SEARCH_ROW) R.string.home_hint_search else R.string.home_hint_select
        else -> R.string.home_hint_select
    }

/**
 * Whether A picks something in [panel] off a text field: a row to choose, Fix name's results, Use
 * my own name's Save, Fix box art's rows (none while a pick is being stored).
 */
internal fun panelCanChoose(panel: OpenPanel): Boolean =
    when (val screen = panel.screen) {
        is PanelScreen.FixName -> screen.typing.canChoose
        is PanelScreen.OwnName -> true
        is PanelScreen.FixBoxArt -> !screen.typing.isStoring
        else -> panel.content.focusableRows.isNotEmpty()
    }

/**
 * The hint bar; each hint is tappable and sends its action through core-input. On a
 * grid with its games focused the bar also shows the focused game's title (RP5-3), read in its
 * own scope so a move recomposes only that bar.
 */
@Composable
internal fun HomeHints(
    content: HomeContent,
    state: StripState,
    gridIds: Set<String>,
    onAction: (Action) -> Unit,
    panel: OpenPanel? = null,
    search: SearchState? = null,
    onCycleGridSize: () -> Unit = {},
    pushRightToPlay: Boolean = false,
) {
    // Read here only: an update found recomposes the bar, not the screen.
    val updateWaiting = LocalUpdateFound.current.collectAsState().value != null
    // Only what the hints depend on, so a row move does not recompose the bar.
    val column by remember(state) { derivedStateOf { state.focus.column } }
    val shownEntry by remember(state, content) { derivedStateOf { content.shownEntry(state.focus) } }
    val entry = shownEntry
    // Changes only when the cursor moves between a favorite and another game (Y Favorite / Unfavorite).
    val focusedIsFavorite by remember(state, entry) { derivedStateOf { entry?.rows?.getOrNull(state.focus.rowOf(entry.id))?.isFavorite == true } }
    val focusedFolderIsNew by remember(state, entry) { derivedStateOf { entry?.isFolderList == true && entry.rows.getOrNull(state.focus.rowOf(entry.id))?.id == HomeContent.NEW_COLLECTION_ID } }
    val showsGrid = content.showsGrid(entry, gridIds) && panel == null
    // Push right to play: after the first Right the hint says "Again to play" (only this reads the arming).
    val stripArmed by remember(state) { derivedStateOf { state.isPushRightArmed } }
    val hasDetailsStop by remember(state) { derivedStateOf { state.hasDetailsStop } }
    val letters = LocalButtonLetters.current
    val shoulders = LocalShoulderKeys.current
    val openSearch = search?.takeIf { it.isOpen && panel == null }
    val isTyping = openSearch?.let { it.inField && it.isTrayUp } == true
    val canPaste = PasteBoard.hasText
    val searchHintsShown = openSearch?.let { open ->
        val inField = open.inField
        val onFavorite = !inField && open.results.getOrNull(open.row)?.row?.isFavorite == true
        val onLetters = isTyping && open.keys.page == KeyboardPage.Letters
        val xTypes = open.usesOwnKeyboard
        val searchArmed = open.isPushRightArmed
        remember(inField, isTyping, onLetters, onFavorite, letters, xTypes, pushRightToPlay, searchArmed, canPaste) {
            if (isTyping) searchTypingHints(onLetters, letters, canPaste) else searchHints(inField, onFavorite, letters, xTypes, pushRightToPlay, searchArmed)
        }
    }
    // A panel with a text field changes what A does without a new panel: read its state here.
    val typingScreen = panel?.screen as? TypingScreen
    val isPanelTrayUp = typingScreen?.typing?.isTrayUp == true
    val typingHints = panel?.takeIf { typingScreen != null }?.let { open ->
        val canChoose = panelCanChoose(open)
        val onField = typingScreen?.typing?.inField == true
        val chooseLabel = panelChooseLabel(open)
        val onLetters = isPanelTrayUp && typingScreen?.typing?.fieldKeys?.keys?.page == KeyboardPage.Letters
        val actionWord = typingScreen?.let(::typingActionWord) ?: R.string.home_hint_search
        remember(open, canChoose, onField, chooseLabel, letters, isPanelTrayUp, onLetters, actionWord, canPaste) {
            if (isPanelTrayUp) typingHints(onLetters, actionWord, hasShift = true, letters, canPaste) else panelHints(open, letters)
        }
    }
    // A on the empty state does what its focused button says; the reason is collected only while that message is up.
    val emptyButton = rememberEmptyButton(column == Column.Systems && content.asksForFolderAt(entry))
    val undoOffered = LocalRecentUndoOffered.current()
    val hints =
        searchHintsShown ?: typingHints ?: remember(content, column, entry, showsGrid, letters, shoulders, panel, focusedIsFavorite, pushRightToPlay, stripArmed, updateWaiting, hasDetailsStop, emptyButton, undoOffered, focusedFolderIsNew) {
            panel?.let { panelHints(it, letters) } ?: hintsFor(content, column, entry, showsGrid, letters, focusedIsFavorite, pushRightToPlay, stripArmed, updateWaiting, shoulders, hasDetailsStop, emptyButton, focusedFolderIsNew).withUndo(undoOffered, letters)
        }
    // While Undo is offered X stays on every bar that has it (Search is dropped last), or its key would work with no hint drawn.
    val drops = (if (isTyping || isPanelTrayUp) TYPING_HINT_DROP_ORDER else if (showsGrid && entry?.isIconGrid == true) APPS_GRID_HINT_DROP_ORDER else if (showsGrid) GRID_HINT_DROP_ORDER else HINT_DROP_ORDER).let { if (undoOffered && panel == null && searchHintsShown == null) it - Action.Search else it }
    val dropOrder = remember(hints, drops) { drops.flatMap { action -> hints.indices.filter { hints[it].action == action } } }
    val views: @Composable () -> Unit = remember(hints, onAction, onCycleGridSize) { { hints.forEach { HintView(it, onAction, onCycleGridSize) } } }
    if (searchHintsShown == null && showsGrid && column == Column.Games && entry != null) {
        // "L2 R2 Switch list" gives way before the game's year and publisher; the keys still work.
        val beforeMeta = hints.indices.filter { hints[it].action == Action.JumpLetterNext }
        GridHintBar(entry, state, dropOrder, beforeMeta, views)
    } else {
        // On a narrow screen in a wide font the games hints do not all fit: L2 R2 Switch list goes first (the
        // system column's edge is a tap away), then L1 R1 Size, then B Back, SELECT last (HINT_DROP_ORDER); never cut or overlapped.
        FittingHintBar(modifier = Modifier.testTag(HomeTags.HINT_BAR), dropOrder = dropOrder, content = views)
    }
}

/**
 * The hint bar of a field typed with Plumemu's keyboard on a screen other than the main one
 * (Settings' fields): [hints] from [typingHints], each tappable, giving way
 * in the same order as the main screen's while typing.
 */
@Composable
internal fun TypingHintBar(
    hints: List<HintSpec>,
    onAction: (Action) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dropOrder = remember(hints) { TYPING_HINT_DROP_ORDER.flatMap { action -> hints.indices.filter { hints[it].action == action } } }
    FittingHintBar(modifier = modifier, dropOrder = dropOrder) { hints.forEach { HintView(it, onAction, onCycleGridSize = {}) } }
}

@Composable
private fun HintView(
    spec: HintSpec,
    onAction: (Action) -> Unit,
    onCycleGridSize: () -> Unit,
) {
    val tap = remember(spec, onAction, onCycleGridSize) {
        if (spec.cyclesGridSize) Modifier.hint(spec.action) { onCycleGridSize() } else Modifier.hint(spec.action) { onAction(spec.tapAction) }
    }
    Hint(
        keyCap = stringResource(spec.key),
        label = stringResource(spec.label),
        modifier = tap.testTag(HomeTags.hint(spec.action)),
        // The confirm key (A) wears the accent, as the board draws it.
        accent = spec.action == Action.Play || spec.action == Action.Enter,
    )
}

/**
 * Which hints give way first when the bar is too narrow for all of them (see [HomeHints]): L2 R2 Switch list, then
 * L1 R1 Size, R3 Sort, B Back, and SELECT last; START Menu and Y Favorite are not in the order (only a bar still too
 * narrow after those would drop hints from its end). By touch SELECT's hint is the only way
 * between a list and its grid, while B has the system's back gesture;
 * `HomeViewToggleHintTest` checks SELECT stays at 576 x 432 at Largest.
 */
private val HINT_DROP_ORDER = listOf(Action.JumpLetterNext, Action.NextSystem, Action.Sort, Action.Back, Action.ToggleView)

/** A grid's hints give way as a list's do, with Y Favorite going right after Switch list (L1 R1 Size stays at 576). */
private val GRID_HINT_DROP_ORDER = listOf(Action.JumpLetterNext, Action.ToggleFavorite, Action.NextSystem, Action.Back, Action.ToggleView)

/** The typing hints (Search's and a panel field's) give way in this order: L1 R1 Cursor, then SELECT Shift, then Y 123; A, B, X and START always stay. */
private val TYPING_HINT_DROP_ORDER = listOf(Action.NextSystem, Action.ToggleView, Action.ToggleFavorite)

/** Test tags of the main screen's parts, for bounds and touch tests. */
internal object HomeTags {
    const val TOP_BAR = "home_top_bar"
    const val HINT_BAR = "home_hint_bar"
    const val GRID_FOCUS_TITLE = "home_grid_focus_title"
    const val STRIP = "home_strip"
    const val SYSTEMS = "home_systems"
    const val GAMES = "home_games"

    /** The band over a system's games list, the list's first item (badge, facts line, hairline). */
    const val SYSTEM_BAND = "home_system_band"
    const val SYSTEM_BAND_FACTS = "home_system_band_facts"
    const val SYSTEM_BAND_ABOUT = "home_system_band_about"
    const val SYSTEM_BAND_RULE = "home_system_band_rule"

    /** A page of the games list (one per entry built); only the shown one has its rows in the tree. */
    const val GAMES_PAGE = "home_games_page"
    const val GRID_PAGE = "home_grid_page"
    const val EMPTY_MESSAGE = "home_empty_message"

    fun emptyButton(button: EmptyButton) = "home_empty_button_${button.name.lowercase()}"

    const val EMPTY_FAVORITES = "home_empty_favorites"
    const val EMPTY_RECENT = "home_empty_recent"
    const val EMPTY_FOLDER = "home_empty_folder"
    const val GRID = "home_grid"
    const val EDGE_FADE = "home_edge_fade"
    const val DETAILS = "home_details"
    const val DETAILS_COVER = "home_details_cover"

    /** The wide details cover's box, inside its collar: the picture's or the tile's bounds. */
    const val DETAILS_COVER_BOX = "home_details_cover_box"

    /** The narrow stop's cover slot: its width is fixed from the first frame; the picture only fits inside it. */
    const val DETAILS_COVER_SLOT = "home_details_cover_slot"
    const val DETAILS_TITLE = "home_details_title"
    const val DETAILS_FACTS = "home_details_facts"
    const val DETAILS_BATTERY = "home_details_battery"

    /** The wide details' line under the title (year and publisher). */
    const val DETAILS_SUBTITLE = "home_details_subtitle"
    const val DETAILS_FACT_LINE = "home_details_fact_line"
    const val DETAILS_EMULATOR = "home_details_emulator"
    const val DETAILS_FACT_VALUE = "home_details_fact_value"
    const val DETAILS_APP = "home_details_app"
    const val DETAILS_APP_PACKAGE = "home_details_app_package"

    /** The five rating dots in the details block. */
    const val DETAILS_RATING_DOTS = "home_details_rating_dots"

    /** The facts table's room kept empty while the play facts are read. */
    const val DETAILS_FACTS_PENDING = "home_details_facts_pending"

    /** The text-only column's "Super Nintendo · USA" line, and its empty place while the system is found. */
    const val DETAILS_SYSTEM = "home_details_system"
    const val DETAILS_SYSTEM_PENDING = "home_details_system_pending"
    const val DETAILS_SYNOPSIS = "home_details_synopsis"

    /** A placeholder's title in a portrait cell (it may ellipsize at the largest text size). */
    const val PORTRAIT_TILE_TITLE = "home_portrait_tile_title"

    /** A placeholder's title in a landscape or square cell (the other half of [PORTRAIT_TILE_TITLE]). */
    const val WIDE_TILE_TITLE = "home_wide_tile_title"

    /** A decoded box-art picture (grid or details), drawn over its placeholder. */
    const val COVER_PICTURE = "home_cover_picture"

    /** A game's poster cover (no picture to show: signed out, ScreenScraper has none, or its file cannot be read): grid cell, details column and second screen alike. */
    const val POSTER_COVER = "home_poster_cover"

    /** In a Recent or Favorites grid, a game's placeholder in its own system's shape inside the cell (no box art). */
    const val COVER_OWN_SHAPE = "home_cover_own_shape"

    /** In the Apps grid: an app's icon, the stand-in square of an app without one, and the name under either. */
    const val APP_ICON = "home_app_icon"

    /** An app game's icon in the details column or on the second screen's card (not the grid tile's, [APP_ICON]). */
    const val APP_GAME_ICON = "home_app_game_icon"
    /** An app game's cover card in a mixed grid while its icon is not there yet (with the icon it carries [APP_ICON]). */
    const val APP_GAME_CARD = "home_app_game_card"
    const val APP_ICON_STAND_IN = "home_app_icon_stand_in"
    const val APP_NAME = "home_app_name"

    fun cover(index: Int) = "home_cover_$index"

    fun system(index: Int) = "home_system_$index"

    fun game(index: Int) = "home_game_$index"

    fun hint(action: Action) = "home_hint_${action::class.simpleName}"
}
