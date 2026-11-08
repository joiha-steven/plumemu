package com.plumemu

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.plumemu.settings.screen.FadeIn
import com.plumemu.settings.screen.SettingsDeviceFacts
import com.plumemu.settings.screen.SettingsHost
import com.plumemu.settings.screen.SettingsScreen
import com.plumemu.settings.screen.SettingsStatus
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.plumemu.coreui.theme.PlumMotion
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.coreinput.Action
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.withFrameNanos
import com.plumemu.settings.screen.SettingsOpening
import com.plumemu.settings.screen.LocalSettingsOpeningStage
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coreinput.InputRouter
import com.plumemu.settings.screen.SettingsContent
import com.plumemu.secondscreen.SecondScreenFeed
import com.plumemu.secondscreen.ShowStatisticsOnSecondScreen
import com.plumemu.settings.screen.StatisticsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import com.plumemu.home.ProvideHomeArt

/** Settings with what it shows besides the store: folders, scan, emulators, whether Plumemu is the home app. */
@Composable
internal fun SettingsOverHome(
    settingsHost: SettingsHost,
    settings: PlumSettings,
    input: InputRouter,
    secondScreen: SecondScreenFeed?,
    opensOnUpdate: Boolean = false,
    showOnHome: (systemId: String, gameId: Long) -> Unit = { _, _ -> },
    opensOnFilesNotAdded: Boolean = false,
    /** Told once Settings is whole ([SettingsOpening.WHOLE]): what lay under it may leave. */
    onWhole: () -> Unit = {},
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
    // A repeat opening starts from the last content; the first one holds the rows back (below) until it arrives.
    val liveContent by settingsHost.content.collectAsStateWithLifecycle(remember(settingsHost) { settingsHost.remembered() })
    var holdOver by remember(settingsHost) { mutableStateOf(false) }
    val content = liveContent
    // Read off the main thread whenever the main screen or Settings comes to the front ([ReadSettingsDeviceFacts]), so the
    // opening frame makes no binder call; asked here only if Settings opens before the first read has answered.
    val readFacts by settingsHost.deviceFacts.collectAsStateWithLifecycle()
    val firstFacts = remember(settingsHost) { if (settingsHost.deviceFacts.value == null) settingsHost.readDeviceFactsNow() else null }
    val facts = readFacts ?: firstFacts ?: SettingsDeviceFacts(isHomeApp = false, hasUsageAccess = false)
    val isHomeApp = facts.isHomeApp
    val hasUsageAccess = facts.hasUsageAccess
    if (content == null && !holdOver) {
        // First content not here yet: the background shows, and Back still closes. A slow read draws from the empty content after the deadline.
        LaunchedEffect(Unit) {
            delay(SETTINGS_CONTENT_WAIT_MS)
            holdOver = true
        }
        LaunchedEffect(input) { input.actions.collect { if (it == Action.Back) currentClose() } }
        // The main screen may still lie under it ([SettingsOpening]): no tap reaches it.
        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
        return
    }
    // One stage a frame ([SettingsOpening]): a press's frame no longer composes and measures the whole screen at once.
    var stage by remember(settingsHost) { mutableIntStateOf(0) }
    val currentOnWhole by rememberUpdatedState(onWhole)
    LaunchedEffect(settingsHost) {
        // The first stage as soon as the press's frame is done; each next one a frame later.
        stage++
        while (stage < SettingsOpening.WHOLE) {
            withFrameNanos { }
            stage++
        }
        currentOnWhole()
    }
    // Until Settings reads the presses itself, Back still closes; nothing passes through to what lies under it.
    if (stage < SettingsOpening.SCREEN) {
        LaunchedEffect(input) { input.actions.collect { if (it == Action.Back) currentClose() } }
        Box(Modifier.fillMaxSize().pointerInput(Unit) {})
        return
    }
    FadeIn(Unit, depth = -PlumMotion.SCREEN_DEPTH, isHeld = stage < SettingsOpening.WHOLE) {
        // Statistics' covers decode through the home screen's loader; nothing is observed (not live).
        ProvideHomeArt(settingsHost.statistics.art, hasBoxArt = false) {
            CompositionLocalProvider(LocalSettingsOpeningStage provides stage) {
                SettingsScreen(
                    settings,
                    input,
                    actions,
                    settingsHost.versionName,
                    status = SettingsStatus(libraryReset, saveFailed, folderAdd),
                    content = (content ?: SettingsContent()).copy(isHomeApp = isHomeApp, hasUsageAccess = hasUsageAccess),
                    onStatistics = onStatistics,
                    opensOnUpdate = opensOnUpdate,
                    opensOnFilesNotAdded = opensOnFilesNotAdded,
                    showOnHome = showOnHome,
                )
            }
        }
    }
}

/** Reads [SettingsHost.deviceFacts] off the main thread each time the activity comes to the front, before Settings can open. */
@Composable
internal fun ReadSettingsDeviceFacts(settingsHost: SettingsHost) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(settingsHost, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { settingsHost.readDeviceFacts() } }
}

/** How long a first opening of Settings waits for its content before drawing without it. */
private const val SETTINGS_CONTENT_WAIT_MS = 300L
