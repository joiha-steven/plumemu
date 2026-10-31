package com.plumemu.home

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.battery.BatteryPaceNow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.plumemu.settings.LocalShoulderKeys
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreinput.strip.rememberStripState
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.fadingIn
import com.plumemu.coreui.theme.rememberContentFade
import com.plumemu.game.AppActions
import com.plumemu.game.GameActions
import com.plumemu.game.GameMenuController
import com.plumemu.game.GamePanelOverlay
import com.plumemu.game.PanelKeyboardTray
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.game.GameRef
import com.plumemu.game.closedAtOnceMessage
import com.plumemu.game.showMessage
import com.plumemu.game.coreQuestionMessage
import com.plumemu.game.showSaveFailed
import com.plumemu.game.OpenPanel
import com.plumemu.game.PanelScreen
import com.plumemu.game.rememberGameMenuController
import com.plumemu.usage.TrackScreen
import com.plumemu.usage.homeScreenIdOf
import com.plumemu.home.search.FollowSearchResults
import com.plumemu.home.search.SearchKeyboardTray
import com.plumemu.home.search.SearchResults
import com.plumemu.home.search.SearchState
import com.plumemu.home.search.SearchTopBar
import com.plumemu.home.search.rememberSearchState
import com.plumemu.secondscreen.FollowHomeOnSecondScreen
import com.plumemu.secondscreen.SecondScreenFeed
import com.plumemu.sound.Feel
import com.plumemu.sound.HearFeel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the screen asks of the app; the reducer decided, these do. */
@Immutable
internal data class HomeCallbacks(
    /** Opens the Apps entry's rows (through the game panel's controller, which shows a failure). Games are played through [games]. */
    val apps: AppActions = AppActions.None,
    /** Play and the game menu (START or a long press on a game): launches, choices, marks. */
    val games: GameActions = GameActions.None,
    /** Settings (START on the system column, or the hint). */
    val onSettings: () -> Unit = {},
    /** The ScreenScraper results and their pictures (`MainScreen` provides them, shown only with box art). */
    val art: HomeArtSource = HomeArtSource.None,
    /** The focused game's play facts for the details column (last played, play time, emulator), read once the focus rests; with or without box art (`MainScreen` provides them). */
    val playFacts: HomePlayFactsSource = HomePlayFactsSource.None,
    /** The Apps grid's icons (Android's launcher icons, `GraphAppIcons`; `MainScreen` provides them as [LocalHomeAppIcons]). */
    val appIcons: HomeAppIcons = HomeAppIcons.None,
    /** Works the battery line's figures out again when the battery changes (`MainScreen` provides it); null: the figures read with the facts. */
    val batteryPaceNow: BatteryPaceNow? = null,
    /** The system shown changed (null: Recent, Favorites or Apps); a box-art run favours it. */
    val onSystemShown: (String?) -> Unit = {},
    /** True while an update was found: START Settings' hint gets a dot (`MainScreen` provides it as [LocalUpdateFound]). */
    val updateFound: StateFlow<Boolean> = MutableStateFlow(false),
    /** A on "No game folder yet" (or a tap on it): opens the folder picker, as Settings › Library › Add folder does. */
    val onAddFolder: () -> Unit = {},
    /** What change checks found: the top bar's "+24 games" notice and the dots on systems (`MainScreen` provides it as [LocalHomeNotices]). */
    val notices: HomeNotices = HomeNotices(),
    /** L1 / R1 (or a tap on the Size hint) in a grid picked this size; written to Settings › Appearance › Grid size; [onFailed] if the write could not be saved. */
    val onGridSize: (GridSize, onFailed: () -> Unit) -> Unit = { _, _ -> },
    /** SELECT made [systemId] a grid ([isGrid]) or a list; written to `appearance.grid_systems`, once per press; [onFailed] if the write could not be saved (on the writer's thread). */
    val onGridView: (systemId: String, isGrid: Boolean, onFailed: () -> Unit) -> Unit = { _, _, _ -> },
    /** Search closed after a search: the query's length, the results and whether one was opened; never the words (`search_used`). */
    val onSearchUsed: (queryLength: Int, results: Int, openedResult: Boolean) -> Unit = { _, _, _ -> },
)

/**
 * What the person switched off around the strip (Settings › Controls › Show button hints, Home
 * screen › Show clock and Show battery). Settings and setup keep their own hints whatever this says, so
 * the way back is always shown there.
 */
@Immutable
internal data class HomeBars(
    val showHints: Boolean = true,
    val showClock: Boolean = true,
    val showBattery: Boolean = true,
    /** Settings › Controls › Push right to play: a fresh Right on a game in a list plays it, and the hint bar says so. */
    val pushRightToPlay: Boolean = false,
    /** Settings › Home screen › System info: the band over a system's games list. Only the top screen's band: the second screen's card keeps its facts. */
    val showSystemInfo: Boolean = true,
)

/**
 * The main screen. Collects
 * [router]'s actions only while the screen is started, turns them into focus moves through
 * core-input's strip state; plays games and opens their menu (game panel), opens apps through the
 * same controller ([HomeCallbacks.apps]), hands Settings to [callbacks].
 */
@Composable
internal fun HomeScreen(
    content: HomeContent,
    router: InputRouter,
    callbacks: HomeCallbacks,
    modifier: Modifier = Modifier,
    bars: HomeBars = HomeBars(),
    secondScreen: SecondScreenFeed? = null,
    landingRequest: Int = 0,
    keyboard: KeyboardChoice = KeyboardChoice.Plumemu,
    gridSystems: Set<String> = emptySet(),
) {
    // List or grid per system: the stored [gridSystems], there from the first frame (MainScreen
    // composes nothing before the settings are read), with this screen's presses over it.
    val gridViews = rememberGridViews(gridSystems)
    // Y shows its star at once, and the Favorites list holds still while the person is in it.
    val marks = remember { FavoriteMarks() }
    val shown by remember(content, marks) { derivedStateOf { marks.shown(content) } }
    LaunchedEffect(content, marks) { marks.settle(content) }
    // Layout C: while the second screen's window is up its card holds the details, so the lists
    // drop their column. Read here once (it changes only when the window comes or goes), never in a row.
    val detailsOnSecondScreen = secondScreen?.isWindowShown?.collectAsState()?.value == true
    val state = rememberHomeStripState(shown, gridViews.ids, PlumTheme.motion.isOn, detailsOnSecondScreen)
    // Each system's list position, kept here beside the focus: the games pages leave the composition while search, a grid or an empty library shows.
    val positions = rememberGamesPagePositions()
    val search = rememberSearchState(keyboard).also { ApplyPushRightToPlay(bars.pushRightToPlay, state, it) }
    val currentContent by rememberUpdatedState(shown)
    val currentLive by rememberUpdatedState(content)
    // The marks as they are now, not as last drawn: a second Y before the screen redraws must
    // read the first one's mark (two quick presses end where they started).
    val markedNow = remember(marks) { { marks.shown(currentLive) } }
    val currentCallbacks by rememberUpdatedState(callbacks)
    val menu = rememberGameMenuController(callbacks.games, callbacks.apps, keyboard)
    val toggleView = remember(gridViews, state, menu) { { id: String -> gridViews.toggle(id, currentCallbacks.onGridView) { showSaveFailed(currentContent, state, menu) } } }
    val gridSizeKeys = rememberGridSizeKeys(
        gridViews.ids,
        onSize = { size, onFailed -> currentCallbacks.onGridSize(size, onFailed) },
        onWriteFailed = { showSaveFailed(currentContent, state, menu) },
    )
    val feel = remember { Feel() }
    HearFeel(feel, menu.feel, search.feel)
    val favoriteListener = remember(marks, state, search) { HomeFavoriteListener(marks, state, search) { currentContent } }
    SideEffect { menu.favoriteListener = favoriteListener }
    ReleaseFavoritesWhenLeft(marks, state)
    // The Home button, or setup's end, asked to return to the landing point. Saved with the screen: it survives Settings and a recreated Activity.
    val obeyedLanding = rememberSaveable { mutableIntStateOf(0) }
    val currentLandingRequest by rememberUpdatedState(landingRequest)
    val shoulders by rememberUpdatedState(LocalShoulderKeys.current)
    val pageRows by rememberUpdatedState(PlumTheme.layout.listRowsThatFit)
    val actions = HomeActionTargets(state, menu, search, feel, gridSizeKeys, toggleView, markedNow, { currentContent }, { currentCallbacks }, { shoulders }, { pageRows })
    FollowRouterActions(router, actions, onEachAction = { obeyedLanding.intValue = currentLandingRequest })
    LandWhenAsked(landingRequest, obeyedLanding, state, menu, search) { currentContent }
    TellRouterAboutTyping(router, search, menu)
    FollowSearchResults(search, shown)
    FollowMenuGame(menu, shown, state, isSearchOpen = search.isOpen)
    // Read in its own effect only: the top screen never recomposes for the bottom one.
    if (secondScreen != null) FollowHomeOnSecondScreen(secondScreen, shown, state, search, menu, gridViews.ids)
    val onAction = remember(router) { { action: Action -> router.send(action) } }
    // Switched off: nothing ticks or listens for it.
    val clock = if (bars.showClock) rememberClockText() else null
    // One battery receiver for the screen, the broadcast only: the top bar's level and the picking line under the details
    // and in Info. Both follow Show battery: off, no reading and no line.
    val batteryReading = if (bars.showBattery) rememberBatteryReading() else null
    val battery = batteryReading?.let { rememberBatteryText(it) }
    val charging = batteryReading?.let { rememberBatteryCharging(it) }
    val wifi = if (bars.showBattery) rememberWifiConnected() else null
    FollowShownSystem(shown, state, callbacks.onSystemShown)
    ShowClosedAtOnce(callbacks.notices, menu)
    ShowCoreQuestion(callbacks.notices, menu)
    TrackHomeScreen(state, search, menu) { currentContent }
    SideEffect { search.onSearchUsed = currentCallbacks.onSearchUsed }
    CompositionLocalProvider(LocalBatteryReading provides (batteryReading ?: NoBatteryReading)) {
        HomeLayout(
            content = shown,
            state = state,
            onAction = onAction,
            clock = { clock?.value },
            battery = { battery?.value },
            wifi = { wifi?.value == true },
            charging = { charging?.value == true },
            modifier = modifier,
            gridIds = gridViews.ids,
            panel = menu.panel,
            showHints = bars.showHints, pushRightToPlay = bars.pushRightToPlay, showSystemInfo = bars.showSystemInfo,
            search = search,
            positions = positions,
            detailsOnSecondScreen = detailsOnSecondScreen,
            onCycleGridSize = { gridSizeKeys.cycle(feel) },
            onKeyboardEvent = menu::onKeyboardEvent,
        )
    }
}

/**
 * Search and the game panel close once, when [request] is first seen. With nothing to land on yet
 * (setup's end can come before the scan writes a game) the request waits, but only until the
 * person's next action spends it ([obeyed] set by the action handler): a scan finishing later must
 * never move the focus under them.
 */
@Composable
private fun LandWhenAsked(
    request: Int,
    obeyed: MutableIntState,
    state: StripState,
    menu: GameMenuController,
    search: SearchState,
    content: () -> HomeContent,
) {
    LaunchedEffect(request) {
        if (request == obeyed.intValue) return@LaunchedEffect
        menu.close()
        search.close()
        snapshotFlow { obeyed.intValue == request || content().landingEntryId() != null }.first { it }
        if (obeyed.intValue == request) return@LaunchedEffect
        obeyed.intValue = request
        content().landingEntryId()?.let(state::landOn)
    }
}

/**
 * An emulator closed within seconds of its start (`PlaySessionOutcome.TooShort`): its message over
 * the list, once. A panel that is open (a menu the person opened first) is waited for, never replaced.
 */
@Composable
private fun ShowClosedAtOnce(
    notices: HomeNotices,
    menu: GameMenuController,
) {
    val message by notices.closedAtOnce.collectAsState()
    LaunchedEffect(message?.serial) {
        val told = message ?: return@LaunchedEffect
        snapshotFlow { menu.isOpen }.first { !it }
        menu.showMessage(GameRef(told.gameKey, told.gameTitle), closedAtOnceMessage(told.emulatorName))
        notices.onClosedAtOnceShown(told)
    }
}

/**
 * A launch with a RetroArch core never confirmed came back: "Did <game> start?" over the list, once
 *. Like the closed-at-once message, a panel the person opened
 * first is waited for, never replaced; the two are never posted for the same return.
 */
@Composable
private fun ShowCoreQuestion(
    notices: HomeNotices,
    menu: GameMenuController,
) {
    val current by rememberUpdatedState(notices)
    // One loop, one question at a time: shown, then over once its panel closes (Yes, B, or after No's
    // emulators), before the next is even looked at, so the order never depends on two effects.
    LaunchedEffect(menu) {
        current.onCoreQuestionsRestarted()
        current.coreQuestion.collect { asked ->
            if (asked == null) return@collect
            snapshotFlow { menu.isOpen }.first { !it }
            val shown = menu.showMessage(GameRef(asked.gameKey, asked.gameTitle), coreQuestionMessage(asked))
            current.onCoreQuestionShown(asked)
            if (shown) snapshotFlow { menu.isOpen }.first { !it }
            current.onCoreQuestionOver(asked)
        }
    }
}

/** A write the main screen made (a grid size, a list or grid) failed: say so over the focused game, as a failed Favorite does (the main screen has no other notice). */
private fun showSaveFailed(
    content: HomeContent,
    state: StripState,
    menu: GameMenuController,
) {
    content.entries.getOrNull(state.focus.systemIndex)?.let { entry -> entry.rows.getOrNull(state.focus.rowOf(entry.id)) }?.let { menu.showSaveFailed(GameRef(it.id, it.title)) }
}

/**
 * What the router must send now: the open game panel's (its field's typing keys, none for a panel
 * without one: a failed write's message can open over typing Search, and its B is Back), else
 * Search's typing keys. The one place the main screen decides it.
 */
internal fun textEntryFor(
    search: SearchState,
    menu: GameMenuController,
): TextEntry = if (menu.isOpen) menu.textEntry else search.textEntry

/**
 * The strip state for [content] with the systems in [gridIds] shown as grids: the grid's columns
 * go to core-input's focus rules, and the geometry follows whether the focused system is a grid
 * (its games column is then the grid area). With [detailsOnSecondScreen] the lists have no
 * details column and no details stop (layout C); the change applies in the composition that
 * sees it, so the strip and the focus switch in the same frame. Shared by [HomeScreen] and the
 * test harness.
 */
@Composable
internal fun rememberHomeStripState(
    content: HomeContent,
    gridIds: Set<String>,
    isMotionOn: Boolean,
    detailsOnSecondScreen: Boolean = false,
): StripState {
    val layout = PlumTheme.layout
    // Set after the state exists (below); a change re-measures the details stop one frame later,
    // long before the person can press Right after SELECT.
    var focusedShowsGrid by remember { mutableStateOf(false) }
    val geometry =
        remember(layout, content.hasBoxArt, focusedShowsGrid, detailsOnSecondScreen) {
            homeStripGeometry(layout, content.hasBoxArt, focusedShowsGrid, detailsOnSecondScreen)
        }
    val strip =
        remember(content, gridIds, layout, detailsOnSecondScreen) {
            content.stripWith(gridIds, layout).let { if (detailsOnSecondScreen) it.withDetailsOnSecondScreen(content, gridIds) else it }
        }
    val state = rememberStripState(strip, geometry, isMotionOn, PlumTheme.motion.slideSpec())
    FollowFocusedGrid(state, content, gridIds) { focusedShowsGrid = it }
    return state
}

/**
 * Reports whether the focused system is a grid, in its own scope: the caller (which holds the
 * strip state) recomposes only when that answer changes, never on a row move.
 */
@Composable
private fun FollowFocusedGrid(
    state: StripState,
    content: HomeContent,
    gridIds: Set<String>,
    onChange: (Boolean) -> Unit,
) {
    val showsGrid by remember(state, content, gridIds) {
        derivedStateOf { content.showsGrid(content.entries.getOrNull(state.focus.systemIndex), gridIds) }
    }
    SideEffect { onChange(showsGrid) }
}

/**
 * The open menu is about one game, not one row: when the content changes under it (a rescan), the
 * focus follows that game to its new row, and the menu closes if the game is gone. Messages stay
 * (a "no longer in the library" message must be read even though the row has left).
 */
@Composable
private fun FollowMenuGame(
    menu: GameMenuController,
    content: HomeContent,
    state: StripState,
    isSearchOpen: Boolean,
) {
    val open = menu.panel
    val game = open?.game?.takeIf { open.screen !is PanelScreen.Message }
    LaunchedEffect(content, game, isSearchOpen) {
        if (game == null) return@LaunchedEffect
        // Opened from a search result: the strip's focus is elsewhere; Hide closes the menu itself.
        if (isSearchOpen) return@LaunchedEffect
        val rows = content.entries.getOrNull(state.focus.systemIndex)?.rows.orEmpty()
        val index = rows.indexOfFirst { it.id == game.key }
        when {
            index < 0 -> {
                Log.i(TAG, "Menu closed: game ${game.key} left the list")
                menu.close()
            }
            index != state.focus.row -> state.focusRow(index)
        }
    }
}

/** Settings › Controls › Push right to play, handed to the strip and to Search (they read it on every press). */
@Composable
private fun ApplyPushRightToPlay(
    isOn: Boolean,
    state: StripState,
    search: SearchState,
) {
    val scope = rememberCoroutineScope()
    // Leaving the screen (or the device going to sleep) forgets the arming: a Right after waking only arms.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state, search) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                state.pushRightArm.disarm()
                search.pushRightArm.disarm()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    SideEffect {
        state.pushRightPlays = isOn
        search.pushRightPlays = isOn
        search.scope = scope
    }
}

internal fun HomeEntry.gameAt(index: Int): GameRef? =
    rows.getOrNull(index)?.let { GameRef(it.id, it.title) } ?: null.also { Log.w(TAG, "No row $index in $id; intent dropped") }

internal fun HomeContent.entry(id: String): HomeEntry? =
    entries.firstOrNull { it.id == id } ?: null.also { Log.w(TAG, "No entry $id in the content; intent dropped") }

/**
 * The screen's layout for a given [state]: top bar, strip, hint bar, inside the strip margins
 * (14 top, 12 bottom, 10 between), and the game [panel] over the strip when one is open. [clock]
 * and [battery] are read only by their texts (null: not shown); without [showHints] the strip takes
 * the hint bar's place. [detailsOnSecondScreen]: the second screen's window is up, so a list shows
 * without its details column (pass the same value to [rememberHomeStripState]). Tests and
 * screenshots drive it with their own state.
 */
@Composable
internal fun HomeLayout(
    content: HomeContent,
    state: StripState,
    onAction: (Action) -> Unit,
    clock: () -> String?,
    battery: () -> String?,
    modifier: Modifier = Modifier,
    wifi: () -> Boolean = { false },
    /** Whether the charging bolt stands before the battery percentage; read only by the bolt. */
    charging: () -> Boolean = { false },
    gridIds: Set<String> = emptySet(),
    panel: OpenPanel? = null,
    showHints: Boolean = true,
    pushRightToPlay: Boolean = false,
    showSystemInfo: Boolean = true,
    search: SearchState? = null,
    positions: GamesPagePositions = rememberGamesPagePositions(),
    detailsOnSecondScreen: Boolean = false,
    onCycleGridSize: () -> Unit = {},
    /** Taps on the keys of a panel field's tray (Plumemu's keyboard): the game panel's controller. */
    onKeyboardEvent: (KeyboardEvent) -> Unit = {},
) {
    val searching = search?.isOpen == true
    // Opening or closing search fades the new bar and content in (the panel overlay keeps its slide).
    val searchFade = rememberContentFade(searching)
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PlumTheme.colors.bg)
                .padding(top = PlumDimens.stripMarginTop, bottom = PlumDimens.stripMarginBottom),
        verticalArrangement = Arrangement.spacedBy(PlumDimens.barGap),
    ) {
        Box(Modifier.fadingIn(searchFade)) {
            if (searching && search != null) SearchTopBar(search, clock, battery, wifi, charging) else HomeTopBar(content, state, clock, battery, wifi, charging)
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.fillMaxSize().fadingIn(searchFade)) {
                if (searching && search != null) {
                    SearchResults(search, onAction, isPanelOpen = panel != null, Modifier.fillMaxHeight())
                } else {
                    HomeStrip(
                        content,
                        state,
                        gridIds,
                        onAction,
                        Modifier.fillMaxSize(),
                        isPanelOpen = panel != null,
                        positions = positions,
                        detailsOnSecondScreen = detailsOnSecondScreen,
                        showSystemInfo = showSystemInfo,
                    )
                }
            }
            // Plumemu's keyboard over the results' lower part, down to the screen's bottom; the hint bar is drawn on it.
            val below = if (showHints) PlumDimens.barGap + PlumTheme.layout.hintBarHeight + PlumDimens.stripMarginBottom else PlumDimens.stripMarginBottom
            if (searching && search != null && search.usesOwnKeyboard) {
                SearchKeyboardTray(search, belowContent = below)
            }
            GamePanelOverlay(panel, onAction, keyboardCovers = PlumTheme.layout.keyboardTrayHeight - below)
            // A panel's field typed with Plumemu's keyboard: its tray over the panel, the same place as Search's.
            PanelKeyboardTray(panel, onKeyboardEvent, belowContent = below)
        }
        if (showHints) HomeHints(content, state, gridIds, onAction, panel, search, onCycleGridSize, pushRightToPlay)
    }
}

/**
 * Keeps [router]'s [InputRouter.textEntry] on what typing needs (typed keys, B erasing while a tray is
 * up): an open game panel's field first ([GameMenuController.textEntry]), else Search's, including
 * changes no action made (a tap on a key, the Home button closing Search, a failed write's message
 * opening over it); none when the main screen leaves (Settings, setup): no other screen ever gets an
 * erase or a typed key.
 */
@Composable
private fun TellRouterAboutTyping(
    router: InputRouter,
    search: SearchState,
    menu: GameMenuController,
) {
    LaunchedEffect(router, search, menu) { snapshotFlow { textEntryFor(search, menu) }.collect { router.textEntry = it } }
    DisposableEffect(router) { onDispose { router.textEntry = TextEntry.None } }
}

internal const val TAG = "Home"

/**
 * The main screen's screen the person settles on (`screen_opened`): its own scope, so a focus move
 * recomposes only this when the screen changes, and [TrackScreen] waits for the focus to rest.
 */
@Composable
private fun TrackHomeScreen(
    state: StripState,
    search: SearchState,
    menu: GameMenuController,
    content: () -> HomeContent,
) {
    val screen by remember(state, search, menu) {
        derivedStateOf {
            val focus = state.focus
            homeScreenIdOf(focus.column, content().entries.getOrNull(focus.systemIndex)?.kind, search.isOpen, menu.isOpen)
        }
    }
    TrackScreen(screen)
}
