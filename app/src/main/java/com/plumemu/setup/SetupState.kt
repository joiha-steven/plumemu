package com.plumemu.setup

import androidx.compose.runtime.Immutable
import com.plumemu.account.SignInForm
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coreinput.TextEntry
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.telemetry.SetupStepId

/**
 * First-run setup, in order: where the games are; which button confirms (only with a gamepad);
 * a look; where box art comes from; the second screen question (only on a device with one);
 * then the scan and the library ready. The scan runs behind the questions.
 */
internal enum class SetupStep {
    Folders,
    Confirm,
    Look,
    BoxArt,
    SecondScreen,
    Scan,
    Ready,
    ;

    /** The second screen's two buttons sit side by side: left / right move too (the folder step's pair: [focusTargetAfter]). */
    val hasSideBySideButtons: Boolean get() = this == SecondScreen
}

/**
 * What a person can focus in setup: one column of controls, top to bottom; a pair of buttons side by side
 * (the second screen's, Choose folder and Set up later, Try again and Set up later) also moves with left / right.
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

    /** "Use A": the confirm step's first row. */
    ConfirmKeep,

    /** "Use B": the confirm step's second row. */
    ConfirmUseB,

    /** The look step's tiles, in [SetupLooks.sets] order (a grid of [SetupLooks.COLUMNS] columns). */
    Look0,
    Look1,
    Look2,
    Look3,
    Look4,
    Look5,

    /** "Box art and game info": ScreenScraper; without a usable account it opens the sign-in form. */
    UseBoxArt,

    /** "Box art only": libretro's box fronts, no account. */
    BoxArtLibretro,

    /** "Plumemu's own covers": no network. */
    BoxArtOwnCovers,
    TryAgain,
    UsageStatistics,
    StartPlaying,
    ;

    /** The index of the look tile this is; null for every other control. */
    val lookIndex: Int? get() = if (this in lookTiles) lookTiles.indexOf(this) else null

    /** The index of the folder this card stands for; null for every other control. */
    val folderIndex: Int? get() = if (this in folderCards) folderCards.indexOf(this) else null

    companion object {
        private val folderCards =
            listOf(FolderCard0, FolderCard1, FolderCard2, FolderCard3, FolderCard4, FolderCard5, FolderCard6, FolderCard7)
        private val lookTiles = listOf(Look0, Look1, Look2, Look3, Look4, Look5)

        /** The control of the look tile at [index]. */
        fun lookTile(index: Int): SetupControl? = lookTiles.getOrNull(index)

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
    /** The build can use ScreenScraper (developer credentials): the box art step has three choices, else two. */
    val hasScreenScraper: Boolean = false,
    /**
     * The sign-in form once the person chose "Box art and game info" without a usable account (or a refused
     * one); it holds its own focus ([SignInForm.focus]). Null while the step shows its choices.
     */
    val boxArt: SignInForm? = null,
    /** The box art source in force (the stored choice, else from the account); the step marks it. Null before the first read. */
    val boxArtSource: BoxArtSourceSetting? = null,
    /** Box art will download: a ScreenScraper account is usable, or "Box art only" is chosen (what the statistics count). */
    val isBoxArtComing: Boolean = false,
    /** What Ready says about box art: only what is true now ([boxArtLineOf]). */
    val boxArtLine: BoxArtLine = BoxArtLine.None,
    /** A gamepad is connected: the confirm step exists. */
    val hasConfirmStep: Boolean = false,
    /** The confirm answer this setup stored; null on the first visit to the step. Set, the step says which button is active. */
    val confirmAnswer: ConfirmButtonSetting? = null,
    /** What this device suggests for the look step (the tile it opens on, the text size it applies). */
    val deviceLook: DeviceLook = DeviceLook.None,
) {
    /** The top bar's dots: folders, confirm with a gamepad, look, box art, the second screen when asked, library. */
    val stepDots: Int get() = 4 + (if (hasConfirmStep) 1 else 0) + (if (hasSecondScreenStep) 1 else 0)

    /** The lit dot; the scan shares the library's. */
    val stepDotIndex: Int
        get() {
            val confirm = if (hasConfirmStep) 1 else 0
            return when (step) {
                SetupStep.Folders -> 0
                SetupStep.Confirm -> 1
                SetupStep.Look -> 1 + confirm
                SetupStep.BoxArt -> 2 + confirm
                SetupStep.SecondScreen -> 3 + confirm
                SetupStep.Scan, SetupStep.Ready -> stepDots - 1
            }
        }

    /**
     * What sits above the last button of the Ready step and may change after the step opened (the emulator check
     * answers, the box art line comes, names are still filled in): a change asks the scroll to show the button again.
     */
    val aboveLastButton: Any get() = Triple(emulators, boxArtLine, isNaming)

    val gameCount: Int get() = systems.sumOf { it.gameCount }
    val systemCount: Int get() = systems.size
    val controls: List<SetupControl>
        get() = if (hasLoadFailed) listOf(SetupControl.TryAgain, SetupControl.SetUpLater) else controlsOf(step, folders.size, hasScanFailed, asksAboutStatistics, hasScreenScraper)

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
    hasScreenScraper: Boolean = true,
): List<SetupControl> =
    when (step) {
        SetupStep.Folders ->
            if (folderCount > 0) {
                List(folderCount) { SetupControl.folderCard(it) }.filterNotNull() + listOf(SetupControl.AddFolder, SetupControl.Continue)
            } else {
                listOf(SetupControl.ChooseFolder, SetupControl.SetUpLater)
            }
        SetupStep.SecondScreen -> listOf(SetupControl.UseSecondScreen, SetupControl.SkipSecondScreen)
        SetupStep.Confirm -> listOf(SetupControl.ConfirmKeep, SetupControl.ConfirmUseB)
        SetupStep.Look -> List(SetupLooks.sets.size) { SetupControl.lookTile(it) }.filterNotNull()
        // The three choices (two without ScreenScraper); the sign-in form has its own items and focus (account/SignInForm.kt).
        SetupStep.BoxArt ->
            listOfNotNull(SetupControl.UseBoxArt.takeIf { hasScreenScraper }, SetupControl.BoxArtLibretro, SetupControl.BoxArtOwnCovers)
        SetupStep.Scan -> if (hasScanFailed) listOf(SetupControl.TryAgain) else emptyList()
        SetupStep.Ready -> listOfNotNull(SetupControl.UsageStatistics.takeIf { asksAboutStatistics }, SetupControl.StartPlaying)
    }

/**
 * The focus a step opens with: its main button, which is always last (Continue, Start playing);
 * the second screen, look and box art steps ask for their own; without ScreenScraper the box art
 * step opens on "Box art only".
 */
internal fun focusIn(
    controls: List<SetupControl>,
    requested: SetupControl?,
): SetupControl? =
    requested?.takeIf { it in controls }
        ?: SetupControl.UseBoxArt.takeIf { it in controls }
        ?: SetupControl.BoxArtLibretro.takeIf { it in controls }
        ?: SetupControl.ConfirmKeep.takeIf { it in controls }
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

/** `setup_step.step` of a setup step (docs/conventions/telemetry.md); null for the confirm and look steps, which have no wire value. */
internal fun setupStepIdOf(step: SetupStep): SetupStepId? =
    when (step) {
        SetupStep.Folders -> SetupStepId.Folders
        SetupStep.Confirm, SetupStep.Look -> null
        SetupStep.SecondScreen -> SetupStepId.SecondScreen
        SetupStep.BoxArt -> SetupStepId.Artwork
        SetupStep.Scan -> SetupStepId.Scan
        SetupStep.Ready -> SetupStepId.Ready
    }

/** Ready's box art line: none, coming, waiting for a network, or an account to sign in to again. */
internal enum class BoxArtLine { None, Coming, WaitsForNetwork, SignInAgain }

/**
 * Own covers or no games: no line. A refused ScreenScraper account: sign in again (online or not). Else it comes
 * when the device is online and waits when it is not.
 */
internal fun boxArtLineOf(
    source: BoxArtSourceSetting,
    account: AccountUse,
    isOnline: Boolean,
    gameCount: Int,
): BoxArtLine =
    when {
        gameCount == 0 || source == BoxArtSourceSetting.OwnCovers -> BoxArtLine.None
        source == BoxArtSourceSetting.ScreenScraper && account == AccountUse.Refused -> BoxArtLine.SignInAgain
        source == BoxArtSourceSetting.ScreenScraper && account == AccountUse.None -> BoxArtLine.None
        isOnline -> BoxArtLine.Coming
        else -> BoxArtLine.WaitsForNetwork
    }
