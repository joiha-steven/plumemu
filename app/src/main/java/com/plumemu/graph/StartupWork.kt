package com.plumemu.graph

import com.plumemu.telemetry.ScanTrigger
import android.os.SystemClock
import android.util.Log
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.scrape.SCRAPE_RULES_VERSION
import com.plumemu.library.scan.FolderChanges
import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanStats
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the app does once its content is on screen; the graph supplies the real work. */
interface StartupTasks {
    /** Reads the launchable apps for the Apps entry. */
    suspend fun refreshApps()

    /** Asks PackageManager which emulators are installed. */
    suspend fun detectEmulators()

    /** Stores the automatic default emulator of every system with games (a person's choice is kept). */
    suspend fun refreshDefaults()

    /**
     * One rescan of every picked folder, to the end, handing each event to [onEvent]. False when
     * no scan could start (the platform data or the scanner could not be set up; logged).
     */
    suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean

    /**
     * Which picked folders (all, or only [onlyFolderIds]) changed since their last scan, asking
     * directory times only (nothing is listed or opened; `LibraryScanner.changedFolders`). None by default.
     */
    suspend fun changedFolders(onlyFolderIds: Set<Long>? = null): FolderChanges = FolderChanges.NONE

    /**
     * A scan of [folderIds] only, to the end, handing each event to [onEvent]; unchanged
     * directories in them are not listed either. False when no scan could start (logged).
     */
    suspend fun scanFolders(folderIds: Set<Long>, onEvent: (ScanEvent) -> Unit): Boolean = false

    /** Stores `flags.rescan_after_reset`: set before Reset library forgets, cleared once a scan ends. */
    suspend fun setRescanAfterReset(pending: Boolean)

    /**
     * A scan pass ended without failing: the graph asks for a box-art run when scraping is set up
     * (the run waits for Wi-Fi and a battery that is not low).
     */
    suspend fun scanEnded()

    /**
     * Marks pinned shortcuts their apps disabled or removed as unavailable, and starts following
     * the apps' changes. Nothing while Plumemu is not the home app.
     */
    suspend fun checkPinnedShortcuts() {}

    /**
     * Lists the installed apps that are games as Android games or Ports, whether or not the
     * Apps entry is shown; package changes later ask for it themselves (`GraphAppGames.onPackageChange`).
     */
    suspend fun syncAppGames() {}

    /**
     * A backup the process did not finish (Settings › Advanced › Back up…): its torn file is deleted
     * and the Back up row says so (`GraphBackup.recoverInterrupted`). Nothing to do by default.
     */
    suspend fun recoverInterruptedBackup() = Unit

    /**
     * Settings › Advanced › Restore…: a swap the last process did
     * not finish is finished or put back, and the result of one that restarted the app is shown
     * (`GraphRestore.onStartup`). True when the library was just replaced: the startup scan does not
     * run this time (the person scans when they choose). Nothing to do by default.
     */
    suspend fun finishRestore(): Boolean = false

    /**
     * Plumemu is in front: once after the startup work, then on every [StartupWork.onStart]. The
     * daily update check's only trigger. Returns at once with the check's job, if one started.
     */
    fun frontReturned(): Job? = null

    /** Plumemu left the front ([StartupWork.onStop]): an update waits to be installed until it is back. */
    fun frontLeft() = Unit
}

/** Settings › Reset library as Settings shows it. */
/** One scan pass ended (`library_scanned`): why it ran, whether it could run, whether a folder could not be read, how long it took. */
data class ScanEnded(
    val trigger: ScanTrigger,
    val isOk: Boolean,
    val hasFolderLost: Boolean,
    val millis: Long,
)

enum class LibraryResetState {
    Idle,

    /** Asked for: waiting for a running scan, or forgetting. */
    Forgetting,

    /** The games are forgotten; the folders are being scanned again. */
    Forgotten,

    /** The database step failed; its transaction left the library as it was. */
    Failed,
}

/**
 * The library scan as first-run setup shows it. [isBusy] is true from a [StartupWork.requestScan]
 * until the last scan it asked for has ended, so a screen never sees "done" between a request and
 * its scan. [hasFailed] and [unavailableFolderIds] describe the last scan that ended.
 */
data class ScanState(
    val isBusy: Boolean = false,
    val hasFailed: Boolean = false,
    val unavailableFolderIds: Set<Long> = emptySet(),
)

/**
 * Work that must never delay what the person sees,
 * run in the process's scope so it outlives the Activity (a rotation, or opening a game, does not
 * stop a scan).
 *
 * - [onContentShown], once per process: the installed packages (app list when Show apps is on,
 *   emulator detection, default emulators); a rescan when Find new games automatically is on and first-run
 *   setup is done, then the defaults again for the systems it found.
 * - [markPackagesChanged] (a package was added, removed, replaced or changed while Plumemu was
 *   started) and [onStart]: the packages again on the next start, only if something changed.
 *   One packages job at a time; a change during a run makes it run once more.
 * - Show apps switched on: the app list is read then.
 * - [requestScan] (first-run setup: a folder was added, or Continue): a scan now, or one more
 *   pass of the running scan, so a folder added while a scan runs is read too.
 * - Every scan pass that ends without failing calls [StartupTasks.scanEnded] (a box-art run).
 * - [onStart] after the startup work, and [onStorageMounted]: a change check
 *   ([StartupTasks.changedFolders], directory times only) when Find new games automatically is on, then a scan
 *   of the changed folders only, holding the scan slot; when and how often: [ChangeChecks].
 *
 * A failed step is logged (class only) and the next one still runs.
 */
class StartupWork(
    private val scope: CoroutineScope,
    private val settings: Flow<PlumSettings?>,
    private val tasks: StartupTasks,
    info: (String) -> Unit = { Log.i(TAG, it) },
    /** Milliseconds that keep counting while the device sleeps, for the change checks' intervals. */
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
    /** Told when each scan pass ends, for the statistics (`library_scanned`); on the scan's coroutine, so it must return at once. */
    private val onScanEnded: (ScanEnded) -> Unit = {},
    /** Told what each change check found (the home's notice); never for the startup scan, a manual rescan or setup's first scan. */
    private val onChangesFound: (Collection<ScanStats>) -> Unit = {},
    // Last, so the existing callers' trailing lambda stays the warning log.
    private val log: (String) -> Unit = { Log.w(TAG, it) },
) {
    private val started = AtomicBoolean(false)

    /**
     * Between [onStart] and [onStop]. The startup work ends whenever its scan does, possibly while a
     * game plays: the front hook is told then only if Plumemu is still in front, otherwise
     * at the next [onStart]. Reading it and calling `frontReturned` happen under [frontLock], as do
     * clearing it and `frontLeft`, so an [onStop] cannot fall between the startup work's read and its
     * call (both calls only launch work).
     */
    private val frontLock = Any()
    private var inFront = false

    private val checks =
        ChangeChecks(
            scope = scope,
            settings = ::currentSettings,
            changedFolders = { ids -> step("change check") { tasks.changedFolders(ids) } },
            scanEnded = { step("box art run after the scan") { tasks.scanEnded() } },
            slot = CheckSlotOfStartupWork(),
            clock = clock,
            info = info,
            log = log,
            onFound = onChangesFound,
        )

    /** Guards [scanRunning] and [rescanWanted]; [ScanState.isBusy] is published under it too. */
    private val scanLock = Any()
    private var scanRunning = false
    private val packagesDirty = AtomicBoolean(false)
    private var rescanWanted = false

    /** Why the next pass over every folder runs (`library_scanned.trigger`); the first reason asked wins. Guarded by [scanLock]. */
    private var pendingTrigger: ScanTrigger? = null

    /** Library resets and folder removals waiting for the scan slot; [ScanState.isBusy] stays true meanwhile. */
    private var slotWaiters = 0

    /** True while nobody holds the scan slot; a waiting reset watches it. */
    private val slotFree = MutableStateFlow(true)

    /** A scan ended without failing in this process; the startup rescan then has nothing to catch up on. */
    private val scannedInThisProcess = AtomicBoolean(false)
    private val mutableScanState = MutableStateFlow(ScanState())

    /** The scan as setup shows it: busy, failed, folders that could not be read. */
    val scanState: StateFlow<ScanState> = mutableScanState.asStateFlow()
    private val mutableScanReport = MutableStateFlow(ScanReport())

    /** Progress counts while a pass runs and its result after (Settings › Rescan library); events only, it changes nothing about the scan. */
    val scanReport: StateFlow<ScanReport> = mutableScanReport.asStateFlow()

    /** Settings forgets the last result: the person left the Rescan panel or Settings. A running pass's progress stays. */
    fun clearScanResult() {
        mutableScanReport.update { if (it.result == null) it else it.copy(result = null) }
    }
    private var packagesJob: Job? = null

    private val mutableLibraryReset = MutableStateFlow(LibraryResetState.Idle)

    /** The last Reset library of this process, for Settings. */
    val libraryReset: StateFlow<LibraryResetState> = mutableLibraryReset.asStateFlow()

    internal val isScanning: Boolean get() = synchronized(scanLock) { scanRunning }

    /** Called after the first frame that shows the main screen's content; later calls do nothing. */
    fun onContentShown(): Job? {
        if (!started.compareAndSet(false, true)) return null
        packagesDirty.set(true)
        return scope.launch {
            launch { refreshAppsWhenShowAppsTurnsOn() }
            launch { step("interrupted backup") { tasks.recoverInterruptedBackup() } }
            // Before the startup scan is decided, and before the package checks read the library.
            val justRestored = step("restore") { tasks.finishRestore() } == true
            try {
                refreshPackagesIfDirty()?.join()
                // After a restore is finished, so the restored library's app games are kept and matched.
                step("app games") { tasks.syncAppGames() }
                val s = currentSettings() ?: return@launch
                // Setup has just scanned in this process: a rescan right after it would find nothing new.
                // A reset whose scan never ended (the process died) scans whatever Find new games automatically says.
                // Older folder, naming or scrape rules: the library is put right once, even with Find new games automatically off.
                val wanted = s.library.scanOnStartup || s.flags.rescanAfterReset || s.flags.folderMatchVersion < FOLDER_MATCH_VERSION ||
                    s.flags.namingRulesVersion < NAMING_RULES_VERSION || s.flags.scrapeRulesVersion < SCRAPE_RULES_VERSION
                val needsRescan = wanted && s.flags.setupCompleted && !scannedInThisProcess.get()
                if (needsRescan && !justRestored && scanIfIdle(ScanTrigger.Startup)) {
                    step("default emulators after the scan") { tasks.refreshDefaults() }
                }
            } finally {
                // The startup scan covers what a check would find; after a restore the person
                // scans when they choose.
                if (justRestored) checks.turnOffAfterRestore() else checks.enable()
                synchronized(frontLock) { if (inFront) tellTasks("front") { tasks.frontReturned() } }
            }
        }
    }

    /** Whether the Activity is started (between [onStart] and [onStop]): the person is in Plumemu, not in a game. */
    val isInFront: Boolean get() = synchronized(frontLock) { inFront }

    private val packageChangesSeen = java.util.concurrent.atomic.AtomicInteger()

    /** How many package changes were reported in this process (tests read it). */
    internal val packageChangeCount: Int get() = packageChangesSeen.get()

    /** A package changed on the device (see `PackageChanges`); nothing runs until [onStart]. */
    fun markPackagesChanged() {
        packageChangesSeen.incrementAndGet()
        packagesDirty.set(true)
    }

    /**
     * The Activity started again (back from a game, a file manager, the screen waking): refresh what
     * depends on installed packages, if they changed, and check the folders for copied or removed
     * games ([checkForChanges]). Returns the packages' job.
     */
    fun onStart(): Job? {
        synchronized(frontLock) {
            inFront = true
            if (!started.get()) {
                checks.markInFront()
                return null
            }
            checks.onFront()
            tellTasks("front") { tasks.frontReturned() }
        }
        return refreshPackagesIfDirty()
    }

    /** The Activity stopped (a game, another app, the screen off): no check runs until [onStart]. */
    fun onStop() {
        synchronized(frontLock) {
            inFront = false
            checks.onStop()
            tellTasks("stop") { tasks.frontLeft() }
        }
    }

    /** A call that must not break the Activity's lifecycle: a failure is logged by class. */
    private fun tellTasks(
        name: String,
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: RuntimeException) {
            log("Startup $name failed (${e.javaClass.simpleName})")
        }
    }

    /** Storage was mounted (an SD card put in) while Plumemu is started: check the folders. */
    fun onStorageMounted(): Job? = checks.onMount()

    /** The scan slot as [ChangeChecks] uses it. */
    private inner class CheckSlotOfStartupWork : CheckSlot {
        override fun claimForCheck(): Boolean =
            synchronized(scanLock) {
                if (scanRunning || rescanWanted || slotWaiters > 0) return false
                // Not published as busy: most checks end in a few milliseconds with nothing to do.
                scanRunning = true
                slotFree.value = false
                true
            }

        override suspend fun releaseAfterCheck() {
            if (releaseScan()) scanIfIdle()
        }

        override suspend fun scanFolders(folderIds: Set<Long>, unavailable: Set<Long>): Map<Long, ScanStats>? {
            synchronized(scanLock) { publishBusyLocked() }
            return scanOnce(onlyFolderIds = folderIds, unavailableBefore = unavailable)
        }

        override fun showUnavailable(folderIds: Set<Long>) = mutableScanState.update { it.copy(unavailableFolderIds = folderIds) }
    }

    @Synchronized
    private fun refreshPackagesIfDirty(): Job? {
        if (!packagesDirty.get()) return null
        // The running job sees the flag before it ends and goes round again.
        packagesJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            while (packagesDirty.getAndSet(false)) {
                if (currentSettings()?.homeScreen?.showApps != false) step("app list") { tasks.refreshApps() }
                step("emulator detection") { tasks.detectEmulators() }
                step("default emulators") { tasks.refreshDefaults() }
                step("pinned shortcuts") { tasks.checkPinnedShortcuts() }
            }
        }.also { packagesJob = it }
    }

    private suspend fun refreshAppsWhenShowAppsTurnsOn() {
        settings.filterNotNull().map { it.homeScreen.showApps }.distinctUntilChanged().drop(1).filter { it }.collect {
            step("app list") { tasks.refreshApps() }
        }
    }

    private suspend fun currentSettings(): PlumSettings? = step("settings") { settings.filterNotNull().first() }

    /**
     * Runs one rescan unless one is already running (then returns false at once): a second scan
     * would only wait for the first and find nothing new. A [requestScan] made while it runs adds
     * one more pass, checked again after the flag is released so no request falls in between.
     */
    suspend fun scanIfIdle(trigger: ScanTrigger = ScanTrigger.Manual): Boolean {
        var ran = false
        while (claimScan()) {
            if (!ran) offerTrigger(trigger)
            ran = true
            if (!scanWhileClaimed()) break
        }
        return ran
    }

    /**
     * Settings › Advanced › Reset library: waits until no scan runs, then, holding the scan slot so
     * no scan writes meanwhile, runs [forget] (the database's reset) and scans every folder again.
     * [scanState] is busy from this call until that scan ends. In the process's scope, so leaving
     * Settings or the app does not stop it; a second reset waits for the first and does it again.
     */
    fun resetLibrary(forget: suspend () -> Unit): Job {
        announceSlotWaiter()
        mutableLibraryReset.value = LibraryResetState.Forgetting
        return scope.launch {
            // Not a request for one more pass of the running scan: that pass would read the
            // library just before it is forgotten.
            claimSlotWhenFree()
            synchronized(scanLock) { rescanWanted = true }
            offerTrigger(ScanTrigger.Manual)
            step("rescan flag") { tasks.setRescanAfterReset(true) }
            val forgotten = step("library reset") { forget() } != null
            mutableLibraryReset.value = if (forgotten) LibraryResetState.Forgotten else LibraryResetState.Failed
            if (scanWhileClaimed()) scanIfIdle()
            step("default emulators after the reset") { tasks.refreshDefaults() }
        }
    }

    /**
     * Settings › Library › ROM folders › Remove: waits until no scan runs, then, holding the scan
     * slot so no scan writes games of that folder meanwhile, runs [remove] (the folder's row with
     * its games, then its grant). A scan of the folders that stay follows either way, then the
     * default emulators (a system may have lost its last game). True when [remove] succeeded; a
     * failure is logged by class and leaves the folder as it was. In the process's scope, so
     * leaving Settings does not stop it.
     */
    fun removeFolder(remove: suspend () -> Unit): Deferred<Boolean> {
        announceSlotWaiter()
        return scope.async {
            claimSlotWhenFree()
            synchronized(scanLock) { rescanWanted = true }
            offerTrigger(ScanTrigger.Manual)
            val removed = step("folder removal") { remove() } != null
            if (scanWhileClaimed()) scanIfIdle()
            step("default emulators after a folder removal") { tasks.refreshDefaults() }
            removed
        }
    }

    /**
     * Settings › Advanced › Restore…: takes the scan slot without scanning, so no scan writes while
     * the library is swapped; false (nothing taken) while a scan runs, waits or is asked for. Not
     * published as busy: the app restarts after the swap, and a failure gives it back
     * ([releaseScansAfterRestore]).
     */
    internal fun holdScansForRestore(): Boolean =
        synchronized(scanLock) {
            if (scanRunning || rescanWanted || slotWaiters > 0) return false
            scanRunning = true
            slotFree.value = false
            true
        }

    /** Gives back what [holdScansForRestore] took; a scan asked for meanwhile runs now. */
    internal fun releaseScansAfterRestore() {
        if (releaseScan()) scope.launch { scanIfIdle() }
    }

    /** Counts a reset or removal as waiting, so [scanState] is busy from the call on. */
    private fun announceSlotWaiter() =
        synchronized(scanLock) {
            slotWaiters++
            publishBusyLocked()
        }

    /** Waits for the scan slot and claims it; the caller then runs its pass ([scanWhileClaimed]). */
    private suspend fun claimSlotWhenFree() {
        try {
            while (!claimScan()) slotFree.first { it }
        } finally {
            synchronized(scanLock) {
                slotWaiters--
                publishBusyLocked()
            }
        }
    }

    /**
     * Scans until no request is left, then frees the slot the caller claimed. True when a request
     * arrived just after it was freed, so the caller claims it again and scans once more.
     */
    private suspend fun scanWhileClaimed(): Boolean {
        var again: Boolean
        try {
            do {
                synchronized(scanLock) { rescanWanted = false }
                scanOnce()
            } while (synchronized(scanLock) { rescanWanted })
        } finally {
            again = releaseScan()
        }
        return again
    }

    /**
     * First-run setup asks for a scan: it starts now, or the running one makes one more pass.
     * [scanState] is busy from this call on.
     */
    fun requestScan(trigger: ScanTrigger = ScanTrigger.Manual): Job {
        synchronized(scanLock) {
            rescanWanted = true
            if (pendingTrigger == null) pendingTrigger = trigger
            publishBusyLocked()
        }
        return scope.launch { scanIfIdle() }
    }

    /** [trigger] is why the next pass runs, unless a reason was already given for it. */
    private fun offerTrigger(trigger: ScanTrigger) =
        synchronized(scanLock) {
            if (pendingTrigger == null) pendingTrigger = trigger
        }

    private fun claimScan(): Boolean =
        synchronized(scanLock) {
            if (scanRunning) return false
            scanRunning = true
            slotFree.value = false
            publishBusyLocked()
            true
        }

    /**
     * Frees the scan slot; true when a request arrived meanwhile, so the caller scans again.
     * Otherwise a mount heard while it was held is checked now ([ChangeChecks.onSlotFreed]).
     */
    private fun releaseScan(): Boolean {
        val (again, isQuiet) =
            synchronized(scanLock) {
                scanRunning = false
                slotFree.value = true
                // Still busy when a request came in after the last pass: it is never seen as done between.
                publishBusyLocked()
                rescanWanted to (!rescanWanted && slotWaiters == 0)
            }
        // Outside scanLock: ChangeChecks takes its own lock, then scanLock.
        if (isQuiet) checks.onSlotFreed()
        return again
    }

    /**
     * One pass over every folder, or over [onlyFolderIds] (a change check's changed folders; the
     * check found [unavailableBefore] unavailable). A pass over every folder asks for a box-art
     * run; for a check's pass [ChangeChecks] decides. Returns the stats of each folder scanned, or
     * null when the scan could not run.
     */
    private suspend fun scanOnce(onlyFolderIds: Set<Long>? = null, unavailableBefore: Set<Long> = emptySet()): Map<Long, ScanStats>? {
        val unavailable = HashSet(unavailableBefore)
        val startedAt = clock()
        // A check's pass is a change check whatever is pending; a pass over every folder takes the pending reason.
        val trigger =
            if (onlyFolderIds != null) {
                ScanTrigger.ChangeCheck
            } else {
                synchronized(scanLock) { pendingTrigger.also { pendingTrigger = null } } ?: ScanTrigger.Manual
            }
        val reporter = ScanReporter()
        val stats = HashMap<Long, ScanStats>()
        mutableScanReport.value = reporter.running()
        val onEvent = { event: ScanEvent ->
            mutableScanReport.value = reporter.onEvent(event)
            when (val outcome = (event as? ScanEvent.FolderFinished)?.outcome) {
                is FolderOutcome.Unavailable -> unavailable += event.folderId
                is FolderOutcome.Scanned -> stats[event.folderId] = outcome.stats
                is FolderOutcome.Deleted, null -> Unit
            }
        }
        val scanned =
            if (onlyFolderIds == null) {
                step("library scan") { tasks.scanLibrary(onEvent) } == true
            } else {
                step("scan of changed folders") { tasks.scanFolders(onlyFolderIds, onEvent) } == true
            }
        if (scanned && onlyFolderIds == null) {
            scannedInThisProcess.set(true)
            if (currentSettings()?.flags?.rescanAfterReset == true) step("rescan flag") { tasks.setRescanAfterReset(false) }
        }
        if (scanned && onlyFolderIds == null) step("box art run after the scan") { tasks.scanEnded() }
        mutableScanState.update { it.copy(hasFailed = !scanned, unavailableFolderIds = unavailable.toSet()) }
        mutableScanReport.value = reporter.finished(scanned)
        tellTasks("scan ended") { onScanEnded(ScanEnded(trigger, scanned, unavailable.isNotEmpty(), (clock() - startedAt).coerceAtLeast(0))) }
        return if (scanned) stats else null
    }

    /** Call with [scanLock] held, so the published value follows the flags in the order they changed. */
    private fun publishBusyLocked() {
        mutableScanState.update { it.copy(isBusy = scanRunning || rescanWanted || slotWaiters > 0) }
    }

    private suspend fun <T> step(name: String, block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Class only: scan and provider errors carry document URIs in their messages.
            log("Startup $name failed (${e.javaClass.simpleName})")
            null
        }

    private companion object {
        const val TAG = "PlumStartup"
    }
}
