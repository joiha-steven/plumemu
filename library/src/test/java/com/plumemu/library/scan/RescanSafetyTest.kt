package com.plumemu.library.scan

import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.ScanBatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** Moves, renames, ghosts, clock granularity and concurrent scans: nothing the person marked is lost. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RescanSafetyTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var f: ScanFixture

    @Before
    fun setUp() {
        f = ScanFixture(temp.root)
    }

    @After
    fun tearDown() = f.close()

    /** Marks a game as favourite and hidden, gives it play time and its own emulator. */
    private suspend fun markEverything(game: GameEntity) {
        f.db.gameDao().setFavorite(game.id, true)
        f.db.gameDao().setHidden(game.id, true)
        f.db.playDao().addSession(game.id, endedAt = 100, seconds = 600)
        f.db.openHelper.writableDatabase.execSQL(
            "INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) " +
                "VALUES ('${game.systemId}', ${game.id}, 'com.example.emu', 1)",
        )
    }

    private suspend fun assertMarksKept(id: Long) {
        val game = f.db.gameDao().game(id) ?: error("game $id was deleted")
        assertTrue("favourite", game.isFavorite)
        assertTrue("hidden", game.isHidden)
        assertEquals(600L, f.db.playDao().play(id)?.totalSeconds)
        assertEquals("com.example.emu", f.db.emulatorChoiceDao().gameChoice(id)?.emulatorId)
    }

    @Test
    fun renamedSystemFolderKeepsFavouriteHiddenPlayAndEmulator() = runTest {
        f.scan()
        val zelda = f.games().single { it.title.contains("Zelda") }
        markEverything(zelda)
        File(f.roms, "snes").renameTo(File(f.roms, "Super Nintendo"))
        f.touch("")
        val stats = f.scan()
        assertMarksKept(zelda.id)
        assertEquals("Super Nintendo/Legend of Zelda, The - A Link to the Past (USA).sfc", f.db.gameDao().game(zelda.id)?.relativePath)
        assertEquals(0, stats.gamesAdded)
        assertEquals(0, stats.gamesRemoved)
        assertEquals(11, f.games().size)
    }

    @Test
    fun renamedFileKeepsItsMarks() = runTest {
        f.scan()
        val zelda = f.games().single { it.title.contains("Zelda") }
        markEverything(zelda)
        File(f.roms, "snes/Legend of Zelda, The - A Link to the Past (USA).sfc")
            .renameTo(File(f.roms, "snes/Legend of Zelda, The - A Link to the Past (USA) [!].sfc"))
        f.touch("snes")
        f.scan()
        assertMarksKept(zelda.id)
        assertEquals("Legend of Zelda, The - A Link to the Past (USA) [!].sfc", f.db.gameDao().game(zelda.id)?.fileName)
    }

    @Test
    fun addedM3uTakesOverTheDiscGameAndKeepsItsMarks() = runTest {
        f.scan()
        val mgs = f.game("Metal Gear Solid (USA) (Disc 1).chd")
        markEverything(mgs)
        File(f.roms, "psx/Metal Gear Solid (USA).m3u").createNewFile()
        f.touch("psx")
        f.scan()
        assertMarksKept(mgs.id)
        assertEquals("Metal Gear Solid (USA).m3u", f.db.gameDao().game(mgs.id)?.fileName)
        assertEquals(1, f.games().count { it.title == "Metal Gear Solid" })
    }

    @Test
    fun olderScansExtraDiscEntryFoldsIntoTheGameWithFavouriteAndPlayButNotItsHiddenMark() = runTest {
        File(f.roms, "psx/Star Rover - Disc 1.chd").createNewFile()
        File(f.roms, "psx/Star Rover - Disc 2.chd").createNewFile()
        f.scan()
        val first = f.game("Star Rover - Disc 1.chd")
        assertEquals(2, first.discCount)
        // What an older scan stored: `- Disc 2` was no disc marker, so Disc 2 was a game of its own.
        val olderEntry = first.copy(
            id = 0,
            documentId = first.documentId.replace("Disc 1", "Disc 2"),
            relativePath = first.relativePath.replace("Disc 1", "Disc 2"),
            fileName = "Star Rover - Disc 2.chd",
            discGroup = null,
            discCount = 1,
        )
        f.db.scanDao().applyBatch(ScanBatch(f.folderId, newGames = listOf(olderEntry)))
        val second = f.game("Star Rover - Disc 2.chd")
        markEverything(second)
        f.touch("psx")
        f.scan()
        assertNull("the extra entry is folded into the game", f.db.gameDao().game(second.id))
        val kept = f.db.gameDao().game(first.id) ?: error("the game's row was deleted")
        assertTrue("favourite", kept.isFavorite)
        assertFalse("a hidden extra disc entry does not hide the game", kept.isHidden)
        assertEquals(600L, f.db.playDao().play(first.id)?.totalSeconds)
        assertEquals("com.example.emu", f.db.emulatorChoiceDao().gameChoice(first.id)?.emulatorId)
        assertEquals(1, f.games().count { it.fileName.startsWith("Star Rover") })
    }

    private val switchRules = com.plumemu.library.TestRules.ALL + com.plumemu.library.TestRules.SWITCH

    @Test
    fun olderScansSwitchUpdateEntryFoldsIntoTheBaseWithFavouriteAndPlayButNotItsHiddenMark() = runTest {
        File(f.roms, "switch").mkdirs()
        File(f.roms, "switch/Star Rover [0100AAAA00010000].xci").createNewFile()
        File(f.roms, "switch/0100AAAA00010800.nsp").createNewFile()
        f.scan(f.scanner(rules = switchRules))
        val base = f.game("Star Rover [0100AAAA00010000].xci")
        assertEquals(listOf(base.id), f.games().filter { it.relativePath.startsWith("switch/") }.map { it.id })
        // What an older scan stored: an update named by its id alone was a game of its own.
        val olderEntry = base.copy(
            id = 0,
            documentId = base.documentId.replace("Star Rover [0100AAAA00010000].xci", "0100AAAA00010800.nsp"),
            relativePath = base.relativePath.replace("Star Rover [0100AAAA00010000].xci", "0100AAAA00010800.nsp"),
            fileName = "0100AAAA00010800.nsp",
        )
        f.db.scanDao().applyBatch(ScanBatch(f.folderId, newGames = listOf(olderEntry)))
        val update = f.game("0100AAAA00010800.nsp")
        markEverything(update)
        f.touch("switch")
        f.scan(f.scanner(rules = switchRules))
        assertNull("the update's entry is folded into the game", f.db.gameDao().game(update.id))
        val kept = f.db.gameDao().game(base.id) ?: error("the base's row was deleted")
        assertEquals("the base is the file that runs", "Star Rover [0100AAAA00010000].xci", kept.fileName)
        assertTrue("favourite", kept.isFavorite)
        assertFalse("a hidden update entry does not hide the game", kept.isHidden)
        assertEquals(600L, f.db.playDao().play(base.id)?.totalSeconds)
        assertEquals("com.example.emu", f.db.emulatorChoiceDao().gameChoice(base.id)?.emulatorId)
        assertEquals(1, f.games().count { it.relativePath.startsWith("switch/") })
    }

    @Test
    fun aJapaneseCopyAddedBesideAMarkedUsaBaseLeavesTheUsaRowTheOneThatRuns() = runTest {
        File(f.roms, "switch").mkdirs()
        File(f.roms, "switch/Star Rover (USA, Europe) [0100AAAA00010000].nsp").createNewFile()
        f.scan(f.scanner(rules = switchRules))
        val usa = f.game("Star Rover (USA, Europe) [0100AAAA00010000].nsp")
        markEverything(usa)
        File(f.roms, "switch/Star Rover (Japan) [0100AAAA00010000].nsp").createNewFile()
        f.touch("switch")
        f.scan(f.scanner(rules = switchRules))
        assertMarksKept(usa.id)
        assertEquals("Star Rover (USA, Europe) [0100AAAA00010000].nsp", f.db.gameDao().game(usa.id)?.fileName)
        assertEquals(listOf(usa.id), f.games().filter { it.relativePath.startsWith("switch/") }.map { it.id })
    }

    @Test
    fun baseAddedBesideAMarkedUpdateTakesOverItsEntryWithEveryMark() = runTest {
        File(f.roms, "switch").mkdirs()
        File(f.roms, "switch/Star Rover Update 1.3.0 [0100AAAA00010800].nsp").createNewFile()
        File(f.roms, "switch/Moon Cart [0100BBBB00020000].nsp").createNewFile()
        f.scan(f.scanner(rules = switchRules))
        val update = f.game("Star Rover Update 1.3.0 [0100AAAA00010800].nsp")
        val other = f.game("Moon Cart [0100BBBB00020000].nsp")
        markEverything(update)
        File(f.roms, "switch/Star Rover [0100AAAA00010000].nsp").createNewFile()
        f.touch("switch")
        f.scan(f.scanner(rules = switchRules))
        assertMarksKept(update.id)
        assertEquals("Star Rover [0100AAAA00010000].nsp", f.db.gameDao().game(update.id)?.fileName)
        val untouched = f.db.gameDao().game(other.id) ?: error("the other game's row was deleted")
        assertFalse("another game of the folder takes no mark", untouched.isFavorite || untouched.isHidden)
        assertNull(f.db.playDao().play(other.id))
        assertEquals(2, f.games().count { it.relativePath.startsWith("switch/") })
    }

    @Test
    fun playlistWithAnotherNameTakesOverTheDiscSetAndKeepsEveryMark() = runTest {
        val folder = File(f.roms, "psx/Ice Path").apply { mkdirs() }
        File(folder, "Ice Path (USA) (Disc 1).chd").createNewFile()
        File(folder, "Ice Path (USA) (Disc 2).chd").createNewFile()
        f.scan()
        val set = f.game("Ice Path (USA) (Disc 1).chd")
        markEverything(set)
        File(folder, "IP.m3u").createNewFile()
        f.touch("psx/Ice Path")
        f.scan()
        assertMarksKept(set.id)
        assertEquals("IP.m3u", f.db.gameDao().game(set.id)?.fileName)
        assertEquals(2, f.db.gameDao().game(set.id)?.discCount)
        assertEquals(1, f.games().count { it.relativePath.startsWith("psx/Ice Path/") })
    }

    @Test
    fun playlistAddedDuringAnInterruptedScanStillTakesOverTheDiscSetWithEveryMark() = runTest {
        val folder = File(f.roms, "psx/Ice Path").apply { mkdirs() }
        File(folder, "Ice Path (USA) (Disc 1).chd").createNewFile()
        File(folder, "Ice Path (USA) (Disc 2).chd").createNewFile()
        f.scan()
        val set = f.game("Ice Path (USA) (Disc 1).chd")
        markEverything(set)
        File(folder, "IP.m3u").createNewFile()
        f.touch("psx/Ice Path")
        try {
            f.scanner(batchSize = 1).scan().collect { event ->
                if (event is ScanEvent.Progress && f.games().any { it.fileName == "IP.m3u" }) throw Interrupted()
            }
        } catch (expected: Interrupted) {
            // Stopped once the m3u's row is written: it stays pending for the next scan.
        }
        f.scan()
        assertMarksKept(set.id)
        assertEquals("IP.m3u", f.db.gameDao().game(set.id)?.fileName)
        assertEquals(1, f.games().count { it.relativePath.startsWith("psx/Ice Path/") })
    }

    @Test
    fun aPlaylistInTheSystemFolderNeverTakesAnUnrelatedGameOrItsMarks() = runTest {
        File(f.roms, "PS1/multidisc").mkdirs()
        File(f.roms, "PS1/multidisc/Final Fantasy VII (USA) (Disc 1).chd").createNewFile()
        File(f.roms, "PS1/multidisc/Final Fantasy VII (USA) (Disc 2).chd").createNewFile()
        File(f.roms, "PS1/Crash Bandicoot (USA).chd").createNewFile()
        f.scan()
        val crash = f.game("Crash Bandicoot (USA).chd")
        markEverything(crash)
        File(f.roms, "PS1/Final Fantasy VII (USA).m3u").createNewFile()
        f.touch("PS1")
        f.scan()
        assertMarksKept(crash.id)
        assertEquals("Crash Bandicoot (USA).chd", f.db.gameDao().game(crash.id)?.fileName)
        assertEquals(1, f.games().count { it.relativePath == "PS1/Final Fantasy VII (USA).m3u" })
        assertEquals(3, f.games().count { it.relativePath.startsWith("PS1/") })
    }

    @Test
    fun renameFinishedByTheScanAfterAnInterruptedOneKeepsTheMarks() = runTest {
        f.scan()
        val zelda = f.games().single { it.title.contains("Zelda") }
        markEverything(zelda)
        File(f.roms, "snes").renameTo(File(f.roms, "Super Nintendo"))
        f.touch("")
        try {
            f.scanner(batchSize = 1).scan().collect { event ->
                val newRowLanded = f.games().any { it.title.contains("Zelda") && it.id != zelda.id }
                if (event is ScanEvent.Progress && newRowLanded) throw Interrupted()
            }
        } catch (expected: Interrupted) {
            // The test stops the scan on purpose once Zelda's new row is written.
        }
        f.scan()
        assertMarksKept(zelda.id)
        assertEquals("Super Nintendo/Legend of Zelda, The - A Link to the Past (USA).sfc", f.db.gameDao().game(zelda.id)?.relativePath)
        assertEquals(1, f.games().count { it.title.contains("Zelda") })
        assertEquals(11, f.games().size)
    }

    @Test
    fun renamedFolderThatFailsToListDefersDeletionsUntilAScanWithoutFailures() = runTest {
        f.scan()
        val zelda = f.games().single { it.title.contains("Zelda") }
        markEverything(zelda)
        File(f.roms, "snes").renameTo(File(f.roms, "Super Nintendo"))
        f.touch("")
        f.tree.failing += "fs:Super Nintendo"
        val failed = f.scan()
        assertMarksKept(zelda.id)
        assertEquals("snes/Legend of Zelda, The - A Link to the Past (USA).sfc", f.db.gameDao().game(zelda.id)?.relativePath)
        assertEquals(0, failed.gamesRemoved)
        f.tree.failing.clear()
        f.scan()
        assertMarksKept(zelda.id)
        assertEquals("Super Nintendo/Legend of Zelda, The - A Link to the Past (USA).sfc", f.db.gameDao().game(zelda.id)?.relativePath)
        assertEquals(11, f.games().size)
    }

    @Test
    fun anotherGameOfTheSameSizeInTheSameFolderDoesNotTakeTheMarks() = runTest {
        val sixteenBytes = ByteArray(16)
        File(f.roms, "gba/Pokemon - Emerald Version (USA, Europe).gba").writeBytes(sixteenBytes)
        f.scan()
        val emerald = f.game("Pokemon - Emerald Version (USA, Europe).gba")
        f.db.gameDao().setFavorite(emerald.id, true)
        f.db.playDao().addSession(emerald.id, endedAt = 100, seconds = 144_000)
        File(f.roms, "gba/Pokemon - Emerald Version (USA, Europe).gba").delete()
        File(f.roms, "gba/Metroid Fusion (USA).gba").writeBytes(sixteenBytes)
        f.touch("gba")
        f.scan()
        val metroid = f.game("Metroid Fusion (USA).gba")
        assertNotEquals(emerald.id, metroid.id)
        assertFalse("favourite", metroid.isFavorite)
        assertNull(f.db.playDao().play(metroid.id))
        assertNull(f.db.gameDao().game(emerald.id))
    }

    @Test
    fun movedFileWithAStableIdKeepsItsRowAndIsNotCountedAsNew() = runTest {
        f.close()
        f = ScanFixture(temp.newFolder(), fileIdsByName = true)
        f.scan()
        val zelda = f.games().single { it.title.contains("Zelda") }
        markEverything(zelda)
        val name = "Legend of Zelda, The - A Link to the Past (USA).sfc"
        File(f.roms, "snes/$name").renameTo(File(f.roms, "snes/hacks/$name"))
        f.touch("snes")
        f.touch("snes/hacks")
        val stats = f.scan()
        assertMarksKept(zelda.id)
        assertEquals("snes/hacks/$name", f.db.gameDao().game(zelda.id)?.relativePath)
        assertEquals(0, stats.gamesAdded)
        assertEquals(0, stats.gamesRemoved)
    }

    @Test
    fun rescanEverythingDropsGamesOfADeletedDirectory() = runTest {
        f.scan()
        File(f.roms, "snes/hacks").deleteRecursively()
        f.scanner().rescanEverything().toList()
        assertTrue(f.games().none { it.title == "Mario Hack" })
        assertEquals(10, f.games().size)
    }

    @Test
    fun scanAfterAnInterruptedFirstScanDropsGamesOfADeletedDirectory() = runTest {
        var progress = 0
        try {
            f.scanner(batchSize = 1).scan().collect { if (it is ScanEvent.Progress && ++progress == 2) throw Interrupted() }
        } catch (expected: Interrupted) {
            // The test stops the scan on purpose after two batches.
        }
        assertTrue("Sonic was written before the stop", f.games().any { it.title == "Sonic the Hedgehog" })
        File(f.roms, "My Games").deleteRecursively()
        f.scan()
        assertTrue(f.games().none { it.title == "Sonic the Hedgehog" })
    }

    @Test
    fun unreadableDirectoryDuringAFullWalkKeepsItsGames() = runTest {
        f.scan()
        f.tree.failing += "fs:snes/hacks"
        f.scanner().rescanEverything().toList()
        assertTrue("an unreadable directory is not a deleted one", f.games().any { it.title == "Mario Hack" })
    }

    @Test
    fun directoryTimeTooCloseToTheScanIsListedAgainNextTime() = runTest {
        f.now = ScanFixture.BASE_TIME + 500 // the scan runs half a second after the last change
        f.scan()
        // Added in the same second: the directory's time does not change.
        File(f.roms, "snes/Actraiser (USA).sfc").createNewFile()
        File(f.roms, "snes").setLastModified(ScanFixture.BASE_TIME)
        f.now = ScanFixture.BASE_TIME + 60_000
        f.scan()
        assertTrue(f.games().any { it.title == "Actraiser" })
    }

    @Test
    fun twoScannerInstancesNeverScanAtTheSameTime() = runBlocking {
        f.tree.queryDelayMs = 20
        val first = f.scanner()
        val second = f.scanner()
        listOf(first, second).map { scanner -> async(Dispatchers.Default) { scanner.scan().toList() } }.awaitAll()
        assertEquals(1, f.tree.maxConcurrentQueries)
        assertEquals(11, f.games().size)
    }

    private class Interrupted : RuntimeException()
}
