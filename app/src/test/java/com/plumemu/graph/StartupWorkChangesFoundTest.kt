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
 * Only a change check tells the home what it found ("+24 games"): the startup scan, a manual rescan,
 * setup's scan and the first scan of a new folder stay silent.
 */
class StartupWorkChangesFoundTest {
    private val infoLines = CopyOnWriteArrayList<String>()
    private val found = CopyOnWriteArrayList<Collection<ScanStats>>()
    private var startupStats = added(7)
    private var changes = FolderChanges(setOf(2L), emptyMap(), foldersChecked = 2, directoriesAsked = 3)

    private val tasks = object : StartupTasks {
        override suspend fun refreshApps() = Unit

        override suspend fun detectEmulators() = Unit

        override suspend fun refreshDefaults() = Unit

        override suspend fun setRescanAfterReset(pending: Boolean) = Unit

        override suspend fun scanLibrary(onEvent: (ScanEvent) -> Unit): Boolean {
            onEvent(ScanEvent.FolderFinished(1, FolderOutcome.Scanned(startupStats)))
            return true
        }

        override suspend fun changedFolders(onlyFolderIds: Set<Long>?): FolderChanges = changes

        override suspend fun scanFolders(folderIds: Set<Long>, onEvent: (ScanEvent) -> Unit): Boolean {
            for (id in folderIds.sorted()) onEvent(ScanEvent.FolderFinished(id, FolderOutcome.Scanned(added(3))))
            return true
        }

        override suspend fun scanEnded() = Unit

        override suspend fun finishRestore(): Boolean = false
    }

    private fun added(n: Int) = ScanStats(1, 5, 3, n, 0, 0, 0, 0, 0)

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
        assertEquals(listOf(3), found.single().map { it.gamesAdded })
    }

    @Test
    fun aFolderOnItsFirstScanIsNotNews() = runTest {
        changes = FolderChanges(setOf(2L, 3L), emptyMap(), foldersChecked = 3, directoriesAsked = 3, neverScannedFolderIds = setOf(3L))
        val work = work()
        work.onStart()
        work.onContentShown()
        runCurrent()
        work.onStart()
        runCurrent()
        assertEquals("only the folder that had been scanned before counts", listOf(3), found.single().map { it.gamesAdded })
        assertEquals(1, found.single().size)
    }

    @Test
    fun anAllNewFolderCheckTellsNothing() = runTest {
        changes = FolderChanges(setOf(5L), emptyMap(), foldersChecked = 1, directoriesAsked = 1, neverScannedFolderIds = setOf(5L))
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
