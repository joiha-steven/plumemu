package com.plumemu.graph

import com.plumemu.library.scan.FolderOutcome
import com.plumemu.library.scan.ScanEvent
import com.plumemu.library.scan.ScanStats
import com.plumemu.library.scan.UnavailableReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Settings › Rescan library's numbers: they follow the scan's real events, walk first, then naming. */
class ScanReporterTest {
    private fun stats(files: Int = 0, added: Int = 0, removed: Int = 0) =
        ScanStats(directoriesListed = 1, directoriesUnchanged = 0, filesSeen = files, gamesAdded = added, gamesUpdated = 0, gamesRemoved = removed, skippedJunk = 0, skippedUnmatched = 0)

    private fun scanned(folder: Long, files: Int = 0, added: Int = 0, removed: Int = 0) = ScanEvent.FolderFinished(folder, FolderOutcome.Scanned(stats(files, added, removed)))

    @Test
    fun aPassStartsWithNoPhaseWork() {
        val report = ScanReporter().running()
        assertEquals(ScanPhase.Starting, report.phase)
        assertEquals(0, report.filesSeen)
        assertNull(report.result)
    }

    @Test
    fun theWalkThenTheNamingGrowTheCountsInOrder() {
        val reporter = ScanReporter()
        val walk = listOf(100, 400, 1200).map { reporter.onEvent(ScanEvent.Progress(1, 3, it, it / 2)) }
        assertEquals(listOf(100, 400, 1200), walk.map { it.filesSeen })
        assertEquals(setOf(ScanPhase.Reading), walk.map { it.phase }.toSet())
        reporter.onEvent(scanned(1, files = 1200))
        val naming = listOf(200, 830).map { reporter.onEvent(ScanEvent.Named(1, it, 2054)) }
        assertEquals(listOf(200, 830), naming.map { it.gamesNamed })
        assertEquals(setOf(2054), naming.map { it.gamesToName }.toSet())
        assertEquals(ScanPhase.Naming, naming.last().phase)
        assertEquals("the walk's count stays", 1200, naming.last().filesSeen)
    }

    @Test
    fun foldersWalkedCountsEveryFolderWhoseWalkEndedUnavailableOnesToo() {
        val reporter = ScanReporter()
        assertEquals(emptySet<Long>(), reporter.running().walkedFolderIds)
        assertEquals(setOf(1L), reporter.onEvent(scanned(1, files = 10)).walkedFolderIds)
        val both = reporter.onEvent(ScanEvent.FolderFinished(2, FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING)))
        assertEquals("setup looks for every folder it shows in this set to know the walk is over", setOf(1L, 2L), both.walkedFolderIds)
        assertEquals("naming does not change it", setOf(1L, 2L), reporter.onEvent(ScanEvent.Named(1, 5, 10)).walkedFolderIds)
    }

    @Test
    fun aFolderProvenDeletedCountsItsGamesAsRemovedAndIsNotUnavailable() {
        val reporter = ScanReporter()
        reporter.onEvent(scanned(1, files = 10, removed = 2))
        val report = reporter.onEvent(ScanEvent.FolderFinished(2, FolderOutcome.Deleted(gamesRemoved = 7, removedBySystem = mapOf("snes" to 7))))
        assertEquals("its walk ended, so setup does not wait for it", setOf(1L, 2L), report.walkedFolderIds)
        val result = reporter.finished(ok = true).result!!
        assertEquals(9, result.gamesRemoved)
        assertEquals(emptyList<UnavailableFolder>(), result.unavailable)
        assertEquals("only folder 1 was scanned", 1, result.scannedFolders)
    }

    @Test
    fun theTotalToNameIsShownOnlyOnceTheLastFolderIsBeingNamed() {
        val reporter = ScanReporter()
        // Every walk first, then naming folder by folder.
        reporter.onEvent(scanned(1, files = 500))
        reporter.onEvent(scanned(2, files = 1500))
        val first = reporter.onEvent(ScanEvent.Named(1, 500, 500))
        assertEquals("500 of 500 would look done while folder 2 still waits", 0, first.gamesToName)
        assertEquals(500, first.gamesNamed)
        val last = reporter.onEvent(ScanEvent.Named(2, 600, 1554))
        assertEquals(1554 + 500, last.gamesToName)
        assertEquals(1100, last.gamesNamed)
    }

    @Test
    fun severalFoldersAddUpAndNeverCountOneTwice() {
        val reporter = ScanReporter()
        reporter.onEvent(ScanEvent.Progress(1, 1, 300, 10))
        reporter.onEvent(ScanEvent.Progress(1, 2, 500, 20))
        reporter.onEvent(scanned(1, files = 500))
        reporter.onEvent(ScanEvent.Named(1, 20, 20))
        val second = reporter.onEvent(ScanEvent.Progress(2, 1, 100, 5))
        assertEquals(600, second.filesSeen)
        assertEquals(ScanPhase.Reading, second.phase)
        assertEquals("the first folder's naming stays in the sum", 20, second.gamesNamed)
        assertEquals(20, second.gamesToName)
    }

    @Test
    fun theResultSumsAddedAndRemovedOverEveryFolder() {
        val reporter = ScanReporter()
        reporter.onEvent(scanned(1, added = 3, removed = 1))
        reporter.onEvent(scanned(2, added = 0, removed = 1))
        val result = reporter.finished(ok = true).result!!
        assertEquals(2, result.scannedFolders)
        assertEquals(3, result.gamesAdded)
        assertEquals(2, result.gamesRemoved)
        assertEquals(emptyList<UnavailableFolder>(), result.unavailable)
    }

    @Test
    fun anUnavailableFolderIsNamedWithItsReasonAndAddsNoCounts() {
        val reporter = ScanReporter()
        reporter.onEvent(scanned(1, added = 4))
        reporter.onEvent(ScanEvent.FolderFinished(2, FolderOutcome.Unavailable(UnavailableReason.FOLDER_MISSING)))
        val result = reporter.finished(ok = true).result!!
        assertEquals(1, result.scannedFolders)
        assertEquals(4, result.gamesAdded)
        assertEquals(listOf(UnavailableFolder(2, UnavailableReason.FOLDER_MISSING)), result.unavailable)
    }

    @Test
    fun aScanThatCouldNotRunIsAFailure() {
        val result = ScanReporter().finished(ok = false).result!!
        assertEquals(true, result.failed)
    }
}
