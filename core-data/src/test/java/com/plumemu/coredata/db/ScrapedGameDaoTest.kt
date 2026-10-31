package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `scraped_game` and the run's queries: keys, what is pending, what a run may not replace. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapedGameDaoTest {
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

    /** Inserts named games: each [fixKeys] entry is one game's `fix_keys` (one key per line). */
    private suspend fun insertNamed(vararg fixKeys: String): List<Long> {
        val docs = fixKeys.indices.map { "g$it.sfc" }
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), docs.map { game(folderId, it) }))
        db.scanDao().finishScan(ScanFinish(folderId))
        val ids = docs.map { doc -> db.scanDao().gamesInFolder(folderId).single { it.documentId == doc }.id }
        ids.zip(fixKeys).forEach { (id, keys) -> sql("UPDATE game SET fix_keys = ?, needs_identify = 0 WHERE id = ?", keys, id) }
        return ids
    }

    private fun sql(statement: String, vararg args: Any) = db.openHelper.writableDatabase.execSQL(statement, args)

    private fun row(key: String, outcome: ScrapeOutcome = ScrapeOutcome.MATCHED, at: Long = NOW, gameId: Long? = null, personChoice: Boolean = false) =
        ScrapedGameEntity(
            scrapeKey = key,
            gameId = gameId,
            outcome = outcome,
            screenScraperId = "1245".takeIf { outcome == ScrapeOutcome.MATCHED },
            matchedBy = ScrapeMatchedBy.HASH.takeIf { outcome == ScrapeOutcome.MATCHED },
            isPersonChoice = personChoice,
            scrapedAt = at,
            detailsJson = "{}".takeIf { outcome == ScrapeOutcome.MATCHED },
            title = "Zork Quest".takeIf { outcome == ScrapeOutcome.MATCHED },
            year = null,
            publisher = null,
            players = null,
            genre = null,
            hasBoxFront = outcome == ScrapeOutcome.MATCHED,
        )

    private suspend fun pendingIds(now: Long = NOW) = dao.pending(now - ScrapedGameDao.NO_MATCH_RETRY_MILLIS, null, 100).map { it.id }

    @Test
    fun theKeyInSqlIsTheFirstFixKeyOrTheRowKey() = runTest {
        val stored = listOf("", "crc:0000abcd:1024", "crc:0000abcd:2048\ncode:SNES:ZQ\nchd:ff")
        val ids = insertNamed(*stored.toTypedArray())
        val keys = dao.pending(0, null, 10).associate { it.id to it.scrapeKey }
        ids.zip(stored).forEach { (id, fixKeys) -> assertEquals(FixKeys.forGame(id, fixKeys).first(), keys[id]) }
    }

    /** The home screen observes results by the key each list row carries: the same key. */
    @Test
    fun listRowsCarryTheSameKey() = runTest {
        val stored = listOf("", "crc:0000abcd:1024", "crc:0000abcd:2048\ncode:SNES:ZQ\nchd:ff")
        val ids = insertNamed(*stored.toTypedArray())
        val rows = db.gameDao().observeSystemGames("snes").first().associate { it.id to it.scrapeKey }
        ids.zip(stored).forEach { (id, fixKeys) -> assertEquals(FixKeys.forGame(id, fixKeys).first(), rows[id]) }
        ids.forEach { db.gameDao().setFavorite(it, true) }
        val favorites = db.gameDao().observeFavorites().first().associate { it.id to it.scrapeKey }
        assertEquals("Favorites carry it too", rows, favorites)
    }

    @Test
    fun aRunNeverReplacesAPersonsChoiceNorAMatchWithANoMatch() = runTest {
        dao.insertOrReplace(row("crc:a:1", personChoice = true))
        assertEquals(ScrapedGameDao.RunStore.KEPT_PERSON_CHOICE, dao.storeFromRun(row("crc:a:1", ScrapeOutcome.NO_MATCH, at = NOW + 5)))
        assertEquals(true, dao.get("crc:a:1")?.isPersonChoice)
        assertEquals(NOW, dao.get("crc:a:1")?.scrapedAt)

        dao.storeFromRun(row("crc:b:1"))
        assertEquals(ScrapedGameDao.RunStore.KEPT_MATCH, dao.storeFromRun(row("crc:b:1", ScrapeOutcome.NO_MATCH, at = NOW + 5)))
        assertEquals(ScrapeOutcome.MATCHED, dao.get("crc:b:1")?.outcome)
        assertEquals("the match stays, its time moves", NOW + 5, dao.get("crc:b:1")?.scrapedAt)

        assertEquals(ScrapedGameDao.RunStore.STORED, dao.storeFromRun(row("crc:b:1", at = NOW + 9).copy(title = "Zork Quest II")))
        assertEquals("Zork Quest II", dao.get("crc:b:1")?.title)
    }

    @Test
    fun pendingLeavesOutUnlistedUnnamedAndStoredGames() = runTest {
        val ids = insertNamed("crc:1:1", "crc:2:1", "crc:3:1", "crc:4:1", "crc:5:1", "crc:6:1", "crc:7:1", "crc:8:1", "crc:9:1")
        sql("UPDATE game SET is_hidden = 1 WHERE id = ?", ids[0])
        sql("UPDATE game SET is_not_game = 1 WHERE id = ?", ids[1])
        sql("UPDATE game SET is_primary_copy = 0 WHERE id = ?", ids[2])
        sql("UPDATE game SET needs_identify = 1 WHERE id = ?", ids[3])
        dao.insertOrReplace(row("crc:5:1"))
        dao.insertOrReplace(row("crc:6:1", ScrapeOutcome.NO_MATCH, at = NOW - DAY))
        dao.insertOrReplace(row("crc:7:1", ScrapeOutcome.NO_MATCH, at = NOW - 31 * DAY))
        dao.insertOrReplace(row("crc:8:1", ScrapeOutcome.NO_MATCH, at = NOW - 31 * DAY, personChoice = true))
        assertEquals("an old no-match and a game never asked", listOf(ids[6], ids[8]), pendingIds())
        assertEquals(2, dao.countPending(NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS))
        assertEquals("listed: not hidden, a game, primary", 6, dao.countListed())
        assertEquals(1, dao.countWithBoxFront())
    }

    @Test
    fun pendingSupportOffersMatchesWithoutTheirDiscPictureWhateverTheirTime() = runTest {
        insertNamed("crc:1:1", "crc:2:1", "crc:3:1", "crc:4:1")
        dao.insertOrReplace(row("crc:1:1", at = NOW - 10).copy(supportMissing = true, supportAttempts = 1))
        dao.insertOrReplace(row("crc:2:1", at = NOW - 10).copy(supportMissing = true, isPersonChoice = true))
        // Stored a moment ago by the same run's main pass: its disc comes in the same run.
        dao.insertOrReplace(row("crc:3:1", at = NOW + 10).copy(supportMissing = true))
        // Asked for again: the main pass takes it first.
        dao.insertOrReplace(row("crc:4:1", at = NOW - 10).copy(supportMissing = true, askAgain = true))
        assertEquals(listOf("crc:1:1", "crc:3:1"), dao.pendingSupport(null, 10).map { it.scrapeKey })
        // Each store of the disc pass still without the picture counts one more attempt; after three it is given up.
        repeat(2) { dao.storeFromRun(row("crc:1:1", at = NOW - 5).copy(supportMissing = true), supportAsked = true) }
        assertEquals(3, dao.get("crc:1:1")?.supportAttempts)
        assertEquals(listOf("crc:3:1"), dao.pendingSupport(null, 10).map { it.scrapeKey })
        dao.storeFromRun(row("crc:1:1", at = NOW - 5), supportAsked = true)
        assertEquals("saved at last: back to 0", 0, dao.get("crc:1:1")?.supportAttempts)
    }

    @Test
    fun aMainPassStoreCountsNoDiscAttempt() = runTest {
        insertNamed("crc:1:1", "crc:2:1")
        // The main pass saves the box front only: missing, but not tried.
        dao.storeFromRun(row("crc:1:1").copy(supportMissing = true), supportAsked = false)
        assertEquals(0, dao.get("crc:1:1")?.supportAttempts)
        assertEquals(listOf("crc:1:1"), dao.pendingSupport(null, 10).map { it.scrapeKey })
        // Given up after three disc-pass failures; the person asks again and the main pass stores it again: three new tries.
        dao.insertOrReplace(row("crc:2:1").copy(supportMissing = true, supportAttempts = 3))
        dao.storeFromRun(row("crc:2:1").copy(supportMissing = true), supportAsked = false)
        assertEquals(0, dao.get("crc:2:1")?.supportAttempts)
        // A main-pass no-match over a match missing its disc keeps the match and counts nothing.
        assertEquals(ScrapedGameDao.RunStore.KEPT_MATCH, dao.storeFromRun(row("crc:1:1", ScrapeOutcome.NO_MATCH, at = NOW + 5), supportAsked = false))
        assertEquals(0, dao.get("crc:1:1")?.supportAttempts)
        assertEquals(listOf("crc:1:1", "crc:2:1"), dao.pendingSupport(null, 10).map { it.scrapeKey })
    }

    @Test
    fun aDiscPassThatFindsNoMatchStillCountsAFailedAttempt() = runTest {
        insertNamed("crc:1:1", "crc:2:1")
        assertEquals(ScrapedGameDao.RunStore.STORED, dao.storeFromRun(row("crc:1:1", at = NOW - 10).copy(supportMissing = true)))
        dao.storeFromRun(row("crc:2:1", at = NOW - 10))
        repeat(2) {
            assertEquals(ScrapedGameDao.RunStore.KEPT_MATCH, dao.storeFromRun(row("crc:1:1", ScrapeOutcome.NO_MATCH, at = NOW - 5)))
            assertEquals(ScrapedGameDao.RunStore.KEPT_MATCH, dao.storeFromRun(row("crc:2:1", ScrapeOutcome.NO_MATCH, at = NOW - 5)))
        }
        assertEquals(3, dao.get("crc:1:1")?.supportAttempts)
        assertEquals("nothing missing, nothing counted", 0, dao.get("crc:2:1")?.supportAttempts)
        assertEquals("given up after three", emptyList<String>(), dao.pendingSupport(null, 10).map { it.scrapeKey })
    }

    @Test
    fun aCopyToANewKeyCarriesEveryColumnButNoGameAndReplacesOnlyAnAutomaticRowWithAPick() = runTest {
        val (plain) = insertNamed("")
        val rowKey = NameFixEntity.rowKey(plain)
        dao.insertOrReplace(row(rowKey, gameId = plain).copy(pictures = "pictures", askAgain = true))
        assertTrue(dao.copyToKey(rowKey, "crc:0000abcd:1024"))
        assertEquals(dao.get(rowKey)!!.copy(scrapeKey = "crc:0000abcd:1024", gameId = null), dao.get("crc:0000abcd:1024"))
        assertNotNull("the old row stays", dao.get(rowKey))

        dao.insertOrReplace(row("crc:0000beef:1024", outcome = ScrapeOutcome.NO_MATCH))
        assertFalse("an automatic row there is kept against an automatic one", dao.copyToKey(rowKey, "crc:0000beef:1024"))
        assertEquals(ScrapeOutcome.NO_MATCH, dao.get("crc:0000beef:1024")?.outcome)
        assertFalse("nothing to copy", dao.copyToKey("crc:00000001:1", "crc:00000002:1"))
        assertNull(dao.get("crc:00000002:1"))
        assertFalse("never to a row key", dao.copyToKey("crc:0000abcd:1024", NameFixEntity.rowKey(plain + 1)))
        assertNull(dao.get(NameFixEntity.rowKey(plain + 1)))

        // A person's pick replaces an automatic row (a match or a no-match), never another pick.
        dao.insertOrReplace(row("pick", personChoice = true).copy(pictures = "picked"))
        assertTrue(dao.copyToKey("pick", "crc:0000beef:1024", replaceAutomatic = true))
        assertEquals("picked", dao.get("crc:0000beef:1024")?.pictures)
        assertTrue(dao.copyToKey("pick", "crc:0000abcd:1024", replaceAutomatic = true))
        assertTrue(dao.get("crc:0000abcd:1024")!!.isPersonChoice)
        dao.insertOrReplace(row("other pick", personChoice = true).copy(pictures = "other"))
        assertFalse(dao.copyToKey("other pick", "crc:0000abcd:1024", replaceAutomatic = true))
        assertEquals("picked", dao.get("crc:0000abcd:1024")?.pictures)

        // The copy is a content-key row: it outlives the game, the row-key one goes with it.
        db.scanDao().forgetLibrary()
        assertNull(dao.get(rowKey))
        assertNotNull(dao.get("crc:0000abcd:1024"))
    }

    @Test
    fun aRowKeyResultGoesWithItsGameAndAContentKeyResultStays() = runTest {
        val (plain) = insertNamed("", "crc:0000abcd:1024")
        dao.insertOrReplace(row(NameFixEntity.rowKey(plain), gameId = plain))
        dao.insertOrReplace(row("crc:0000abcd:1024"))
        db.scanDao().forgetLibrary()
        assertNull(dao.get(NameFixEntity.rowKey(plain)))
        assertEquals("1245", dao.get("crc:0000abcd:1024")?.screenScraperId)
    }

    @Test
    fun observeByKeysAnswersForMoreKeysThanOneStatementTakes() = runTest {
        val keys = (0 until 1_200).map { "crc:$it:1" }
        keys.forEach { dao.insertOrReplace(row(it)) }
        val seen = dao.observeByKeys(keys + "crc:none:1").first()
        assertEquals(keys.toSet(), seen.map { it.scrapeKey }.toSet())
        assertEquals(emptyList<ScrapedGameSummary>(), dao.observeByKeys(emptyList()).first())
    }

    @Test
    fun picturesRoundTripAndADamagedValueIsNoPicture() {
        val pictures = listOf(
            ScrapedPicture(ScrapedPicture.BOX_FRONT, "grid", "/m/1245/box-front-grid-240x336.webp", 234, 320),
            ScrapedPicture(ScrapedPicture.SUPPORT, "details", "/m/1245/support-details-480x480.webp", 480, 470),
        )
        assertEquals(pictures, ScrapedPictures.decode(ScrapedPictures.encode(pictures)))
        assertEquals(emptyList<ScrapedPicture>(), ScrapedPictures.decode(""))
        assertEquals(emptyList<ScrapedPicture>(), ScrapedPictures.decode("[{\"kind\":"))
    }

    // Settings' Download missing and Re-download.

    /** Six games: a match with its box front, one without, an old and a fresh no-match, a person's choice, and one with no row. */
    private suspend fun libraryForAskingAgain(): List<Long> {
        val ids = insertNamed("crc:a:1", "crc:b:1", "crc:c:1", "crc:d:1", "crc:e:1", "crc:f:1")
        dao.insertOrReplace(row("crc:a:1"))
        dao.insertOrReplace(row("crc:b:1").copy(hasBoxFront = false, supportMissing = true))
        dao.insertOrReplace(row("crc:c:1", ScrapeOutcome.NO_MATCH, at = NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS - 1))
        dao.insertOrReplace(row("crc:d:1", ScrapeOutcome.NO_MATCH, at = NOW))
        dao.insertOrReplace(row("crc:e:1", ScrapeOutcome.NO_MATCH, at = NOW, personChoice = true))
        return ids
    }

    @Test
    fun downloadMissingAsksAgainEveryGameWithoutABoxFrontButNeverAPersonsChoice() = runTest {
        val ids = libraryForAskingAgain()
        val match = ids[0]
        val noPicture = ids[1]
        val oldNoMatch = ids[2]
        val freshNoMatch = ids[3]
        val chosen = ids[4]
        val noRow = ids[5]
        assertEquals(listOf(oldNoMatch, noRow), pendingIds())
        assertEquals("the match without its picture and both no-matches", 3, dao.markMissingToAskAgain())
        assertEquals(listOf(noPicture, oldNoMatch, freshNoMatch, noRow), pendingIds())
        assertEquals(4, dao.countPending(NOW - ScrapedGameDao.NO_MATCH_RETRY_MILLIS))
        assertEquals("a person's choice is never marked", false, dao.get("crc:e:1")?.askAgain)
        assertEquals(false, dao.get("crc:a:1")?.askAgain)
        assertEquals("the row stays as it was until replaced", "Zork Quest", dao.get("crc:b:1")?.title)
        listOf(match, chosen).forEach { assertFalse(it in pendingIds()) }
    }

    @Test
    fun reDownloadAsksAgainEveryStoredGameButNeverAPersonsChoiceAndKeepsThePictures() = runTest {
        val ids = libraryForAskingAgain()
        val match = ids[0]
        val noPicture = ids[1]
        val oldNoMatch = ids[2]
        val freshNoMatch = ids[3]
        val chosen = ids[4]
        val noRow = ids[5]
        assertEquals(4, dao.markAllToAskAgain())
        assertEquals(listOf(match, noPicture, oldNoMatch, freshNoMatch, noRow), pendingIds())
        assertFalse(chosen in pendingIds())
        val kept = dao.get("crc:a:1")
        assertEquals("the match and its pictures stay until the new answer", true, kept?.hasBoxFront)
        assertEquals(ScrapeOutcome.MATCHED, kept?.outcome)
        // The new answer clears the mark; a no-match for a match keeps the match and clears it too.
        dao.storeFromRun(row("crc:a:1", ScrapeOutcome.NO_MATCH, at = NOW + 5))
        assertEquals(ScrapeOutcome.MATCHED, dao.get("crc:a:1")?.outcome)
        assertEquals(false, dao.get("crc:a:1")?.askAgain)
        dao.storeFromRun(row("crc:c:1", ScrapeOutcome.MATCHED, at = NOW + 5))
        assertEquals(false, dao.get("crc:c:1")?.askAgain)
        assertEquals(listOf(noPicture, freshNoMatch, noRow), pendingIds())
    }

    @Test
    fun askingAgainForChosenSystemsMarksOnlyTheirGames() = runTest {
        val ids = libraryForAskingAgain()
        val match = ids[0]
        val noPicture = ids[1]
        val oldNoMatch = ids[2]
        val freshNoMatch = ids[3]
        val chosen = ids[4]
        val noRow = ids[5]
        sql("INSERT INTO system (id, name) VALUES ('nes', 'NES')")
        listOf(match, freshNoMatch).forEach { sql("UPDATE game SET system_id = 'nes' WHERE id = ?", it) }
        assertEquals("only the NES no-match has no box front", 1, dao.markMissingToAskAgainIn(listOf("nes")))
        assertEquals(listOf(oldNoMatch, freshNoMatch, noRow), pendingIds())
        assertEquals("both NES games, the person's choice never", 2, dao.markAllToAskAgainIn(listOf("nes")))
        assertEquals(listOf(match, oldNoMatch, freshNoMatch, noRow), pendingIds())
        assertEquals("the SNES games stay as they were", false, dao.get("crc:b:1")?.askAgain)
        assertEquals(0, dao.markAllToAskAgainIn(listOf("gba")))
        assertFalse(chosen in pendingIds())
        dao.markAllToAskAgainIn(listOf("nes", "snes"))
        assertEquals(listOf(match, noPicture, oldNoMatch, freshNoMatch, noRow), pendingIds())
    }

    @Test
    fun aHiddenGameIsNotMarked() = runTest {
        val ids = insertNamed("crc:a:1")
        dao.insertOrReplace(row("crc:a:1"))
        sql("UPDATE game SET is_hidden = 1 WHERE id = ?", ids.single())
        assertEquals(0, dao.markAllToAskAgain())
        assertEquals(false, dao.get("crc:a:1")?.askAgain)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_800_000_000_000L
    }
}
