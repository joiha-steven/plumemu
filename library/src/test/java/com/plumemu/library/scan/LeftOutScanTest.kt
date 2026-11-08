package com.plumemu.library.scan

import com.plumemu.coredata.db.LeftOutFileDao
import com.plumemu.coredata.db.LeftOutFileEntity
import com.plumemu.coredata.db.PlumMigrations
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Files not added: the scan keeps what the catalogue left out, each file once with its reason,
 * from the same listing (each directory is listed once); parts of games (a cue's bin, a later
 * disc, a regional copy) get none; a rescan replaces only the directories it listed again; a gone
 * directory loses its rows; a folder of thousands of pictures stores a bounded number of rows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LeftOutScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val fixture by lazy { ScanFixture(temp.root, copyFixtures = false) }

    @After
    fun tearDown() = fixture.close()

    private fun file(path: String) = File(fixture.roms, path).also { it.parentFile?.mkdirs() }.writeBytes(ByteArray(16))

    private fun library() {
        file("notes.txt")
        file("Mystery.iso")
        file("psx/scph1001.bin")
        file("psx/Game (USA).cue")
        file("psx/Game (USA).bin")
        file("psx/Game (USA).srm")
        file("psx/Game (USA).state1")
        file("psx/tool.exe")
        file("psx/Saga (USA) (Disc 1).chd")
        file("psx/Saga (USA) (Disc 2).chd")
        file("snes/Mario (USA).sfc")
        file("snes/Mario (Europe).sfc")
        file("snes/._Mario (USA).sfc")
        file("Stuff/Other.iso")
        file("Stuff/data.xyz")
        file("media/cover.png")
        fixture.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
    }

    private suspend fun leftOut(): Map<String, Pair<String, String?>> =
        fixture.db.leftOutFileDao().inFolder(fixture.folderId).associate { (it.relativePath ?: "more:${it.directoryPath}") to (it.reason to it.systemId) }

    @Test
    fun eachLeftOutFileIsStoredOnceWithItsReasonAndPartsOfGamesHaveNone() = runTest {
        library()
        fixture.scan()
        assertEquals(
            mapOf(
                "notes.txt" to ("junk" to null),
                "Mystery.iso" to ("no_system" to null),
                "psx/scph1001.bin" to ("bios" to "psx"),
                "psx/Game (USA).srm" to ("save" to "psx"),
                "psx/Game (USA).state1" to ("save" to "psx"),
                "psx/tool.exe" to ("unmatched" to "psx"),
                "snes/._Mario (USA).sfc" to ("junk" to "snes"),
                "Stuff/Other.iso" to ("no_system" to null),
                "Stuff/data.xyz" to ("unmatched" to null),
            ),
            leftOut(),
        )
        val games = fixture.games().map { it.relativePath }.toSet()
        assertTrue("the games are in the library: $games", games.containsAll(setOf("psx/Game (USA).cue", "psx/Saga (USA) (Disc 1).chd", "snes/Mario (USA).sfc", "snes/Mario (Europe).sfc")))
    }

    @Test
    fun theReasonsComeFromTheScansOwnListingEachDirectoryListedOnce() = runTest {
        library()
        fixture.scan()
        val listed = fixture.tree.listed.toList()
        assertEquals("no directory listed twice: $listed", listed.distinct(), listed)
        assertEquals("root, psx, snes, Stuff; never media/", 4, listed.size)
    }

    @Test
    fun aRescanKeepsUnchangedDirectoriesRowsAndReplacesTheChangedOnes() = runTest {
        library()
        fixture.scan()
        File(fixture.roms, "psx/Game (USA).srm").delete()
        file("psx/Game (USA).sav")
        fixture.touch("psx", step = 2)
        File(fixture.roms, "Stuff").deleteRecursively()
        fixture.touch("", step = 2)
        fixture.scan()
        val listed = fixture.tree.listed.map { it.removePrefix("fs:") }
        assertTrue("snes/ was not listed again: $listed", listed.none { it.startsWith("snes") })
        assertEquals(
            mapOf(
                "notes.txt" to ("junk" to null),
                "Mystery.iso" to ("no_system" to null),
                "psx/scph1001.bin" to ("bios" to "psx"),
                "psx/Game (USA).sav" to ("save" to "psx"),
                "psx/Game (USA).state1" to ("save" to "psx"),
                "psx/tool.exe" to ("unmatched" to "psx"),
                "snes/._Mario (USA).sfc" to ("junk" to "snes"),
            ),
            leftOut(),
        )
    }

    @Test
    fun aDirectoryMadeAgainAtTheSamePathKeepsTheNewListingsRows() = runTest {
        library()
        fixture.scan()
        File(fixture.roms, "psx").deleteRecursively()
        file("psx/scph1001.bin")
        file("psx/tool.exe")
        fixture.tree.recreatedDirectories["psx"] = "new:psx"
        fixture.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        fixture.touch("", step = 2)
        fixture.scan()
        assertTrue("psx/ was listed by its new id: ${fixture.tree.listed}", "new:psx" in fixture.tree.listed)
        val psx = leftOut().filterKeys { it.startsWith("psx/") }
        assertEquals(mapOf("psx/scph1001.bin" to ("bios" to "psx"), "psx/tool.exe" to ("unmatched" to "psx")), psx)
        assertEquals(2, fixture.db.leftOutFileDao().inFolder(fixture.folderId).count { it.directoryPath == "psx" })
    }

    @Test
    fun aDirectoryMadeAgainAtTheSamePathIsRecordedSoALaterAdditionIsFound() = runTest {
        library()
        fixture.scan()
        File(fixture.roms, "psx").deleteRecursively()
        file("psx/Game (USA).chd")
        fixture.tree.recreatedDirectories["psx"] = "new:psx"
        fixture.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        fixture.touch("", step = 2)
        fixture.scan()
        val psx = fixture.db.scanDao().directories(fixture.folderId).filter { it.relativePath == "psx" }
        assertEquals("the new psx/ is recorded by its new id", listOf("new:psx"), psx.map { it.documentId })
        // Something added to it later changes only its own time: the rescan must ask it.
        file("psx/Other (USA).chd")
        fixture.touch("psx", step = 3)
        fixture.scan()
        assertTrue("the later addition is found: ${fixture.games().map { it.fileName }}", fixture.games().any { it.fileName == "Other (USA).chd" })
    }

    @Test
    fun anUpgradedLibraryListsEachDirectoryOnceAndKeepsItsGames() = runTest {
        library()
        fixture.scan()
        val before = fixture.games().associateBy { it.id }
        // A library scanned before schema 15: no left-out rows, every directory unchanged.
        fixture.db.openHelper.writableDatabase.execSQL("DELETE FROM left_out_file")
        PlumMigrations.MIGRATION_14_15.migrate(fixture.db.openHelper.writableDatabase)
        val stats = fixture.scan()
        assertEquals("every directory listed once again", 4, fixture.tree.listed.size)
        assertEquals("psx/scph1001.bin" to ("bios" to "psx"), "psx/scph1001.bin" to leftOut().getValue("psx/scph1001.bin"))
        assertEquals("notes.txt" to ("junk" to null), "notes.txt" to leftOut().getValue("notes.txt"))
        assertEquals(9, leftOut().size)
        val after = fixture.games().associateBy { it.id }
        assertEquals("the same rows, unchanged", before, after)
        assertTrue("nothing to name again", after.values.none { it.needsIdentify && !before.getValue(it.id).needsIdentify })
        assertEquals(0, stats.gamesAdded)
        assertEquals(0, stats.gamesUpdated)
        assertEquals(0, stats.gamesRemoved)
        fixture.scan()
        assertEquals("then a rescan lists nothing again", 0, fixture.tree.listed.size)
    }

    @Test
    fun aSubfolderThePersonSkipsLosesItsRows() = runTest {
        library()
        fixture.scan()
        fixture.scanner().skipSubfolder(fixture.folderId, "Stuff", skip = true)
        fixture.scan()
        assertTrue(leftOut().keys.none { it.startsWith("Stuff/") })
    }

    @Test
    fun aJunkHeavyFolderStoresABoundedNumberOfRowsAndCountsTheRest() = runTest {
        val pictures = 5_000
        repeat(pictures) { file("snes/Art/%05d.png".format(it)) }
        file("snes/Mario (USA).sfc")
        fixture.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }
        val started = System.nanoTime()
        fixture.scan()
        val millis = (System.nanoTime() - started) / 1_000_000
        val rows = fixture.db.leftOutFileDao().inFolder(fixture.folderId)
        assertEquals(LeftOutFileDao.MAX_ROWS_PER_REASON, rows.count { it.relativePath != null })
        assertEquals(pictures, rows.sumOf { if (it.relativePath == null) it.more else 1 })
        val bytes = rows.sumOf { rowBytes(it) }
        println("left-out: $pictures pictures scanned in $millis ms; ${rows.size} rows stored, about $bytes bytes of text")
        assertTrue("rows stay small: $bytes bytes", bytes < 64_000)
    }

    private fun rowBytes(row: LeftOutFileEntity): Int =
        row.directoryPath.length + (row.relativePath?.length ?: 0) + row.reason.length + (row.systemId?.length ?: 0) + 16
}
