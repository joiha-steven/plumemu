package com.plumemu.library.scan

import com.plumemu.library.TestRules
import com.plumemu.library.matching.SystemRule
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
import java.io.File

/**
 * A `.cue`, `.gdi` or `.m3u` names the files that are parts of its game, whatever they are called
 *: the sheet is the row, the parts are not rows of their own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SheetPartsScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private val dreamcast = SystemRule("dreamcast", setOf("dreamcast"), setOf("gdi", "cdi", "bin"))

    @Before
    fun setUp() {
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, text: String = "x") = File(fx.roms, path).also { it.parentFile.mkdirs() }.writeText(text)

    private suspend fun scan() = fx.scan(fx.scanner(rules = TestRules.ALL + dreamcast))

    private suspend fun rows() = fx.games().map { it.fileName }.sorted()

    @Test
    fun aCueHidesABinWithAnotherNameAndAStrayBinStaysARow() = runTest {
        write("psx/Game (USA).cue", "FILE \"Track 01.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n")
        write("psx/Track 01.bin")
        write("psx/Other (USA).cue", "FILE game_t1.bin BINARY\n")
        write("psx/GAME_T1.BIN")
        write("psx/Stray.bin")
        scan()
        assertEquals(listOf("Game (USA).cue", "Other (USA).cue", "Stray.bin"), rows())
    }

    @Test
    fun aGdiHidesItsTracks() = runTest {
        write("dreamcast/Crazy Taxi (USA).gdi", "3\n1 0 4 2352 \"Track 01.bin\" 0\n2 756 0 2352 \"Track 02.raw\" 0\n3 45000 4 2352 \"Track 03.bin\" 0\n")
        write("dreamcast/Track 01.bin")
        write("dreamcast/Track 02.raw")
        write("dreamcast/Track 03.bin")
        scan()
        assertEquals(listOf("Crazy Taxi (USA).gdi"), rows())
    }

    @Test
    fun anM3uNamedDifferentlyFromItsDiscsIsTheOnlyRow() = runTest {
        write("psx/Final Fantasy VII.m3u", "#EXTM3U\nFF7 (Disc 1).chd\nFF7 (Disc 2).chd\nFF7 (Disc 3).chd\n")
        for (n in 1..3) write("psx/FF7 (Disc $n).chd")
        write("psx/Crash (USA).chd")
        scan()
        assertEquals(listOf("Crash (USA).chd", "Final Fantasy VII.m3u"), rows())
        assertEquals(3, fx.game("Final Fantasy VII.m3u").discCount)
    }

    @Test
    fun aSheetNamingAFileInASubfolderLeavesItsOwnRow() = runTest {
        write("psx/Game.cue", "FILE \"bin/Track 01.bin\" BINARY\n")
        write("psx/bin/Track 01.bin")
        write("psx/bin/Elsewhere.chd")
        scan()
        assertEquals(listOf("Elsewhere.chd", "Game.cue", "Track 01.bin"), rows())
    }

    @Test
    fun aSheetNamingAMissingFileStaysARowAndHidesNothingElse() = runTest {
        write("psx/Game.cue", "FILE \"Gone.bin\" BINARY\n")
        write("psx/Other.bin")
        scan()
        assertEquals(listOf("Game.cue", "Other.bin"), rows())
    }

    @Test
    fun aSheetOverTheSizeCapIsNotReadAndItsFilesKeepTheirRows() = runTest {
        write("psx/Game.cue", "FILE \"Track 01.bin\" BINARY\n" + " ".repeat(70 * 1024))
        write("psx/Track 01.bin")
        scan()
        assertEquals(listOf("Game.cue", "Track 01.bin"), rows())
    }

    @Test
    fun aRescanAfterTheSheetAppearsFoldsTheOldRowsIntoIt() = runTest {
        write("psx/Track 01.bin")
        write("psx/Track 02.bin")
        scan()
        fx.db.gameDao().setFavorite(fx.game("Track 01.bin").id, true)

        write("psx/Game.cue", "FILE \"Track 01.bin\" BINARY\nFILE \"Track 02.bin\" BINARY\n")
        fx.touch("psx")
        fx.now += 100_000
        scan()

        assertEquals(listOf("Game.cue"), rows())
        assertTrue("the person's favourite follows the game", fx.game("Game.cue").isFavorite)
    }

    @Test
    fun aProviderThatThrowsOnOpeningASheetDoesNotFailTheScan() = runTest {
        write("psx/Game.cue", "FILE \"Track 01.bin\" BINARY\n")
        write("psx/Track 01.bin")
        write("psx/Other (USA).chd")
        fx.tree.beforeQuery = { if (it.endsWith("Game.cue")) throw SecurityException("provider says no") }
        scan()
        assertEquals(listOf("Game.cue", "Other (USA).chd", "Track 01.bin"), rows())
    }

    @Test
    fun aRescanOnlyVisitsChangedFoldersWhenNoSheetIsAround() = runTest {
        write("psx/Game.cue", "FILE \"Track 01.bin\" BINARY\n")
        write("psx/Track 01.bin")
        write("snes/Game.sfc")
        scan()
        scan()
        assertEquals("no folder changed, none listed, no sheet read again", emptyList<String>(), fx.tree.listed.toList())
        assertEquals(listOf("Game.cue", "Game.sfc"), rows())
    }
}
