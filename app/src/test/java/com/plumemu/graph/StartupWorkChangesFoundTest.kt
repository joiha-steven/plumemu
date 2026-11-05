package com.plumemu.graph

import com.plumemu.coredata.settings.LibrarySettings
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SetupFlags
import com.plumemu.library.matching.FOLDER_MATCH_VERSION
import com.plumemu.library.scan.FolderChanges
import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanStats
import com.plumemu.library.scan.UnknownFolder
import com.plumemu.telemetry.ScanTrigger
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The home is told what a scan found ("+24 games") only for scans that stamped added_at, from a change check or a
 * folder added in Settings; the startup scan, a manual rescan and setup's scans stay silent.
 */
class StartupWorkChangesFoundTest {
    private val infoLines = CopyOnWriteArrayList<String>()
    private val found = CopyOnWriteArrayList<Collection<ScanStats>>()
    private var startupStats = added(7)
    /** Folders whose scan stamps no added_at (setup, Try again, the first scan after Reset library). */
    private var unstamped = emptySet<Long>()
    private var scanFails = false
    private var changes = FolderChanges(setOf(2L), emptyMap(), foldersChecked = 2, directoriesAsked = 3)

    private val tasks = object : StartupTasks {
        override suspend fun refreshApps() = Unit

        override suspend fun detectEmulators() = Unit

        override suspend fun refreshDefaults() = Unit

        override suspend fun setRescanAfterReset(pending: Boolean) = Unit

        override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
            finish(1, startupStats, onEvent)
            return !scanFails
        }

        override suspend fun changedFolders(onlyFolderIds: Set<Long>?): FolderChanges = changes

        override suspend fun scanFolders(folderIds: Set<Long>, onEvent: (ScanEvent) -> Unit): Boolean {
            for (id in folderIds.sorted()) finish(id, added(3, stamped = id !in unstamped), onEvent)
            return true
        }

        override suspend fun scanEnded() = Unit

        override suspend fun finishRestore(): Boolean = false
    }

    /** What the scanner sends: the folder's stats, then (when it stamped) the count after naming, one fewer than the rows added. */
    private fun finish(id: Long, stats: ScanStats, onEvent: (ScanEvent) -> Unit) {
        onEvent(ScanEvent.FolderFinished(id, FolderOutcome.Scanned(stats)))
        if (stats.stampedAddedAt) onEvent(ScanEvent.AddedListed(id, mapOf("snes" to stats.gamesAdded - 1)))
    }

    private fun listed(stats: Collection<ScanStats>) = stats.map { it.listedAddedBySystem.values.sum() }

    private fun added(n: Int, stamped: Boolean = true) = ScanStats(1, 5, 3, n, 0, 0, 0, 0, 0, stampedAddedAt = stamped)

    private fun withUnknown(vararg names: String) = ScanStats(1, 5, 3, 0, 0, 0, 0, 0, 0, newUnknownFolders = names.map { UnknownFolder(it, 2) })

    private fun TestScope.work() =
        StartupWork(
            backgroundScope,
            MutableStateFlow<PlumSettings?>(
                PlumSettings(
                    library = LibrarySettings(scanOnStartup = true),
                    flags = SetupFlags(setupCompleted = true, folderMatchVersion = FOLDER_MATCH_VERSION),
                ),
            ),
            tasks,
            info = { infoLines += it },
            clock = { testScheduler.currentTime },
            onChangesFound = { found += it },
        ) {}

    @Test
    fun theStartupScanAndAManualRescanAreSilent() = runTest {
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        assertEquals("the startup scan", 0, found.size)
        work.requestScan(ScanTrigger.Manual)
        runCurrent()
        assertEquals("a manual rescan", 0, found.size)
    }

    @Test
    fun aChangeCheckThatScannedTellsWhatItFound() = runTest {
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        work.onStart()
        runCurrent()
        assertEquals(infoLines.toString(), 1, found.size)
        assertEquals(listOf(2), listed(found.single()))
    }

    @Test
    fun aFolderAddedInSettingsReachesTheNoticeOnlyWhenItsScanStamped() = runTest {
        startupStats = added(4, stamped = true)
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        assertEquals("the startup scan", 0, found.size)
        work.requestScan(ScanTrigger.FolderAdded)
        runCurrent()
        assertEquals(listOf(3), listed(found.single()))

        found.clear()
        startupStats = added(4, stamped = false)
        work.requestScan(ScanTrigger.FolderAdded)
        runCurrent()
        assertEquals("a scan that stamped nothing tells nothing", listOf(emptyList<ScanStats>()), found.map { it.toList() })
    }

    @Test
    fun aFolderAddMergedIntoAPendingManualRequestStillTellsTheNotice() = runTest {
        startupStats = added(4, stamped = true)
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        found.clear()
        work.requestScan(ScanTrigger.Manual)
        work.requestScan(ScanTrigger.FolderAdded)
        runCurrent()
        assertEquals(listOf(3), listed(found.single()))
    }

    @Test
    fun aFolderAddWhoseScanFailedIsKeptForTheNextFullScan() = runTest {
        startupStats = added(4, stamped = true)
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        found.clear()
        scanFails = true
        work.requestScan(ScanTrigger.FolderAdded)
        runCurrent()
        assertEquals("a failed scan tells nothing", 0, found.size)
        scanFails = false
        work.requestScan(ScanTrigger.Manual)
        runCurrent()
        assertEquals("the next full scan still tells about the added folder", listOf(3), listed(found.single()))
    }

    @Test
    fun aScanThatStampedNothingIsNotNews() = runTest {
        changes = FolderChanges(setOf(2L, 3L), emptyMap(), foldersChecked = 3, directoriesAsked = 3)
        unstamped = setOf(3L)
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        work.onStart()
        runCurrent()
        assertEquals("only the scan that stamped added_at counts", listOf(2), listed(found.single()))
        assertEquals(1, found.single().size)
    }

    @Test
    fun aCheckWhoseScansStampedNothingTellsNothing() = runTest {
        changes = FolderChanges(setOf(5L), emptyMap(), foldersChecked = 1, directoriesAsked = 1)
        unstamped = setOf(5L)
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        work.onStart()
        runCurrent()
        assertEquals(listOf(emptyList<ScanStats>()), found.map { it.toList() })
    }

    @Test
    fun theScanAfterAnUpgradeThatListedEveryFolderAgainDoesNotAnnounceTheUnknownFoldersItFinds() = runTest {
        // The folder rules changed (stored version older): the startup scan lists every folder and so reports
        // every existing unknown folder as new. Only a change check reaches the home, so none is announced.
        startupStats = withUnknown("Xbox 360", "Wii U")
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        assertEquals(0, found.size)
        val notices = LibraryNotices()
        notices.checked(found.flatten())
        assertEquals(null, notices.notice.value)
    }
}
