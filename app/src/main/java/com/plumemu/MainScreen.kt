package com.plumemu

import com.plumemu.usage.TrackError
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import com.plumemu.coreui.theme.BackgroundChoice
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import com.plumemu.settings.CachedAppearance
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import com.plumemu.coredata.settings.AppearanceSettings
import com.plumemu.settings.screen.ColorPreview
import com.plumemu.settings.screen.FadeIn
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsScreen
import com.plumemu.settings.screen.SettingsStatus
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.hint
import com.plumemu.coreui.components.Hint
import com.plumemu.coreui.components.LocalConfirmCap
import com.plumemu.coreui.components.HintBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.plumemu.content.HomeState
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.InputRouter
import com.plumemu.coreui.components.CentredMessage
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.HomeBars
import com.plumemu.home.HomeCallbacks
import com.plumemu.home.LocalHomeAppIcons
import com.plumemu.home.LocalHomePlayFacts
import com.plumemu.battery.LocalBatteryPaceNow
import com.plumemu.home.LocalHomeNotices
import com.plumemu.home.LocalUpdateFound
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.home.HomeScreen
import com.plumemu.secondscreen.FollowSetupOnSecondScreen
import com.plumemu.secondscreen.SecondScreenContent
import com.plumemu.secondscreen.SecondScreenFeed
import com.plumemu.secondscreen.ShowOnSecondScreen
import com.plumemu.secondscreen.ShowStatisticsOnSecondScreen
import com.plumemu.settings.screen.StatisticsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.plumemu.home.ProvideHomeArt
import com.plumemu.settings.ButtonLetters
import com.plumemu.game.GameActions
import com.plumemu.settings.LocalButtonLetters
import com.plumemu.settings.LocalShoulderKeys
import com.plumemu.settings.ShoulderKeys
import com.plumemu.settings.SettingsTheme
import com.plumemu.settings.ViewedSystem
import com.plumemu.settings.toKeyboardChoice
import com.plumemu.setup.HomeRoleActions
import com.plumemu.setup.HomeRolePrompt
import com.plumemu.setup.HomeRolePromptState
import com.plumemu.setup.SetupModel
import com.plumemu.setup.SetupScreen
import com.plumemu.telemetry.ScreenId
import com.plumemu.usage.TrackScreen
import com.plumemu.sound.LocalUiSounds
import com.plumemu.sound.UiSounds

/** What the Activity hands the main screen for first-run setup and the home screen question. */
internal class SetupHost(
    provideModel: () -> SetupModel,
    val onOpenFolderPicker: () -> Unit,
    val homeRole: HomeRoleActions,
) {
    /** Built the first time setup shows: a person past setup never creates it. */
    val model: SetupModel by lazy(provideModel)
}

/**
 * What MainActivity draws: nothing until the settings arrive (the window shows the cached
 * background; after [OPENING_TEXT_DELAY_MS] "Opening your library…" in [waitingAppearance]'s colours), then
 * first-run setup while it is not completed, else the main screen in the stored theme once its content is
 * there ("Opening your library…" again while it is not, after the same delay), or a message when the library
 * could not be read, with Try again and Settings. With no
 * [setup] (benchmark builds) the main screen always shows. [secondScreen] is told what the second
 * screen shows (the cursor's game or system, the calm mark under Settings), read in effects only.
 */
@Composable
internal fun MainScreen(
    settings: PlumSettings?,
    state: HomeState?,
    input: InputRouter,
    callbacks: HomeCallbacks,
    onRetry: () -> Unit,
    setup: SetupHost? = null,
    settingsHost: SettingsHost? = null,
    sounds: UiSounds = UiSounds.Silent,
    secondScreen: SecondScreenFeed? = null,
    homePresses: HomePresses? = null,
    viewedSystem: ViewedSystem? = null,
    waitingAppearance: CachedAppearance? = null,
) {
    // One timer for the whole wait, settings and library alike: the line never goes off and on again between them.
    val openingTextDue = rememberOpeningTextDue()
    val isOpeningTextDue = remember(openingTextDue) { { openingTextDue.value } }
    if (settings == null) {
        // The window already paints the cached background; a slow store gets the calm line in the cached colours (A3).
        if (waitingAppearance != null) PlumTheme(waitingAppearance.waitingTheme()) { OpeningLibrary(isOpeningTextDue) }
        return
    }
    // A finger on a colour slider shows its colours here before the store has them (ColorPreview).
    val preview = settingsHost?.colorPreview
    if (preview != null) FollowColorPreview(preview, settings.appearance)
    val letters = ButtonLetters.of(settings.controls.confirmButton)
    val shoulders = ShoulderKeys.of(settings.controls.switchSystemWith)
    // Off at once: the flag is read at every play, so no sound outlives the switch.
    SideEffect { sounds.follow(settings.advanced) }
    SettingsTheme(settings, viewedSystem, preview) {
        CompositionLocalProvider(LocalButtonLetters provides letters, LocalShoulderKeys provides shoulders, LocalConfirmCap provides stringResource(letters.confirm), LocalUiSounds provides sounds) {
            MainContent(settings, state, input, callbacks, onRetry, setup, settingsHost, letters, secondScreen, homePresses, isOpeningTextDue)
        }
    }
}

/**
 * Lets go of the colour preview once the store shows what it showed, or after
 * [ColorPreview.SETTLE_LIMIT_MS] when it never will (a write that failed or was refused): an
 * overlay is never left on.
 */
@Composable
private fun FollowColorPreview(
    preview: ColorPreview,
    stored: AppearanceSettings,
) {
    LaunchedEffect(preview.appearance, preview.isReleased, stored) { preview.settleAgainst(stored) }
    LaunchedEffect(preview.isReleased, preview.appearance) {
        if (!preview.isReleased) return@LaunchedEffect
        delay(ColorPreview.SETTLE_LIMIT_MS)
        preview.clear()
    }
}

@Composable
private fun MainContent(
    settings: PlumSettings,
    state: HomeState?,
    input: InputRouter,
    callbacks: HomeCallbacks,
    onRetry: () -> Unit,
    setup: SetupHost?,
    settingsHost: SettingsHost?,
    letters: ButtonLetters,
    secondScreen: SecondScreenFeed?,
    homePresses: HomePresses?,
    isOpeningTextDue: () -> Boolean,
) {
    // Set while setup shows: the main screen that follows lands on Recent or the first system.
    val hasShownSetup = remember { BooleanArray(1) }
    // Laid out for the full screen: the system bars are hidden (the top bar has its own clock
    // and battery). Only a display cutout, where a device has one, is kept clear.
    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg).windowInsetsPadding(WindowInsets.displayCutout)) {
        if (setup != null && !settings.flags.setupCompleted) {
            hasShownSetup[0] = true
            FollowSetupOnSecondScreen(secondScreen, setup.model)
            TrackScreen(ScreenId.Setup)
            SetupScreen(setup.model, input, letters, setup.onOpenFolderPicker, keyboard = settings.controls.toKeyboardChoice())
            return@Box
        }
        when (state) {
            is HomeState.Ready -> HomeWithSettings(state, settings, input, callbacks, letters, setup?.homeRole, settingsHost, secondScreen, homePresses, landsOnStart = hasShownSetup[0])
            HomeState.Failed -> {
                TrackError(ScreenId.Home, LibraryReadError.LibraryUnreadable)
                LibraryErrorWithSettings(settings, input, letters, onRetry, settingsHost, secondScreen, callbacks.games, homePresses)
            }
            null -> {
                ShowOnSecondScreen(secondScreen, SecondScreenContent.Calm)
                OpeningLibrary(isOpeningTextDue)
            }
        }
    }
}

/**
 * The main screen and, in its place, Settings (START on the system column or its hint). The main
 * screen leaves the composition while Settings shows; its saved state comes back with it, so Back
 * from the sections column returns to it with the focus where it was. With no [settingsHost]
 * (benchmark builds) the callbacks are used as given.
 */
@Composable
private fun HomeWithSettings(
    state: HomeState.Ready,
    settings: PlumSettings,
    input: InputRouter,
    callbacks: HomeCallbacks,
    letters: ButtonLetters,
    homeRole: HomeRoleActions?,
    settingsHost: SettingsHost?,
    secondScreen: SecondScreenFeed?,
    homePresses: HomePresses?,
    landsOnStart: Boolean,
) {
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    // Counts the times the main screen must return to its landing point (HomeScreen compares it
    // with the count it last obeyed, kept in its own saved state). Setup's end is the first one.
    var landingRequest by rememberSaveable { mutableIntStateOf(if (landsOnStart) 1 else 0) }
    // Home pressed while Plumemu is in front: close Settings (the main screen then fades in) and
    // ask the main screen to land. Search, the game panel and dialogs are closed by HomeScreen.
    if (homePresses != null) {
        LaunchedEffect(homePresses) {
            homePresses.presses.collect {
                isSettingsOpen = false
                landingRequest++
            }
        }
    }
    val homeCallbacks = remember(callbacks, settingsHost) { if (settingsHost == null) callbacks else callbacks.copy(onSettings = { isSettingsOpen = true }) }
    val showsSettings = isSettingsOpen && settingsHost != null
    // Settings covers the whole screen: composing the main screen under it re-ran its layout on
    // every colour Settings previews (19% janky frames walking the palette on the Android 6
    // emulator, 3% without). Its saveable state (the strip's focus) is kept for Back.
    val homeState = rememberSaveableStateHolder()
    // Settings and the main screen swap places; the one coming in fades in over 110 ms (no pop).
    // The first start of the app shows the main screen at once, with nothing to fade from.
    val hasOpenedSettings = remember { BooleanArray(1) }
    if (showsSettings) hasOpenedSettings[0] = true
    if (!showsSettings) {
        homeState.SaveableStateProvider(HOME_STATE_KEY) {
            // The same call either way: a different branch would give the main screen's
            // rememberSaveable state a different key, and Back would find none (focus on Recent).
            FadeIn(Unit, isFading = hasOpenedSettings[0]) { HomeWithRolePrompt(state, settings, input, homeCallbacks, letters, homeRole, secondScreen, landingRequest) }
        }
    }
    if (showsSettings && settingsHost != null) {
        SettingsOverHome(settingsHost, settings, input, secondScreen, homeCallbacks.games) { isSettingsOpen = false }
    }
}

/** Settings with what it shows besides the store: folders, scan, emulators, whether Plumemu is the home app. */
@Composable
private fun SettingsOverHome(
    settingsHost: SettingsHost,
    settings: PlumSettings,
    input: InputRouter,
    secondScreen: SecondScreenFeed?,
    games: GameActions,
    close: () -> Unit,
) {
    // The second screen shows the figures Settings reads for its Statistics page: handed over
    // through a flow its effect collects, so Settings' composition reads nothing for it.
    val statistics = remember(settingsHost) { MutableStateFlow<StatisticsState>(StatisticsState.NotRead) }
    val onStatistics = remember(statistics) { { state: StatisticsState -> statistics.value = state } }
    ShowStatisticsOnSecondScreen(secondScreen, statistics)
    val currentClose by rememberUpdatedState(close)
    val actions = remember(settingsHost) { settingsHost.actions(close = { currentClose() }) }
    val libraryReset by settingsHost.libraryReset.collectAsStateWithLifecycle()
    val saveFailed by settingsHost.saveFailed.collectAsStateWithLifecycle()
    val folderAdd by settingsHost.folderAdd.collectAsStateWithLifecycle()
    val content by settingsHost.content.collectAsStateWithLifecycle(SettingsContent())
    // Right on the first frame; refreshed whenever Settings comes back (the person may have changed it outside).
    var isHomeApp by remember(settingsHost) { mutableStateOf(settingsHost.activity.isHomeApp()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(settingsHost, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { isHomeApp = settingsHost.activity.isHomeApp() } }
    FadeIn(Unit) {
        // Statistics' covers decode through the home screen's loader; nothing is observed (not live).
        ProvideHomeArt(settingsHost.statistics.art, hasBoxArt = false) {
            SettingsScreen(
                settings,
                input,
                actions,
                settingsHost.versionName,
                status = SettingsStatus(libraryReset, saveFailed, folderAdd),
                content = content.copy(isHomeApp = isHomeApp),
                onStatistics = onStatistics,
                games = games,
            )
        }
    }
}

private const val HOME_STATE_KEY = "home"

/**
 * The main screen, and over it, once, the home screen question (after the library
 * first opens). While the question shows, the main screen gets a router nobody sends to, so a
 * press answers the question only.
 */
@Composable
private fun HomeWithRolePrompt(
    state: HomeState.Ready,
    settings: PlumSettings,
    input: InputRouter,
    callbacks: HomeCallbacks,
    letters: ButtonLetters,
    homeRole: HomeRoleActions?,
    secondScreen: SecondScreenFeed?,
    landingRequest: Int,
) {
    val needsAsking = homeRole != null && !settings.flags.homeRoleAsked
    // Asked once per composition, not per frame: a binder call to PackageManager or RoleManager.
    val isHomeAlready = remember(needsAsking) { needsAsking && homeRole?.isHomeApp() == true }
    if (isHomeAlready) LaunchedEffect(Unit) { homeRole?.markAsked() }
    val prompt = remember(homeRole) { homeRole?.let(::HomeRolePromptState) }
    val showsPrompt = prompt != null && !prompt.isClosed && !isHomeAlready && (needsAsking || prompt.showsUnavailable)
    val scope = rememberCoroutineScope()
    val quiet = remember { InputRouter(scope, onDebug = {}) }
    val home = settings.homeScreen
    val bars = HomeBars(showHints = settings.controls.showButtonHints, showClock = home.showClock, showBattery = home.showBattery, pushRightToPlay = settings.controls.pushRightToPlay, showSystemInfo = home.isSystemInfoShown)
    ProvideHomeArt(callbacks.art, state.content.hasBoxArt) {
        CompositionLocalProvider(
            LocalHomePlayFacts provides callbacks.playFacts,
            LocalBatteryPaceNow provides callbacks.batteryPaceNow,
            LocalUpdateFound provides callbacks.updateFound,
            LocalHomeNotices provides callbacks.notices,
            LocalHomeAppIcons provides callbacks.appIcons,
        ) {
            HomeScreen(
                state.content,
                if (showsPrompt) quiet else input,
                callbacks,
                bars = bars,
                secondScreen = secondScreen,
                landingRequest = landingRequest,
                keyboard = settings.controls.toKeyboardChoice(),
                gridSystems = settings.appearance.gridSystems,
            )
        }
    }
    if (showsPrompt && prompt != null) HomeRolePrompt(prompt, input, letters)
}

/**
 * The library could not be read: the message, or Settings in its place, so Restore, Reset
 * library, Report a problem and Home app stay reachable even when the database never opens and Plumemu is the home
 * app. Settings reads the library on its own and says so in its sections when it cannot (`SettingsContent.loadFailed`,
 * `StatisticsState.Failed`); Back from its sections, or the Home button, comes back here and tries the library again
 * once ([onRetry], as A does), since Settings may have fixed it. Without [settingsHost]
 * (benchmark builds) only Try again is offered.
 */
@Composable
private fun LibraryErrorWithSettings(
    settings: PlumSettings,
    input: InputRouter,
    letters: ButtonLetters,
    onRetry: () -> Unit,
    settingsHost: SettingsHost?,
    secondScreen: SecondScreenFeed?,
    games: GameActions,
    homePresses: HomePresses?,
) {
    var isSettingsOpen by rememberSaveable { mutableStateOf(false) }
    val currentRetry by rememberUpdatedState(onRetry)
    // Settings may have fixed the library (Reset library, a restore, a folder): coming back tries again once, as A would.
    val closeSettings = remember {
        {
            if (isSettingsOpen) {
                isSettingsOpen = false
                currentRetry()
            }
        }
    }
    if (homePresses != null) {
        LaunchedEffect(homePresses) { homePresses.presses.collect { closeSettings() } }
    }
    if (isSettingsOpen && settingsHost != null) {
        SettingsOverHome(settingsHost, settings, input, secondScreen, games, closeSettings)
    } else {
        ShowOnSecondScreen(secondScreen, SecondScreenContent.Calm)
        LibraryError(input, letters, onRetry, onSettings = if (settingsHost != null) ({ isSettingsOpen = true }) else null)
    }
}

/**
 * The library could not be read: what happened, Try again (A on the gamepad, or a tap on its hint, which
 * sends the same Enter through the router) and, with [onSettings], Settings (START, or a tap on its hint).
 */
@Composable
internal fun LibraryError(
    input: InputRouter,
    letters: ButtonLetters,
    onRetry: () -> Unit,
    onSettings: (() -> Unit)?,
) {
    val currentRetry by rememberUpdatedState(onRetry)
    val currentSettings by rememberUpdatedState(onSettings)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(input, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            input.actions.collect { action ->
                when (action) {
                    Action.Enter -> currentRetry()
                    // START is Menu from the gamepad; the hint's tap sends OpenSettings, as on the main screen.
                    Action.Menu, Action.OpenSettings -> currentSettings?.invoke()
                    else -> Unit
                }
            }
        }
    }
    val send = remember(input) { { action: Action -> input.send(action) } }
    Column(Modifier.fillMaxSize()) {
        CentredMessage(
            title = stringResource(R.string.home_library_error_title),
            body = stringResource(R.string.home_library_error_body),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = PlumDimens.stripMarginSide).testTag(MainScreenTags.LIBRARY_ERROR),
        )
        HintBar {
            Hint(
                keyCap = stringResource(letters.confirm),
                label = stringResource(R.string.home_hint_try_again),
                modifier = Modifier.hint(Action.Enter, send).testTag(MainScreenTags.TRY_AGAIN),
            )
            if (onSettings != null) {
                Hint(
                    keyCap = stringResource(R.string.home_key_start),
                    label = stringResource(R.string.home_hint_settings),
                    modifier = Modifier.hint(Action.OpenSettings, send).testTag(MainScreenTags.SETTINGS),
                )
            }
        }
    }
}

/**
 * True once [appearAfterMs] have passed since the main screen was first composed: the one timer of
 * [OpeningLibrary], so the wait for the settings and the wait for the library count as one.
 */
@Composable
private fun rememberOpeningTextDue(appearAfterMs: Long = OPENING_TEXT_DELAY_MS): State<Boolean> {
    val isDue = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(appearAfterMs)
        isDue.value = true
    }
    return isDue
}

/**
 * "Opening your library…", centred, once [isShown] says so (the main screen's one timer, [OPENING_TEXT_DELAY_MS] with
 * nothing else to show: a slow migration or first query on a 2 GB device left a blank screen). A
 * fast start never shows it. [isShown] is read here, so only this composable recomposes when the timer ends.
 */
@Composable
internal fun OpeningLibrary(isShown: () -> Boolean) {
    if (!isShown()) return
    Box(Modifier.fillMaxSize().padding(horizontal = PlumDimens.stripMarginSide).testTag(MainScreenTags.OPENING_LIBRARY), contentAlignment = Alignment.Center) {
        BasicText(
            text = stringResource(R.string.home_opening_library),
            style = PlumTheme.type.meta.copy(color = PlumTheme.colors.muted, textAlign = TextAlign.Center),
        )
    }
}

/** How long the main screen stays blank before it says it is opening the library. */
internal const val OPENING_TEXT_DELAY_MS = 500L

/** The theme of the wait before the settings arrive: the window's cached background and text, the rest by default. */
private fun CachedAppearance.waitingTheme(): PlumThemeSettings =
    PlumThemeSettings.Default.copy(background = BackgroundChoice.Custom(Rgb(backgroundArgb and 0xFFFFFF)), text = textMode.color)

/** `error_shown.code` of the main screen's own message. */
internal enum class LibraryReadError { LibraryUnreadable }

internal object MainScreenTags {
    const val LIBRARY_ERROR = "main_library_error"
    const val TRY_AGAIN = "main_try_again"
    const val SETTINGS = "main_settings"
    const val OPENING_LIBRARY = "main_opening_library"
}
