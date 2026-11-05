package com.plumemu.library.scan

import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.library.TestRules
import com.plumemu.library.catalog.CatalogSettings
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `game.added_at` through the real scan: no scan before setup is finished (one per chosen folder,
 * Try again) and not the first scan after Reset library (setup stays finished, the library is
 * empty) writes one, so day one never reads as hundreds of new games; a later scan stamps the
 * files it adds with its clock and leaves every stored row as it was.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddedAtScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var f: ScanFixture

    @Before
    fun setUp() {
        f = ScanFixture(temp.root)
    }

    @After
    fun tearDown() = f.close()

    private suspend fun addedTimes(): Map<String, Long?> = f.games().associate { it.fileName to it.addedAt }

    private fun addSnesFiles(vararg names: String) {
        for (name in names) File(f.roms, "snes/$name").writeBytes(ByteArray(512) { it.toByte() })
        f.touch("snes")
    }

    @Test
    fun theFirstScanOfAnEmptyLibraryStampsNoGame() = runTest {
        f.scan()
        val times = addedTimes()
        assertTrue("the fixture has games", times.size > 2)
        assertEquals(emptySet<Long>(), times.values.filterNotNull().toSet())
        assertEquals(0, f.db.gameDao().observeAddedSince("snes", 0).first())
    }

    @Test
    fun aLaterScanStampsOnlyTheFilesItAdds() = runTest {
        f.scan()
        val before = addedTimes()
        f.now = 5_000
        addSnesFiles("Zork One (USA).sfc", "Zork Two (USA).sfc")

        val stats = f.scan()

        assertEquals(2, stats.gamesAdded)
        val after = addedTimes()
        assertEquals(5_000L, after["Zork One (USA).sfc"])
        assertEquals(5_000L, after["Zork Two (USA).sfc"])
        assertEquals("stored rows keep no time", before, after - "Zork One (USA).sfc" - "Zork Two (USA).sfc")
        assertEquals(2, f.db.gameDao().observeAddedSince("snes", 5_000).first())
    }

    @Test
    fun theScanAfterResetLibraryIsAFirstScanAgain() = runTest {
        f.scan()
        f.now = 5_000
        addSnesFiles("Zork One (USA).sfc")
        f.scan()
        assertEquals(5_000L, addedTimes()["Zork One (USA).sfc"])

        f.db.scanDao().forgetLibrary()
        f.now = 9_000
        f.scan()

        assertEquals(emptySet<Long>(), addedTimes().values.filterNotNull().toSet())
        assertEquals(0, f.db.gameDao().observeAddedSince("snes", 0).first())
    }

    @Test
    fun setupScanningTwoChosenFoldersOneAfterTheOtherStampsNoGame() = runTest {
        f.setupDone = false
        val scanner = f.scanner()
        scanner.scan(setOf(f.folderId)).toList()
        // The second folder chosen in setup starts its own scan once the first one has games.
        val second = f.db.scanDao().insertFolder(RomFolderEntity(treeUri = SECOND_URI, displayName = "More", addedAt = 0))
        f.permissions.held += SECOND_URI
        f.now = 5_000
        scanner.scan(setOf(second)).toList()

        val secondRows = f.db.scanDao().gamesInFolder(second)
        assertTrue("both folders have games", secondRows.isNotEmpty())
        assertEquals(emptySet<Long>(), (f.games() + secondRows).mapNotNull { it.addedAt }.toSet())
    }

    @Test
    fun tryAgainDuringSetupStampsNoGame() = runTest {
        f.setupDone = false
        f.scan()
        f.now = 5_000
        addSnesFiles("Zork One (USA).sfc")
        f.scan()
        assertTrue("Zork One (USA).sfc" in addedTimes())
        assertEquals(emptySet<Long>(), addedTimes().values.filterNotNull().toSet())
    }

    @Test
    fun theFirstScanAfterSetupIsDoneStampsTheFilesItAdds() = runTest {
        f.setupDone = false
        f.scan()
        f.setupDone = true
        f.now = 5_000
        addSnesFiles("Zork One (USA).sfc", "Zork Two (USA).sfc")
        f.scan()
        val stamped = addedTimes().filterValues { it != null }
        assertEquals(mapOf("Zork One (USA).sfc" to 5_000L, "Zork Two (USA).sfc" to 5_000L), stamped)
    }

    /** The folder's stats the way the app folds them: the finished event plus the count after naming. */
    private fun statsOf(events: List<ScanEvent>): ScanStats {
        val stats = (events.filterIsInstance<ScanEvent.FolderFinished>().single().outcome as FolderOutcome.Scanned).stats
        return events.filterIsInstance<ScanEvent.AddedListed>().fold(stats) { acc, listed -> acc.withListed(listed) }
    }

    private suspend fun scanStats(scanner: LibraryScanner, folderId: Long): ScanStats = statsOf(scanner.scan(setOf(folderId)).toList())

    /** What the home notice shows per system and what the second screen shows per system: one number. */
    private suspend fun assertNoticeAgreesWithSecondScreen(stats: ScanStats, since: Long, expectedTotal: Int) {
        assertEquals(expectedTotal, stats.gamesAdded)
        assertEquals(expectedTotal, stats.listedAddedBySystem.values.sum())
        for ((system, n) in stats.listedAddedBySystem) assertEquals(system, n, f.db.gameDao().observeAddedSince(system, since).first())
    }

    /** A scanner whose second picked folder holds files of its own: two new SNES games and one GBA game. */
    private fun scannerWithSecondFolder(
        files: List<String> = listOf("snes/Zork One (USA).sfc", "snes/Zork Two (USA).sfc", "gba/Zork Three (USA).gba"),
    ): LibraryScanner {
        val roms = File(temp.root, "second/ROMs")
        for ((i, name) in files.withIndex()) {
            File(roms, name).also { it.parentFile.mkdirs() }.writeBytes(ByteArray(512 + i) { it.toByte() })
        }
        roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        val second = FileSystemDocumentTree(roms)
        return LibraryScanner(
            f.db, f.permissions, { folder -> if (folder.treeUri == SECOND_URI) second else f.tree }, TestRules.ALL, CatalogSettings(),
            LibraryScanner.DEFAULT_BATCH_SIZE, clock = { f.now }, isSetupDone = { f.setupDone },
        )
    }

    @Test
    fun aFolderAddedAfterSetupIsNewsAndItsNumberIsTheSecondScreens() = runTest {
        val scanner = scannerWithSecondFolder()
        scanner.scan(setOf(f.folderId)).toList()
        val second = f.db.scanDao().insertFolder(RomFolderEntity(treeUri = SECOND_URI, displayName = "More", addedAt = 0))
        f.permissions.held += SECOND_URI
        f.now = 5_000

        val stats = scanStats(scanner, second)

        assertTrue("setup is done and the library has games: this scan stamps, so it is news", stats.stampedAddedAt)
        assertEquals(mapOf("snes" to 2, "gba" to 1), stats.listedAddedBySystem)
        assertNoticeAgreesWithSecondScreen(stats, since = 5_000, expectedTotal = 3)
    }

    @Test
    fun aEuropeAndAUsaCopyInTheNewFolderAreOneGameInBothNumbers() = runTest {
        val scanner = scannerWithSecondFolder(listOf("snes/Zork One (USA).sfc", "snes/Zork One (Europe).sfc", "snes/Zork Two (USA).sfc"))
        scanner.scan(setOf(f.folderId)).toList()
        val second = f.db.scanDao().insertFolder(RomFolderEntity(treeUri = SECOND_URI, displayName = "More", addedAt = 0))
        f.permissions.held += SECOND_URI
        f.now = 5_000

        val stats = scanStats(scanner, second)

        assertEquals("three files were added", 3, stats.gamesAdded)
        assertEquals("the lists show two games: the copies are one", mapOf("snes" to 2), stats.listedAddedBySystem)
        assertEquals(2, f.db.gameDao().observeAddedSince("snes", 5_000).first())
    }

    @Test
    fun anArcadeCloneAndANotAGameFileAreNotInTheNoticeOrOnTheCard() = runTest {
        val dir = temp.newFolder("arcade-fixture")
        val fx = ScanFixture(dir, copyFixtures = false)
        try {
            val naming = TestNaming.naming(temp.newFolder("packs"))
            fun write(path: String) = File(fx.roms, path).apply {
                parentFile?.mkdirs()
                writeBytes(TestNaming.zip("x.rom", ByteArray(16)))
            }
            var step = 0L
            suspend fun scan(): ScanStats {
                val time = ScanFixture.BASE_TIME + 10_000L * step++
                fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(time) }
                return statsOf(fx.scanner(naming = naming).scan().toList())
            }
            write("arcade/zorkqst.zip")
            scan()
            fx.now = 5_000
            write("arcade/zorkfght.zip")
            write("arcade/zorkfghtu.zip")
            write("arcade/testbios.zip")

            val stats = scan()

            assertTrue(stats.stampedAddedAt)
            assertEquals("three files were added", 3, stats.gamesAdded)
            assertEquals("the clone and the BIOS are not listed: one game", mapOf("arcade" to 1), stats.listedAddedBySystem)
            assertEquals(1, fx.db.gameDao().observeAddedSince("arcade", 5_000).first())
        } finally {
            fx.close()
        }
    }

    @Test
    fun aStoredFutureTimeIsNotCountedByALaterScanThatAddsNothing() = runTest {
        val scanner = f.scanner()
        scanner.scan().toList()
        f.db.openHelper.writableDatabase.execSQL("UPDATE game SET added_at = 99999")
        f.now = 5_000
        f.touch("snes", step = 2)

        val stats = scanStats(scanner, f.folderId)

        assertTrue(stats.stampedAddedAt)
        assertEquals(0, stats.gamesAdded)
        assertEquals(emptyMap<String, Int>(), stats.listedAddedBySystem)
    }

    @Test
    fun setupScansAreNotNewsAndBothNumbersAreZero() = runTest {
        f.setupDone = false
        val scanner = f.scanner()
        val first = scanStats(scanner, f.folderId)
        val second = f.db.scanDao().insertFolder(RomFolderEntity(treeUri = SECOND_URI, displayName = "More", addedAt = 0))
        f.permissions.held += SECOND_URI
        f.now = 5_000
        val next = scanStats(scanner, second)

        for (stats in listOf(first, next)) assertEquals(false, stats.stampedAddedAt)
        for (system in (first.addedBySystem.keys + next.addedBySystem.keys)) assertEquals(0, f.db.gameDao().observeAddedSince(system, 0).first())
    }

    @Test
    fun theFirstScanAfterResetLibraryIsNotNews() = runTest {
        val scanner = f.scanner()
        scanner.scan().toList()
        f.db.scanDao().forgetLibrary()
        f.now = 9_000
        val stats = scanStats(scanner, f.folderId)
        assertEquals(false, stats.stampedAddedAt)
        for (system in stats.addedBySystem.keys) assertEquals(0, f.db.gameDao().observeAddedSince(system, 0).first())
    }

    private companion object {
        const val SECOND_URI = "content://test/tree/more"
    }
}
