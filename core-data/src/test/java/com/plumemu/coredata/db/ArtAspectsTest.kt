package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** `game.art_aspect` follows `scraped_game`: filled, overwritten, cleared, whichever path changed the art. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArtAspectsTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.scrapedGameDao()
    private var folderId = 0L
    private var nextDoc = 0

    @Before
    fun setUp() = runBlocking {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun addGame(): Long {
        val doc = "g${nextDoc++}.rom"
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), listOf(game(folderId, doc, systemId = "snes"))))
        db.scanDao().finishScan(ScanFinish(folderId))
        return db.scanDao().gamesInFolder(folderId).single { it.documentId == doc }.id
    }

    private fun result(key: String, gameId: Long?, pictures: List<ScrapedPicture>, outcome: ScrapeOutcome = ScrapeOutcome.MATCHED, isPerson: Boolean = false) =
        ScrapedGameEntity(
            scrapeKey = key,
            gameId = gameId,
            outcome = outcome,
            screenScraperId = "1",
            matchedBy = ScrapeMatchedBy.HASH,
            isPersonChoice = isPerson,
            scrapedAt = 1,
            detailsJson = "{}",
            title = "Game",
            year = null,
            publisher = null,
            players = null,
            genre = null,
            pictures = ScrapedPictures.encode(pictures),
            hasBoxFront = pictures.isNotEmpty(),
        )

    private fun front(size: String, width: Int, height: Int) = ScrapedPicture(ScrapedPicture.BOX_FRONT, size, "/covers/$size.jpg", width, height)

    private suspend fun store(id: Long, width: Int, height: Int) =
        dao.insertOrReplace(result(NameFixEntity.ROW_PREFIX + id, id, listOf(front(GRID, width, height), front(LARGE, width * 2 + 1, height * 2))))

    private suspend fun sync() = ArtAspects.sync(db.artAspectDao(), GRID, LARGE).rowsWritten

    private suspend fun stored(id: Long) = db.gameDao().game(id)?.artAspect

    @Test
    fun aFirstPassFillsTheGridPicturesShapeToTheBit() = runBlocking {
        val id = addGame()
        store(id, 317, 223)
        assertNull(stored(id))
        assertEquals(1, sync())
        assertEquals("as ArtPicture.aspect", 317f / 223, stored(id))
    }

    @Test
    fun aSecondPassWritesNothingSoAPassNeverStartsAnother() = runBlocking {
        val id = addGame()
        store(id, 317, 223)
        sync()
        assertEquals(0, sync())
        assertEquals(317f / 223, stored(id))
    }

    @Test
    fun withOnlyALargePictureItsShapeIsStored() = runBlocking {
        val id = addGame()
        dao.insertOrReplace(result(NameFixEntity.ROW_PREFIX + id, id, listOf(ScrapedPicture(ScrapedPicture.SUPPORT, GRID, "/d.jpg", 9, 9), front(LARGE, 300, 400))))
        sync()
        assertEquals(0.75f, stored(id))
    }

    @Test
    fun replacedArtOverwritesTheStoredShape() = runBlocking {
        val id = addGame()
        store(id, 300, 400)
        sync()
        store(id, 400, 300)
        assertEquals(1, sync())
        assertEquals(400f / 300, stored(id))
    }

    @Test
    fun clearedArtClearsTheShapeButNotAPersonsChoice() = runBlocking {
        val automatic = addGame()
        val chosen = addGame()
        store(automatic, 300, 400)
        dao.insertOrReplace(result(NameFixEntity.ROW_PREFIX + chosen, chosen, listOf(front(GRID, 300, 300)), isPerson = true))
        sync()
        dao.forgetPictures()
        assertEquals(1, sync())
        assertNull(stored(automatic))
        assertEquals(1f, stored(chosen))
    }

    @Test
    fun aNoMatchReplacingAResultClearsTheShape() = runBlocking {
        val id = addGame()
        store(id, 300, 400)
        sync()
        dao.insertOrReplace(result(NameFixEntity.ROW_PREFIX + id, id, listOf(front(GRID, 300, 400)), ScrapeOutcome.NO_MATCH))
        sync()
        assertNull(stored(id))
    }

    @Test
    fun aGameWhoseKeyChangesTakesTheShapeStoredUnderItsNewKey() = runBlocking {
        val id = addGame()
        store(id, 300, 400)
        sync()
        dao.insertOrReplace(result("crc:new", null, listOf(front(GRID, 500, 400))))
        db.openHelper.writableDatabase.execSQL("UPDATE game SET fix_keys = 'crc:new' WHERE id = ?", arrayOf<Any>(id))
        sync()
        assertEquals(1.25f, stored(id))
    }

    @Test
    fun aRescanKeepsTheStoredShape() = runBlocking {
        val id = addGame()
        store(id, 300, 400)
        sync()
        val row = checkNotNull(db.gameDao().game(id))
        db.scanDao().applyBatch(ScanBatch(folderId, emptyList(), listOf(game(folderId, row.documentId, systemId = "snes"))))
        db.scanDao().finishScan(ScanFinish(folderId))
        assertEquals(0.75f, stored(id))
        assertEquals(0, sync())
    }

    @Test
    fun aListRowCarriesTheStoredShape() = runBlocking {
        val id = addGame()
        db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 0 WHERE id = ?", arrayOf<Any>(id))
        store(id, 300, 400)
        sync()
        assertEquals(0.75f, db.gameDao().observeSystemGames("snes").first().single().artAspect)
        assertEquals(0.75f, db.gameDao().rowsOf(listOf(id)).single().artAspect)
    }

    /** Many covers, one transaction: the list queries see one change. */
    @Test
    fun manyChangesReachTheListsAsOneWrite() = runBlocking {
        val ids = (1..40).map { addGame() }
        ids.forEach { store(it, 300, 400) }
        val emissions = mutableListOf<List<GameListRow>>()
        val job = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { db.gameDao().observeSystemGames("snes").collect { emissions += it } }
        withTimeout(WAIT_MILLIS) { while (emissions.isEmpty()) kotlinx.coroutines.yield() }
        sync()
        withTimeout(WAIT_MILLIS) { while (emissions.last().any { it.artAspect == null }) kotlinx.coroutines.delay(5) }
        job.cancel()
        assertEquals("the first read and one change: $emissions".take(60), 2, emissions.size)
    }

    /** Stands for Room's DAO: the same rows, and what happened between the writes. */
    private class CountingDao(
        count: Int,
        private val events: MutableList<String>,
    ) : ArtAspectDao() {
        private val sizes = (1L..count).map { ArtSizes(it, null, 300, 400, 0, 0) }

        override suspend fun artSizesOf(grid: String, large: String) = sizes

        override suspend fun setArtAspect(gameId: Long, aspect: Float?) = Unit

        override suspend fun write(changes: List<ArtAspectChange>) {
            events += "write ${changes.size}"
        }
    }

    /** A library's first pass: a transaction of about 200 rows at a time, and other work gets its turn between them. */
    @Test
    fun aFirstPassWritesInChunksAndLetsOtherWorkIn() = runBlocking {
        val events = mutableListOf<String>()
        val other = launch {
            repeat(2) {
                events += "other"
                kotlinx.coroutines.yield()
            }
        }
        kotlinx.coroutines.yield()
        val pass = ArtAspects.sync(CountingDao(450, events), GRID, LARGE)
        other.join()
        assertEquals(listOf("other", "write 200", "other", "write 200", "write 50"), events)
        assertEquals(450, pass.rowsWritten)
    }

    @Test
    fun aPassReportsItsTimes() = runBlocking {
        val slow =
            object : ArtAspectDao() {
                override suspend fun artSizesOf(grid: String, large: String): List<ArtSizes> {
                    kotlinx.coroutines.delay(30)
                    return listOf(ArtSizes(1, null, 300, 400, 0, 0))
                }

                override suspend fun setArtAspect(gameId: Long, aspect: Float?) = Unit

                override suspend fun write(changes: List<ArtAspectChange>) = kotlinx.coroutines.delay(30)
            }
        val pass = ArtAspects.sync(slow, GRID, LARGE)
        assertTrue("read: $pass", pass.readMillis >= 25)
        assertTrue("write: $pass", pass.writeMillis >= 25)
    }

    @Test
    fun followingStoresThePictureThenTheOneThatReplacesIt() = runBlocking {
        val id = addGame()
        store(id, 300, 400)
        val passes = launch {
            ArtAspects.follow(db, GRID, LARGE, firstDelayMillis = 0, intervalMillis = 1, settleMillis = 1).collect {}
        }
        withTimeout(WAIT_MILLIS) { while (stored(id) == null) kotlinx.coroutines.delay(5) }
        store(id, 400, 300)
        withTimeout(WAIT_MILLIS) { while (stored(id) != 400f / 300) kotlinx.coroutines.delay(5) }
        passes.cancel()
        assertTrue(stored(id) == 400f / 300)
    }

    private companion object {
        const val GRID = "grid"
        const val LARGE = "large"
        const val WAIT_MILLIS = 10_000L
    }
}
