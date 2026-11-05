package com.plumemu.graph

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.ArtAspects
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.NameFixEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.ScanFinish
import com.plumemu.coredata.db.ScrapeMatchedBy
import com.plumemu.coredata.db.ScrapeOutcome
import com.plumemu.coredata.db.ScrapedGameEntity
import com.plumemu.coredata.db.ScrapedPicture
import com.plumemu.coredata.db.ScrapedPictures
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.coredata.settings.BoxArtSourceSetting
import com.plumemu.coreui.testing.ReferenceShots
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The stored shapes on a real database: a pass that fails half way is caught up by a later one, and the app's home content runs the pass at all. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class KeepArtAspectsDatabaseTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun entity(folderId: Long, name: String) =
        GameEntity(
            folderId = folderId,
            documentId = name,
            parentDocumentId = "root",
            relativePath = name,
            fileName = "$name.sfc",
            sizeBytes = 1,
            modifiedAt = 1,
            title = name,
            sortName = name,
            regions = "",
            discGroup = null,
            discCount = 1,
            systemId = "snes",
            isPrimaryCopy = true,
        )

    private fun cover(gameId: Long) =
        ScrapedGameEntity(
            scrapeKey = NameFixEntity.ROW_PREFIX + gameId,
            gameId = gameId,
            outcome = ScrapeOutcome.MATCHED,
            screenScraperId = "1",
            matchedBy = ScrapeMatchedBy.HASH,
            isPersonChoice = false,
            scrapedAt = 1,
            detailsJson = "{}",
            title = "Game",
            year = null,
            publisher = null,
            players = null,
            genre = null,
            pictures = ScrapedPictures.encode(listOf(ScrapedPicture(ScrapedPicture.BOX_FRONT, ScrapeMediaSizes.Grid.name, "/covers/grid.jpg", 300, 400))),
            hasBoxFront = true,
        )

    /** [count] games, each with a saved box front; their ids. */
    private suspend fun library(db: PlumDatabase, count: Int): List<Long> {
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/roms", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "snes")), (0 until count).map { entity(folder, "g$it") }))
        db.scanDao().finishScan(ScanFinish(folder))
        val ids = db.scanDao().gamesInFolder(folder).map { it.id }.sorted()
        ids.forEach { db.scrapedGameDao().insertOrReplace(cover(it)) }
        return ids
    }

    private suspend fun waitFor(condition: suspend () -> Boolean) = withTimeout(WAIT_MILLIS) { while (!condition()) delay(POLL_MILLIS) }

    @Test
    fun aPassThatFailsMidWayIsCaughtUpByALaterOne() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()
        val ids = library(db, GAMES)
        // The second chunk fails on one row until the trigger goes: a real SQL failure inside `sync`, not a stand-in.
        val bad = ids[ArtAspects.WRITE_CHUNK + 10]
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_shape BEFORE UPDATE OF art_aspect ON game WHEN NEW.id = $bad BEGIN SELECT RAISE(ABORT, 'no'); END")
        val logs = CopyOnWriteArrayList<String>()
        val passes = ArtAspects.follow(db, ScrapeMediaSizes.Grid.name, ScrapeMediaSizes.Large.name, firstDelayMillis = 0, intervalMillis = 1, settleMillis = 1)
        val keeping = CoroutineScope(Dispatchers.Default).launch { keepArtAspects(flowOf(true), passes, logs::add, {}, retryMillis = 1).collect {} }
        waitFor { logs.isNotEmpty() }
        assertTrue(logs.toString(), logs[0].startsWith("Box art shapes could not be stored (SQLite"))
        assertNotNull("the chunk before the failure stands", db.gameDao().game(ids.first())?.artAspect)
        assertNull("the failing row", db.gameDao().game(bad)?.artAspect)
        sql.execSQL("DROP TRIGGER fail_shape")
        waitFor { ids.all { db.gameDao().game(it)?.artAspect != null } }
        keeping.cancelAndJoin()
        db.close()
    }

    /** Removing the pass from `AppGraph.homeContent` leaves shapes unstored however long the screen is open. */
    @Test
    fun theHomeContentRunsThePass(): Unit = runBlocking {
        val graph = AppGraph(context, log = {})
        val id = library(graph.database, 1).single()
        graph.settingsStore.update { it.copy(library = it.library.copy(boxArtSource = BoxArtSourceSetting.LibretroOnly)) }
        val screen = CoroutineScope(Dispatchers.Default).launch { graph.homeContent.collect {} }
        waitFor { graph.database.gameDao().game(id)?.artAspect != null }
        screen.cancelAndJoin()
        assertEquals(300f / 400, graph.database.gameDao().game(id)?.artAspect)
        graph.database.close()
        assertTrue(context.deleteDatabase("plumemu.db"))
    }

    private companion object {
        const val GAMES = 230
        const val WAIT_MILLIS = 15_000L
        const val POLL_MILLIS = 20L
    }
}
