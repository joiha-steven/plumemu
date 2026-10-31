package com.plumemu.graph

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.SystemEntity
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Audit item 21 on a real database and real files: the size of the saved box art, Clear box art
 * (automatic results forget their pictures and the folders go, a person's pick stays, Download
 * missing then asks again) and the daily sweep (folders of games the library no longer has go; a
 * hidden game's, a person's pick's, a young folder and every folder of an empty library stay).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraphBoxArtTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: PlumDatabase
    private lateinit var media: File
    private lateinit var mark: FakeMark
    private val logs = CopyOnWriteArrayList<String>()
    private var now = NOW
    private var cancelledRuns = 0
    private var mayTouch = true
    private var released = 0
    private val runLock = Mutex()
    private var folderId = 0L

    private class FakeMark : SweepMark {
        var value = 0L

        override fun get() = value

        override fun set(millis: Long) {
            value = millis
        }
    }

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).allowMainThreadQueries().build()
        media = temp.newFolder("media")
        mark = FakeMark()
        folderId = runBlocking { db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0)) }
    }

    @After
    fun tearDown() = db.close()

    private fun boxArt(stopTimeoutMillis: Long = 30_000) =
        GraphBoxArt(
            mediaDir = { media },
            dao = { db.scrapedGameDao() },
            deliveries = flowOf(0),
            cancelRuns = { cancelledRuns++ },
            lastSweep = mark,
            runLock = runLock,
            hold = { mayTouch },
            release = { released++ },
            runStopTimeoutMillis = stopTimeoutMillis,
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
            scope = CoroutineScope(Dispatchers.Unconfined),
            clock = { now },
            log = { logs += it },
        )

    /** A picture folder `media/<id>` with one file of [bytes], last changed [ageMillis] ago. */
    private fun folder(id: String, bytes: Int = 1_000, ageMillis: Long = DAY * 3): File =
        File(media, id).apply {
            mkdirs()
            File(this, "box-front-grid-560x400.webp").writeBytes(ByteArray(bytes))
            setLastModified(now - ageMillis)
        }

    private fun picturesOf(id: String) = "[{\"kind\":\"box-front\",\"size\":\"grid\",\"path\":\"${File(media, id).path}/box.webp\",\"w\":1,\"h\":1}]"

    /** A game in the library ([hidden]: only hidden) with its stored result and picture folder; returns the key. */
    private fun gameWithArt(id: String, hidden: Boolean = false, personChoice: Boolean = false): String {
        val key = "crc:$id:1"
        runBlocking {
            db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "snes")), listOf(gameRow("doc$id"))))
            db.scanDao().finishScan(ScanFinish(folderId))
            val gameId = db.scanDao().gamesInFolder(folderId).single { it.documentId == "doc$id" }.id
            val sql = db.openHelper.writableDatabase
            sql.execSQL("UPDATE game SET fix_keys = ?, needs_identify = 0, is_hidden = ? WHERE id = ?", arrayOf<Any>(key, if (hidden) 1 else 0, gameId))
        }
        storeResult(key, id, personChoice)
        folder(id)
        return key
    }

    private fun storeResult(key: String, id: String, personChoice: Boolean = false) =
        runBlocking {
            db.scrapedGameDao().insertOrReplace(
                ScrapedGameEntity(
                    scrapeKey = key, gameId = null, outcome = ScrapeOutcome.MATCHED, screenScraperId = id, matchedBy = ScrapeMatchedBy.HASH,
                    isPersonChoice = personChoice, scrapedAt = NOW, detailsJson = "{}", title = "Zork $id", year = null, publisher = null,
                    players = null, genre = null, pictures = picturesOf(id), hasBoxFront = true,
                ),
            )
        }

    private fun gameRow(doc: String) =
        GameEntity(
            folderId = folderId, documentId = doc, parentDocumentId = "root", relativePath = doc, fileName = doc, sizeBytes = 1, modifiedAt = 1,
            title = doc, sortName = doc, regions = "", discGroup = null, discCount = 1, systemId = "snes", isPrimaryCopy = true,
        )

    private fun folders() = media.listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.sorted()

    private fun pendingTitles() = runBlocking { db.scrapedGameDao().pending(NOW - DAY * 40, null, 100).size }

    @Test
    fun theSizeIsTheBytesOfEveryPictureFolder() = runBlocking {
        gameWithArt("10")
        folder("11", bytes = 2_000)
        val boxArt = boxArt()
        assertEquals(3_000L, boxArt.size.first())
        assertEquals(0L, GraphBoxArt.sizeOf(File(temp.root, "nothing")))
    }

    @Test
    fun clearAsksNothingByItselfDeletesAutomaticPicturesAndKeepsAPersonsChoiceAndDownloadMissingAsksAgain() = runBlocking {
        val auto = gameWithArt("10")
        val mine = gameWithArt("20", personChoice = true)
        folder("99")
        val boxArt = boxArt()
        val done = CompletableDeferred<BoxArtClearResult>()
        boxArt.clear { done.complete(it) }
        assertEquals(BoxArtClearResult.Done, done.await())
        assertEquals("a run going is stopped first", 1, cancelledRuns)
        assertEquals("the person's pick keeps its folder, the rest goes", listOf("20"), folders())
        val dao = db.scrapedGameDao()
        assertEquals("", dao.get(auto)?.pictures)
        assertFalse(dao.get(auto)!!.hasBoxFront)
        assertEquals("Zork 10", dao.get(auto)?.title)
        assertEquals(picturesOf("20"), dao.get(mine)?.pictures)
        assertEquals("no run asks it by itself", 0, pendingTitles())
        assertEquals(1, dao.markMissingToAskAgain())
        assertEquals("Download missing asks it", 1, pendingTitles())
        assertEquals(1_000L, boxArt.size.first())
        assertEquals("the pictures are given back", 1, released)
    }

    @Test
    fun theSweepCleansAFallbackSourcesFoldersButKeepsOneARowHoldsAndTouchesNoOtherName() = runBlocking {
        val key = gameWithArt("10")
        // A match ScreenScraper had no box front for: its row has ScreenScraper's id, its box is in a Steam folder.
        db.openHelper.writableDatabase.execSQL("UPDATE scraped_game SET pictures = ? WHERE scrape_key = ?", arrayOf<Any>(picturesOf("steam_98765"), key))
        folder("steam_98765")
        folder("libretro_ab12cd")
        folder("notes")
        assertEquals(1, boxArt().sweepIfDue())
        assertEquals(listOf("10", "notes", "steam_98765"), folders())
    }

    @Test
    fun clearDeletesFallbackFoldersToo() = runBlocking {
        folder("steam_98765")
        folder("libretro_ab12cd")
        val done = CompletableDeferred<BoxArtClearResult>()
        boxArt().clear { done.complete(it) }
        assertEquals(BoxArtClearResult.Done, done.await())
        assertEquals(emptyList<String>(), folders())
    }

    @Test
    fun clearIsRefusedWhileABackupOrARestoreUsesThePictures() = runBlocking {
        gameWithArt("10")
        mayTouch = false
        val done = CompletableDeferred<BoxArtClearResult>()
        boxArt().clear { done.complete(it) }
        assertEquals(BoxArtClearResult.InUse, done.await())
        assertEquals(listOf("10"), folders())
        assertEquals(0, cancelledRuns)
        assertEquals("nothing was taken, nothing is given back", 0, released)
    }

    @Test
    fun clearWaitsForARunPausedBetweenSavingAndWritingItsRowAndThenLeavesNoRowWithoutAFile() = runBlocking {
        val key = gameWithArt("30")
        val saved = CompletableDeferred<Unit>()
        val rowWritten = CompletableDeferred<Unit>()
        // A run that has saved its picture and has not yet written the row (it holds the run lock the whole time).
        val run =
            launch(Dispatchers.Default) {
                runLock.withLock {
                    folder("30")
                    saved.complete(Unit)
                    rowWritten.await()
                    storeResult(key, "30")
                }
            }
        saved.await()
        val done = CompletableDeferred<BoxArtClearResult>()
        boxArt().clear { done.complete(it) }
        delay(300)
        assertFalse("it waits for the run", done.isCompleted)
        assertEquals("the run's picture is still there", listOf("30"), folders())
        rowWritten.complete(Unit)
        run.join()
        assertEquals(BoxArtClearResult.Done, done.await())
        assertEquals(emptyList<String>(), folders())
        val row = db.scrapedGameDao().get(key)!!
        assertEquals("", row.pictures)
        assertFalse("no row says it has a cover that is not on disk", row.hasBoxFront)
        assertEquals(1, released)
    }

    @Test
    fun clearGivesUpAndSaysSoWhenTheRunDoesNotStop() = runBlocking {
        gameWithArt("10")
        val done = CompletableDeferred<BoxArtClearResult>()
        runLock.withLock {
            boxArt(stopTimeoutMillis = 200).clear { done.complete(it) }
            assertEquals(BoxArtClearResult.RunStillGoing, done.await())
        }
        assertEquals(listOf("10"), folders())
        assertTrue(logs.any { it.contains("did not stop in time") })
        assertEquals(1, released)
    }

    @Test
    fun theSweepSkipsWhileARunIsGoing() = runBlocking {
        gameWithArt("10")
        folder("40")
        runLock.withLock { assertNull(boxArt().sweepIfDue()) }
        assertEquals(listOf("10", "40"), folders())
        assertEquals("a skipped sweep is not counted as done", 0L, mark.value)
        assertEquals("the pictures are given back", 1, released)
        assertEquals(1, boxArt().sweepIfDue())
    }

    @Test
    fun theFactsCountGamesGivenUpAfterThreeFailedDays() = runBlocking {
        val key = gameWithArt("10")
        val dao = db.scrapedGameDao()
        assertEquals(0, boxArt().facts.first().givenUp)
        repeat(3) { dao.recordFailure(key, 100L + it) }
        assertEquals(1, boxArt().facts.first { it.givenUp > 0 }.givenUp)
    }

    @Test
    fun theSweepDeletesFoldersOfGamesTheLibraryLacksButKeepsHiddenGamesPicksYoungFoldersAndRunsOnceADay() = runBlocking {
        gameWithArt("10")
        gameWithArt("20", hidden = true)
        gameWithArt("30", personChoice = true)
        // Game 40 is gone from the library: its row and folder remain.
        storeResult("crc:40:1", "40")
        folder("40")
        // Game 50 is gone too, but a run saved its folder a minute ago and has not stored the row.
        folder("50", ageMillis = 60_000)
        // A folder with no row at all.
        folder("60")
        // Not a picture folder: left alone.
        File(media, "notes").mkdirs()

        val boxArt = boxArt()
        assertEquals(2, boxArt.sweepIfDue())
        assertEquals(listOf("10", "20", "30", "50", "notes"), folders())
        assertNull("its row goes with it, so a game that comes back is asked again", db.scrapedGameDao().get("crc:40:1"))
        assertNotNull(db.scrapedGameDao().get("crc:20:1"))
        assertEquals(now, mark.value)

        folder("70")
        now += DAY / 2
        assertNull("not twice in a day", boxArt.sweepIfDue())
        assertTrue("70" in folders())
        now += DAY
        assertEquals(2, boxArt.sweepIfDue())
        assertEquals(listOf("10", "20", "30", "notes"), folders())
    }

    @Test
    fun aSweepMarkInTheFutureIsDueNotWaitedFor() = runBlocking {
        gameWithArt("10")
        folder("60")
        mark.value = now + DAY
        assertEquals(1, boxArt().sweepIfDue())
        assertEquals(now, mark.value)
    }

    @Test
    fun theSweepDoesNothingForAnEmptyLibraryOrWhileABackupRuns() = runBlocking {
        folder("40")
        assertNull("an empty library would make everything look orphaned", boxArt().sweepIfDue())
        assertEquals(listOf("40"), folders())
        gameWithArt("10")
        mayTouch = false
        assertNull(boxArt().sweepIfDue())
        assertEquals(listOf("10", "40"), folders())
        assertEquals("a skipped sweep is not counted as done", 0L, mark.value)
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val NOW = 1_800_000_000_000L
    }
}
