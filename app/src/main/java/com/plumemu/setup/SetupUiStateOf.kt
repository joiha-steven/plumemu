package com.plumemu.setup

import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.effectiveBoxArtSource
import com.plumemu.account.AccountStatus
import com.plumemu.graph.ScanPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** What [SetupModel] shows: its own state, the library and the stored choices put together. */
internal fun setupUiStateOf(
    l: SetupLocal,
    folders: List<SetupFolder>,
    systems: List<SystemCount>,
    scanning: Scanning,
    choices: SetupChoices,
    asksAboutStatistics: Boolean,
    hasScreenScraper: Boolean,
    deviceLook: DeviceLook,
): SetupUiState {
    val scan = scanning.state
    val report = scanning.report
    val shownFolders = folders.map { it.copy(isUnavailable = it.id in scan.unavailableFolderIds, isRemoving = it.id in l.removing) }
    val scanEndedInFailure = !scan.isBusy && scan.hasFailed
    // The screen went away (or was decided elsewhere) while asked about: the scan shows at once.
    // A question answered shows again when Back comes to it (its kind is kept), so it is not "gone".
    val step = if (l.step == SetupStep.SecondScreen && choices.secondScreen == null && l.askedSecondScreen == null) SetupStep.Scan else l.step
    val failed = step == SetupStep.Scan && scanEndedInFailure
    val controls = controlsOf(step, shownFolders.size, failed, asksAboutStatistics, hasScreenScraper)
    val boxArtSource = effectiveBoxArtSource(choices.boxArtSource, choices.hasAccount || l.signedInHere != null)
    // A scan started from the folder step (a folder was added) that failed says so there too.
    val scanMessage = if (step == SetupStep.Folders && scanEndedInFailure) SetupMessage.ScanFailed else null
    return SetupUiState(
        step = step,
        folders = shownFolders,
        message = l.message ?: scanMessage,
        systems = systems,
        isScanning = scan.isBusy,
        scanProgress = SetupScanProgress(report.phase, report.gamesNamed, report.gamesToName),
        isNaming = scan.isBusy && (report.phase == ScanPhase.Naming || walkEnded(report, folders.map { it.id })),
        hasScanFailed = failed,
        emulators = l.emulators,
        usageStatistics = choices.usageStatistics,
        asksAboutStatistics = asksAboutStatistics,
        focus = focusIn(controls, l.requestedFocus),
        secondScreenKind = choices.secondScreen,
        hasSecondScreenStep = !l.hasSkippedSecondScreen && (choices.secondScreen != null || l.hasAnsweredSecondScreen),
        hasScreenScraper = hasScreenScraper,
        boxArt = l.boxArt.takeIf { step == SetupStep.BoxArt && l.isBoxArtFormOpen },
        boxArtSource = boxArtSource,
        // Whichever choice was made: box art downloads unless Plumemu's own covers are in force.
        isBoxArtComing = boxArtSource != BoxArtSourceSetting.OwnCovers,
        boxArtLine = boxArtLineOf(boxArtSource, if (l.signedInHere != null) AccountUse.Usable else choices.account, l.isOnline, systems.sumOf { it.gameCount }),
        hasConfirmStep = choices.hasGamepad,
        confirmAnswer = l.confirmAnswer,
        deviceLook = deviceLook,
    )
}

/** The controls of the library-read failure screen. */
private val LOAD_FAILED_CONTROLS = listOf(SetupControl.TryAgain, SetupControl.SetUpLater)

/** What [SetupModel] shows, collected anew for each of [attempts] (Try again after a read error). */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun setupStateFlow(
    attempts: Flow<Int>,
    local: Flow<SetupLocal>,
    source: SetupSource,
    accountStatus: Flow<AccountStatus>,
    asksAboutStatistics: Boolean,
    hasScreenScraper: Boolean,
    deviceLook: DeviceLook,
    log: (String) -> Unit,
): Flow<SetupUiState> =
    attempts.flatMapLatest {
        val hasAccount =
            accountStatus.map {
                when {
                    it !is AccountStatus.SignedIn -> AccountUse.None
                    it.isRefused -> AccountUse.Refused
                    else -> AccountUse.Usable
                }
            }
        val choices = combine(source.usageStatistics, source.secondScreenQuestion, hasAccount, source.boxArtSource, source.hasGamepad, ::SetupChoices)
        combine(local, source.folders, source.systems, combine(source.scan, source.scanReport, ::Scanning), choices) { l, f, s, sc, c -> setupUiStateOf(l, f, s, sc, c, asksAboutStatistics, hasScreenScraper, deviceLook) }.catch { e ->
            // Caught inside, so the outer flow lives on for Try again: an uncaught Room error
            // would end the shared flow for the life of the process and freeze setup silently.
            log("Setup could not read the library (${e.javaClass.simpleName})")
            // Still driven by `local`, so the focus can move between Try again and Set up later.
            emitAll(
                local.map {
                    SetupUiState(
                        hasLoadFailed = true,
                        focus = it.requestedFocus?.takeIf { control -> control in LOAD_FAILED_CONTROLS } ?: SetupControl.TryAgain,
                        // A refused save (Set up later) must be seen, or the button looks dead.
                        message = it.message,
                    )
                },
            )
        }
    }
