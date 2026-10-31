package com.plumemu.setup

import com.plumemu.telemetry.SetupAction
import com.plumemu.telemetry.TelemetryEvent
import com.plumemu.telemetry.TelemetrySink
import android.util.Log
import com.plumemu.account.AccountStatus
import com.plumemu.account.ScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.keyboard.KeyboardChoice
import com.plumemu.graph.BuildFeatures
import com.plumemu.graph.ScanPhase
import com.plumemu.graph.ScanReport
import com.plumemu.graph.ScanState
import com.plumemu.secondscreen.ScreenKind
import com.plumemu.sound.Feel
import com.plumemu.sound.Outcome
import com.plumemu.library.scan.AddFolderResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The settings setup shows or asks about, read together. */
private data class SetupChoices(
    val usageStatistics: Boolean,
    val pushRightToPlay: Boolean,
    /** The second screen's kind while it should be asked about; null otherwise. */
    val secondScreen: ScreenKind?,
    /** A ScreenScraper account is stored and not refused: box art will download. */
    val hasAccount: Boolean,
)

/** What came back from Android's folder picker. */
internal sealed interface PickResult {
    data class Picked(val treeUri: String) : PickResult

    /** The person backed out of the picker: nothing changes. */
    data object Cancelled : PickResult

    /** No app answers the picker intent. */
    data object NoPicker : PickResult
}

/** What the screen must do for the model: only opening the picker needs the Activity. */
internal enum class SetupEffect { OpenFolderPicker }

/**
 * First-run setup's state and rules, one per process (`AppGraph.setup`), so leaving the app and
 * coming back keeps the step; after process death setup starts again at the folder step, with
 * the folders still granted. The scan itself belongs to `StartupWork` (one at a time) and goes on
 * when the person steps back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SetupModel(
    private val scope: CoroutineScope,
    private val source: SetupSource,
    /** The ScreenScraper account; [ScraperAccounts.Unavailable] (no box art step) without developer credentials. */
    private val accounts: ScraperAccounts = ScraperAccounts.Unavailable,
    /** Usage statistics: each step left (`setup_step`) and the end (`setup_completed`); null sends nothing. */
    private val telemetry: TelemetrySink? = null,
    /** Whether Ready asks about usage statistics; false in a build with no statistics server (BuildFeatures). */
    private val asksAboutStatistics: Boolean = BuildFeatures.hasStatistics,
    /** Milliseconds for the steps' times (monotonic; setup runs in front, so deep sleep does not matter). */
    private val clock: () -> Long = { System.nanoTime() / NANOS_PER_MILLI },
    // Last: callers pass the log as a trailing lambda.
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    /** When this setup began, and when the step shown now began (statistics only). */
    private val setupSince = clock()
    private var stepSince = setupSince

    private data class Local(
        val step: SetupStep = SetupStep.Folders,
        val message: SetupMessage? = null,
        val requestedFocus: SetupControl? = null,
        val emulators: EmulatorCoverage? = null,
        /** The second screen question was answered in this setup: its dot stays. */
        val hasAnsweredSecondScreen: Boolean = false,
        /**
         * The question was skipped because the screen went away: its dot stays gone until the next
         * Continue, even if the screen comes back during that scan (it is not asked then).
         */
        val hasSkippedSecondScreen: Boolean = false,
        /** The box art step's form, made each time the step opens. */
        val boxArt: SignInForm? = null,
        /** "Use my account" was chosen (or the step opened signed in): the step shows [boxArt], not the question. */
        val isBoxArtFormOpen: Boolean = false,
        /** The name of a sign-in this setup saw succeed: the step stays signed in even before the stored status says so. */
        val signedInHere: String? = null,
        /** Folders whose removal was asked and has not landed yet (it waits for a running scan). */
        val removing: Set<Long> = emptySet(),
    )

    private val local = MutableStateFlow(Local())

    /** The stored account, so the box art step opens signed in when an account is there (the store is read only in a build that has the step). */
    private val accountStatus: StateFlow<AccountStatus> = accounts.status.stateIn(scope, SharingStarted.Eagerly, AccountStatus.SignedOut)
    private var scanWait: Job? = null

    /** Bumped by Try again after a read error: the database flows are collected anew. */
    private val attempts = MutableStateFlow(0)

    private var hasPressedStartPlaying = false

    val state: StateFlow<SetupUiState> =
        attempts
            .flatMapLatest {
                val hasAccount = accountStatus.map { (it as? AccountStatus.SignedIn)?.isRefused == false }
                val choices = combine(source.usageStatistics, source.pushRightToPlay, source.secondScreenQuestion, hasAccount, ::SetupChoices)
                combine(local, source.folders, source.systems, combine(source.scan, source.scanReport, ::Scanning), choices, ::uiState).catch { e ->
                    // Caught inside, so the outer flow lives on for Try again: an uncaught Room error
                    // would end the shared flow for the life of the process and freeze setup silently.
                    log("Setup could not read the library (${e.javaClass.simpleName})")
                    // Still driven by `local`, so the focus can move between Try again and Set up later.
                    emitAll(local.map { SetupUiState(
                        hasLoadFailed = true,
                        focus = it.requestedFocus?.takeIf { control -> control in LOAD_FAILED_CONTROLS } ?: SetupControl.TryAgain,
                        // A refused save (Set up later) must be seen, or the button looks dead.
                        message = it.message,
                    )
                })
                }
            }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupUiState())

    init {
        scope.launch { scanFoldersNeverCounted() }
    }

    private fun uiState(
        l: Local,
        folders: List<SetupFolder>,
        systems: List<SystemCount>,
        scanning: Scanning,
        choices: SetupChoices,
    ): SetupUiState {
        val scan = scanning.state
        val report = scanning.report
        val shownFolders = folders.map { it.copy(isUnavailable = it.id in scan.unavailableFolderIds, isRemoving = it.id in l.removing) }
        val scanEndedInFailure = !scan.isBusy && scan.hasFailed
        // The screen went away (or was decided elsewhere) while asked about: the scan shows at once.
        val step = if (l.step == SetupStep.SecondScreen && choices.secondScreen == null) boxArtOrScan(accounts) else l.step
        val failed = step == SetupStep.Scan && scanEndedInFailure
        val controls = controlsOf(step, shownFolders.size, failed, asksAboutStatistics)
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
            pushRightToPlay = choices.pushRightToPlay,
            focus = focusIn(controls, l.requestedFocus),
            secondScreenKind = choices.secondScreen,
            hasSecondScreenStep = !l.hasSkippedSecondScreen && (choices.secondScreen != null || l.hasAnsweredSecondScreen),
            hasBoxArtStep = accounts.isAvailable,
            boxArt = l.boxArt.takeIf { step == SetupStep.BoxArt && l.isBoxArtFormOpen },
            // Whatever button left the step: an account stored now means box art is coming.
            isBoxArtComing = accounts.isAvailable && (choices.hasAccount || l.signedInHere != null),
        )
    }

    /**
     * A folder recorded and granted but never counted (the process died during its first scan, or
     * that scan failed): scan now, so its card does not wait for a count that never comes.
     */
    private suspend fun scanFoldersNeverCounted() {
        val folders =
            try {
                source.folders.first()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The state shows the read error and its Try again; nothing to scan meanwhile.
                log("Setup could not read the folders (${e.javaClass.simpleName})")
                return
            }
        if (folders.any { !it.isCounted } && !source.scan.value.isBusy) source.requestScan()
    }

    /** Says what each action did, for the sounds (set by the screen). */
    val feel = Feel()

    /**
     * Settings › Controls › Keyboard, set by the screen before each action (plain, not state): the box
     * art step's form, made on Continue, types with it.
     */
    var keyboardChoice: KeyboardChoice = KeyboardChoice.Plumemu

    /** What the input router must send now: the form's typing keys (Plumemu's keyboard), else none. */
    val textEntry: TextEntry get() = state.value.textEntry

    /** A tap on a key of Plumemu's tray over the box art step's form. */
    fun onTrayKey(event: KeyboardEvent) =
        feel.act {
            val now = state.value
            val form = now.boxArt ?: return@act
            if (now.hasLoadFailed) return@act
            onBoxArtKey(form, event, accounts, feel, onSignedIn = ::signedInHere)
        }

    private fun signedInHere(name: String) = local.update { it.copy(signedInHere = name) }

    /** A gamepad press or a tap, already turned into an action by core-input. */
    fun onAction(action: Action): SetupEffect? =
        feel.act {
            val now = state.value
            val form = now.boxArt
            // Back is the form's only while Plumemu's tray is up (it puts the tray away); otherwise the step's own.
            if (form != null && !now.hasLoadFailed && (action != Action.Back || form.isTrayUp)) {
                return@act onBoxArtAction(form, action, accounts, feel, onSignedIn = { name -> local.update { it.copy(signedInHere = name) } }) { leaveBoxArt(SetupAction.Next) }
            }
            when (action) {
                is Action.Move -> {
                    if (moveFocus(now, action.direction)) feel.report(Outcome.Moved)
                    null
                }
                Action.Enter -> now.focus?.let(::activate)
                Action.Back -> {
                    if (form != null && form.signedInAs == null) {
                        // The sign-in form goes back to its question; the keyboard goes with the form.
                        // Once signed in (here or before setup) the question is not asked again: Back goes to the folders.
                        form.stopTyping()
                        local.update { if (it.step == SetupStep.BoxArt) it.copy(isBoxArtFormOpen = false, requestedFocus = SetupControl.UseBoxArt) else it }
                        feel.report(Outcome.WentBack)
                    } else if (now.step != SetupStep.Folders) {
                        backToFolders()
                        feel.report(Outcome.WentBack)
                    }
                    null
                }
                is Action.TapItem -> now.controls.getOrNull(action.index)?.let(::focusAndActivate)
                is Action.LongPressItem -> now.controls.getOrNull(action.index)?.let(::focusAndActivate)
                else -> null
            }
        }

    fun onPicked(result: PickResult) {
        when (result) {
            PickResult.Cancelled -> Unit
            PickResult.NoPicker -> local.update { it.copy(message = SetupMessage.NoFolderPicker) }
            is PickResult.Picked -> scope.launch { addPicked(result.treeUri) }
        }
    }

    private suspend fun addPicked(treeUri: String) {
        val before = state.value.folders
        val message =
            try {
                when (val added = source.addFolder(treeUri)) {
                    is AddFolderResult.Added -> {
                        // Counting starts at once: the card fills in while the person decides.
                        source.requestScan()
                        null
                    }
                    is AddFolderResult.AlreadyAdded -> {
                        val card = before.firstOrNull { it.id == added.folderId }
                        if (card != null && !card.isUnavailable) {
                            SetupMessage.FolderAlreadyAdded
                        } else {
                            // Picked again as its card asked (unreadable, or its grant was revoked
                            // and is held again now): read it again instead of "already there".
                            source.requestScan()
                            null
                        }
                    }
                    AddFolderResult.PermissionNotGranted -> SetupMessage.FolderNotKept
                    AddFolderResult.NotRecorded -> SetupMessage.FolderCouldNotOpen
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Class only: provider exceptions carry the folder's URI.
                log("Picked folder could not be added (${e.javaClass.simpleName})")
                SetupMessage.FolderCouldNotOpen
            }
        local.update { it.copy(message = message, requestedFocus = SetupControl.Continue) }
    }

    /** True when the focus goes to another control. */
    private fun moveFocus(
        now: SetupUiState,
        direction: Direction,
    ): Boolean {
        val controls = now.controls
        val at = controls.indexOf(now.focus)
        if (at < 0) return false
        val next =
            when (direction) {
                Direction.Up -> at - 1
                Direction.Down -> at + 1
                // The second screen's and the box art question's buttons sit side by side; elsewhere setup is one column.
                Direction.Left -> if (now.step.hasSideBySideButtons || now.hasLoadFailed) at - 1 else return false
                Direction.Right -> if (now.step.hasSideBySideButtons || now.hasLoadFailed) at + 1 else return false
            }
        val target = controls.getOrNull(next) ?: return false
        local.update { it.copy(requestedFocus = target) }
        return true
    }

    private fun focusAndActivate(control: SetupControl): SetupEffect? {
        local.update { it.copy(requestedFocus = control) }
        return activate(control)
    }

    private fun activate(control: SetupControl): SetupEffect? {
        when (control) {
            SetupControl.ChooseFolder, SetupControl.AddFolder -> {
                feel.report(Outcome.Activated)
                return SetupEffect.OpenFolderPicker
            }
            SetupControl.SetUpLater -> if (state.value.hasLoadFailed) leaveSetupUnread() else setUpLater()
            SetupControl.FolderCard0, SetupControl.FolderCard1, SetupControl.FolderCard2, SetupControl.FolderCard3,
            SetupControl.FolderCard4, SetupControl.FolderCard5, SetupControl.FolderCard6, SetupControl.FolderCard7,
            -> removeFolderOf(control)
            SetupControl.TryAgain ->
                if (state.value.hasLoadFailed) {
                    attempts.value++
                    feel.report(Outcome.Activated)
                } else if (scanAndWait()) {
                    feel.report(Outcome.Activated)
                }
            SetupControl.Continue -> if (scanAndWait()) feel.report(Outcome.Activated)
            SetupControl.UseSecondScreen -> answerSecondScreen(isOn = true)
            SetupControl.SkipSecondScreen -> answerSecondScreen(isOn = false)
            SetupControl.UseBoxArt -> openBoxArtForm()
            SetupControl.SkipBoxArt -> {
                if (state.value.step == SetupStep.BoxArt) {
                    leaveBoxArt(SetupAction.Skip)
                    feel.report(Outcome.Activated)
                }
            }
            SetupControl.UsageStatistics -> {
                save { source.setUsageStatistics(!state.value.usageStatistics) }
                feel.report(Outcome.Activated)
            }
            SetupControl.PushRightToPlay -> {
                save { source.setPushRightToPlay(!state.value.pushRightToPlay) }
                feel.report(Outcome.Activated)
            }
            SetupControl.StartPlaying -> finishSetup()
        }
        return null
    }

    /** No folder yet: setup goes to its last step (statistics, one hand) and the empty home asks for a folder. */
    private fun setUpLater() {
        val now = state.value
        if (now.step != SetupStep.Folders || now.folders.isNotEmpty()) return
        stepLeft(SetupStep.Folders, SetupAction.Skip)
        local.update { it.copy(step = SetupStep.Ready, message = null, requestedFocus = SetupControl.StartPlaying, emulators = null) }
        feel.report(Outcome.Activated)
    }

    /**
     * Set up later when the library could not be read: there is no Ready step to show (it needs the library),
     * so setup is left at once, as Set up later followed by Start playing would.
     */
    private fun leaveSetupUnread() = finishSetup(leftStep = local.value.step)

    /** Stores that setup is done. The screen goes a moment after the first press; a second one must not count or store it again. */
    private fun finishSetup(leftStep: SetupStep? = null) {
        if (hasPressedStartPlaying) return
        hasPressedStartPlaying = true
        // Set up later on a failed load: the step the failure happened on is recorded as skipped, once.
        leftStep?.let { stepLeft(it, SetupAction.Skip) }
        setupCompleted(state.value)
        save {
            try {
                source.completeSetup()
            } catch (e: Exception) {
                hasPressedStartPlaying = false // stored nothing: the message shows, and it may be pressed again
                throw e
            }
        }
        feel.report(Outcome.Activated)
    }

    /** A or a tap on a folder's card: the folder goes (a wrongly picked one). */
    private fun removeFolderOf(control: SetupControl) {
        val folder = control.folderIndex?.let { state.value.folders.getOrNull(it) } ?: return
        // A second A while the removal waits for a running scan does nothing.
        if (folder.isRemoving || folder.id in local.value.removing) return
        feel.report(Outcome.Activated)
        local.update { it.copy(removing = it.removing + folder.id) }
        scope.launch {
            val removed =
                try {
                    source.removeFolder(folder.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Class only: provider exceptions carry the folder's URI.
                    log("Folder could not be removed (${e.javaClass.simpleName})")
                    false
                }
            val remaining = state.value.folders.count { it.id != folder.id }
            local.update {
                it.copy(
                    removing = it.removing - folder.id,
                    message = if (removed) null else SetupMessage.FolderCouldNotRemove,
                    requestedFocus = if (remaining == 0) SetupControl.ChooseFolder else SetupControl.Continue,
                )
            }
        }
    }

    /**
     * The scan step: one more scan (cheap after the first), then the library once it has ended.
     * With a second screen not decided yet, its question comes first while the scan runs.
     */
    private fun scanAndWait(): Boolean {
        val now = state.value
        if (now.folders.isEmpty()) return false
        // A folder being removed would be scanned as it goes: Continue waits until the removal lands.
        if (now.folders.any { it.isRemoving }) return false
        // Try again on the scan step stays on it: only the folder step is left here.
        if (now.step == SetupStep.Folders) stepLeft(SetupStep.Folders, SetupAction.Next)
        // Ask first: requestScan marks the scan busy at once, so the Scan step never shows the
        // previous scan's failure for a frame.
        source.requestScan()
        val question = now.secondScreenKind
        val step = if (question != null) SetupStep.SecondScreen else boxArtOrScan(accounts)
        val focus = question?.let(::secondScreenFocusFor)
        val status = accountStatus.value
        val form = newBoxArtForm(accounts, status, local.value.signedInHere, keyboardChoice)
        // A stored account needs no question: the step opens on "Signed in as …" and Continue, or,
        // when its password was refused, on the form with the name filled in (B: the question).
        val opensOnForm = opensOnTheForm(form, status)
        local.update {
            it.copy(
                step = step,
                message = null,
                requestedFocus = focus,
                emulators = null,
                hasSkippedSecondScreen = false,
                boxArt = form,
                isBoxArtFormOpen = opensOnForm,
            )
        }
        scanWait?.cancel()
        scanWait =
            scope.launch {
                if (question != null) waitForTheSecondScreenAnswer()
                // Signed in or skipped: the scan's own step (or the library, if it has ended) comes next.
                local.first { it.step != SetupStep.BoxArt }
                val ended = source.untilTheWalkEnds()
                if (!ended.isBusy && ended.hasFailed) {
                    local.update { it.copy(requestedFocus = SetupControl.TryAgain) }
                    return@launch
                }
                if (local.value.step == SetupStep.Scan) stepLeft(SetupStep.Scan, SetupAction.Next)
                local.update { it.copy(step = SetupStep.Ready, requestedFocus = SetupControl.StartPlaying) }
                val coverage =
                    try {
                        source.emulatorCoverage()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        log("Emulators could not be checked (${e.javaClass.simpleName})")
                        null
                    }
                local.update { it.copy(emulators = coverage) }
            }
        return true
    }

    /** Until the question is answered, or skipped because the screen went away (then its dot goes too). */
    private suspend fun waitForTheSecondScreenAnswer() {
        // From the model's own state and the source, not [state]: that may not show the step yet.
        combine(local, source.secondScreenQuestion) { l, question -> l.step != SetupStep.SecondScreen || question == null }.first { it }
        local.update {
            if (it.step == SetupStep.SecondScreen) it.copy(step = boxArtOrScan(accounts), requestedFocus = null, hasSkippedSecondScreen = true) else it
        }
    }

    /** Use it / Not now: stored in `second_screen.enabled`, then the scan (or the library, if it has ended). */
    private fun answerSecondScreen(isOn: Boolean) {
        if (state.value.step != SetupStep.SecondScreen) return
        stepLeft(SetupStep.SecondScreen, if (isOn) SetupAction.Next else SetupAction.Skip)
        save { source.setSecondScreenEnabled(isOn) }
        local.update { it.copy(step = boxArtOrScan(accounts), requestedFocus = null, hasAnsweredSecondScreen = true) }
        feel.report(Outcome.Activated)
    }

    /** "Use my account": the sign-in form, its cursor on the username and the keyboard down. */
    private fun openBoxArtForm() {
        if (state.value.step != SetupStep.BoxArt) return
        local.update { if (it.step == SetupStep.BoxArt) it.copy(isBoxArtFormOpen = true) else it }
        feel.report(Outcome.Activated)
    }

    /** Not now, Skip or Continue: the scan's step (or the library, if the scan has ended). */
    private fun leaveBoxArt(action: SetupAction) {
        if (local.value.step == SetupStep.BoxArt) stepLeft(SetupStep.BoxArt, action)
        local.update { if (it.step == SetupStep.BoxArt) it.copy(step = SetupStep.Scan, requestedFocus = null) else it }
    }

    private fun backToFolders() {
        scanWait?.cancel()
        stepLeft(local.value.step, SetupAction.Back)
        local.update { it.copy(step = SetupStep.Folders, requestedFocus = SetupControl.Continue, message = null) }
    }

    /** `setup_step`: [step] was left by [action] after the time since the last step was left. */
    private fun stepLeft(
        step: SetupStep,
        action: SetupAction,
    ) {
        val now = clock()
        val seconds = ((now - stepSince) / MILLIS_PER_SECOND).coerceAtLeast(0)
        stepSince = now
        telemetry?.track(TelemetryEvent.SetupStep(setupStepIdOf(step), seconds, action))
    }

    /** Start playing: the last step and `setup_completed` (counts only, never a folder's or a system's name). */
    private fun setupCompleted(now: SetupUiState) {
        stepLeft(SetupStep.Ready, SetupAction.Next)
        val seconds = ((clock() - setupSince) / MILLIS_PER_SECOND).coerceAtLeast(0)
        telemetry?.track(
            TelemetryEvent.SetupCompleted(
                seconds = seconds,
                artwork = now.isBoxArtComing,
                folders = now.folders.size,
                systemsFound = now.systems.size,
                emulatorsPicked = now.emulators?.systemsWithEmulator ?: 0,
            ),
        )
    }

    private fun save(write: suspend () -> Unit) {
        scope.launch {
            try {
                write()
                local.update { it.copy(message = null) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Setup could not store a setting (${e.javaClass.simpleName})")
                local.update { it.copy(message = SetupMessage.SaveFailed) }
            }
        }
    }

    private companion object {
        const val TAG = "PlumSetup"
        val LOAD_FAILED_CONTROLS = listOf(SetupControl.TryAgain, SetupControl.SetUpLater)
        const val STOP_TIMEOUT_MS = 5_000L
        const val MILLIS_PER_SECOND = 1_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
