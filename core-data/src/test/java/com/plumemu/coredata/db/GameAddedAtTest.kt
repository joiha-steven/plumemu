package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `game.added_at` (schema 17): the scan's insert writes it, nothing after it changes it (rescan,
 * update, rename, identification, a move), and [GameDao.observeAddedSince] counts it as the
 * system's list shows games.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameAddedAtTest {
    private lateinit var db: PlumDatabase
    private lateinit var scan: ScanDao
    private lateinit var games: GameDao
    private var folder = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        scan = db.scanDao()
        games = db.gameDao()
        folder = scan.insertFolder(RomFolderEntity(treeUri = "content://tree/1", displayName = "ROMs", addedAt = 1))
    }

    @After
    fun tearDown() = db.close()

    private val systems = listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("gba", "Game Boy Advance"))

    private suspend fun add(at: Long?, vararg rows: GameEntity): List<StoredGame> {
        val inserted = scan.applyBatch(ScanBatch(folder, systems, rows.toList(), addedAt = at))
        scan.finishScan(ScanFinish(folder))
        return inserted
    }

    private suspend fun addedAt(documentId: String): Long? = scan.gamesInFolder(folder).single { it.documentId == documentId }.addedAt

    @Test
    fun anEmptyLibraryCountsNothing() = runTest {
        assertEquals(0, games.observeAddedSince("snes", 0).first())
    }

    @Test
    fun onlyAFinishedScansGameInAPickedFolderMakesTheNextScanALaterOne() = runTest {
        assertEquals("empty library", false, scan.hasScannedGames())
        val shortcuts = scan.insertFolder(RomFolderEntity(treeUri = PinnedShortcutDao.FOLDER_TREE_URI, displayName = "", addedAt = 1))
        scan.applyBatch(ScanBatch(shortcuts, systems, listOf(game(shortcuts, "shortcut")), addedAt = null))
        scan.finishScan(ScanFinish(shortcuts))
        assertEquals("a shortcut row is not a scanned game", false, scan.hasScannedGames())
        scan.applyBatch(ScanBatch(folder, systems, listOf(game(folder, "zork.sfc")), addedAt = null))
        assertEquals("a stopped first scan's pending row", false, scan.hasScannedGames())
        scan.finishScan(ScanFinish(folder))
        assertEquals(true, scan.hasScannedGames())
        scan.forgetLibrary()
        assertEquals("after Reset library", false, scan.hasScannedGames())
    }

    @Test
    fun aFirstScanBatchWritesNoTime() = runTest {
        add(null, game(folder, "zork.sfc"))
        assertEquals(null, addedAt("zork.sfc"))
    }

    @Test
    fun aNewGameGetsTheScansTime() = runTest {
        add(1_000, game(folder, "zork.sfc"))
        assertEquals(1_000L, addedAt("zork.sfc"))
    }

    @Test
    fun aRescanAnUpdateARenameAndAnIdentificationNeverChangeIt() = runTest {
        val stored = add(1_000, game(folder, "zork.sfc")).single()
        // The same file found again by a later scan: the insert is ignored, its scan fields updated.
        add(5_000, game(folder, "zork.sfc", title = "Zork (Rev 1)").copy(sizeBytes = 2, modifiedAt = 9))
        assertEquals(1_000L, addedAt("zork.sfc"))
        // A changed file (size, name) updated in place.
        scan.applyBatch(
            ScanBatch(folder, changedGames = listOf(stored.copy(fields = stored.fields.copy(fileName = "zork2.sfc", sizeBytes = 3))), addedAt = 6_000),
        )
        scan.finishScan(ScanFinish(folder))
        assertEquals(1_000L, addedAt("zork.sfc"))
        // Identification renames the shown game.
        db.identifyDao().setShownName(stored.id, "Zork I", "zork i", "USA", NameTrustNames.IDENTIFIER_EXACT, isNotGame = false)
        assertEquals(1_000L, addedAt("zork.sfc"))
    }

    @Test
    fun aMovedFileKeepsTheOldRowsTime() = runTest {
        val old = add(1_000, game(folder, "old", relativePath = "snes/a.sfc")).single()
        val moved = scan.applyBatch(ScanBatch(folder, systems, listOf(game(folder, "new", relativePath = "snes/b/a.sfc")), addedAt = 8_000)).single()
        // While the scan runs the moved file's temporary row is pending: not counted as new.
        assertEquals(0, games.observeAddedSince("snes", 2_000).first())
        scan.finishScan(ScanFinish(folder, moves = listOf(GameMove(old.id, moved))))
        val after = scan.gamesInFolder(folder).single()
        assertEquals(old.id, after.id)
        assertEquals(1_000L, after.addedAt)
        assertEquals(0, games.observeAddedSince("snes", 2_000).first())
    }

    @Test
    fun theCountTakesOneSystemFromATimeAndLeavesOutWhatTheListLeavesOut() = runTest {
        add(1_000, game(folder, "old.sfc"))
        add(
            5_000,
            game(folder, "new.sfc"),
            game(folder, "hidden.sfc"),
            game(folder, "copy.sfc", isPrimaryCopy = false),
            game(folder, "bios.sfc"),
            game(folder, "new.gba", systemId = "gba"),
            game(folder, "new2.gba", systemId = "gba"),
        )
        val ids = scan.gamesInFolder(folder).associate { it.documentId to it.id }
        games.setHidden(ids.getValue("hidden.sfc"), true)
        db.identifyDao().setShownName(ids.getValue("bios.sfc"), null, null, "", NameTrustNames.IDENTIFIER_EXACT, isNotGame = true)
        // A row from before schema 17 (no time).
        db.openHelper.writableDatabase.execSQL("UPDATE game SET added_at = NULL WHERE id = ${ids.getValue("old.sfc")}")

        assertEquals("only the listed new game of the system", 1, games.observeAddedSince("snes", 5_000).first())
        assertEquals("a row without a time never counts", 1, games.observeAddedSince("snes", 0).first())
        assertEquals(2, games.observeAddedSince("gba", 4_999).first())
        assertEquals("after the scan's time", 0, games.observeAddedSince("gba", 5_001).first())
        assertEquals("a system with no games", 0, games.observeAddedSince("psx", 0).first())
    }
}
