package com.plumemu.coredata.db

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit item 21: the retry cap (a game whose lookup failed on three different days is no longer
 * offered), Settings' Clear box art (pictures forgotten, rows kept) and the sweep's database half
 * (rows of games the library no longer has).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapedGameFailuresAndClearTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.scrapedGameDao()
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun insertNamed(vararg fixKeys: String): List<Long> {
        val docs = fixKeys.indices.map { "g$it.sfc" }
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), docs.map { game(folderId, it) }))
        db.scanDao().finishScan(ScanFinish(folderId))
        val ids = docs.map { doc -> db.scanDao().gamesInFolder(folderId).single { it.documentId == doc }.id }
        ids.zip(fixKeys).forEach { (id, keys) -> sql("UPDATE game SET fix_keys = ?, needs_identify = 0 WHERE id = ?", keys, id) }
        return ids
    }

    private fun sql(statement: String, vararg args: Any) = db.openHelper.writableDatabase.execSQL(statement, args)

    private fun row(key: String, id: String = "1245", pictures: String = PICTURES, personChoice: Boolean = false) =
        ScrapedGameEntity(
            scrapeKey = key, gameId = null, outcome = ScrapeOutcome.MATCHED, screenScraperId = id, matchedBy = ScrapeMatchedBy.HASH,
            isPersonChoice = personChoice, scrapedAt = NOW, detailsJson = "{}", title = "Zork Quest", year = null, publisher = null,
            players = null, genre = null, pictures = pictures, hasBoxFront = pictures.isNotEmpty(), supportMissing = true, supportAttempts = 2,
        )

    private suspend fun pendingIds() = dao.pending(NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS, null, 100).map { it.id }

    @Test
    fun aGameThatFailedOnThreeDifferentDaysIsNotOfferedAgainAndReDownloadAllOffersItAgain() = runTest {
        val (id) = insertNamed("crc:a:1")
        assertEquals(listOf(id), pendingIds())
        dao.recordFailure("crc:a:1", DAY_1)
        dao.recordFailure("crc:a:1", DAY_1)
        dao.recordFailure("crc:a:1", DAY_1)
        assertEquals("three failures on one day are one day", 1, dao.failure("crc:a:1")?.failedDays)
        dao.recordFailure("crc:a:1", DAY_1 + 1)
        assertEquals("two days: still asked", listOf(id), pendingIds())
        dao.recordFailure("crc:a:1", DAY_1 + 2)
        assertEquals("the third day stops it", emptyList<Long>(), pendingIds())
        assertEquals(0, dao.countPending(NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS))
        assertEquals(1, dao.countGivenUp())
        dao.markAllToAskAgain()
        assertEquals("a mark alone does not lift it", emptyList<Long>(), pendingIds())
        assertEquals(1, dao.forgetFailures())
        assertEquals(listOf(id), pendingIds())
        assertNull(dao.failure("crc:a:1"))
    }

    @Test
    fun aGameMarkedToBeAskedAgainIsCappedToo() = runTest {
        val (id) = insertNamed("crc:a:1")
        dao.insertOrReplace(row("crc:a:1", pictures = "").copy(hasBoxFront = false))
        dao.markMissingToAskAgain()
        assertEquals(listOf(id), pendingIds())
        (0L..2L).forEach { dao.recordFailure("crc:a:1", DAY_1 + it) }
        assertEquals(emptyList<Long>(), pendingIds())
    }

    @Test
    fun anAnswerStartsTheCountAgain() = runTest {
        insertNamed("crc:a:1")
        dao.recordFailure("crc:a:1", DAY_1)
        dao.recordFailure("crc:a:1", DAY_1 + 1)
        dao.storeFromRun(row("crc:a:1"))
        assertNull(dao.failure("crc:a:1"))
    }

    @Test
    fun reDownloadForChosenSystemsLiftsTheCapOfTheirGamesOnly() = runTest {
        val (snes, nes) = insertNamed("crc:a:1", "crc:b:1")
        sql("INSERT INTO system (id, name) VALUES ('nes', 'NES')")
        sql("UPDATE game SET system_id = 'nes' WHERE id = ?", nes)
        for (key in listOf("crc:a:1", "crc:b:1")) (0L..2L).forEach { dao.recordFailure(key, DAY_1 + it) }
        assertEquals(emptyList<Long>(), pendingIds())
        assertEquals(1, dao.forgetFailuresIn(listOf("nes")))
        assertEquals(listOf(nes), pendingIds())
        assertTrue(snes !in pendingIds())
    }

    @Test
    fun clearingForgetsAutomaticPicturesKeepsRowsAndAPersonsChoiceAndDownloadMissingThenAsksAgain() = runTest {
        val (auto, chosen) = insertNamed("crc:a:1", "crc:b:1")
        dao.insertOrReplace(row("crc:a:1", id = "10"))
        dao.insertOrReplace(row("crc:b:1", id = "20", personChoice = true))
        assertEquals(1, dao.forgetPictures())
        val cleared = dao.get("crc:a:1")!!
        assertEquals("", cleared.pictures)
        assertFalse(cleared.hasBoxFront)
        assertEquals("the details stay", "Zork Quest", cleared.title)
        assertEquals(0, cleared.supportAttempts)
        assertEquals(PICTURES, dao.get("crc:b:1")?.pictures)
        assertEquals("no run asks it by itself", emptyList<Long>(), pendingIds())
        assertEquals(listOf("20"), dao.idsWithPictures())
        assertEquals(1, dao.markMissingToAskAgain())
        assertEquals("Download missing would ask it again", listOf(auto), pendingIds())
        assertFalse(chosen in pendingIds())
    }

    @Test
    fun theSweepDeletesPictureRowsOfGamesTheLibraryLacksButNotHiddenGamesNorAPersonsChoice() = runTest {
        val (_, hidden) = insertNamed("crc:a:1", "crc:b:1")
        sql("UPDATE game SET is_hidden = 1 WHERE id = ?", hidden)
        dao.insertOrReplace(row("crc:a:1", id = "10"))
        dao.insertOrReplace(row("crc:b:1", id = "20"))
        dao.insertOrReplace(row("crc:gone:1", id = "30"))
        dao.insertOrReplace(row("crc:gone:2", id = "40", personChoice = true))
        dao.insertOrReplace(row("crc:gone:3", id = "50", pictures = ""))
        dao.recordFailure("crc:gone:1", DAY_1)
        dao.recordFailure("crc:a:1", DAY_1)
        assertTrue(dao.hasAnyGame())
        assertEquals(1, dao.deleteOrphanPictureRows())
        assertEquals(1, dao.deleteOrphanFailures())
        assertNull(dao.get("crc:gone:1"))
        assertEquals(listOf("10", "20", "40"), dao.idsWithPictures().sorted())
        assertEquals("a no-match-like row without pictures stays", "50", dao.get("crc:gone:3")?.screenScraperId)
        assertEquals(1, dao.failure("crc:a:1")?.failedDays)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val DAY_1 = 20_000L
        const val PICTURES = "[{\"kind\":\"box-front\",\"size\":\"grid\",\"path\":\"/m/10/box.webp\",\"w\":1,\"h\":1}]"
    }
}
