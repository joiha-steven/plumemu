package com.plumemu.setup

import com.plumemu.telemetry.SetupAction
import com.plumemu.telemetry.TelemetrySink
import android.util.Log
import com.plumemu.account.AccountStatus
import com.plumemu.account.ScraperAccounts
import com.plumemu.account.SignInForm
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coredata.settings.ConfirmButtonSetting
import com.plumemu.coredata.settings.effectiveBoxArtSource
import com.plumemu.coreinput.TextEntry
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.theme.ColorSet
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * First-run setup's state and rules, one per process (`AppGraph.setup`), so leaving the app and
 * coming back keeps the step; after process death setup starts again at the folder step, with
 * the folders still granted. The scan itself belongs to `StartupWork` (one at a time) and goes on
 * when the person steps back.
 */
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
    /** The steps' times for the statistics. */
    private val times = SetupTimes(telemetry, clock)

    private val local = MutableStateFlow(SetupLocal())

    /** The stored account, so the box art step opens signed in when an account is there (the store is read only in a build that has the step). */
    private val accountStatus: StateFlow<AccountStatus> = accounts.status.stateIn(scope, SharingStarted.Eagerly, AccountStatus.SignedOut)
    private var scanWait: Job? = null

    /** An answer is being stored: a second press or tap on the same step is ignored until it lands. */
    private var isStoring = false

    /** The colour set in force, when it is one of the look tiles; the look step opens on it. */
    private var currentSet: ColorSet? = null

    /** What the look step suggests for this device, worked out once. */
    private val deviceLook = deviceLookOf(source.deviceFacts)

    /** Bumped by Try again after a read error: the database flows are collected anew. */
    private val attempts = MutableStateFlow(0)

    private var hasPressedStartPlaying = false

    /** A reopened setup: the statistics counted this person's setup at its first end. */
    private var wasCounted = false

    private val leftReopened = MutableStateFlow(false)

    val state: StateFlow<SetupUiState> =
        setupStateFlow(attempts, local, source, accountStatus, asksAboutStatistics, accounts.isAvailable, deviceLook, log)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SetupUiState())

    init {
        scope.launch { scanFoldersNeverCounted() }
        scope.launch { source.currentColorSet.collect { currentSet = it } }
        // The Scan step shows without a move into it when the second screen goes away while asked about: its wait must be alive then.
        scope.launch {
            local.map { it.step == SetupStep.SecondScreen }.distinctUntilChanged().collectLatest { isAsked ->
                if (!isAsked) return@collectLatest
                // The sources, not `state`: what `state` holds now was worked out before this step began.
                val isGone = combine(source.secondScreenQuestion, local) { question, l -> question == null && l.askedSecondScreen == null }
                isGone.catch { log("Setup could not read the second screen (${it.javaClass.simpleName})") }.firstOrNull { it } ?: return@collectLatest
                if (scanWait?.isActive != true) resumeScanWait()
            }
        }
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

    /** A folder is recorded and granted: what a reopened setup (`SetupFlags.setupQuestionsSkipped`) waits for. */
    val hasFolder: Flow<Boolean> =
        source.folders
            .map { it.isNotEmpty() }
            .catch {
                // The reopened setup shows the read error; on Home it just waits.
                log("Setup could not read the folders (${it.javaClass.simpleName})")
                emit(false)
            }

    /** A reopened setup was left and stored: it goes whatever the stored mark says, and comes back at the next start at the earliest. */
    val hasLeftReopened: StateFlow<Boolean> get() = leftReopened

    /**
     * Setup shown again after Set up later, once a folder exists: the process-death path (the folder step with its folder),
     * then Continue's own move, so the questions and the scan follow and Ready counts the games. Suspends until the folder is read.
     */
    suspend fun reopenForSkippedQuestions() {
        if (wasCounted) return // already open: a recreated Activity keeps its place
        scanWait?.cancel()
        hasPressedStartPlaying = false
        isStoring = false
        wasCounted = true
        local.value = SetupLocal()
        state.first { it.step == SetupStep.Folders && it.folders.isNotEmpty() && !it.hasLoadFailed }
        scanAndWait()
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
            onBoxArtKey(form, event, accounts, feel, onSignedIn = { name -> signedInHere(form, name) })
        }

    /**
     * A sign-in worked: signing in to ScreenScraper is choosing it (what Settings does too), but only while [form] is still
     * the open one. Left meanwhile (B, then another choice), the answer is dropped so it cannot overwrite that choice; the
     * account layer has stored the account anyway.
     */
    private fun signedInHere(
        form: SignInForm,
        name: String,
    ) {
        val l = local.value
        if (l.boxArt !== form || !l.isBoxArtFormOpen || l.step != SetupStep.BoxArt) return
        local.update { it.copy(signedInHere = name) }
        save { source.setBoxArtSource(BoxArtSourceSetting.ScreenScraper) }
    }

    /** A gamepad press or a tap, already turned into an action by core-input. */
    fun onAction(action: Action): SetupEffect? =
        feel.act {
            val now = state.value
            val form = now.boxArt
            // Back is the form's only while Plumemu's tray is up (it puts the tray away); otherwise the step's own.
            if (form != null && !now.hasLoadFailed && (action != Action.Back || form.isTrayUp)) {
                return@act onBoxArtAction(form, action, accounts, feel, onSignedIn = { name -> signedInHere(form, name) }, goOn = ::leaveBoxArtForm)
            }
            when (action) {
                is Action.Move -> {
                    if (moveFocus(now, action.direction)) feel.report(Outcome.Moved)
                    null
                }
                Action.Enter -> now.focus?.let(::activate)
                Action.Back -> {
                    if (form != null && form.signedInAs == null) {
                        closeBoxArtForm(form)
                        feel.report(Outcome.WentBack)
                    } else {
                        previousStepOf(now.step, now.hasConfirmStep, local.value.lastQuestion)?.let {
                            goBackTo(it, now)
                            feel.report(Outcome.WentBack)
                        }
                    }
                    null
                }
                is Action.TapItem -> now.controls.getOrNull(action.index)?.let { tapped(now, it) }
                is Action.LongPressItem -> now.controls.getOrNull(action.index)?.let { tapped(now, it) }
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
        val message = addPickedFolder(source, state.value.folders, treeUri, log)
        local.update { it.copy(message = message, requestedFocus = SetupControl.Continue) }
    }

    /** True when the focus goes to another control. */
    private fun moveFocus(
        now: SetupUiState,
        direction: Direction,
    ): Boolean {
        val target = focusTargetAfter(now, direction) ?: return false
        local.update { it.copy(requestedFocus = target) }
        return true
    }

    private fun focusAndActivate(control: SetupControl): SetupEffect? {
        local.update { it.copy(requestedFocus = control) }
        return activate(control)
    }

    /**
     * A tap: on a folder card that is not focused it only focuses it (its line then offers "Remove this
     * folder"), as a gamepad's first move onto it does; a tap on the focused card removes it, as A does.
     */
    private fun tapped(
        now: SetupUiState,
        control: SetupControl,
    ): SetupEffect? {
        if (control.folderIndex == null || now.focus == control) return focusAndActivate(control)
        local.update { it.copy(requestedFocus = control) }
        return null
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
            SetupControl.ConfirmKeep -> if (state.value.step == SetupStep.Confirm) chooseConfirmRow(ConfirmButtonSetting.Right)
            SetupControl.ConfirmUseB -> if (state.value.step == SetupStep.Confirm) chooseConfirmRow(ConfirmButtonSetting.Bottom)
            SetupControl.Look0, SetupControl.Look1, SetupControl.Look2, SetupControl.Look3, SetupControl.Look4, SetupControl.Look5 -> answerLook(control)
            SetupControl.UseBoxArt -> chooseScreenScraper()
            SetupControl.BoxArtLibretro -> goOnAfter(SetupStep.BoxArt) { source.setBoxArtSource(BoxArtSourceSetting.LibretroOnly) }
            SetupControl.BoxArtOwnCovers -> goOnAfter(SetupStep.BoxArt) { source.setBoxArtSource(BoxArtSourceSetting.OwnCovers) }
            SetupControl.UsageStatistics -> {
                save { source.setUsageStatistics(!state.value.usageStatistics) }
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
        local.update { it.copy(step = SetupStep.Ready, message = null, requestedFocus = SetupControl.StartPlaying, emulators = null, lastQuestion = null, skippedQuestions = true) }
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
        if (!wasCounted) times.completed(state.value)
        save {
            try {
                // A failed load left a reopened setup's questions unasked again.
                source.completeSetup(questionsSkipped = local.value.skippedQuestions || (wasCounted && leftStep != null))
                if (wasCounted) leftReopened.value = true
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
        // Try again on the scan step stays on it: only the folder step is left here, for the questions.
        val fromFolders = now.step == SetupStep.Folders
        if (fromFolders) stepLeft(SetupStep.Folders, SetupAction.Next)
        // Ask first: requestScan marks the scan busy at once, so the Scan step never shows the
        // previous scan's failure for a frame.
        source.requestScan()
        local.update {
            val to = if (fromFolders) stepAfter(SetupStep.Folders, now.hasConfirmStep, now.secondScreenKind != null) else SetupStep.Scan
            val fresh = if (fromFolders) it.copy(lastQuestion = null, askedSecondScreen = null, skippedQuestions = false) else it
            enter(fresh, to, now).copy(emulators = null, hasSkippedSecondScreen = false)
        }
        startScanWait()
        return true
    }

    /** Shows the library once the questions are answered and the walk has ended; one at a time. */
    private fun startScanWait() {
        scanWait?.cancel()
        scanWait =
            scope.launch {
                waitForTheQuestions()
                val ended = source.untilTheWalkEnds()
                if (!ended.isBusy && ended.hasFailed) {
                    local.update { it.copy(requestedFocus = SetupControl.TryAgain) }
                    return@launch
                }
                if (local.value.step == SetupStep.Scan) stepLeft(SetupStep.Scan, SetupAction.Next)
                local.update { it.copy(step = SetupStep.Ready, requestedFocus = SetupControl.StartPlaying, isOnline = source.isOnline()) }
                local.update { it.copy(emulators = source.coverageOrNull(log)) }
            }
    }

    /**
     * Into the scan after a question was answered again by Back (its wait was stopped then): waits anew, and asks
     * for a scan only when the last one failed, so a running or finished scan is left as it is.
     */
    private fun resumeScanWait() {
        if (scanWait?.isActive == true) return
        if (source.scan.value.let { !it.isBusy && it.hasFailed }) source.requestScan()
        local.update { it.copy(emulators = null) }
        startScanWait()
    }

    /** Until the questions are answered, or the second screen's skipped because the screen went away (then its dot goes too). */
    private suspend fun waitForTheQuestions() {
        // From the model's own state and the source, not [state]: that may not show the step yet.
        combine(local, source.secondScreenQuestion) { l, question -> l.step == SetupStep.Scan || (l.step == SetupStep.SecondScreen && question == null) }.first { it }
        local.update {
            if (it.step == SetupStep.SecondScreen) it.copy(step = SetupStep.Scan, requestedFocus = null, hasSkippedSecondScreen = true) else it
        }
    }

    /** Use it / Not now: stored in `second_screen.enabled`, then the scan (or the library, if it has ended). */
    private fun answerSecondScreen(isOn: Boolean) {
        if (state.value.step != SetupStep.SecondScreen) return
        stepLeft(SetupStep.SecondScreen, if (isOn) SetupAction.Next else SetupAction.Skip)
        val kind = state.value.secondScreenKind
        save { source.setSecondScreenEnabled(isOn) }
        local.update {
            it.copy(step = SetupStep.Scan, requestedFocus = null, hasAnsweredSecondScreen = true, lastQuestion = SetupStep.SecondScreen, askedSecondScreen = kind ?: it.askedSecondScreen)
        }
        resumeScanWait()
        feel.report(Outcome.Activated)
    }

    /** "Box art and game info": a usable account is the choice at once; otherwise the sign-in form, opened with the tray down on the username so Sign in and Skip are seen at once (A on a field opens the tray). */
    private fun chooseScreenScraper() {
        if (state.value.step != SetupStep.BoxArt) return
        val usable = (accountStatus.value as? AccountStatus.SignedIn)?.isRefused == false || local.value.signedInHere != null
        if (usable) {
            goOnAfter(SetupStep.BoxArt) { source.setBoxArtSource(BoxArtSourceSetting.ScreenScraper) }
            return
        }
        local.value.boxArt?.focusFirstEmpty()
        local.update { if (it.step == SetupStep.BoxArt) it.copy(isBoxArtFormOpen = true) else it }
        feel.report(Outcome.Activated)
    }

    /** Skip goes on storing nothing (`effectiveBoxArtSource`: own covers until an account is usable), as Continue after "Signed in as …"; B goes back ([closeBoxArtForm]). */
    private fun leaveBoxArtForm() {
        val form = local.value.boxArt ?: return
        form.stopTyping()
        goForward(SetupStep.BoxArt, if (form.signedInAs != null) SetupAction.Next else SetupAction.Skip)
    }

    /** The sign-in form goes back to the choices; the keyboard goes with it. */
    private fun closeBoxArtForm(form: SignInForm) {
        form.stopTyping()
        local.update { if (it.step == SetupStep.BoxArt) it.copy(isBoxArtFormOpen = false, requestedFocus = SetupControl.UseBoxArt) else it }
    }

    /**
     * A row of the confirm step. First visit: either row stores its answer and goes on. Reached again by Back: the row of the
     * stored answer goes on, the other row stores its answer and stays, so the hints change in front of the person.
     */
    private fun chooseConfirmRow(setting: ConfirmButtonSetting) {
        val stored = state.value.confirmAnswer
        if (stored == null || stored == setting) {
            goOnAfter(SetupStep.Confirm) {
                // First visit: either row is the answer and is stored; the stored row on a revisit has it already.
                if (stored == null) source.setConfirmButton(setting)
                local.update { it.copy(confirmAnswer = setting) }
            }
            return
        }
        feel.report(Outcome.Activated)
        save {
            source.setConfirmButton(setting)
            local.update { it.copy(confirmAnswer = setting) }
        }
    }

    /** A tile of the look step: its colours, and the text size this device suggests, then on. */
    private fun answerLook(control: SetupControl) {
        val set = control.lookIndex?.let(SetupLooks.sets::getOrNull) ?: return
        goOnAfter(SetupStep.Look) {
            source.applyLook(set, deviceLook.textSize)
            local.update { it.copy(pickedLook = control) }
        }
    }

    /** Stores what [write] stores, then goes on from [from]; a failed write says so and stays. */
    private fun goOnAfter(
        from: SetupStep,
        write: suspend () -> Unit,
    ) {
        if (local.value.step != from || isStoring) return
        isStoring = true
        feel.report(Outcome.Activated)
        scope.launch {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Setup could not store a setting (${e.javaClass.simpleName})")
                local.update { it.copy(message = SetupMessage.SaveFailed) }
                return@launch
            } finally {
                isStoring = false
            }
            goForward(from, SetupAction.Next)
        }
    }

    private fun goForward(
        from: SetupStep,
        action: SetupAction,
    ) {
        val now = state.value
        if (local.value.step != from) return
        stepLeft(from, action)
        val to = stepAfter(from, now.hasConfirmStep, now.secondScreenKind != null)
        local.update {
            if (it.step != from) return@update it
            val left = if (from == SetupStep.BoxArt) it.copy(lastQuestion = SetupStep.BoxArt) else it
            enter(left, to, now)
        }
        if (to == SetupStep.Scan) resumeScanWait()
    }

    private fun goBackTo(
        to: SetupStep,
        now: SetupUiState,
    ) {
        // The scan itself goes on; only its wait stops, so the library does not replace a question being answered.
        if (to == SetupStep.Folders || local.value.step == SetupStep.Scan || local.value.step == SetupStep.Ready) scanWait?.cancel()
        stepLeft(local.value.step, SetupAction.Back)
        local.update { enter(it, to, now) }
    }

    /** [l] on entering [to]: its focus, no old message, and the box art step with a fresh form and its choices. */
    private fun enter(
        l: SetupLocal,
        to: SetupStep,
        now: SetupUiState,
    ): SetupLocal {
        val focus =
            when (to) {
                SetupStep.Folders -> SetupControl.Continue
                SetupStep.Confirm -> if (l.confirmAnswer == ConfirmButtonSetting.Bottom) SetupControl.ConfirmUseB else SetupControl.ConfirmKeep
                // First visit: what the device suggests; once a look was answered: the tile picked.
                SetupStep.Look -> l.pickedLook ?: SetupControl.lookTile(SetupLooks.sets.indexOf(deviceLook.colorSet ?: currentSet).coerceAtLeast(0))
                SetupStep.BoxArt -> boxArtFocus(now)
                SetupStep.SecondScreen -> (now.secondScreenKind ?: l.askedSecondScreen)?.let(::secondScreenFocusFor)
                SetupStep.Scan, SetupStep.Ready -> null
            }
        val form = if (to == SetupStep.BoxArt) newBoxArtForm(accounts, accountStatus.value, l.signedInHere, keyboardChoice) else l.boxArt
        return l.copy(step = to, message = null, requestedFocus = focus, boxArt = form, isBoxArtFormOpen = false)
    }

    private fun stepLeft(
        step: SetupStep,
        action: SetupAction,
    ) = times.stepLeft(step, action)

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
        const val STOP_TIMEOUT_MS = 5_000L
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
