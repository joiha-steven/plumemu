package com.plumemu.library.scan

import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NameFixKind
import com.plumemu.coredata.db.NameTrustNames
import com.plumemu.library.corpus.RealData
import com.plumemu.library.naming.NAMING_RULES_VERSION
import com.plumemu.library.naming.setHiddenWithArcadeCopies
import kotlinx.coroutines.flow.toList
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
import org.robolectric.shadows.ShadowLog
import java.io.File

/**
 * Arcade sets of one game in one directory are one row (arcade versions step 2, `ArcadeCopies`)
 * through real scans, with the arcade pack the app ships and real short names: the first scan,
 * rescans, files added and removed, the person's marks, Back to automatic, the upgrade from
 * 0.0.2-alpha's rows and a real arcade folder. Arcade files are never opened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcadeCopiesScanTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private lateinit var naming: GameNaming

    @Before
    fun setUp() {
        ShadowLog.clear()
        fx = ScanFixture(temp.newFolder("fixture"), copyFixtures = false)
        naming = RealData.naming(temp.newFolder("packs")).naming
    }

    @After
    fun tearDown() = fx.close()

    private fun write(path: String) = File(fx.roms, path).apply {
        parentFile?.mkdirs()
        writeBytes(TestNaming.zip("x.rom", ByteArray(16)))
        setLastModified(ScanFixture.BASE_TIME - 60_000)
    }

    private var scans = 0

    /** Every scan gives the folders a new time, so the walk lists them again. */
    private suspend fun scan() {
        val time = ScanFixture.BASE_TIME + 10_000L * scans++
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(time) }
        fx.scanner(naming = naming).scan().toList()
    }

    /** What the lists show: file name to title of every listed row. */
    private suspend fun listed(): Map<String, String> =
        fx.games().filter { it.isPrimaryCopy && !it.isHidden && !it.isNotGame }.associate { it.fileName to it.title }

    private suspend fun row(fileName: String): GameEntity = fx.game(fileName)

    @Test
    fun aParentAndItsRegionCloneAreOneRowThroughRescans() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        write("arcade/pang.zip")
        write("arcade/bbros.zip")
        scan()
        val expected = mapOf("dinou.zip" to "Cadillacs and Dinosaurs", "pang.zip" to "Pang", "bbros.zip" to "Buster Bros.")
        assertEquals(expected, listed())
        assertEquals("the copy left out shows the short title too", "Cadillacs and Dinosaurs", row("dino.zip").title)
        assertEquals(4, fx.games().size)

        // A rescan that lists the directory again (new time) and one that lists nothing.
        scan()
        assertEquals(expected, listed())
        fx.scanner(naming = naming).scan().toList()
        assertEquals(expected, listed())
        assertTrue("arcade files are never opened", fx.tree.opened.isEmpty())
    }

    @Test
    fun removingTheShownCopyShowsTheOtherWithTheShortTitleAndAddingItBackRegroups() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        scan()
        val dino = row("dino.zip").id
        File(fx.roms, "arcade/dinou.zip").delete()
        scan()
        assertEquals(mapOf("dino.zip" to "Cadillacs and Dinosaurs"), listed())
        assertEquals("the kept row is the same row", dino, row("dino.zip").id)

        write("arcade/dinou.zip")
        scan()
        assertEquals(mapOf("dinou.zip" to "Cadillacs and Dinosaurs"), listed())
        assertFalse(row("dino.zip").isPrimaryCopy)
    }

    @Test
    fun aParentAloneThenACloneAddedLaterRegroups() = runTest {
        write("arcade/dino.zip")
        scan()
        assertEquals(mapOf("dino.zip" to "Cadillacs and Dinosaurs"), listed())
        write("arcade/dinou.zip")
        write("arcade/dinoj.zip")
        scan()
        // dinoj is "Cadillacs: Kyouryuu Shin Seiki (Japan 930201)": another short title, its own row.
        assertEquals(mapOf("dinou.zip" to "Cadillacs and Dinosaurs", "dinoj.zip" to "Cadillacs: Kyouryuu Shin Seiki"), listed())
    }

    @Test
    fun theMarksOfThePersonChooseTheShownCopyAtTheNextScan() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        scan()
        val dino = row("dino.zip")
        fx.db.gameDao().setFavorite(dino.id, true)
        assertFalse("no instant regroup: the next scan does it", row("dino.zip").isPrimaryCopy)
        fx.scanner(naming = naming).scan().toList() // nothing changed on the card
        assertEquals(mapOf("dino.zip" to "Cadillacs and Dinosaurs"), listed())
        assertTrue("the mark stays on its row", row("dino.zip").isFavorite)
        assertFalse(row("dinou.zip").isFavorite)

        fx.db.gameDao().setHidden(dino.id, true)
        fx.scanner(naming = naming).scan().toList()
        assertEquals("a hidden copy gives way to one that is not", mapOf("dinou.zip" to "Cadillacs and Dinosaurs"), listed())

        fx.db.gameDao().setHidden(row("dinou.zip").id, true)
        fx.scanner(naming = naming).scan().toList()
        assertEquals("every copy hidden: the game leaves the lists", emptyMap<String, String>(), listed())
        assertEquals(1, fx.games().count { it.isPrimaryCopy })
    }

    @Test
    fun aNameFixOnTheCloneKeepsItShownWithTheFixAndBackToAutomaticRegroups() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        scan()
        val dao = fx.db.identifyDao()
        // The person fixes the copy the lists leave out (as a later version picker could).
        val dino = row("dino.zip")
        assertTrue(dao.setFix(dino.id, NameFixKind.OWN_NAME, "My Dinosaurs", "my dinosaurs", null, fixedAt = 1))
        fx.scanner(naming = naming).scan().toList()
        assertEquals(mapOf("dino.zip" to "My Dinosaurs"), listed())
        assertEquals(NameTrustNames.PERSON_FIX, row("dino.zip").nameTrust)

        assertTrue(dao.backToAutomatic(dino.id))
        fx.scanner(naming = naming).nameWaiting().toList()
        assertEquals(mapOf("dinou.zip" to "Cadillacs and Dinosaurs"), listed())
        assertEquals("Cadillacs and Dinosaurs", row("dino.zip").title)
    }

    @Test
    fun aBootlegAndAHackAreRowsOfTheirOwnTitledByTheirDescriptions() = runTest {
        write("arcade/outrun.zip")
        write("arcade/outrunb.zip")
        write("arcade/ffightu.zip")
        write("arcade/ffightj.zip")
        write("arcade/ffightjh.zip")
        scan()
        val shown = listed()
        assertEquals(setOf("outrun.zip", "outrunb.zip", "ffightu.zip", "ffightjh.zip"), shown.keys)
        assertEquals("Out Run", shown["outrun.zip"])
        assertEquals("Final Fight", shown["ffightu.zip"])
        assertTrue(shown.getValue("outrunb.zip"), shown.getValue("outrunb.zip").contains("bootleg"))
        // "Street Smart / Final Fight (Japan, hack)" shows "Street Smart": no clash with Final Fight.
        assertEquals("Street Smart", shown["ffightjh.zip"])
    }

    /**
     * Sets the pack does not know are not the arcade grouping's: the catalogue's regional-copy
     * rule by file name still makes one row of `x.zip` + `x.7z` and of two regions of one name.
     */
    @Test
    fun setsThePackDoesNotKnowKeepTheCataloguesRegionalCopies() = runTest {
        write("fbneo/plumtestgame.zip")
        write("fbneo/plumtestgame.7z")
        write("fbneo/Metal Slug (USA).zip")
        write("fbneo/Metal Slug (Japan).zip")
        write("fbneo/mslug.zip")
        scan()
        val expected = setOf("plumtestgame.7z", "Metal Slug (USA).zip", "mslug.zip")
        assertEquals(expected, listed().keys)
        scan() // listed again
        assertEquals(expected, listed().keys)
        File(fx.roms, "fbneo/Metal Slug (USA).zip").delete()
        scan()
        assertEquals(setOf("plumtestgame.7z", "Metal Slug (Japan).zip", "mslug.zip"), listed().keys)
    }

    @Test
    fun anEmulatorOrAScreenScraperChoiceMakesItsCopyTheOneShown() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        write("fbneo/dino.zip")
        write("fbneo/dinou.zip")
        scan()
        val arcadeDino = fx.games().single { it.systemId == "arcade" && it.fileName == "dino.zip" }
        fx.db.emulatorChoiceDao().setPersonGameChoice(arcadeDino.id, "arcade", "some_core")
        // A ScreenScraper pick is stored under the set's first key (arcade:<short name>), for both systems here.
        fx.db.openHelper.writableDatabase.execSQL(
            "INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, title, pictures, has_box_front, is_person_choice) " +
                "VALUES ('arcade:dino', NULL, 'MATCHED', 1, 'Cadillacs and Dinosaurs', '', 0, 1)",
        )
        fx.scanner(naming = naming).scan().toList()
        val shown = fx.games().filter { it.isPrimaryCopy }.map { it.systemId to it.fileName }.toSet()
        assertEquals(setOf("arcade" to "dino.zip", "fbneo" to "dino.zip"), shown)
    }

    @Test
    fun hidingAnArcadeGameHidesEveryCopyAtOnceAndShowingItAgainShowsThemAll() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        write("arcade/pang.zip")
        write("snes/Zelda (USA).sfc")
        write("snes/Zelda (Europe).sfc")
        scan()
        val dinou = row("dinou.zip")
        assertEquals(setOf(dinou.id, row("dino.zip").id), setHiddenWithArcadeCopies(fx.db, naming, dinou.id, isHidden = true).toSet())
        assertTrue(row("dino.zip").isHidden && row("dinou.zip").isHidden)
        assertFalse("another game is untouched", row("pang.zip").isHidden)
        assertEquals(setOf("pang.zip", "Zelda (USA).sfc"), listed().keys)
        scan()
        assertEquals("still gone after a rescan", setOf("pang.zip", "Zelda (USA).sfc"), listed().keys)

        // Shown again from the copy left out (Settings' hidden games list shows every copy).
        assertEquals(2, setHiddenWithArcadeCopies(fx.db, naming, row("dino.zip").id, isHidden = false).size)
        assertFalse(row("dino.zip").isHidden || row("dinou.zip").isHidden)
        scan()
        assertEquals(setOf("dinou.zip", "pang.zip", "Zelda (USA).sfc"), listed().keys)

        // Console copies: only the row asked for, as before.
        val zelda = row("Zelda (USA).sfc")
        assertEquals(listOf(zelda.id), setHiddenWithArcadeCopies(fx.db, naming, zelda.id, isHidden = true))
        assertFalse(row("Zelda (Europe).sfc").isHidden)
        assertEquals("a game that is gone", emptyList<Long>(), setHiddenWithArcadeCopies(fx.db, naming, 999_999, isHidden = true))
    }

    /**
     * 0.0.2-alpha (and step 1) stored every set as a shown row, titled by its description when two
     * shared a short title, with the arcade pack the app still ships. After the upgrade marking and
     * one scan the dino group is one row; both copies carry marks (favourite on dino, play time on
     * dinou), so the region order decides: dinou (USA) shows.
     */
    @Test
    fun anUpgradeGroupsAlphaRowsOnceAndBothMarkedCopiesGiveWayToTheUsaCopy() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        write("arcade/pang.zip")
        write("arcade/outrun.zip")
        scan()
        val sql = fx.db.openHelper.writableDatabase
        sql.execSQL("UPDATE game SET is_primary_copy = 1, regions = ''")
        sql.execSQL("UPDATE game SET title = 'Cadillacs and Dinosaurs (World 930201)', sort_name = 'cadillacs and dinosaurs (world 930201)' WHERE file_name = 'dino.zip'")
        sql.execSQL("UPDATE game SET title = 'Cadillacs and Dinosaurs (USA 930201)', sort_name = 'cadillacs and dinosaurs (usa 930201)' WHERE file_name = 'dinou.zip'")
        val dino = row("dino.zip")
        val dinou = row("dinou.zip")
        val pang = row("pang.zip")
        val outrun = row("outrun.zip")
        fx.db.gameDao().setFavorite(dino.id, true)
        fx.db.playDao().addSession(dinou.id, endedAt = 5_000, seconds = 600)
        assertTrue(fx.db.identifyDao().setFix(pang.id, NameFixKind.OWN_NAME, "My Pang", "my pang", null, fixedAt = 1))
        fx.db.gameDao().setHidden(outrun.id, true)
        val before = fx.games()
        val packVersion = dino.packVersion

        val scanner = fx.scanner(naming = naming)
        assertEquals("every arcade row", before.size, scanner.nameAgainForNewRules(storedVersion = 1))
        assertEquals(0, scanner.nameAgainForNewRules(NAMING_RULES_VERSION))
        scanner.scan().toList()

        assertTrue("arcade files are never opened", fx.tree.opened.isEmpty())
        assertEquals("nothing deleted", before.map { it.id }.toSet(), fx.games().map { it.id }.toSet())
        assertEquals(mapOf("dinou.zip" to "Cadillacs and Dinosaurs", "pang.zip" to "My Pang"), listed())
        assertEquals("Cadillacs and Dinosaurs", row("dino.zip").title)
        assertFalse(row("dino.zip").isPrimaryCopy)
        assertTrue("the favourite stays on dino", row("dino.zip").isFavorite)
        assertFalse(row("dinou.zip").isFavorite)
        assertEquals("play time stays on dinou", 600L, fx.db.playDao().play(dinou.id)!!.totalSeconds)
        assertEquals(null, fx.db.playDao().play(dino.id))
        assertEquals(NameTrustNames.PERSON_FIX, row("pang.zip").nameTrust)
        assertTrue("the hidden game stays hidden", row("outrun.zip").isHidden)
        assertEquals("USA", row("dinou.zip").regions)
        for (game in fx.games()) assertEquals(game.fileName, packVersion, game.packVersion)
    }

    /**
     * A real arcade folder (376 MAME 2003-Plus short
     * names, files named only): exactly one game has two sets, Cadillacs and Dinosaurs.
     */
    @Test
    fun theOwnersArcadeFolderHasExactlyOneGameOfTwoSets() = runTest {
        RealData.assumePrivateFile("tools/corpus/owner-arcade.tsv")
        val names = File(RealData.ROOT, "tools/corpus/owner-arcade.tsv").readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("folder\t") }
            .map { it.split('\t') }
            .filter { it[0] == "arcade" }
            .map { it[1] }
        assertEquals(376, names.size)
        for (name in names) write("arcade/$name.zip")
        scan()
        val games = fx.games()
        assertEquals(376, games.size)
        val listedRows = games.filter { it.isPrimaryCopy && !it.isHidden && !it.isNotGame }
        val leftOut = games.filter { !it.isPrimaryCopy }
        assertEquals(listOf("dino.zip"), leftOut.map { it.fileName })
        assertEquals(375 - games.count { it.isNotGame }, listedRows.size)
        assertEquals("no BIOS or device set in his arcade folder", 0, games.count { it.isNotGame })
        assertEquals("Cadillacs and Dinosaurs", games.single { it.fileName == "dinou.zip" }.title)
        assertTrue("arcade files are never opened", fx.tree.opened.isEmpty())
    }
}
