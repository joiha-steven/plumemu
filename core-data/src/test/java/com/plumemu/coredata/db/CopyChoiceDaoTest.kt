package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Other versions' candidates and the stored choice (`copy_choice`, schema 7). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyChoiceDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.copyChoiceDao()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun add(vararg games: GameEntity): List<Long> {
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("arcade", "Arcade")), games.toList()))
        val rows = db.scanDao().gamesInFolder(folderId)
        return games.map { g -> rows.single { it.documentId == g.documentId }.id }
    }

    private fun copy(file: String, parent: String = "root", system: String = "snes", sort: String = "zork quest", method: String? = null) =
        game(folderId, file, systemId = system, parent = parent).copy(fileSortName = sort, idMethod = method)

    @Test
    fun theFileNameCandidatesAreOnePlaceAndSortNameWithoutArcadeSetsOrNotAGames() = runTest {
        val (usa, eur, other, sub, set, notAGame) =
            add(
                copy("Zork Quest (USA).sfc"),
                copy("Zork Quest (Europe).sfc"),
                copy("Galfen (USA).sfc", sort = "galfen"),
                copy("Zork Quest (Japan).sfc", parent = "sub"),
                copy("zorkq.zip", system = "snes", method = IdentifyDao.ARCADE_NAME),
                copy("Zork Quest (Beta).sfc").copy(isNotGame = true),
            )
        assertEquals(listOf(eur, usa), dao.fileNameCopies(usa).map { it.id })
        assertEquals(listOf(sub), dao.fileNameCopies(sub).map { it.id })
        assertEquals(listOf(other), dao.fileNameCopies(other).map { it.id })
        assertEquals(listOf(set), dao.arcadeSetsBeside(usa).map { it.id })
        assertTrue(notAGame !in dao.fileNameCopies(usa).map { it.id })
        assertEquals(emptyList<GameCopyRow>(), dao.fileNameCopies(99_999))
    }

    @Test
    fun choosingACopyStoresItAndChoosingTheOwnFileClearsEveryChoice() = runTest {
        val (usa, eur, jpn) = add(copy("Zork Quest (USA).sfc"), copy("Zork Quest (Europe).sfc"), copy("Zork Quest (Japan).sfc"))
        val copies = listOf(usa, eur, jpn)
        assertFalse(dao.hasChoiceBeside(usa))
        dao.choose(shownId = usa, chosenId = eur, copies = copies, at = 10)
        assertTrue(dao.hasChoiceBeside(usa))
        assertEquals(listOf(CopyChoice(eur, 10)), dao.choicesAmong(copies))
        dao.choose(shownId = usa, chosenId = jpn, copies = copies, at = 20)
        assertEquals("one choice per game: the last one", listOf(CopyChoice(jpn, 20)), dao.choicesAmong(copies))
        dao.choose(shownId = usa, chosenId = usa, copies = copies, at = 30)
        assertEquals(emptyList<CopyChoice>(), dao.choicesAmong(copies))
        assertFalse(dao.hasChoiceBeside(usa))
    }

    @Test
    fun aChoiceInAnotherDirectoryIsNotBesideTheRow() = runTest {
        val (usa, elsewhere) = add(copy("Zork Quest (USA).sfc"), copy("Zork Quest (Europe).sfc", parent = "sub"))
        dao.choose(shownId = elsewhere, chosenId = elsewhere, copies = listOf(elsewhere), at = 5)
        assertFalse("the own file is no stored choice", dao.hasChoiceBeside(elsewhere))
        db.openHelper.writableDatabase.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES ($elsewhere, 5)")
        assertTrue(dao.hasChoiceBeside(elsewhere))
        assertFalse(dao.hasChoiceBeside(usa))
    }

    /** A row at [path] (`PS2/Ape (USA)/Ape (USA).iso`); its directory's id is the path's directory. */
    private fun at(path: String, sort: String = "ape escape") =
        game(folderId, path.substringAfterLast('/'), parent = path.substringBeforeLast('/', ""), relativePath = path)
            .copy(documentId = path, fileSortName = sort)

    @Test
    fun gameFolderCandidatesAreLoneRowsOfSiblingDirectories() = runTest {
        val (usa, eur, mixedA, deeper, other, otherParent) =
            add(
                at("ps2/Ape (USA)/Ape (USA).iso"),
                at("ps2/Ape (Europe)/Ape (Europe).iso"),
                at("ps2/Mixed/Ape (Japan).iso"),
                at("ps2/Deep/Ape (Korea)/Ape (Korea).iso"),
                at("ps2/Jak (USA)/Jak (USA).iso", sort = "jak"),
                at("psp/Ape (Spain)/Ape (Spain).iso"),
            )
        val (mixedB, notAGame) = add(at("ps2/Mixed/Jak (Japan).iso", sort = "jak"), at("ps2/Ape (Beta)/Ape (Beta).iso").copy(isNotGame = true))
        assertEquals(1, dao.rowsBeside(usa))
        assertEquals(2, dao.rowsBeside(mixedA))
        assertEquals(listOf(eur, usa), dao.gameFolderCopies(usa, parentPrefix = "ps2/", slashes = 2).map { it.id })
        assertEquals("ps2/Ape (Europe)/Ape (Europe).iso", dao.gameFolderCopies(usa, "ps2/", 2).first().relativePath)
        for (id in listOf(mixedA, deeper, other, otherParent, mixedB, notAGame)) {
            assertTrue("$id is no candidate", id !in dao.gameFolderCopies(usa, "ps2/", 2).map { it.id })
        }

        assertFalse(dao.hasChoiceNear(usa))
        db.openHelper.writableDatabase.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES ($eur, 5)")
        assertFalse("another directory is not beside the row", dao.hasChoiceBeside(usa))
        assertTrue("a choice with the row's sort name in another directory is near it", dao.hasChoiceNear(usa))
        assertFalse("another game's choice is not near", dao.hasChoiceNear(other))
    }

    @Test
    fun theScanReadsDirectoriesRowsAndSetsWhichCopyShows() = runTest {
        val (usa, eur) = add(at("ps2/Ape (USA)/Ape (USA).iso"), at("ps2/Ape (Europe)/Ape (Europe).iso"))
        val scan = db.scanDao()
        assertEquals(setOf(usa, eur), scan.gamesInDirectories(folderId, listOf("ps2/Ape (USA)", "ps2/Ape (Europe)", "ps2/Gone")).map { it.id }.toSet())
        assertEquals(1, scan.setPrimaryCopies(shown = listOf(usa), leftOut = listOf(eur)))
        assertEquals("nothing left to change", 0, scan.setPrimaryCopies(shown = listOf(usa), leftOut = listOf(eur)))
        val rows = scan.gamesInDirectories(folderId, listOf("ps2/Ape (USA)", "ps2/Ape (Europe)")).associateBy { it.id }
        assertTrue(rows.getValue(usa).isPrimaryCopy)
        assertFalse(rows.getValue(eur).isPrimaryCopy)
    }

    @Test
    fun choicesAreReadLatestFirstAndInChunksForLongLists() = runTest {
        val (a, b) = add(copy("Zork Quest (USA).sfc"), copy("Zork Quest (Europe).sfc"))
        db.openHelper.writableDatabase.execSQL("INSERT INTO copy_choice (game_id, chosen_at) VALUES ($a, 5), ($b, 9)")
        val many = (1L..1_200L).map { it + 100_000 } + listOf(a, b)
        assertEquals(listOf(CopyChoice(b, 9), CopyChoice(a, 5)), dao.choicesAmong(many))
        assertEquals(listOf(b, a), dao.copyRows(many).map { it.id })
    }
}

private operator fun <T> List<T>.component6(): T = this[5]
