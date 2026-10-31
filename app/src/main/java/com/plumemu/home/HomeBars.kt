package com.plumemu.home

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
) {
    // Only what the bar shows: a row move changes the focus but neither of these.
    val column by remember(state) { derivedStateOf { state.focus.column } }
    val systemIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    val entry = content.entries.getOrNull(systemIndex)
    val numbers = rememberCountFormat()
    val (lead, count) =
        if (column == Column.Systems || entry == null) {
            pluralStringResource(R.plurals.home_systems_count, content.systemCount, numbers.format(content.systemCount)) to
                pluralStringResource(R.plurals.home_games_count, content.gameCount, numbers.format(content.gameCount))
        } else {
            val countRes = if (entry.kind == EntryKind.Apps) R.plurals.home_apps_count else R.plurals.home_games_count
            entryName(entry) to pluralStringResource(countRes, entry.shownCount, numbers.format(entry.shownCount))
        }
    val shown = rememberShownNotice()
    val notice = shown.notice
    val noticeLead = notice?.let { noticeLead(it, numbers) }
    val noticeTail = notice?.let { noticeTail(it, content, numbers) }
    TopBar(
        place =
            if (noticeLead != null) {
                if (noticeTail != null) stringResource(R.string.home_place, noticeLead, noticeTail) else noticeLead
            } else {
                stringResource(R.string.home_place, lead, count)
            },
        emphasis = noticeLead ?: lead,
        clock = clock,
        battery = battery,
        wifi = wifi,
        wifiDescription = stringResource(R.string.wifi_connected),
        charging = charging,
        chargingDescription = stringResource(R.string.battery_charging),
        modifier = Modifier.testTag(HomeTags.TOP_BAR),
        placeAlpha = shown.alpha,
    )
}

/** The name shown for an entry: the system's own, or Recent / Favorites / Apps from strings.xml. */
@Composable
internal fun entryName(entry: HomeEntry): String =
    when (entry.kind) {
        EntryKind.Recent -> stringResource(R.string.home_entry_recent)
        EntryKind.Favorites -> stringResource(R.string.home_entry_favorites)
        EntryKind.Apps -> stringResource(R.string.home_entry_apps)
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
        EntryKind.System -> entry.code.ifEmpty { entry.name.orEmpty().filter { it.isLetterOrDigit() }.take(CODE_LENGTH).uppercase() }
    }

private const val CODE_LENGTH = 3

/** True while an update was found (`HomeCallbacks.updateFound`, provided by `MainScreen`): START Settings gets a dot. */
internal val LocalUpdateFound = staticCompositionLocalOf<StateFlow<Boolean>> { MutableStateFlow(false) }

/** One hint: its key cap, its label and the action a tap sends (the same as the button). */
@Immutable
internal data class HintSpec(
    @StringRes val key: Int,
    @StringRes val label: Int,
    val action: Action,
    /** A tap cycles the grid size (Small, Medium, Large, Small) instead of sending [action]; the keys stop at the ends. */
    val cyclesGridSize: Boolean = false,
    /** The accent dot after the label: an update was found (START Settings only). */
    val hasDot: Boolean = false,
    /** What a tap sends. It differs from [action] only for "Switch system" under Switch system with L1 / R1, where [action] stays L2 / R2's name so the hints keep their order and test tags. */
    val tapAction: Action = action,
)

/**
 * The hints for where the focus is (the board's RP5-1, RP5-2, RP5-3 and Nova-3). SELECT shows for an
 * entry that can be a grid ([HomeContent.hasGridFor]): a system, Recent and Favorites with box art, Apps always. "A Select" only where there is something to open
 * (an empty Recent has nothing), "L1 R1" only when there is another list to switch to (with no
 * games only Apps), "X Search" only when there are games to find, "Y Favorite" (or "Y Unfavorite"
 * for a favorite, [focusedIsFavorite]) only on a game. A grid shows A, B and "SELECT List view"
 * (RP5-3) and "L1 R1 Size" (they change the grid size); Apps' grid has none (its
 * size is fixed and L1 / R1 do nothing there); every list and grid with another
 * entry to go to also shows "L2 R2 Switch system" (L2 / R2 switch the entry everywhere, L1 / R1 jump a letter in a list); the narrow details stop A, B, Y and "START More" (Nova-3).
 * The pair names here are the default's: with Switch system with = L1 / R1 ([shoulders]) the two pairs trade places in these hints.
 */
internal fun hintsFor(
    content: HomeContent,
    column: Column,
    entry: HomeEntry?,
    showsGrid: Boolean = false,
    letters: ButtonLetters = ButtonLetters.Default,
    focusedIsFavorite: Boolean = false,
    pushRightPlays: Boolean = false,
    pushRightArmed: Boolean = false,
    settingsDot: Boolean = false,
    shoulders: ShoulderKeys = ShoulderKeys.Default,
    hasDetailsStop: Boolean = false,
): List<HintSpec> {
    val settings = HintSpec(R.string.home_key_start, R.string.home_hint_settings, Action.OpenSettings, hasDot = settingsDot)
    val hasItems = (entry?.count ?: 0) > 0
    val isApps = entry?.kind == EntryKind.Apps
    val play = HintSpec(letters.confirm, if (isApps) R.string.home_hint_open else R.string.home_hint_play, Action.Play)
    // Push right to play: "→ Twice to play" beside "A Play" (a list; not a grid); after the first Right "→ Again to play".
    val pushRight = if (pushRightPlays && hasItems) HintSpec(R.string.home_key_right, pushRightLabel(isApps, pushRightArmed), Action.Play) else null
    val back = HintSpec(letters.back, R.string.home_hint_back, Action.Back)
    val search = if (content.gameCount > 0) HintSpec(letters.search, R.string.home_hint_search, Action.Search) else null
    val favorite = if (hasItems && !isApps) favoriteHint(letters, focusedIsFavorite) else null
    return when {
        column == Column.Systems ->
            listOfNotNull(
                when {
                    hasItems -> HintSpec(letters.confirm, R.string.home_hint_select, Action.Enter)
                    content.asksForFolderAt(entry) -> HintSpec(letters.confirm, R.string.home_hint_add_folder, Action.Enter)
                    else -> null
                },
                search,
                settings,
            )
        // Narrow with box art and Push right to play: Right at the stop arms, so the hint is here too.
        column == Column.Details -> listOfNotNull(play, pushRight.takeIf { !showsGrid }, back, favorite, HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu))
        // Apps' grid has one fixed size and L1 R1 do nothing there: no L1 R1 hint.
        showsGrid && isApps ->
            listOfNotNull(
                play,
                back,
                switchSystem(content, shoulders),
                HintSpec(R.string.home_key_select, R.string.home_hint_list_view, Action.ToggleView),
            )
        // L1 R1 change the grid size here, not the system (the system column is a Back away).
        showsGrid ->
            listOfNotNull(
                play,
                back,
                HintSpec(shoulders.sizeCap, R.string.home_hint_grid_size, Action.NextSystem, cyclesGridSize = true),
                switchSystem(content, shoulders),
                HintSpec(R.string.home_key_select, R.string.home_hint_list_view, Action.ToggleView),
            )
        else ->
            listOfNotNull(
                play,
                // With a details stop Right goes there first, so the hint waits for the stop.
                pushRight.takeIf { !hasDetailsStop },
                back,
                favorite,
                search,
                HintSpec(R.string.home_key_start, R.string.home_hint_menu, Action.Menu),
                switchSystem(content, shoulders),
                if (entry != null && content.hasGridFor(entry)) HintSpec(R.string.home_key_select, R.string.home_hint_grid_view, Action.ToggleView) else null,
            )
    }
}

/**
 * True where the games area says "No game folder yet": no system has games and [entry] shows nothing.
 * A there (or a tap on the message) opens the folder picker instead of a column.
 */
internal fun HomeContent.asksForFolderAt(entry: HomeEntry?): Boolean = isLibraryEmpty && entry != null && entry.rows.isEmpty()

/**
 * "L2 R2 Switch system" when there is another list to switch to (with no games only Apps has rows). Its
 * action is the switching pair's own name, so a tap goes through the same reading as the key ([ShoulderKeys.read], [homeAction]).
 */
private fun switchSystem(
    content: HomeContent,
    shoulders: ShoulderKeys,
): HintSpec? =
    if (content.entries.count { it.count > 0 } > 1) {
        // Under L1 / R1 the tap sends L1 / R1's name, the one the screen reads as "switch system" there.
        val tap = if (shoulders.bumpersSwitch) Action.NextSystem else Action.JumpLetterNext
        HintSpec(shoulders.switchSystemCap, R.string.home_hint_switch_system, Action.JumpLetterNext, tapAction = tap)
    } else {
        null
    }

@StringRes
private fun pushRightLabel(
    isApps: Boolean,
    armed: Boolean,
): Int =
    when {
        armed -> if (isApps) R.string.home_hint_again_open else R.string.home_hint_again_play
        else -> if (isApps) R.string.home_hint_twice_open else R.string.home_hint_twice_play
    }

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
): List<HintSpec> = typingHints(onLetters, R.string.home_hint_search, hasShift = false, letters)

/**
 * Any field while Plumemu's keyboard is up: "A Type", "B Delete", "X Space", "START <[actionWord]>",
 * "Y 123" / "Y ABC", "SELECT Shift" when the keys have shift ([hasShift]: not Search) and "L1 R1 Cursor".
 * Short of room they give way in [TYPING_HINT_DROP_ORDER]: L1 R1, SELECT, Y.
 */
internal fun typingHints(
    onLetters: Boolean,
    @StringRes actionWord: Int,
    hasShift: Boolean,
    letters: ButtonLetters = ButtonLetters.Default,
): List<HintSpec> =
    listOfNotNull(
        HintSpec(letters.confirm, R.string.account_hint_type, Action.Enter),
        HintSpec(letters.back, R.string.home_hint_delete, Action.Erase()),
        HintSpec(letters.search, R.string.home_hint_space, Action.Search),
        HintSpec(R.string.home_key_start, actionWord, Action.Menu),
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
    val dot by LocalUpdateFound.current.collectAsState()
    // Only what the hints depend on, so a row move does not recompose the bar.
    val column by remember(state) { derivedStateOf { state.focus.column } }
    val systemIndex by remember(state) { derivedStateOf { state.focus.systemIndex } }
    val entry = content.entries.getOrNull(systemIndex)
    // Changes only when the cursor moves between a favorite and another game (Y Favorite / Unfavorite).
    val focusedIsFavorite by remember(state, entry) { derivedStateOf { entry?.rows?.getOrNull(state.focus.rowOf(entry.id))?.isFavorite == true } }
    val showsGrid = content.showsGrid(entry, gridIds) && panel == null
    // Push right to play: after the first Right the hint says "Again to play" (only this reads the arming).
    val stripArmed by remember(state) { derivedStateOf { state.isPushRightArmed } }
    val hasDetailsStop by remember(state) { derivedStateOf { state.hasDetailsStop } }
    val letters = LocalButtonLetters.current
    val shoulders = LocalShoulderKeys.current
    val openSearch = search?.takeIf { it.isOpen && panel == null }
    val isTyping = openSearch?.let { it.inField && it.isTrayUp } == true
    val searchHintsShown = openSearch?.let { open ->
        val inField = open.inField
        val onFavorite = !inField && open.results.getOrNull(open.row)?.row?.isFavorite == true
        val onLetters = isTyping && open.keys.page == KeyboardPage.Letters
        val xTypes = open.usesOwnKeyboard
        val searchArmed = open.isPushRightArmed
        remember(inField, isTyping, onLetters, onFavorite, letters, xTypes, pushRightToPlay, searchArmed) {
            if (isTyping) searchTypingHints(onLetters, letters) else searchHints(inField, onFavorite, letters, xTypes, pushRightToPlay, searchArmed)
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
        remember(open, canChoose, onField, chooseLabel, letters, isPanelTrayUp, onLetters, actionWord) {
            if (isPanelTrayUp) typingHints(onLetters, actionWord, hasShift = true, letters) else panelHints(open, letters)
        }
    }
    val hints =
        searchHintsShown ?: typingHints ?: remember(content, column, entry, showsGrid, letters, shoulders, panel, focusedIsFavorite, pushRightToPlay, stripArmed, dot, hasDetailsStop) {
            panel?.let { panelHints(it, letters) } ?: hintsFor(content, column, entry, showsGrid, letters, focusedIsFavorite, pushRightToPlay, stripArmed, dot, shoulders, hasDetailsStop)
        }
    val drops = if (isTyping || isPanelTrayUp) TYPING_HINT_DROP_ORDER else HINT_DROP_ORDER
    val dropOrder = remember(hints, drops) { drops.flatMap { action -> hints.indices.filter { hints[it].action == action } } }
    val views: @Composable () -> Unit = remember(hints, onAction, onCycleGridSize) { { hints.forEach { HintView(it, onAction, onCycleGridSize) } } }
    if (searchHintsShown == null && showsGrid && column == Column.Games && entry != null) {
        // "L2 R2 Switch system" gives way before the game's year and publisher; the keys still work.
        val beforeMeta = hints.indices.filter { hints[it].action == Action.JumpLetterNext }
        GridHintBar(entry, state, dropOrder, beforeMeta, views)
    } else {
        // On a narrow screen in a wide font the games hints do not all fit: L2 R2 Switch system goes first (the
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
        hasDot = spec.hasDot,
    )
}

/**
 * The grid's bar: "Mega Man X · 1993 · Capcom" from where the grid starts, the hints at the right. In
 * Recent and Favorites the meta starts with the game's system ("SNES · 1993 · Capcom"), as the list does.
 */
@Composable
private fun GridHintBar(
    entry: HomeEntry,
    state: StripState,
    dropOrder: List<Int>,
    dropBeforeMeta: List<Int>,
    hints: @Composable () -> Unit,
) {
    val row by remember(state, entry.id) { derivedStateOf { state.focus.rowOf(entry.id) } }
    val game = entry.rows.getOrNull(row)
    val art = LocalHomeArt.current
    // Through a derived state: read straight in this scope, a result that landed after the bar was
    // first composed did not reach it (HomeCoverArtTest).
    val details by remember(game, art) { derivedStateOf { game?.let { detailsOf(it, art.slot(it.scrapeKey)) } } }
    TitledHintBar(
        title = game?.title.orEmpty(),
        meta = gridMeta(game?.systemAbbr.takeIf { entry.kind.mixesSystems }, details?.let { yearAndPublisher(it) }.orEmpty(), LocalResources.current),
        startInset = PlumTheme.layout.peek + PlumDimens.columnGap,
        modifier = Modifier.testTag(HomeTags.HINT_BAR),
        dropOrder = dropOrder,
        dropBeforeMeta = dropBeforeMeta,
        hints = hints,
    )
}

/** [system] (Recent and Favorites only; null or empty is left out) joined to [rest] with the same " · " as year and publisher. */
private fun gridMeta(
    system: String?,
    rest: String,
    resources: Resources,
): String =
    when {
        system.isNullOrEmpty() -> rest
        rest.isEmpty() -> system
        else -> resources.getString(R.string.home_details_pair, system, rest)
    }

/**
 * Which hints give way first when the bar is too narrow for all of them (see [HomeHints]): L2 R2 Switch system, then
 * L1 R1 Size, B Back, and SELECT last; START Menu and Y Favorite are not in the order (only a bar still too
 * narrow after those would drop hints from its end). By touch SELECT's hint is the only way
 * between a list and its grid, while B has the system's back gesture;
 * `HomeViewToggleHintTest` checks SELECT stays at 576 x 432 at Largest.
 */
private val HINT_DROP_ORDER = listOf(Action.JumpLetterNext, Action.NextSystem, Action.Back, Action.ToggleView)

/** The typing hints (Search's and a panel field's) give way in this order: L1 R1 Cursor, then SELECT Shift, then Y 123; A, B, X and START always stay. */
private val TYPING_HINT_DROP_ORDER = listOf(Action.NextSystem, Action.ToggleView, Action.ToggleFavorite)

/** Test tags of the main screen's parts, for bounds and touch tests. */
internal object HomeTags {
    const val TOP_BAR = "home_top_bar"
    const val HINT_BAR = "home_hint_bar"
    const val STRIP = "home_strip"
    const val SYSTEMS = "home_systems"
    const val GAMES = "home_games"

    /** The letter rail in the system peek, and one of its letters (a slot of equal height, for touch later). */
    const val RAIL = "home_letter_rail"

    fun railLetter(letter: Char) = "home_rail_letter_$letter"

    /** The band over a system's games list, the list's first item (badge, facts line, hairline). */
    const val SYSTEM_BAND = "home_system_band"
    const val SYSTEM_BAND_FACTS = "home_system_band_facts"
    const val SYSTEM_BAND_ABOUT = "home_system_band_about"
    const val SYSTEM_BAND_RULE = "home_system_band_rule"

    /** A page of the games list (one per entry built); only the shown one has its rows in the tree. */
    const val GAMES_PAGE = "home_games_page"
    const val EMPTY_MESSAGE = "home_empty_message"
    const val EMPTY_FAVORITES = "home_empty_favorites"
    const val GRID = "home_grid"
    const val EDGE_FADE = "home_edge_fade"
    const val DETAILS = "home_details"
    const val DETAILS_COVER = "home_details_cover"
    const val DETAILS_TITLE = "home_details_title"
    const val DETAILS_FACTS = "home_details_facts"

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

    /** In a Recent or Favorites grid, a game's placeholder in its own system's shape inside the cell (no box art). */
    const val COVER_OWN_SHAPE = "home_cover_own_shape"

    /** In the Apps grid: an app's icon, the stand-in square of an app without one, and the name under either. */
    const val APP_ICON = "home_app_icon"
    const val APP_ICON_STAND_IN = "home_app_icon_stand_in"
    const val APP_NAME = "home_app_name"

    fun cover(index: Int) = "home_cover_$index"

    fun system(index: Int) = "home_system_$index"

    fun game(index: Int) = "home_game_$index"

    fun hint(action: Action) = "home_hint_${action::class.simpleName}"
}
