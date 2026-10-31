package com.plumemu.graph

import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.scan.FolderChanges
import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanStats
import com.plumemu.library.scan.UnavailableReason
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The change check that finds games copied while Plumemu was in the background or on a card just
 * put in: only on coming
 * back to the front and on a mount, never over a running scan, a scan of the changed folders only,
 * and a box-art run only for added games no longer being copied. Time is the test scheduler's.
 */
class StartupWorkChangeCheckTest {
    private val done = CopyOnWriteArrayList<String>()
    private val logLines = CopyOnWriteArrayList<String>()
    private val infoLines = CopyOnWriteArrayList<String>()
    private var changes: () -> FolderChanges = changed()
    private var scanGate: CompletableDeferred<Unit>? = null
    private var statsOf: (Long) -> ScanStats = { stats() }
    private var scansEnded = 0
    private var justRestored = false

    private val tasks = object : StartupTasks {
        override suspend fun refreshApps() = Unit

        override suspend fun detectEmulators() = Unit

        override suspend fun refreshDefaults() = Unit

        override suspend fun setRescanAfterReset(pending: Boolean) = Unit

        override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
            scanGate?.await()
            done += "full scan"
            return true
        }

        override suspend fun changedFolders(onlyFolderIds: Set<Long>?): FolderChanges {
            val all = changes()
            if (onlyFolderIds == null) {
                done += "check"
                return all
            }
            done += "recheck ${onlyFolderIds.sorted()}"
            return all.copy(changedFolderIds = all.changedFolderIds intersect onlyFolderIds)
        }

        override suspend fun scanFolders(folderIds: Set<Long>, onEvent: (ScanEvent) -> Unit): Boolean {
            scanGate?.await()
            done += "scan ${folderIds.sorted()}"
            for (id in folderIds.sorted()) onEvent(ScanEvent.FolderFinished(id, FolderOutcome.Scanned(statsOf(id))))
            return true
        }

        override suspend fun scanEnded() {
            scansEnded++
        }

        override suspend fun finishRestore(): Boolean = justRestored
    }

    private fun stats(added: Int = 0, updated: Int = 0, stillWriting: Int = 0) = ScanStats(1, 5, 3, added, updated, 0, 0, 0, stillWriting)

    private fun changed(vararg ids: Long, unavailable: Map<Long, UnavailableReason> = emptyMap()): () -> FolderChanges = {
        FolderChanges(ids.toSet(), unavailable, foldersChecked = 2, directoriesAsked = 3)
    }

    private fun settings(scanOnStartup: Boolean = true) =
        MutableStateFlow<PlumSettings?>(
            PlumSettings(
                library = LibrarySettings(scanOnStartup = scanOnStartup),
                flags = SetupFlags(setupCompleted = true, folderMatchVersion = FOLDER_MATCH_VERSION),
            ),
        )

    private fun TestScope.work(settings: MutableStateFlow<PlumSettings?> = settings()) =
        StartupWork(backgroundScope, settings, tasks, info = { infoLines += it }, clock = { testScheduler.currentTime }) { logLines += it }

    /** The work with its startup done (the startup scan ran), the state every check starts from. */
    private fun TestScope.started(settings: MutableStateFlow<PlumSettings?> = settings()): StartupWork {
        val work = work(settings)
        // As MainActivity: started before its first frame.
        work.onStart()
        work.onContentShown()
        runCurrent()
        done.clear()
        scansEnded = 0
        return work
    }

    private fun TestScope.wait(ms: Long) {
        advanceTimeBy(ms)
        runCurrent()
    }

    @Test
    fun noChangeMeansNoScanAndNoBoxArtRun() = runTest {
        val work = started()

        work.onStart()
        runCurrent()

        assertEquals(listOf("check"), done.toList())
        assertEquals(0, scansEnded)
        assertEquals("not shown as busy", false, work.scanState.value.isBusy)
    }

    @Test
    fun oneChangedFolderMeansOnlyThatFolderIsScanned() = runTest {
        changes = changed(2)
        statsOf = { stats(added = 1) }
        val work = started()

        work.onStart()
        runCurrent()

        assertEquals(listOf("check", "scan [2]"), done.toList())
        assertTrue(infoLines.contains("Change check (back in front): scanning changed folders [2]"))
        assertEquals(false, work.scanState.value.isBusy)
    }

    @Test
    fun comingBackToTheFrontIsCheckedAtMostOncePerInterval() = runTest {
        // About 30 s, as specified; written out so a changed constant fails here.
        assertEquals(30_000L, ChangeChecks.FRONT_INTERVAL_MS)
        val work = started()

        work.onStart()
        runCurrent()
        wait(29_999)
        work.onStart()
        runCurrent()
        assertEquals("a second return within the interval asks nothing", listOf("check"), done.toList())
        assertTrue(infoLines.contains("Change check (back in front) skipped: the last one was 29 s ago"))

        wait(1)
        work.onStart()
        runCurrent()
        assertEquals(listOf("check", "check"), done.toList())
    }

    @Test
    fun aMountIsCheckedEvenRightAfterAReturnToTheFront() = runTest {
        statsOf = { stats(added = 1) }
        val work = started()
        work.onStart()
        runCurrent()

        wait(1_000)
        changes = changed(3)
        work.onStorageMounted()
        runCurrent()

        assertEquals(listOf("check", "check", "scan [3]"), done.toList())
        assertTrue(infoLines.contains("Change check (storage mounted): scanning changed folders [3]"))
    }

    @Test
    fun mountsCloseTogetherAreCheckedOnceAtTheEndOfTheInterval() = runTest {
        assertEquals(5_000L, ChangeChecks.MOUNT_INTERVAL_MS)
        val work = started()
        work.onStorageMounted()
        runCurrent()
        assertEquals(listOf("check"), done.toList())

        wait(1_000)
        work.onStorageMounted()
        wait(1_000)
        work.onStorageMounted()
        work.onStorageMounted()
        runCurrent()
        assertEquals("nothing before the interval ends", listOf("check"), done.toList())

        wait(3_000)
        assertEquals("one check for the three mounts", listOf("check", "check"), done.toList())
        assertTrue(infoLines.contains("Change check (storage mounted): joins the mount check already due"))
    }

    @Test
    fun aScanThatAddsGamesAsksForBoxArtAndOneThatAddsNothingDoesNot() = runTest {
        changes = changed(1)
        val work = started()

        statsOf = { stats(updated = 1) }
        work.onStorageMounted()
        runCurrent()
        assertEquals("a changed folder with no new game (a file renamed)", 0, scansEnded)

        statsOf = { stats(added = 1) }
        wait(5_000)
        work.onStorageMounted()
        runCurrent()
        assertEquals(1, scansEnded)
    }

    @Test
    fun boxArtWaitsWhileAFileMayStillBeCopiedAndIsAskedOnceItRests() = runTest {
        changes = changed(1)
        val work = started()

        statsOf = { stats(added = 1, stillWriting = 1) }
        work.onStart()
        runCurrent()
        assertEquals("the new file may be half copied", 0, scansEnded)
        assertTrue(infoLines.contains("Change check: 1 files may still be copied; box art waits for the next check"))

        // The copy ended: its directory is read again (stored as "read again") and finds it settled.
        statsOf = { stats() }
        wait(30_000)
        work.onStart()
        runCurrent()
        assertEquals(1, scansEnded)

        wait(30_000)
        work.onStart()
        runCurrent()
        assertEquals("owed once only", 1, scansEnded)
    }

    @Test
    fun aFailingCheckIsLoggedByClassAndFreesTheSlot() = runTest {
        changes = { throw IllegalStateException("content://x/tree/primary%3AROMs/Secret Quest.sfc") }
        val work = started()

        work.onStart()
        runCurrent()

        assertEquals(listOf("Startup change check failed (IllegalStateException)"), logLines.toList())
        assertEquals(emptyList<String>(), done.toList())
        // The slot is free again: Settings' Rescan runs.
        work.requestScan()
        runCurrent()
        assertEquals(listOf("full scan"), done.toList())
        assertEquals(false, work.scanState.value.isBusy)
    }

    @Test
    fun aMountDuringTheStartupScanIsCheckedWhenThatScanEnds() = runTest {
        scanGate = CompletableDeferred()
        statsOf = { stats(added = 1) }
        val work = work()
        assertNull("a start before the first frame does nothing", work.onStart())
        work.onContentShown()
        runCurrent()

        changes = changed(2)
        work.onStorageMounted()
        work.onStart()
        runCurrent()
        assertEquals("nothing while the startup scan runs", emptyList<String>(), done.toList())
        assertTrue(infoLines.any { it.startsWith("Change check (storage mounted) waits:") })

        scanGate?.complete(Unit)
        runCurrent()
        assertEquals(listOf("full scan", "check", "scan [2]"), done.toList())
    }

    @Test
    fun aMountDuringARescanIsCheckedRightAfterIt() = runTest {
        val work = started()
        scanGate = CompletableDeferred()
        work.requestScan()
        runCurrent()

        work.onStorageMounted()
        work.onStart()
        runCurrent()
        assertTrue(infoLines.any { it.startsWith("Change check (storage mounted) waits:") })
        assertTrue(infoLines.any { it.startsWith("Change check (back in front) waits:") })

        scanGate?.complete(Unit)
        runCurrent()
        assertEquals("one check for the mount, after the scan", listOf("full scan", "check"), done.toList())
    }

    @Test
    fun aReturnToTheFrontDuringAScanIsCheckedWhenItEnds() = runTest {
        val work = started()
        scanGate = CompletableDeferred()
        work.requestScan()
        runCurrent()

        work.onStart()
        runCurrent()
        scanGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("full scan", "check"), done.toList())
    }

    @Test
    fun aSlotFreedWhileStoppedChecksNothingUntilBackInFront() = runTest {
        val work = started()
        work.onStart()
        runCurrent()
        scanGate = CompletableDeferred()
        work.requestScan()
        runCurrent()
        work.onStorageMounted()
        runCurrent()

        // A game starts; the scan ends while it plays.
        work.onStop()
        scanGate?.complete(Unit)
        wait(10_000)
        assertEquals("no check while stopped", listOf("check", "full scan"), done.toList())

        // Back 10 s after the last check: the owed check runs at once, inside the 30 s interval.
        work.onStart()
        runCurrent()
        assertEquals(listOf("check", "full scan", "check"), done.toList())
        wait(1_000)
        work.onStart()
        runCurrent()
        assertEquals("owed once only, then the interval again", listOf("check", "full scan", "check"), done.toList())
    }

    @Test
    fun aRetryDueWhileStoppedRunsOnTheNextReturnInsideTheInterval() = runTest {
        changes = changed(unavailable = mapOf(2L to UnavailableReason.FOLDER_MISSING))
        val work = started()
        work.onStart()
        runCurrent()
        work.onStorageMounted()
        runCurrent()
        assertEquals(listOf("check", "check"), done.toList())

        wait(1_000)
        work.onStop()
        wait(10_000)
        assertEquals("the retry did not run while stopped", listOf("check", "check"), done.toList())
        assertTrue(infoLines.contains("Change check: 1 due checks wait until Plumemu is back in front"))

        changes = changed()
        wait(1_000)
        work.onStart()
        runCurrent()
        assertEquals(listOf("check", "check", "check"), done.toList())
        assertEquals(emptySet<Long>(), work.scanState.value.unavailableFolderIds)
    }

    @Test
    fun aMountDueWhileStoppedIsCheckedOnTheNextReturn() = runTest {
        val work = started()
        work.onStorageMounted()
        runCurrent()
        wait(1_000)
        work.onStorageMounted()
        work.onStop()
        wait(10_000)
        assertEquals("the coalesced mount did not run while stopped", listOf("check"), done.toList())

        work.onStart()
        runCurrent()
        assertEquals(listOf("check", "check"), done.toList())
    }

    @Test
    fun aRescanAskedForDuringACheckRunsRightAfterIt() = runTest {
        changes = changed(2)
        statsOf = { stats(added = 1) }
        val work = started()
        scanGate = CompletableDeferred()

        work.onStart()
        runCurrent()
        work.requestScan()
        assertTrue("busy from the request on", work.scanState.value.isBusy)
        scanGate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("check", "scan [2]", "full scan"), done.toList())
        assertEquals(false, work.scanState.value.isBusy)
    }

    @Test
    fun noCheckWhenScanOnStartupIsOff() = runTest {
        changes = changed(1)
        val work = started(settings(scanOnStartup = false))

        work.onStart()
        work.onStorageMounted()
        runCurrent()

        assertEquals(emptyList<String>(), done.toList())
    }

    @Test
    fun noCheckInTheProcessThatJustRestoredALibrary() = runTest {
        justRestored = true
        changes = changed(1)
        val work = started()

        assertNull(work.onStorageMounted())
        work.onStart()
        wait(60_000)

        assertEquals(emptyList<String>(), done.toList())
    }

    @Test
    fun aFolderUnreachableRightAfterAMountIsCheckedOnceMore() = runTest {
        assertEquals(5_000L, ChangeChecks.MOUNT_RETRY_MS)
        changes = changed(unavailable = mapOf(2L to UnavailableReason.FOLDER_MISSING))
        val work = started()

        work.onStorageMounted()
        runCurrent()
        assertEquals(setOf(2L), work.scanState.value.unavailableFolderIds)

        // The documents provider sees the card a moment later, and something changed on it.
        changes = changed(2)
        statsOf = { stats(added = 1) }
        wait(5_000)
        assertEquals(listOf("check", "check", "scan [2]"), done.toList())
        assertEquals(emptySet<Long>(), work.scanState.value.unavailableFolderIds)
    }

    @Test
    fun aFolderStillUnreachableAfterTheRetryIsLeftForTheNextReturn() = runTest {
        changes = changed(unavailable = mapOf(2L to UnavailableReason.FOLDER_MISSING))
        val work = started()

        work.onStorageMounted()
        wait(5_000)
        wait(60_000)

        assertEquals("the mount's check and one retry", listOf("check", "check"), done.toList())
        assertTrue(infoLines.contains("Change check: folders [2] still unreachable after a mount; checked again when Plumemu comes back to the front"))
        assertEquals(setOf(2L), work.scanState.value.unavailableFolderIds)
    }

    @Test
    fun aFolderThatStaysChangedAfterAScanThatChangedNothingIsPassedOverForTenMinutes() = runTest {
        assertEquals(10 * 60_000L, ChangeChecks.BACK_OFF_MS)
        // A provider with no directory times: the folder reads as changed after every scan.
        changes = changed(4)
        val work = started()

        work.onStart()
        runCurrent()
        assertEquals(listOf("check", "scan [4]", "recheck [4]"), done.toList())
        assertEquals(1, infoLines.count { it.contains("still read as changed after a scan that changed nothing") })

        done.clear()
        for (i in 1..19) {
            wait(30_000)
            work.onStart()
            runCurrent()
        }
        assertEquals("checked, never scanned, for 10 min", List(19) { "check" }, done.toList())
        assertEquals("logged once", 1, infoLines.count { it.contains("still read as changed") })

        done.clear()
        wait(30_000)
        work.onStart()
        runCurrent()
        assertEquals("then read again", listOf("check", "scan [4]", "recheck [4]"), done.toList())
    }
}
