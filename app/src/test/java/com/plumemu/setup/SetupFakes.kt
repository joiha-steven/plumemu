package com.plumemu.setup

import com.plumemu.account.SignInForm
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.plumemu.coreui.components.LocalScanPulsePhase
import androidx.compose.ui.Modifier
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BoardSystemsFocused
import com.plumemu.home.FakeHomeContent
import com.plumemu.home.HomeAt
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.settings.ButtonLetters

/** Setup's states with the board's numbers (RP5-O1, RP5-O4, Nova-7, Nova-9). */
internal object SetupFakes {
    const val ROMS_TREE = "content://$EXTERNAL_STORAGE_AUTHORITY/tree/primary%3AROMs"

    val romsFolder = SetupFolder(1, ROMS_TREE, "ROMs", gameCount = 1031, systemCount = 14, isCounted = true)

    /** 1,284 games on 18 systems; the four biggest are the board's. */
    val boardSystems: List<SystemCount> =
        listOf(
            SystemCount("Super Nintendo", 248),
            SystemCount("Mega Drive", 173),
            SystemCount("PlayStation", 142),
            SystemCount("Game Boy Advance", 96),
        ) + (1..14).map { SystemCount("System $it", if (it <= 9) 45 else 44) }

    val chooseFolder = SetupUiState()

    /** The library could not be read: Try again and Set up later. */
    val loadFailed = SetupUiState(hasLoadFailed = true, focus = SetupControl.TryAgain)

    val folderChosen = SetupUiState(folders = listOf(romsFolder), focus = SetupControl.Continue)

    val scanning =
        SetupUiState(
            step = SetupStep.Scan,
            folders = listOf(romsFolder.copy(isCounted = false)),
            systems = boardSystems,
            isScanning = true,
            focus = null,
        )

    /** The second screen question on a device whose screen is built in (Use it focused). */
    val secondScreen =
        SetupUiState(
            step = SetupStep.SecondScreen,
            folders = listOf(romsFolder.copy(isCounted = false)),
            systems = boardSystems,
            isScanning = true,
            focus = SetupControl.UseSecondScreen,
            secondScreenKind = ScreenKind.BuiltIn,
            hasSecondScreenStep = true,
        )

    /** The box art question while the scan runs (Use my account focused), before any form. */
    val boxArtQuestion =
        SetupUiState(
            step = SetupStep.BoxArt,
            folders = listOf(romsFolder.copy(isCounted = false)),
            systems = boardSystems,
            isScanning = true,
            focus = SetupControl.UseBoxArt,
            hasBoxArtStep = true,
        )

    /** The box art step while the scan runs, its form in [form]'s state (account/AccountShots.kt). */
    fun boxArt(form: SignInForm) =
        SetupUiState(
            step = SetupStep.BoxArt,
            folders = listOf(romsFolder.copy(isCounted = false)),
            systems = boardSystems,
            isScanning = true,
            focus = null,
            hasBoxArtStep = true,
            boxArt = form,
        )

    val ready =
        SetupUiState(
            step = SetupStep.Ready,
            folders = listOf(romsFolder),
            systems = boardSystems,
            emulators = EmulatorCoverage(16, 18, listOf("GameCube", "PlayStation 2")),
            focus = SetupControl.StartPlaying,
        )

    /** The scan step once the walk has ended and the games are being named: 640 of 1,837 (a Classic library). */
    val scanningNaming =
        scanning.copy(
            folders = listOf(romsFolder.copy(gameCount = 1837, isCounted = true)),
            scanProgress = SetupScanProgress(com.plumemu.graph.ScanPhase.Naming, gamesNamed = 640, gamesToName = 1837),
        )

    /** The library ready while the games are still being named: the quiet line under the description. */
    val readyStillNaming =
        ready.copy(isScanning = true, isNaming = true, scanProgress = SetupScanProgress(com.plumemu.graph.ScanPhase.Naming, gamesNamed = 640, gamesToName = 1837))

    /** The library ready after a sign-in on the box art step: its box art line (three dots: folder, box art, library). */
    val readyWithBoxArt = ready.copy(hasBoxArtStep = true, isBoxArtComing = true)
}

private const val PINNED_PULSE_PHASE = 0.5f

/** Setup as the screen draws it, on the theme's background, with the board's clock and battery. */
@Composable
internal fun SetupAt(
    state: SetupUiState,
    pulsePhase: Float? = PINNED_PULSE_PHASE,
    wifi: Boolean = false,
    /** On the charger: the bolt before the battery percentage. */
    charging: Boolean = false,
) {
    // The scan dot is pinned half way through its breath so a shot is the same every time.
    CompositionLocalProvider(LocalScanPulsePhase provides pulsePhase) {
        Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) {
            SetupLayout(state, ButtonLetters.Default, onAction = {}, clock = { "21:40" }, battery = { "82%" }, wifi = { wifi }, charging = { charging })
        }
    }
}

/** The home screen question over the board's main screen. */
@Composable
internal fun HomeRolePromptAt(showsUnavailable: Boolean = false) {
    Box(Modifier.fillMaxSize().background(PlumTheme.colors.bg)) {
        HomeAt(FakeHomeContent.board(), BoardSystemsFocused)
        HomeRolePromptLayout(showsUnavailable, focus = 0, letters = ButtonLetters.Default, onAction = {})
    }
}
