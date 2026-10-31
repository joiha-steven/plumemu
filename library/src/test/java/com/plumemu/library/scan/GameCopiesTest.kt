package com.plumemu.library.scan

import com.plumemu.coredata.db.GameEntity
import com.plumemu.library.corpus.RealData
import com.plumemu.library.naming.GameCopy
import com.plumemu.library.naming.chooseCopy
import com.plumemu.library.naming.copiesOf
import com.plumemu.library.naming.copyToPlay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * The game menu's Other versions on real scans: which rows are copies of the game a row stands for, by the grouping's own rules
 * (consoles by file name, arcade sets by the shipped arcade pack), and which copy a launch opens.
 * Console titles are made up; arcade short names are the pack's (as in ArcadeCopiesScanTest).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameCopiesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var fx: ScanFixture
    private lateinit var naming: GameNaming
    private var scans = 0

    @Before
    fun setUp() {
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

    private suspend fun scan() {
        val time = ScanFixture.BASE_TIME + 10_000L * scans++
        fx.roms.walkTopDown().filter { it.isDirectory }.forEach { it.setLastModified(time) }
        fx.scanner(naming = naming).scan().toList()
    }

    private suspend fun row(fileName: String): GameEntity = fx.game(fileName)

    private suspend fun copiesOf(fileName: String): List<GameCopy> = requireNotNull(copiesOf(fx.db, naming, row(fileName).id)).copies

    private suspend fun filesOf(fileName: String): Set<String> = copiesOf(fileName).map { it.fileName }.toSet()

    @Test
    fun regionalCopiesOfAConsoleGameAreItsCopiesButARevisionOrAnotherDirectoryIsNot() = runTest {
        write("snes/Zork Quest (USA).sfc")
        write("snes/Zork Quest (Europe).sfc")
        write("snes/Zork Quest (USA) (Rev 1).sfc")
        write("snes/more/Zork Quest (Japan).sfc")
        write("snes/Galfen Island (USA).sfc")
        scan()
        assertEquals(setOf("Zork Quest (USA).sfc", "Zork Quest (Europe).sfc"), filesOf("Zork Quest (USA).sfc"))
        assertEquals("the same from the copy the lists leave out", setOf("Zork Quest (USA).sfc", "Zork Quest (Europe).sfc"), filesOf("Zork Quest (Europe).sfc"))
        assertEquals(setOf("Zork Quest (USA) (Rev 1).sfc"), filesOf("Zork Quest (USA) (Rev 1).sfc"))
        assertEquals(setOf("Zork Quest (Japan).sfc"), filesOf("Zork Quest (Japan).sfc"))
        assertEquals(setOf("Galfen Island (USA).sfc"), filesOf("Galfen Island (USA).sfc"))
        assertEquals(mapOf("Zork Quest (Europe).sfc" to listOf("EUR"), "Zork Quest (USA).sfc" to listOf("USA")), copiesOf("Zork Quest (USA).sfc").associate { it.fileName to it.regions })
        assertTrue("console copies have no arcade details", copiesOf("Zork Quest (USA).sfc").all { it.arcadeDetails.isEmpty() })
        assertNull(copiesOf(fx.db, naming, 99_999))
    }

    @Test
    fun anArcadeParentAndItsRegionCloneAreCopiesButAnotherTitleOrABootlegIsNot() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        write("arcade/dinoj.zip")
        write("arcade/outrun.zip")
        write("arcade/outrunb.zip")
        write("arcade/pang.zip")
        scan()
        assertEquals(setOf("dino.zip", "dinou.zip"), filesOf("dinou.zip"))
        assertEquals(setOf("dino.zip", "dinou.zip"), filesOf("dino.zip"))
        assertEquals(setOf("dinoj.zip"), filesOf("dinoj.zip"))
        assertEquals(setOf("outrun.zip"), filesOf("outrun.zip"))
        assertEquals(setOf("outrunb.zip"), filesOf("outrunb.zip"))
        assertEquals(setOf("pang.zip"), filesOf("pang.zip"))
        assertEquals(
            mapOf("dino.zip" to listOf("World 930201"), "dinou.zip" to listOf("USA 930201")),
            copiesOf("dinou.zip").associate { it.fileName to it.arcadeDetails },
        )
        assertEquals("without the pack the row stands alone", listOf(row("dinou.zip").id), copiesOf(fx.db, null, row("dinou.zip").id)?.copies?.map { it.id })
    }

    @Test
    fun setsThePackDoesNotKnowFollowTheConsoleRule() = runTest {
        write("fbneo/Metal Slug (USA).zip")
        write("fbneo/Metal Slug (Japan).zip")
        write("fbneo/mslug.zip")
        scan()
        assertEquals(setOf("Metal Slug (USA).zip", "Metal Slug (Japan).zip"), filesOf("Metal Slug (USA).zip"))
        assertEquals("the known set is the arcade rule's alone", setOf("mslug.zip"), filesOf("mslug.zip"))
    }

    @Test
    fun aLaunchOpensTheChosenCopyTheLatestChoiceWinsAndTheOwnFileClearsIt() = runTest {
        write("snes/Zork Quest (USA).sfc")
        write("snes/Zork Quest (Europe).sfc")
        write("snes/Zork Quest (Japan).sfc")
        write("snes/Galfen Island (USA).sfc")
        scan()
        val usa = row("Zork Quest (USA).sfc").id
        val eur = row("Zork Quest (Europe).sfc").id
        val jpn = row("Zork Quest (Japan).sfc").id
        assertEquals("no choice: the row's own file", usa, copyToPlay(fx.db, naming, usa))

        assertTrue(chooseCopy(fx.db, naming, usa, eur, at = 100))
        assertEquals(eur, copyToPlay(fx.db, naming, usa))
        assertEquals(eur, copiesOf(fx.db, naming, usa)?.playedId)
        assertFalse("another game is no copy: nothing stored", chooseCopy(fx.db, naming, usa, row("Galfen Island (USA).sfc").id, at = 200))
        assertEquals(eur, copyToPlay(fx.db, naming, usa))
        assertEquals("another game nearby plays its own file", row("Galfen Island (USA).sfc").id, copyToPlay(fx.db, naming, row("Galfen Island (USA).sfc").id))

        // Two choices stored (the shown copy changed between them): the latest wins.
        fx.db.openHelper.writableDatabase.execSQL("INSERT OR REPLACE INTO copy_choice (game_id, chosen_at) VALUES ($jpn, 300)")
        assertEquals(jpn, copyToPlay(fx.db, naming, usa))

        assertTrue(chooseCopy(fx.db, naming, usa, usa, at = 400))
        assertEquals(usa, copyToPlay(fx.db, naming, usa))
        assertEquals(0, countChoices())
    }

    @Test
    fun aChosenCopyWhoseFileIsRemovedLeavesTheRowPlayingItsOwnFile() = runTest {
        write("snes/Zork Quest (USA).sfc")
        write("snes/Zork Quest (Europe).sfc")
        scan()
        val usa = row("Zork Quest (USA).sfc").id
        val eur = row("Zork Quest (Europe).sfc").id
        assertTrue(chooseCopy(fx.db, naming, usa, eur, at = 100))
        val marksBefore = row("Zork Quest (USA).sfc")
        File(fx.roms, "snes/Zork Quest (Europe).sfc").delete()
        scan()
        assertEquals(usa, copyToPlay(fx.db, naming, usa))
        assertEquals(0, countChoices())
        assertEquals("the row is the same row", marksBefore.id, row("Zork Quest (USA).sfc").id)
    }

    @Test
    fun anArcadeRowPlaysTheChosenSetAndAChoiceDoesNotChangeTheShownCopy() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        scan()
        val dinou = row("dinou.zip").id
        val dino = row("dino.zip").id
        assertTrue(chooseCopy(fx.db, naming, dinou, dino, at = 100))
        assertEquals(dino, copyToPlay(fx.db, naming, dinou))
        scan()
        assertTrue("the shown copy stays the shown copy", row("dinou.zip").isPrimaryCopy)
        assertFalse(row("dino.zip").isPrimaryCopy)
        assertEquals(dino, copyToPlay(fx.db, naming, dinou))
    }

    /**
     * The read Other versions makes of an arcade game in a folder of 10,000 sets: the sets are read
     * by id and file name, and only the game's possible members are looked up in the pack. Printed
     * for the record; the menu never waits for it (it adds the entry when the read returns).
     */
    @Test
    fun anArcadeGameAmongTenThousandSetsIsReadQuickly() = runTest {
        write("arcade/dino.zip")
        write("arcade/dinou.zip")
        scan()
        val dinou = row("dinou.zip")
        val filler = (1..10_000).map { i ->
            dinou.copy(id = 0, documentId = "${dinou.documentId}-$i", fileName = "zz$i.zip", relativePath = "arcade/zz$i.zip", isPrimaryCopy = true)
        }
        fx.db.scanDao().applyBatch(com.plumemu.coredata.db.ScanBatch(fx.folderId, newGames = filler))
        copiesOf(fx.db, naming, dinou.id) // the pack's first use unpacks it
        val times = (1..5).map {
            val start = System.nanoTime()
            val copies = requireNotNull(copiesOf(fx.db, naming, dinou.id)).copies
            assertEquals(2, copies.size)
            (System.nanoTime() - start) / 1_000_000
        }
        println("GameCopiesTest: copiesOf among 10,000 arcade sets took ${times.joinToString()} ms")
        org.junit.Assume.assumeTrue("absolute time budgets are checked on the development machine, not on shared CI runners", System.getProperty("plumemu.timingBudgets") != "false")
        assertTrue("copiesOf took $times ms", times.min() < 500)
    }

    private fun countChoices(): Int =
        fx.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM copy_choice").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
}
