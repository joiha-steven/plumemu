package com.plumemu.library.scan

import com.plumemu.coredata.db.GameEntity
import com.plumemu.identify.name.Region
import com.plumemu.library.catalog.CatalogSettings
import com.plumemu.library.naming.chooseCopy
import com.plumemu.library.naming.copiesOf
import com.plumemu.library.naming.copyToPlay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Regional copies kept one game per folder (`PS2/Ape Escape (USA)/Ape Escape (USA).iso` beside
 * `PS2/Ape Escape (Europe)/Ape Escape (Europe).iso`, a common layout)
 * group like copies in one directory: one row shown, the others listed by Other versions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameFolderCopiesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private var step = 0L

    @Before
    fun setUp() {
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String, text: String = "x") = File(fx.roms, path).also {
        it.parentFile.mkdirs()
        it.writeText(text)
        it.setLastModified(ScanFixture.BASE_TIME - 60_000)
    }

    /** Gives [paths] (directories below the picked folder) a new time, as adding or removing a child does. */
    private fun changed(vararg paths: String) {
        step++
        for (path in paths) fx.touch(path, step)
    }

    /** Every directory the same time, so the next scan lists only what [changed] touches. */
    private fun settle() = fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(ScanFixture.BASE_TIME) }

    private suspend fun scan(preferred: List<Region> = CatalogSettings().preferredRegions): ScanStats =
        fx.scan(fx.scanner(settings = CatalogSettings(preferredRegions = preferred)))

    private suspend fun row(fileName: String): GameEntity = fx.game(fileName)

    private suspend fun shown(): List<String> = fx.games().filter { it.isPrimaryCopy }.map { it.relativePath }.sorted()

    private suspend fun copies(fileName: String): List<String> =
        requireNotNull(copiesOf(fx.db, null, row(fileName).id)).copies.map { it.fileName }.sorted()

    private fun apeEscape(region: String) = write("ps2/Ape Escape ($region)/Ape Escape ($region).iso")

    @Test
    fun twoSiblingFoldersOfOneGameAreOneRowWithOtherVersions() = runTest {
        apeEscape("USA")
        apeEscape("Europe")
        settle()
        scan()
        assertEquals(listOf("ps2/Ape Escape (USA)/Ape Escape (USA).iso"), shown())
        val both = listOf("Ape Escape (Europe).iso", "Ape Escape (USA).iso")
        assertEquals(both, copies("Ape Escape (USA).iso"))
        assertEquals(both, copies("Ape Escape (Europe).iso"))

        val nothingChanged = scan()
        assertEquals("an unchanged rescan changes no row", 0, nothingChanged.gamesUpdated)
        changed("ps2/Ape Escape (Europe)")
        val europeListed = scan()
        assertEquals("listing a grouped copy's folder again does not count it as changed", 0, europeListed.gamesUpdated)
        assertEquals(listOf("ps2/Ape Escape (USA)/Ape Escape (USA).iso"), shown())
    }

    @Test
    fun thePreferredRegionPicksTheShownCopy() = runTest {
        apeEscape("USA")
        apeEscape("Europe")
        apeEscape("Japan")
        settle()
        scan(preferred = listOf(Region.EUROPE, Region.USA, Region.JAPAN))
        assertEquals(listOf("ps2/Ape Escape (Europe)/Ape Escape (Europe).iso"), shown())
    }

    @Test
    fun aFavouriteOnARowThatJoinsAGroupIsKept() = runTest {
        apeEscape("Europe")
        settle()
        scan()
        val europe = row("Ape Escape (Europe).iso")
        assertTrue(europe.isPrimaryCopy)
        fx.db.gameDao().setFavorite(europe.id, true)
        fx.db.playDao().addSession(europe.id, endedAt = 100, seconds = 600)
        fx.db.openHelper.writableDatabase.execSQL(
            "INSERT INTO emulator_choice (system_id, game_id, emulator_id, is_person_choice) VALUES ('ps2', ${europe.id}, 'com.example.emu', 1)",
        )

        apeEscape("USA")
        settle()
        changed("ps2", "ps2/Ape Escape (USA)")
        val stats = scan()
        assertEquals("only the changed system folder and the new game folder are listed", 2, stats.directoriesListed)
        assertEquals(listOf("ps2/Ape Escape (USA)/Ape Escape (USA).iso"), shown())
        val after = fx.db.gameDao().game(europe.id) ?: error("the Europe row was deleted")
        assertTrue("favourite", after.isFavorite)
        assertFalse(after.isPrimaryCopy)
        assertEquals(600L, fx.db.playDao().play(europe.id)?.totalSeconds)
        assertEquals("com.example.emu", fx.db.emulatorChoiceDao().gameChoice(europe.id)?.emulatorId)
        assertEquals(listOf("Ape Escape (Europe).iso", "Ape Escape (USA).iso"), copies("Ape Escape (USA).iso"))
    }

    @Test
    fun aSiblingWithAnotherGameStaysSeparate() = runTest {
        apeEscape("USA")
        write("ps2/Jak Raiders (Europe)/Jak Raiders (Europe).iso")
        settle()
        scan()
        assertEquals(2, shown().size)
        assertEquals(listOf("Ape Escape (USA).iso"), copies("Ape Escape (USA).iso"))
    }

    @Test
    fun aTranslationOrAHackSiblingStaysSeparate() = runTest {
        apeEscape("USA")
        write("ps2/Ape Escape (Japan) [T+Eng]/Ape Escape (Japan) [T+Eng].iso")
        write("ps2/Ape Escape (USA) [Hack]/Ape Escape (USA) [Hack].iso")
        // The tag only on the folder: still a translation, not a copy.
        write("ps2/Ape Escape [T+Spa]/Ape Escape (Japan).iso")
        settle()
        scan()
        assertEquals(4, shown().size)
        assertEquals(listOf("Ape Escape (USA).iso"), copies("Ape Escape (USA).iso"))
        assertEquals(listOf("Ape Escape (Japan).iso"), copies("Ape Escape (Japan).iso"))
    }

    @Test
    fun aHackInAnUntaggedFolderStaysSeparate() = runTest {
        write("snes/Super Mario World (USA)/Super Mario World (USA).sfc")
        // The hack's file keeps the original's name; only its folder says it is another game.
        write("snes/SMW Kaizo Dreams/Super Mario World (USA).sfc")
        settle()
        scan()
        assertEquals(listOf("snes/SMW Kaizo Dreams/Super Mario World (USA).sfc", "snes/Super Mario World (USA)/Super Mario World (USA).sfc"), shown())
        for (row in fx.games()) {
            assertEquals(row.relativePath, listOf(row.id), requireNotNull(copiesOf(fx.db, null, row.id)).copies.map { it.id })
        }
    }

    @Test
    fun discsKeptOneFolderEachAreNeverCopiesOfEachOther() = runTest {
        for (region in listOf("USA", "Europe")) {
            for (n in 1..2) {
                val name = "Final Fantasy VII ($region) (Disc $n)"
                write("psx/$name/$name.cue", "FILE \"$name.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n")
                write("psx/$name/$name.bin")
            }
        }
        settle()
        scan()
        assertEquals(
            "every disc stays on Home; only the same disc of another region is a copy",
            listOf(
                "psx/Final Fantasy VII (USA) (Disc 1)/Final Fantasy VII (USA) (Disc 1).cue",
                "psx/Final Fantasy VII (USA) (Disc 2)/Final Fantasy VII (USA) (Disc 2).cue",
            ),
            shown(),
        )
        assertEquals(
            listOf("Final Fantasy VII (Europe) (Disc 2).cue", "Final Fantasy VII (USA) (Disc 2).cue"),
            copies("Final Fantasy VII (USA) (Disc 2).cue"),
        )
    }

    @Test
    fun multiDiscSiblingFoldersWithPlaylistsAreOneRowEachAndGrouped() = runTest {
        for (region in listOf("USA", "Europe")) {
            val name = "Final Fantasy VII ($region)"
            write("psx/$name/$name.m3u", (1..3).joinToString("\n") { "$name (Disc $it).chd" })
            for (n in 1..3) write("psx/$name/$name (Disc $n).chd")
        }
        settle()
        scan()
        assertEquals(listOf("Final Fantasy VII (Europe).m3u", "Final Fantasy VII (USA).m3u"), fx.games().map { it.fileName }.sorted())
        assertEquals(listOf("psx/Final Fantasy VII (USA)/Final Fantasy VII (USA).m3u"), shown())
        assertEquals(listOf("Final Fantasy VII (Europe).m3u", "Final Fantasy VII (USA).m3u"), copies("Final Fantasy VII (USA).m3u"))
    }

    @Test
    fun genericFileNamesGroupByTheirFoldersAndALaunchPlaysTheChosenCopy() = runTest {
        write("psx/Galerians (USA)/disc.chd")
        write("psx/Galerians (Europe)/disc.chd")
        write("psx/Galerians [T+Eng]/disc.chd")
        settle()
        scan()
        val rows = fx.games().associateBy { it.relativePath }
        val usa = rows.getValue("psx/Galerians (USA)/disc.chd")
        val europe = rows.getValue("psx/Galerians (Europe)/disc.chd")
        assertEquals(listOf("psx/Galerians (USA)/disc.chd", "psx/Galerians [T+Eng]/disc.chd"), shown())
        val listed = requireNotNull(copiesOf(fx.db, null, usa.id)).copies.map { it.id }.toSet()
        assertEquals(setOf(usa.id, europe.id), listed)

        assertEquals(usa.id, copyToPlay(fx.db, null, usa.id))
        assertTrue(chooseCopy(fx.db, null, usa.id, europe.id, at = 100))
        assertEquals(europe.id, copyToPlay(fx.db, null, usa.id))
    }

    @Test
    fun removingOneSiblingFolderSplitsTheGroupBack() = runTest {
        apeEscape("USA")
        apeEscape("Europe")
        settle()
        scan()
        assertEquals(listOf("ps2/Ape Escape (USA)/Ape Escape (USA).iso"), shown())

        File(fx.roms, "ps2/Ape Escape (USA)").deleteRecursively()
        settle()
        changed("ps2")
        scan()
        assertEquals(listOf("ps2/Ape Escape (Europe)/Ape Escape (Europe).iso"), shown())
        assertEquals(listOf("Ape Escape (Europe).iso"), copies("Ape Escape (Europe).iso"))
    }

    @Test
    fun aFolderOfTwoGamesIsNoGameFolder() = runTest {
        apeEscape("USA")
        write("ps2/Mixed/Ape Escape (Europe).iso")
        write("ps2/Mixed/Jak Raiders (Europe).iso")
        settle()
        scan()
        assertEquals(3, shown().size)
        assertEquals(listOf("Ape Escape (USA).iso"), copies("Ape Escape (USA).iso"))
    }
}
