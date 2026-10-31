package com.plumemu.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Where an update the person asked for is. */
sealed interface UpdateStep {
    data object Idle : UpdateStep

    /** On a metered network: the download waits for the person's yes. */
    data object AskMetered : UpdateStep

    data class Downloading(val done: Long, val total: Long) : UpdateStep

    data object Verifying : UpdateStep

    /** Android 8+: Plumemu may not install apps yet; continues when the person comes back with it allowed. */
    data object NeedsPermission : UpdateStep

    /** As [NeedsPermission], but this device has no Install unknown apps screen to open; the person allows it in Android's settings. */
    data object NoPermissionScreen : UpdateStep

    /** Checked and waiting for Plumemu to be in front again before Android's screen is shown. */
    data object ReadyToInstall : UpdateStep

    /** Handed to Android; [askedPerson] once Android's confirm screen was shown. */
    data class Installing(val askedPerson: Boolean = false) : UpdateStep

    data object Cancelled : UpdateStep

    /** The person said no on Android's screen. */
    data object InstallAborted : UpdateStep

    data class Failed(val failure: UpdateFailure) : UpdateStep
}

/** What Settings › About shows. [isChecking] is a check of either kind running. */
data class UpdateView(
    val lastCheck: LastCheck? = null,
    val found: UpdateManifest? = null,
    val isChecking: Boolean = false,
    val step: UpdateStep = UpdateStep.Idle,
) {
    /** An update is downloading, being checked or handed to Android: no check runs meanwhile. */
    val isUpdateRunning: Boolean
        get() = step is UpdateStep.Downloading || step == UpdateStep.Verifying || step == UpdateStep.ReadyToInstall || (step is UpdateStep.Installing && !step.askedPerson)

    /** Waiting for the person to allow installs (either way). */
    val isWaitingForPermission: Boolean get() = step == UpdateStep.NeedsPermission || step == UpdateStep.NoPermissionScreen
}

/**
 * In-app updates. Checks run only from [onFront] (once a day, by the rules
 * of [UpdateGate]) and [checkNow] (the person's); never on a timer. An update runs only
 * when the person asks ([startUpdate]): download, size and SHA-256, the APK checks, then Android's
 * installer. One check and one update at a time; a check never runs during an update.
 *
 * [wallClock] is `System.currentTimeMillis`: the daily check's time must survive a reboot. Every
 * failure is logged in one line and shown as an [UpdateStep] or a [CheckOutcome]; nothing throws
 * out of here.
 */
class Updates(
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val checkDaily: suspend () -> Boolean,
    private val network: () -> NetworkKind,
    private val http: UpdateHttp,
    private val manifestUrl: String,
    /** The running app's versionCode, read from Android once, off the main thread. */
    private val installedVersionCode: () -> Long,
    private val deviceSdk: Int,
    private val store: UpdateStore,
    private val downloader: ApkDownloader,
    private val inspector: ApkInspector,
    private val installer: UpdateInstaller,
    private val wallClock: () -> Long,
    /** Told each check that ended ([manual]: Settings' Check for updates), for the statistics (`update_check`). */
    private val onChecked: (manual: Boolean, outcome: CheckOutcome) -> Unit = { _, _ -> },
    private val info: (String) -> Unit,
    private val log: (String) -> Unit,
) {
    private val lock = Any()
    private val mutableView = MutableStateFlow(UpdateView())
    val view: StateFlow<UpdateView> = mutableView.asStateFlow()
    private val mutableHasUpdate = MutableStateFlow(false)

    /** A check found a release newer than this one (the main screen's dot on START Settings). */
    val hasUpdate: StateFlow<Boolean> = mutableHasUpdate.asStateFlow()

    private var record: UpdateRecord? = null
    private var installed: Long = 0
    private var checkJob: Job? = null
    private var updateJob: Job? = null
    /** Told by [onFront] / [onStop] (StartupWork reports the front only while Plumemu is in it). Until then nothing is installed. */
    private var isInFront = false
    private var readyFile: File? = null

    /** Android's confirm screen that came while Plumemu was not in front; shown at the next [onFront]. */
    private var pendingConfirm: (() -> Boolean)? = null

    /** Reads the stored record once (off the main thread); an update that is now installed is forgotten. */
    private suspend fun loaded(): UpdateRecord {
        synchronized(lock) { record }?.let { return it }
        val (read, code) = withContext(io) { store.read() to installedVersionCode() }
        synchronized(lock) { installed = code }
        val fresh =
            if (read.found != null && read.found.versionCode <= code) {
                info("Update ${read.found.versionName} is installed; forgetting it")
                read.copy(found = null, lastCheck = read.lastCheck?.copy(outcome = CheckOutcome.UpToDate))
            } else {
                read
            }
        synchronized(lock) {
            if (record == null) record = fresh
        }
        if (fresh != read) save(fresh)
        withContext(io) { downloader.clearLeftovers() }
        publish()
        return synchronized(lock) { checkNotNull(record) { "the record was set above, before it was published" } }
    }

    /** Plumemu came to the front: the daily check if it is due, or an update waiting for the permission or for the front. */
    fun onFront(): Job {
        val confirm = synchronized(lock) { isInFront = true; pendingConfirm.also { pendingConfirm = null } }
        if (confirm != null) showConfirm(confirm)
        return scope.launch {
            loaded()
            val view = mutableView.value
            when {
                view.isWaitingForPermission -> if (installer.canRequestInstalls()) resumeAfterPermission()
                view.step == UpdateStep.ReadyToInstall -> installReady()
            }
            val stored = synchronized(lock) { record } ?: return@launch
            val due = UpdateGate.isDailyCheckDue(checkDaily(), network(), stored.lastSuccessAt, wallClock(), stored.lastAutoFailureAt)
            if (due) runCheck(manual = false)
        }
    }

    /**
     * Plumemu left the front (a game, the screen off): nothing new is shown over another app. Left
     * during an install Android finishes alone (no confirm screen), Plumemu does not open itself
     * after it, which could cover a game the person started.
     */
    fun onStop() {
        val silentInstall =
            synchronized(lock) {
                isInFront = false
                mutableView.value.step.let { it is UpdateStep.Installing && !it.askedPerson }
            }
        if (silentInstall) forgetReopen("Plumemu left the front during the install")
    }

    private fun forgetReopen(why: String) {
        info("Update: $why; no reopen after it")
        try {
            installer.forgetReopen()
        } catch (e: RuntimeException) {
            log("Update: reopen marker not cleared (${e.javaClass.simpleName})")
        }
    }

    /** Settings › About › Check for updates: ignores the switch and the 24 h, still needs a network. */
    fun checkNow(): Job =
        scope.launch {
            loaded()
            // The check row says "An update is downloading." meanwhile (UpdateView.isUpdateRunning).
            if (synchronized(lock) { updateJob?.isActive == true } || mutableView.value.isUpdateRunning) {
                info("Update check: not while an update runs")
                return@launch
            }
            if (!UpdateGate.canCheckNow(network())) {
                info("Update check: no network")
                finishCheck(CheckOutcome.Failed(CheckFailure.Offline), keepFound, success = false, automatic = false)
                return@launch
            }
            runCheck(manual = true)
        }

    private suspend fun runCheck(manual: Boolean) {
        // A build from source names no manifest: it never asks anyone (the About rows are hidden too).
        if (manifestUrl.isBlank()) {
            info("Update check: this build has no manifest address")
            return
        }
        val job: Job
        synchronized(lock) {
            if (checkJob?.isActive == true || updateJob?.isActive == true) return
            mutableView.update { it.copy(isChecking = true) }
            job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) { check(manual) }
            checkJob = job
        }
        job.start()
        job.join()
    }

    private suspend fun check(manual: Boolean) {
        val label = if (manual) "manual" else "daily"
        try {
            val text = http.text(manifestUrl, UpdateManifests.MAX_MANIFEST_BYTES)
            val verdict = UpdateManifests.read(text, manifestUrl, synchronized(lock) { installed }, deviceSdk)
            info("Update check ($label): ${verdict.reason}")
            when (verdict) {
                is ManifestVerdict.Newer -> finishCheck(CheckOutcome.Found, verdict.manifest, success = true, automatic = !manual)
                is ManifestVerdict.UpToDate -> finishCheck(CheckOutcome.UpToDate, null, success = true, automatic = !manual)
                is ManifestVerdict.NeedsNewerAndroid -> finishCheck(CheckOutcome.NeedsNewerAndroid, null, success = true, automatic = !manual)
                is ManifestVerdict.Rejected -> finishCheck(CheckOutcome.Failed(CheckFailure.BadManifest), keepFound, success = false, automatic = !manual)
            }
        } catch (e: UpdateNetException) {
            log("Update check ($label) failed: ${e.message}")
            val failure =
                when (e.failure) {
                    NetFailure.Offline -> CheckFailure.Offline
                    NetFailure.Timeout -> CheckFailure.Timeout
                    NetFailure.Http -> CheckFailure.Http
                }
            finishCheck(CheckOutcome.Failed(failure), keepFound, success = false, automatic = !manual)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Not a network failure: said as "The update check failed.", the class in the log.
            log("Update check ($label) failed unexpectedly (${e.javaClass.simpleName})")
            finishCheck(CheckOutcome.Failed(CheckFailure.Unexpected), keepFound, success = false, automatic = !manual)
        } finally {
            mutableView.update { it.copy(isChecking = false) }
        }
    }

    /** A failed check leaves what an earlier check found. */
    private val keepFound: UpdateManifest? get() = synchronized(lock) { record?.found }

    /**
     * Stores the check; only a [success] moves the time the daily check counts from, and clears the
     * failure an [automatic] check left; an automatic failure delays the next automatic try.
     */
    private suspend fun finishCheck(
        outcome: CheckOutcome,
        found: UpdateManifest?,
        success: Boolean,
        automatic: Boolean,
    ) {
        val now = wallClock()
        val next =
            synchronized(lock) {
                val before = record ?: UpdateRecord()
                before.copy(
                    lastSuccessAt = if (success) now else before.lastSuccessAt,
                    lastAutoFailureAt =
                        when {
                            success -> null
                            automatic -> now
                            else -> before.lastAutoFailureAt
                        },
                    lastCheck = LastCheck(now, outcome),
                    found = found,
                ).also { record = it }
            }
        save(next)
        publish()
        onChecked(!automatic, outcome)
    }

    private suspend fun save(next: UpdateRecord) {
        try {
            withContext(io) { store.write(next) }
        } catch (e: IOException) {
            // Shown from memory for this process; the next start checks again.
            log("Update record not saved (${e.javaClass.simpleName})")
        }
    }

    private fun publish() =
        synchronized(lock) {
            val r = record ?: return
            mutableView.update { it.copy(lastCheck = r.lastCheck, found = r.found) }
            mutableHasUpdate.value = r.found != null
        }

    /**
     * The person's Update. On a metered network the first press asks ([UpdateStep.AskMetered]) and
     * [allowMetered] is the yes. Without the install permission (Android 8+) it stops at
     * [UpdateStep.NeedsPermission] before downloading anything.
     */
    fun startUpdate(allowMetered: Boolean = false): Job? {
        val manifest = synchronized(lock) { record?.found } ?: return null
        // Also after Android's screen was shown: Android 6 says nothing when its dialog is cancelled.
        synchronized(lock) {
            if (updateJob?.isActive == true || checkJob?.isActive == true) return null
        }
        when (network()) {
            NetworkKind.None -> return failed(UpdateFailure.Offline, "no network").let { null }
            NetworkKind.Metered -> if (!allowMetered) return setStep(UpdateStep.AskMetered).let { null }
            NetworkKind.Unmetered, NetworkKind.Wifi -> Unit
        }
        if (!installer.canRequestInstalls()) {
            info("Update: waiting for Install unknown apps")
            setStep(UpdateStep.NeedsPermission)
            return null
        }
        return launchUpdate(manifest)
    }

    /**
     * The person's "Open Android settings" on the permission line: Android's screen; the update goes
     * on when they come back ([onFront]). A device without that screen gets a line saying where to
     * allow it instead ([UpdateStep.NoPermissionScreen]).
     */
    fun openInstallPermission(): Boolean {
        val opened = installer.openInstallPermission()
        if (!opened) {
            log("Update: no Install unknown apps screen on this device")
            setStep(UpdateStep.NoPermissionScreen)
        }
        return opened
    }

    /** Back with installs allowed: on as if Update were pressed again (the network and a running job are checked again). */
    private fun resumeAfterPermission() {
        if (synchronized(lock) { record?.found } == null) return setStep(UpdateStep.Idle)
        info("Update: Install unknown apps allowed, going on")
        startUpdate(allowMetered = false)
    }

    /** A cancelled download may still be stopping: the new one waits for it, so two never share a file or a step. */
    private fun launchUpdate(manifest: UpdateManifest): Job =
        synchronized(lock) {
            val previous = updateJob
            // Shown at once, also while a cancelled download still ends (the join below).
            setStep(UpdateStep.Downloading(0, manifest.size))
            scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
                previous?.join()
                download(manifest)
            }.also { updateJob = it }
        }.also { it.start() }

    private suspend fun download(manifest: UpdateManifest) {
        setStep(UpdateStep.Downloading(0, manifest.size))
        val result =
            try {
                downloader.download(manifest) { done, total -> setStep(UpdateStep.Downloading(done, total)) }
            } catch (e: CancellationException) {
                // Not over a newer press's download, which is already showing.
                val self = currentCoroutineContext()[Job]
                if (synchronized(lock) { updateJob === self }) setStep(UpdateStep.Cancelled)
                throw e
            }
        when (result) {
            is DownloadResult.Failed -> return failed(result.failure, result.detail)
            is DownloadResult.Done -> verify(manifest, result.file)
        }
    }

    private suspend fun verify(
        manifest: UpdateManifest,
        file: File,
    ) {
        setStep(UpdateStep.Verifying)
        val refusal =
            withContext(io) {
                try {
                    ApkChecks.refusal(inspector.archive(file), inspector.installed(), manifest)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: RuntimeException) {
                    UpdateFailure.NotAnApk to "inspect: ${e.javaClass.simpleName}"
                }
            }
        if (refusal != null) {
            withContext(io) { file.delete() }
            return failed(refusal.first, refusal.second)
        }
        info("Update ${manifest.versionName}: size, SHA-256, package, version and certificate checked")
        val inFront = synchronized(lock) { isInFront.also { if (!it) readyFile = file } }
        if (inFront) install(file, manifest.versionCode) else setStep(UpdateStep.ReadyToInstall)
    }

    private suspend fun installReady() {
        // onFront launched this; Plumemu may have left again before it ran.
        val file =
            synchronized(lock) {
                if (!isInFront) return
                readyFile.also { readyFile = null }
            } ?: return setStep(UpdateStep.Idle)
        val manifest = synchronized(lock) { record?.found } ?: return setStep(UpdateStep.Idle)
        install(file, manifest.versionCode)
    }

    private suspend fun install(
        file: File,
        versionCode: Long,
    ) {
        setStep(UpdateStep.Installing())
        try {
            withContext(io) { installer.commit(file, versionCode) }
            // Left while the session was written: onStop ran before the marker existed.
            if (!synchronized(lock) { isInFront }) forgetReopen("Plumemu left the front while the install was handed over")
        } catch (e: IOException) {
            failed(UpdateFailure.InstallFailed, "install session: ${e.message}")
        } catch (e: SecurityException) {
            failed(UpdateFailure.InstallFailed, "install session refused")
        } finally {
            // The session holds its own copy once committed; ours is deleted either way.
            withContext(io) { file.delete() }
        }
    }

    /** What Android said about the session ([UpdateInstallReceiver]). */
    fun onInstallStatus(status: InstallStatus) {
        when (status) {
            is InstallStatus.NeedsConfirm -> {
                val now = synchronized(lock) { isInFront.also { if (!it) pendingConfirm = status.show } }
                if (now) showConfirm(status.show) else info("Update install: Android's screen waits until Plumemu is in front")
            }
            InstallStatus.Success -> {
                info("Update installed")
                setStep(UpdateStep.Idle)
            }
            InstallStatus.Aborted -> {
                info("Update install: the person said no")
                setStep(UpdateStep.InstallAborted)
            }
            is InstallStatus.Failed -> failed(UpdateFailure.InstallFailed, "installer status ${status.code}: ${status.message}")
        }
        if (status !is InstallStatus.NeedsConfirm) scope.launch(io) { downloader.clearLeftovers() }
    }

    private fun showConfirm(show: () -> Boolean) {
        if (show()) {
            setStep(UpdateStep.Installing(askedPerson = true))
        } else {
            forgetReopen("Android's install screen did not open")
            failed(UpdateFailure.InstallFailed, "no confirm screen")
        }
    }

    /** Cancel in the panel: stops a download; a metered ask or a wait for the permission is dropped. */
    fun cancel() {
        val job = synchronized(lock) { updateJob }
        when {
            job?.isActive == true -> {
                info("Update download cancelled")
                job.cancel()
                // Also when the job had not started yet: its body, which says so too, never runs then.
                setStep(UpdateStep.Cancelled)
            }
            mutableView.value.step in setOf(UpdateStep.AskMetered, UpdateStep.NeedsPermission, UpdateStep.NoPermissionScreen, UpdateStep.ReadyToInstall) -> {
                synchronized(lock) { readyFile?.delete(); readyFile = null }
                setStep(UpdateStep.Cancelled)
            }
        }
    }

    private fun failed(
        failure: UpdateFailure,
        detail: String,
    ) {
        log("Update failed: $failure ($detail)")
        setStep(UpdateStep.Failed(failure))
    }

    private fun setStep(step: UpdateStep) = mutableView.update { it.copy(step = step) }
}
