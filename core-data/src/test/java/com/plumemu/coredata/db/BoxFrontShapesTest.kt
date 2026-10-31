package com.plumemu.coredata.db

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Each system's box-front shape from the person's own covers. */
@OptIn(ExperimentalCoroutinesApi::class) // currentTime: virtual time in readTimes' tests
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxFrontShapesTest {
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

    /** A listed game of [system] (no fix keys, so its result is stored under `game:<id>`). */
    private suspend fun addGame(system: String): Long {
        val doc = "g${nextDoc++}.rom"
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity(system, system)), listOf(game(folderId, doc, systemId = system))))
        db.scanDao().finishScan(ScanFinish(folderId))
        val id = db.scanDao().gamesInFolder(folderId).single { it.documentId == doc }.id
        db.openHelper.writableDatabase.execSQL("UPDATE game SET needs_identify = 0 WHERE id = ?", arrayOf<Any>(id))
        return id
    }

    /** [system]'s game with its box front saved at [width] x [height] (large) and a grid copy of another size. */
    private suspend fun addCover(
        system: String,
        width: Int,
        height: Int,
        outcome: ScrapeOutcome = ScrapeOutcome.MATCHED,
        path: String = "/covers/$system/$nextDoc.jpg",
    ) {
        val id = addGame(system)
        val pictures =
            listOf(
                ScrapedPicture(ScrapedPicture.BOX_FRONT, GRID, path, width / 2 + 1, height / 2 + 3),
                ScrapedPicture(ScrapedPicture.BOX_FRONT, LARGE, path, width, height),
                ScrapedPicture(ScrapedPicture.SUPPORT, LARGE, "$path.disc", 999, 111),
            )
        dao.insertOrReplace(
            ScrapedGameEntity(
                scrapeKey = NameFixEntity.ROW_PREFIX + id,
                gameId = id,
                outcome = outcome,
                screenScraperId = "1",
                matchedBy = ScrapeMatchedBy.HASH,
                scrapedAt = 1,
                detailsJson = "{}",
                title = "Game $id",
                year = null,
                publisher = null,
                players = null,
                genre = null,
                pictures = ScrapedPictures.encode(pictures),
                hasBoxFront = true,
            ),
        )
    }

    private suspend fun medians() = BoxFrontShapes.medians(dao.boxFrontSizes(LARGE))

    @Test
    fun theSqlReadsTheLargeBoxFrontsSizeFromWhatTheEncoderWrote() = runBlocking {
        // A path holding quotes and `"w":` cannot fool the cut: JSON escapes the quotes.
        addCover("snes", 680, 500, path = "/covers/Mario \"w\":9 \"h\":9 Kart.jpg")
        assertEquals(listOf(BoxFrontSize("snes", 680, 500)), dao.boxFrontSizes(LARGE))
        assertEquals(listOf(BoxFrontSize("snes", 341, 253)), dao.boxFrontSizes(GRID))
    }

    @Test
    fun fiveCoversGiveTheMedianFourDoNot() = runBlocking {
        // Japanese Super Famicom boxes are tall where the platform data's US SNES is 1.37.
        listOf(360 to 500, 355 to 500, 370 to 500, 340 to 500).forEach { (w, h) -> addCover("snes", w, h) }
        assertEquals("four covers keep the platform data's ratio", emptyMap<String, Float>(), medians())
        addCover("snes", 900, 500)
        // 0.68, 0.71, 0.72, 0.74, 1.8: the median, not the mean (one odd box does not decide).
        assertEquals(mapOf("snes" to 0.72f), medians())
    }

    @Test
    fun anEvenCountTakesTheMiddleTwoAndEachSystemIsItsOwn() = runBlocking {
        listOf(500 to 450, 502 to 449, 510 to 450, 480 to 450, 500 to 440, 520 to 440).forEach { (w, h) -> addCover("nds", w, h) }
        addCover("gba", 524, 520)
        // Middle two of six: 1.1180 and 1.1333 -> 1.1257 -> 1.13; one Game Boy Advance box says nothing yet.
        assertEquals(mapOf("nds" to 1.13f), medians())
    }

    @Test
    fun aSystemWithoutCoversOrWithOnlyNoMatchesHasNoShape() = runBlocking {
        addGame("psx")
        repeat(5) { addCover("ps2", 286, 400, outcome = ScrapeOutcome.NO_MATCH) }
        assertEquals(emptyMap<String, Float>(), medians())
    }

    @Test
    fun hiddenGamesAndExtraCopiesAreNotCounted() = runBlocking {
        // Five covers each; one game per system is left out of the lists, so four count.
        val leftOut = mapOf("gb" to "is_hidden = 1", "gbc" to "is_not_game = 1", "gba" to "is_primary_copy = 0")
        for ((system, mark) in leftOut) {
            repeat(5) { addCover(system, 400, 400) }
            db.openHelper.writableDatabase.execSQL("UPDATE game SET $mark WHERE id = (SELECT MAX(id) FROM game WHERE system_id = '$system')")
        }
        assertEquals(emptyMap<String, Float>(), medians())
        // The same five with nothing left out give a median.
        repeat(5) { addCover("nds", 400, 400) }
        assertEquals(mapOf("nds" to 1f), medians())
    }

    @Test
    fun storesThatKeepComingAreReadAtMostOnceAMinuteThenOnceTheyStop() = runTest {
        // The state when observed, then 30 games stored 4 s apart (a box-art run).
        val changes = flow {
            emit(Unit)
            repeat(30) {
                delay(4_000)
                emit(Unit)
            }
        }
        val reads = mutableListOf<Long>()
        BoxFrontShapes.readTimes(changes, BoxFrontShapes.READ_INTERVAL_MILLIS, BoxFrontShapes.SETTLE_MILLIS).collect { reads += currentTime }
        assertEquals("at once, then about once a minute: $reads", 3, reads.size)
        assertEquals(0L, reads.first())
        reads.zipWithNext().forEach { (a, b) -> assertTrue("a minute apart at least: $reads", b - a >= BoxFrontShapes.READ_INTERVAL_MILLIS) }
        // The last store is at 120 s: read within about a settle of it (the minute after 62 s had passed).
        assertTrue("$reads", reads.last() in 120_000L..124_000L)
    }

    @Test
    fun aSingleStoreAfterAQuietMinuteIsReadOneSettleLater() = runTest {
        val changes = flow {
            emit(Unit)
            delay(90_000)
            emit(Unit)
            delay(600_000)
        }
        val reads = mutableListOf<Long>()
        BoxFrontShapes.readTimes(changes, BoxFrontShapes.READ_INTERVAL_MILLIS, BoxFrontShapes.SETTLE_MILLIS).collect { reads += currentTime }
        // Nothing more when the changes end without a new one.
        assertEquals(listOf(0L, 92_000L), reads)
    }

    @Test
    fun theObservedShapesFollowNewCoversOnceTheyAreStored() = runBlocking {
        repeat(4) { addCover("nds", 502, 449) }
        val seen = Channel<Map<String, Float>>(Channel.UNLIMITED)
        val watching = launch { BoxFrontShapes.observe(db, LARGE, intervalMillis = 50, settleMillis = 50).collect { seen.send(it) } }
        assertEquals("read at once when observed", emptyMap<String, Float>(), withTimeout(TIMEOUT_MS) { seen.receive() })
        addCover("nds", 502, 449)
        assertEquals(mapOf("nds" to 1.12f), withTimeout(TIMEOUT_MS) { seen.receive() })
        // A change that leaves the medians as they were sends nothing.
        addGame("psx")
        delay(300)
        assertEquals(null, seen.tryReceive().getOrNull())
        watching.cancel()
    }

    private companion object {
        const val GRID = "grid"
        const val LARGE = "large"
        const val TIMEOUT_MS = 5_000L
    }
}
