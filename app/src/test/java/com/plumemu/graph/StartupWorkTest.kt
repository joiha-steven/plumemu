package com.plumemu.graph

import com.plumemu.coredata.settings.HomeScreenSettings
import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.scrape.SCRAPE_RULES_VERSION
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanStats
import com.plumemu.library.scan.UnavailableReason
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the startup rescan, emulator detection and app list run: after the content is
 * on screen, and again only when packages changed. Work in `backgroundScope` runs on
 * `runCurrent()` (`advanceUntilIdle` skips background work).
 */
class StartupWorkTest {
    private val done = CopyOnWriteArrayList<String>()
    private val logLines = CopyOnWriteArrayList<String>()
    private var scanGate: CompletableDeferred<Unit>? = null
    private var detectGate: CompletableDeferred<Unit>? = null
    private var failing: String? = null
    private var scanEvents: List<ScanEvent> = emptyList()
    private var scannerAvailable = true
    private var flow: MutableStateFlow<PlumSettings?>? = null
    private var scansEnded = 0
    private var backupChecks = 0

    private val tasks = object : StartupTasks {
        override suspend fun refreshApps() = record("apps")

        override suspend fun detectEmulators() {
            detectGate?.await()
            record("detect")
        }

        override suspend fun refreshDefaults() = record("defaults")

        override suspend fun setRescanAfterReset(pending: Boolean) {
            record(if (pending) "flag on" else "flag off")
            flow?.update { it?.copy(flags = it.flags.copy(rescanAfterReset = pending)) }
        }

        override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
            scanGate?.await()
            record("scan")
            scanEvents.forEach(onEvent)
            return scannerAvailable
        }

        // Counted, not recorded in [done]: the order tests list the startup steps only.
        override suspend fun scanEnded() {
            scansEnded++
        }

        override suspend fun recoverInterruptedBackup() {
            backupChecks++
        }

        override suspend fun finishRestore(): Boolean = justRestored
    }

    /** What `GraphRestore.onStartup` answers: the library was just replaced by a restore. */
    private var justRestored = false

    private fun record(task: String) {
        if (task == failing) throw IllegalStateException("content://x/tree/primary%3AROMs/Secret Quest.sfc")
        done += task
    }

    private fun settings(scanOnStartup: Boolean = true, setupCompleted: Boolean = true, showApps: Boolean = true, rescanAfterReset: Boolean = false, folderMatchVersion: Int = FOLDER_MATCH_VERSION, namingRulesVersion: Int = NAMING_RULES_VERSION, scrapeRulesVersion: Int = SCRAPE_RULES_VERSION) =
        MutableStateFlow<PlumSettings?>(
            PlumSettings(
                library = LibrarySettings(scanOnStartup = scanOnStartup),
                homeScreen = HomeScreenSettings(showApps = showApps),
                flags = SetupFlags(setupCompleted = setupCompleted, rescanAfterReset = rescanAfterReset, folderMatchVersion = folderMatchVersion, namingRulesVersion = namingRulesVersion, scrapeRulesVersion = scrapeRulesVersion),
            ),
        )

    private fun TestScope.work(settings: MutableStateFlow<PlumSettings?> = settings()): StartupWork {
        flow = settings
        return StartupWork(backgroundScope, settings, tasks, info = {}, clock = { 0L }) { logLines += it }
    }

    @Test
    fun nothingRunsBeforeTheContentIsShown() = runTest {
        val work = work()
        assertNull("a start before the content does nothing", work.onStart())
        runCurrent()
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun anInterruptedBackupIsLookedForOnceAfterTheContentIsShown() = runTest {
        val work = work()
        work.onStart()
        runCurrent()
        assertEquals("nothing before the content", 0, backupChecks)
        work.onContentShown()
        work.onContentShown()
        runCurrent()
        assertEquals(1, backupChecks)
    }

    /** Restore…: the start right after a restore does not scan; the person chooses when. */
    @Test
    fun theStartRightAfterARestoreDoesNotScanTheRestoredLibrary() = runTest {
        justRestored = true
        work().onContentShown()
        runCurrent()
        assertEquals(listOf("apps", "detect", "defaults"), done.toList())
    }

    @Test
    fun aHeldScanSlotRefusesScansUntilGivenBack() = runTest {
        val work = work(settings(scanOnStartup = false))
        scanGate = CompletableDeferred()
        work.requestScan()
        runCurrent()
        assertFalse("a running scan refuses the hold", work.holdScansForRestore())
        scanGate?.complete(Unit)
        runCurrent()
        done.clear()
        assertTrue(work.holdScansForRestore())
        assertFalse("only one holder", work.holdScansForRestore())
        assertFalse("no scan while the restore holds the slot", work.scanIfIdle())
        work.requestScan()
        runCurrent()
        assertFalse("scan" in done)
        work.releaseScansAfterRestore()
        runCurrent()
        assertTrue("the scan asked for meanwhile runs once given back", "scan" in done)
    }

    @Test
    fun afterTheContentTheRescanFollowsDetection() = runTest {
        work().onContentShown()
        runCurrent()
        assertEquals(listOf("apps", "detect", "defaults", "scan", "defaults"), done.toList())
    }

    @Test
    fun theStartupWaitsForTheSettingsToBeRead() = runTest {
        val settings = MutableStateFlow<PlumSettings?>(null)
        work(settings).onContentShown()
        runCurrent()
        assertFalse(done.toString(), "scan" in done)
        settings.value = PlumSettings(flags = SetupFlags(setupCompleted = true))
        runCurrent()
        assertTrue(done.toString(), "scan" in done)
    }

    @Test
    fun noRescanWhenScanOnStartupIsOff() = runTest {
        work(settings(scanOnStartup = false)).onContentShown()
        runCurrent()
        assertFalse(done.toString(), "scan" in done)
        assertTrue("detection still runs", "detect" in done)
    }

    @Test
    fun scansAtStartWhenTheLibraryWasMatchedByOlderFolderRules() = runTest {
        work(settings(scanOnStartup = false, folderMatchVersion = 0)).onContentShown()
        runCurrent()
        assertTrue(done.toString(), "scan" in done)
    }

    @Test
    fun scansAtStartWhenTheLibraryWasNamedByOlderNamingRules() = runTest {
        work(settings(scanOnStartup = false, namingRulesVersion = 0)).onContentShown()
        runCurrent()
        assertTrue(done.toString(), "scan" in done)
    }

    @Test
    fun scansAtStartWhenBoxArtWasJudgedByOlderScrapeRules() = runTest {
        work(settings(scanOnStartup = false, scrapeRulesVersion = 0)).onContentShown()
        runCurrent()
        assertTrue(done.toString(), "scan" in done)
    }

    @Test
    fun noRescanBeforeSetupIsCompleted() = runTest {
        work(settings(setupCompleted = false)).onContentShown()
        runCurrent()
        assertFalse(done.toString(), "scan" in done)
    }

    @Test
    fun noAppListWhenShowAppsIsOffUntilItIsSwitchedOn() = runTest {
        val settings = settings(showApps = false)
        work(settings).onContentShown()
        runCurrent()
        assertFalse(done.toString(), "apps" in done)

        settings.value = settings.value?.copy(homeScreen = HomeScreenSettings(showApps = true))
        runCurrent()
        assertEquals(1, done.count { it == "apps" })
    }

    @Test
    fun aSecondContentFrameDoesNothing() = runTest {
        val work = work()
        work.onContentShown()
        runCurrent()
        assertNull("a recreated Activity does not start the work again", work.onContentShown())
        runCurrent()
        assertEquals(1, done.count { it == "scan" })
    }

    @Test
    fun aStartWithoutPackageChangesDoesNothing() = runTest {
        val work = work()
        work.onContentShown()
        runCurrent()
        done.clear()

        assertNull(work.onStart())
        runCurrent()
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun aPackageChangeRefreshesOnTheNextStartButDoesNotRescan() = runTest {
        val work = work()
        work.onContentShown()
        runCurrent()
        done.clear()

        work.markPackagesChanged()
        runCurrent()
        assertEquals("nothing until the next start", emptyList<String>(), done)
        work.onStart()
        runCurrent()
        assertEquals(listOf("apps", "detect", "defaults"), done.toList())
        work.onStart()
        runCurrent()
        assertEquals("once per change", 3, done.size)
    }

    @Test
    fun packageRefreshesNeverOverlapAndAChangeDuringOneRunsOnceMore() = runTest {
        val work = work(settings(scanOnStartup = false))
        work.onContentShown()
        runCurrent()
        done.clear()

        detectGate = CompletableDeferred()
        work.markPackagesChanged()
        val first = work.onStart()
        runCurrent()
        work.markPackagesChanged()
        assertEquals("the running job is reused, not a second one", first, work.onStart())
        detectGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("apps", "detect", "defaults", "apps", "detect", "defaults"), done.toList())
    }

    @Test
    fun neverTwoScansAtOnce() = runTest {
        scanGate = CompletableDeferred()
        val work = work()
        work.onContentShown()
        runCurrent()
        assertTrue("the startup scan is running", work.isScanning)

        assertFalse("a second scan is refused while one runs", work.scanIfIdle())

        scanGate?.complete(Unit)
        runCurrent()
        assertFalse(work.isScanning)
        assertEquals(1, done.count { it == "scan" })
        assertTrue("free again after the first one ended", work.scanIfIdle())
    }

    @Test
    fun aFailedStepIsLoggedByClassAndTheNextStepsRun() = runTest {
        failing = "detect"
        work().onContentShown()
        runCurrent()

        assertTrue(done.toString(), "scan" in done)
        assertEquals(listOf("Startup emulator detection failed (IllegalStateException)"), logLines.toList())
    }

    @Test
    fun aFailedScanFreesTheScanSlot() = runTest {
        failing = "scan"
        val work = work()
        work.onContentShown()
        runCurrent()
        assertFalse(work.isScanning)
        assertTrue(logLines.toString(), logLines.single().contains("library scan failed"))
    }

    @Test
    fun setupsRequestIsBusyAtOnceAndUntilItsScanEnds() = runTest {
        scanGate = CompletableDeferred()
        val work = work(settings(setupCompleted = false))
        assertFalse(work.scanState.value.isBusy)

        work.requestScan()
        assertTrue("busy before the scan has even started", work.scanState.value.isBusy)
        runCurrent()
        assertTrue(work.scanState.value.isBusy)

        scanGate?.complete(Unit)
        runCurrent()
        assertEquals(ScanState(isBusy = false, hasFailed = false), work.scanState.value)
        assertEquals(1, done.count { it == "scan" })
    }

    @Test
    fun theScanReportShowsTheRunThenItsResultUntilCleared() = runTest {
        scanGate = CompletableDeferred()
        scanEvents =
            listOf(
                ScanEvent.Progress(1, 2, 40, 10),
                ScanEvent.FolderFinished(1, FolderOutcome.Scanned(ScanStats(2, 0, 40, 7, 0, 2, 0, 0))),
            )
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        assertEquals("a pass that has begun, before its first event", ScanPhase.Starting, work.scanReport.value.phase)
        assertNull(work.scanReport.value.result)

        scanGate?.complete(Unit)
        runCurrent()
        val result = work.scanReport.value.result
        assertEquals(7, result?.gamesAdded)
        assertEquals(2, result?.gamesRemoved)
        assertEquals(ScanPhase.Idle, work.scanReport.value.phase)

        work.clearScanResult()
        assertNull(work.scanReport.value.result)
    }

    @Test
    fun aRequestWhileAScanRunsAddsExactlyOnePass() = runTest {
        scanGate = CompletableDeferred()
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        // A folder added, then Continue, while the first pass still walks.
        work.requestScan()
        work.requestScan()
        runCurrent()
        assertEquals("still the first pass", 0, done.count { it == "scan" })

        scanGate?.complete(Unit)
        runCurrent()
        assertEquals("the running pass, then one more for the requests made meanwhile", 2, done.count { it == "scan" })
        assertFalse(work.scanState.value.isBusy)
    }

    @Test
    fun aScanThatEndsAsksForABoxArtRunAndAFailedOneDoesNot() = runTest {
        scannerAvailable = false
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        assertEquals("a failed scan asks for no run", 0, scansEnded)

        scannerAvailable = true
        work.requestScan()
        runCurrent()
        assertEquals(1, scansEnded)
    }

    @Test
    fun aFailingBoxArtRequestIsLoggedAndTheScanStillEnds() = runTest {
        failing = "scan ended"
        val failingTasks = object : StartupTasks by tasks {
            override suspend fun scanEnded() = record("scan ended")
        }
        val work = StartupWork(backgroundScope, settings(setupCompleted = false), failingTasks, info = {}, clock = { 0L }) { logLines += it }
        work.requestScan()
        runCurrent()
        assertEquals(ScanState(isBusy = false, hasFailed = false), work.scanState.value)
        assertTrue(logLines.toString(), logLines.any { it == "Startup box art run after the scan failed (IllegalStateException)" })
    }

    @Test
    fun noScannerMeansAFailedScanAndTheNextRequestTriesAgain() = runTest {
        scannerAvailable = false
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        assertEquals(ScanState(isBusy = false, hasFailed = true), work.scanState.value)

        scannerAvailable = true
        work.requestScan()
        runCurrent()
        assertFalse(work.scanState.value.hasFailed)
    }

    @Test
    fun aThrowingScanIsAFailureLoggedByClassOnly() = runTest {
        failing = "scan"
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        assertTrue(work.scanState.value.hasFailed)
        assertFalse(work.scanState.value.isBusy)
        assertEquals(listOf("Startup library scan failed (IllegalStateException)"), logLines.toList())
    }

    @Test
    fun foldersTheScanCouldNotReadAreNamedByIdUntilTheNextScan() = runTest {
        scanEvents =
            listOf(
                ScanEvent.FolderFinished(1, FolderOutcome.Unavailable(UnavailableReason.PERMISSION_REVOKED)),
                ScanEvent.FolderFinished(2, FolderOutcome.Scanned(ScanStats(1, 0, 3, 3, 0, 0, 0, 0))),
            )
        val work = work(settings(setupCompleted = false))
        work.requestScan()
        runCurrent()
        assertEquals(setOf(1L), work.scanState.value.unavailableFolderIds)

        scanEvents = emptyList()
        work.requestScan()
        runCurrent()
        assertEquals(emptySet<Long>(), work.scanState.value.unavailableFolderIds)
    }

    @Test
    fun noStartupRescanRightAfterSetupScannedInThisProcess() = runTest {
        val settings = settings(setupCompleted = false)
        val work = work(settings)
        work.requestScan()
        runCurrent()
        settings.value = settings.value?.copy(flags = SetupFlags(setupCompleted = true))

        work.onContentShown()
        runCurrent()
        assertEquals("setup's scan only", 1, done.count { it == "scan" })
        assertTrue(done.toString(), "detect" in done)
    }

    @Test
    fun aLibraryResetForgetsThenScansAndPicksDefaults() = runTest {
        val work = work()
        work.resetLibrary { record("forget") }
        assertTrue("busy from the request on", work.scanState.value.isBusy)
        runCurrent()
        assertEquals(listOf("flag on", "forget", "scan", "flag off", "defaults"), done)
        assertFalse(work.scanState.value.isBusy)
    }

    @Test
    fun aLibraryResetWaitsForTheRunningScanAndNeverForgetsDuringIt() = runTest {
        val work = work()
        val gate = CompletableDeferred<Unit>().also { scanGate = it }
        work.requestScan()
        runCurrent()
        work.resetLibrary { record("forget") }
        runCurrent()
        assertEquals("nothing forgotten while the scan runs", emptyList<String>(), done)
        assertTrue(work.scanState.value.isBusy)
        gate.complete(Unit)
        runCurrent()
        assertEquals("the running scan ends without an extra pass, then reset and its scan", listOf("scan", "flag on", "forget", "scan", "flag off", "defaults"), done)
        assertFalse(work.scanState.value.isBusy)
    }

    @Test
    fun aFailedForgetIsLoggedAndTheFoldersAreStillScanned() = runTest {
        val work = work()
        failing = "forget"
        work.resetLibrary { record("forget") }
        runCurrent()
        assertEquals(listOf("flag on", "scan", "flag off", "defaults"), done)
        assertEquals(LibraryResetState.Failed, work.libraryReset.value)
        assertTrue(logLines.any { it.contains("library reset failed (IllegalStateException)") })
        assertFalse("no path in the log", logLines.any { it.contains("content://") })
    }

    @Test
    fun aResetSaysForgettingUntilTheDatabaseStepEnds() = runTest {
        val work = work()
        val gate = CompletableDeferred<Unit>()
        assertEquals(LibraryResetState.Idle, work.libraryReset.value)
        work.resetLibrary { gate.await() }
        runCurrent()
        assertEquals(LibraryResetState.Forgetting, work.libraryReset.value)
        gate.complete(Unit)
        runCurrent()
        assertEquals(LibraryResetState.Forgotten, work.libraryReset.value)
    }

    @Test
    fun aResetCutShortIsScannedAtTheNextStartEvenWithScanOnStartupOff() = runTest {
        val settings = settings(scanOnStartup = false, rescanAfterReset = true)
        val work = work(settings)
        work.onContentShown()
        runCurrent()
        assertTrue("scanned", "scan" in done)
        assertTrue("the flag is cleared once the scan ends", "flag off" in done)
        assertFalse(settings.value?.flags?.rescanAfterReset == true)
    }

    @Test
    fun withScanOnStartupOffAndNoResetPendingNothingIsScanned() = runTest {
        val work = work(settings(scanOnStartup = false))
        work.onContentShown()
        runCurrent()
        assertFalse("scan" in done)
    }
}
