package com.plumemu

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.plumemu.coredata.db.GameEntity
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.RomFolderEntity
import com.plumemu.coredata.db.ScanBatch
import com.plumemu.coredata.db.SystemEntity
import com.plumemu.graph.GraphStatistics
import com.plumemu.home.HomeArtSource
import com.plumemu.settings.screen.StatisticsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings › Statistics' read on a device: the same
 * library as the JVM's `GraphStatisticsTest` budget check (10,000 games, 300 played, 7,000 with box
 * art), on the device's SQLite. Prints the first read and the median of five more under the log tag
 * `StatisticsTiming`. Run it on an emulator only, with `ANDROID_SERIAL` set:
 * `adb shell am instrument -w -e class com.plumemu.StatisticsTimingOnDeviceTest com.plumemu.test/androidx.test.runner.AndroidJUnitRunner`.
 */
@RunWith(AndroidJUnit4::class)
class StatisticsTimingOnDeviceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, PlumDatabase::class.java).build()

    @After
    fun tearDown() = db.close()

    @Test
    fun readOfTenThousandGames() =
        runBlocking {
            val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://t/tree/roms", displayName = "ROMs", addedAt = 0))
            val systems = listOf("snes", "gba", "md")
            db.scanDao().applyBatch(
                ScanBatch(
                    folderId,
                    systems = systems.map { SystemEntity(it, it) },
                    newGames = List(GAMES) { game(folderId, systems[it % 3], "Game %05d".format(it)) },
                ),
            )
            val ids = db.scanDao().gamesInFolder(folderId).sortedBy { it.title }.map { it.id }
            val sql = db.openHelper.writableDatabase
            sql.beginTransaction()
            try {
                ids.forEachIndexed { at, id ->
                    if (at % 10 < 7) {
                        sql.execSQL(
                            "INSERT INTO scraped_game (scrape_key, game_id, outcome, scraped_at, title, pictures, has_box_front) VALUES (?, ?, 'MATCHED', 1, 't', '', 1)",
                            arrayOf<Any>("game:$id", id),
                        )
                    }
                }
                sql.setTransactionSuccessful()
            } finally {
                sql.endTransaction()
            }
            ids.take(PLAYED).forEachIndexed { at, id -> db.playDao().addSession(id, endedAt = at.toLong(), seconds = 60L * (at + 1)) }
            val read =
                GraphStatistics(
                    plays = { db.playDao() },
                    scraped = { db.scrapedGameDao() },
                    catalog = { null },
                    hasBoxArt = { true },
                    art = HomeArtSource.None,
                    io = Dispatchers.IO,
                    log = { Log.e(TAG, it) },
                )
            val first = timed { read.read() }
            val again = List(5) { timed { read.read() } }.sorted()
            Log.i(TAG, "Statistics read on $GAMES games: first ${first.ms} ms, median of five more ${again[2].ms} ms")
            val figures = (read.read() as StatisticsState.Read).statistics
            assertEquals(GAMES, figures.libraryGames)
            assertEquals(PLAYED, figures.playedGames)
        }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private val Long.ms: String get() = "%.1f".format(this / 1_000_000.0)

    private fun game(
        folderId: Long,
        system: String,
        title: String,
    ) = GameEntity(
        folderId = folderId,
        documentId = "root/$system/$title",
        parentDocumentId = "root/$system",
        relativePath = "$system/$title",
        fileName = "$title.bin",
        sizeBytes = 1,
        modifiedAt = 1,
        title = title,
        sortName = title.lowercase(),
        regions = "",
        discGroup = null,
        discCount = 1,
        systemId = system,
        isPrimaryCopy = true,
    )

    private companion object {
        const val TAG = "StatisticsTiming"
        const val GAMES = 10_000
        const val PLAYED = 300
    }
}
