package com.plumemu.setup

import androidx.compose.runtime.Immutable
import com.plumemu.account.SignInForm
import com.plumemu.coreinput.TextEntry
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.telemetry.SetupStepId

/**
 * First-run setup: where the games are, the second screen question (only on a device with one,
 * while the scan runs), box art from ScreenScraper (only in a build with developer credentials,
 * while the scan runs; skippable), the scan, the library ready.
 */
internal enum class SetupStep {
    Folders,
    SecondScreen,
    BoxArt,
    Scan,
    Ready,
    ;

    /** Its two buttons sit side by side (the second screen's, the box art question's): left / right move too. */
    val hasSideBySideButtons: Boolean get() = this == SecondScreen || this == BoxArt
}

/**
 * What a person can focus in setup: one column of controls, top to bottom; the second screen's
 * two buttons, and the box art question's, sit side by side and left / right move between them too.
 */
internal enum class SetupControl {
    ChooseFolder,

    /** "Set up later": ends setup with no folder (the empty home then asks for one). */
    SetUpLater,

    /** The n-th chosen folder's card: A or a tap removes it (a wrongly picked folder). */
    FolderCard0,
    FolderCard1,
    FolderCard2,
    FolderCard3,
    FolderCard4,
    FolderCard5,
    FolderCard6,
    FolderCard7,
    AddFolder,
    Continue,
    UseSecondScreen,
    SkipSecondScreen,

    /** "Use my account": the box art question's yes, it opens the sign-in form. */
    UseBoxArt,

    /** "Not now": no box art, on to the scan. */
    SkipBoxArt,
    TryAgain,
    UsageStatistics,

    /** "Play with one hand": the unchecked switch on the last step (Settings › Controls › Push right to play). */
    PushRightToPlay,
    StartPlaying,
    ;

    /** The index of the folder this card stands for; null for every other control. */
    val folderIndex: Int? get() = if (this in folderCards) folderCards.indexOf(this) else null

    companion object {
        private val folderCards =
            listOf(FolderCard0, FolderCard1, FolderCard2, FolderCard3, FolderCard4, FolderCard5, FolderCard6, FolderCard7)

        /** The card control of the folder at [index]; null past the eighth (removed in Settings › Library). */
        fun folderCard(index: Int): SetupControl? = folderCards.getOrNull(index)
    }
}

/** A picked folder Plumemu still has access to, with what the library found in it so far. */
@Immutable
internal data class SetupFolder(
    val id: Long,
    val treeUri: String,
    /** The name stored when it was picked; shown for folders outside the device's own storage. */
    val displayName: String,
    val gameCount: Int,
    val systemCount: Int,
    /** Its first scan has ended, so the counts are whole. */
    val isCounted: Boolean,
    /** The last scan could not read it (access revoked, SD card out). */
    val isUnavailable: Boolean = false,
    /** The person removed it and the removal has not landed (it waits for a running scan): a second A is ignored. */
    val isRemoving: Boolean = false,
)

/** A plain message on the step, in place of the note under it. */
internal enum class SetupMessage {
    /** Android gave no lasting access to the picked folder. */
    FolderNotKept,

    /** The folder could not be opened or recorded (provider or database error; logged). */
    FolderCouldNotOpen,

    /** The person picked a folder that is already on the list. */
    FolderAlreadyAdded,

    /** No app on the device answers the folder picker. */
    NoFolderPicker,

    /** A setting could not be stored (logged); the person can try again. */
    SaveFailed,

    /** The last scan could not run (logged); Continue tries again. */
    ScanFailed,

    /** A folder could not be removed (logged); the person can try again. */
    FolderCouldNotRemove,
}

/** A system and its games, under the platform data's display name. */
@Immutable
internal data class SystemCount(
    val name: String,
    val gameCount: Int,
)

/** "Emulators picked for 16 of 18 systems" and the systems still without one, biggest first. */
@Immutable
internal data class EmulatorCoverage(
    val systemsWithEmulator: Int,
    val systems: Int,
    val missing: List<String>,
)

/**
 * What the running scan is doing, for the scan step's line and bar: [phase], and in the naming
 * phase how many games are named of how many are to be named ([gamesToName] is 0 while that total is not final yet).
 */
@Immutable
internal data class SetupScanProgress(
    val phase: ScanPhase = ScanPhase.Idle,
    val gamesNamed: Int = 0,
    val gamesToName: Int = 0,
) {
    /** How far naming is, 0..1; null when there is no total to measure against. */
    val namedFraction: Float? get() = if (phase == ScanPhase.Naming && gamesToName > 0) (gamesNamed.toFloat() / gamesToName).coerceIn(0f, 1f) else null
}

/**
 * The folder walk of the running pass has ended in every folder in [shownFolderIds]: the games are
 * in the database and the counts are whole, whatever the naming still has to do. By id, not by count:
 * the scan also walks folders setup does not show (access revoked), and they must not stand in for a shown one.
 */
internal fun walkEnded(
    report: ScanReport,
    shownFolderIds: Collection<Long>,
): Boolean = shownFolderIds.isNotEmpty() && report.walkedFolderIds.containsAll(shownFolderIds)

@Immutable
internal data class SetupUiState(
    val step: SetupStep = SetupStep.Folders,
    val folders: List<SetupFolder> = emptyList(),
    val message: SetupMessage? = null,
    /** Systems with games, biggest first. */
    val systems: List<SystemCount> = emptyList(),
    val isScanning: Boolean = false,
    /** The running scan's phase and counts. */
    val scanProgress: SetupScanProgress = SetupScanProgress(),
    /** The walk has ended and the scan is still busy: the games are being named (Ready's quiet line). A scan that only walks is not this. */
    val isNaming: Boolean = false,
    /** The scan asked for by Continue could not run (shown on the scan step only). */
    val hasScanFailed: Boolean = false,
    /** Null while it is being worked out, or when the emulators could not be checked (logged). */
    val emulators: EmulatorCoverage? = null,
    val usageStatistics: Boolean = false,
    /** The build names a statistics server: Ready asks about usage statistics. A build from source has no such question. */
    val asksAboutStatistics: Boolean = true,
    /** Push right to play; off until the person turns it on. */
    val pushRightToPlay: Boolean = false,
    /** The folders or systems could not be read (database error, logged): message and Try again. */
    val hasLoadFailed: Boolean = false,
    /** Null only while the scan runs: that step has nothing to choose. */
    val focus: SetupControl? = SetupControl.ChooseFolder,
    /** The second screen's kind while setup should ask about it; null without a screen or once decided. */
    val secondScreenKind: ScreenKind? = null,
    /**
     * This setup has the second screen step: a second screen is there and the person had not
     * decided (or answered in this setup). Without it the step and its dot do not exist.
     */
    val hasSecondScreenStep: Boolean = false,
    /** The build can fetch box art (developer credentials): the ScreenScraper step and its dot exist. */
    val hasBoxArtStep: Boolean = false,
    /**
     * The sign-in form once the person chose "Use my account" on the box art step (or the step
     * opened signed in); it holds its own focus ([SignInForm.focus]). Null while the step asks.
     */
    val boxArt: SignInForm? = null,
    /** A ScreenScraper account is stored (signed in during setup or before): the library step says box art is coming. */
    val isBoxArtComing: Boolean = false,
) {
    /** The top bar's dots: folder, the second screen when asked, box art when the build has it, library. */
    val stepDots: Int get() = 2 + (if (hasSecondScreenStep) 1 else 0) + (if (hasBoxArtStep) 1 else 0)

    /** The lit dot; the scan shares the library's. */
    val stepDotIndex: Int
        get() =
            when (step) {
                SetupStep.Folders -> 0
                SetupStep.SecondScreen -> 1
                SetupStep.BoxArt -> stepDots - 2
                SetupStep.Scan, SetupStep.Ready -> stepDots - 1
            }

    val gameCount: Int get() = systems.sumOf { it.gameCount }
    val systemCount: Int get() = systems.size
    val controls: List<SetupControl>
        get() = if (hasLoadFailed) listOf(SetupControl.TryAgain, SetupControl.SetUpLater) else controlsOf(step, folders.size, hasScanFailed, asksAboutStatistics)

    /**
     * What the input router must send: the form's typing keys while it types
     * with Plumemu's keyboard ([SignInForm.textEntry]: B erasing while its tray is up), none anywhere else.
     */
    val textEntry: TextEntry get() = if (hasLoadFailed) TextEntry.None else boxArt?.textEntry ?: TextEntry.None
}

/** The controls of a step in screen order; a tap on the n-th sends `TapItem(_, n)`. */
internal fun controlsOf(
    step: SetupStep,
    folderCount: Int,
    hasScanFailed: Boolean,
    asksAboutStatistics: Boolean = true,
): List<SetupControl> =
    when (step) {
        SetupStep.Folders ->
            if (folderCount > 0) {
                List(folderCount) { SetupControl.folderCard(it) }.filterNotNull() + listOf(SetupControl.AddFolder, SetupControl.Continue)
            } else {
                listOf(SetupControl.ChooseFolder, SetupControl.SetUpLater)
            }
        SetupStep.SecondScreen -> listOf(SetupControl.UseSecondScreen, SetupControl.SkipSecondScreen)
        // The question; the sign-in form after it has its own items and focus (account/SignInForm.kt).
        SetupStep.BoxArt -> listOf(SetupControl.UseBoxArt, SetupControl.SkipBoxArt)
        SetupStep.Scan -> if (hasScanFailed) listOf(SetupControl.TryAgain) else emptyList()
        SetupStep.Ready -> listOfNotNull(SetupControl.UsageStatistics.takeIf { asksAboutStatistics }, SetupControl.PushRightToPlay, SetupControl.StartPlaying)
    }

/**
 * The focus a step opens with: its main button, which is always last (Continue, Start playing);
 * the second screen step asks for its own ([secondScreenFocusFor]); the box art question opens on
 * Use my account, the main button on the left like the second screen's Use it.
 */
internal fun focusIn(
    controls: List<SetupControl>,
    requested: SetupControl?,
): SetupControl? =
    requested?.takeIf { it in controls }
        ?: SetupControl.UseBoxArt.takeIf { it in controls }
        ?: SetupControl.ChooseFolder.takeIf { it in controls }
        ?: controls.lastOrNull()

/** Use it for a built-in screen (on by default); Not now when Plumemu is unsure what the screen is. */
internal fun secondScreenFocusFor(kind: ScreenKind): SetupControl =
    when (kind) {
        ScreenKind.BuiltIn -> SetupControl.UseSecondScreen
        ScreenKind.Unsure -> SetupControl.SkipSecondScreen
    }

/**
 * The emulator line from the systems with games and an installed emulator found per system
 * (null = none installed). Found, not picked: the person chooses on each system's first game.
 */
internal fun emulatorCoverageOf(
    systems: List<Pair<String, Int>>,
    picks: Map<String, String?>,
    displayName: (String) -> String,
): EmulatorCoverage {
    val missing = systems.filter { (id, _) -> picks[id] == null }.sortedByDescending { it.second }.map { displayName(it.first) }
    return EmulatorCoverage(systemsWithEmulator = systems.size - missing.size, systems = systems.size, missing = missing)
}

/** `setup_step.step` of a setup step (docs/conventions/telemetry.md). */
internal fun setupStepIdOf(step: SetupStep): SetupStepId =
    when (step) {
        SetupStep.Folders -> SetupStepId.Folders
        SetupStep.SecondScreen -> SetupStepId.SecondScreen
        SetupStep.BoxArt -> SetupStepId.Artwork
        SetupStep.Scan -> SetupStepId.Scan
        SetupStep.Ready -> SetupStepId.Ready
    }
