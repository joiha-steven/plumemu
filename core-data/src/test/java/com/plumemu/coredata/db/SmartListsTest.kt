package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmartListsTest {
    private lateinit var db: PlumDatabase
    private lateinit var library: ScannedLibrary
    private val dao get() = db.smartListDao()
    private val folderId get() = library.folderId

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        library = ScannedLibrary(db)
        library.pickFolder()
    }

    @After
    fun tearDown() = db.close()

    private fun sql(statement: String) = db.openHelper.writableDatabase.execSQL(statement)

    private fun play(id: Long, seconds: Long, at: Long = 100, removedFromRecent: Boolean = false) =
        sql("INSERT INTO play (game_id, last_played_at, total_seconds, recent_removed) VALUES ($id, $at, $seconds, ${if (removedFromRecent) 1 else 0})")

    @Test
    fun emptyLibraryGivesEmptyLists() = runTest {
        assertEquals(emptyList<GameListRow>(), dao.observeMostPlayed().first())
        assertEquals(emptyList<GameListRow>(), dao.observeRecentlyAdded(now = NOW).first())
    }

    @Test
    fun mostPlayedLeavesOutNotAGameAndCountsAGameRemovedFromRecent() = runTest {
        val ids = library.scanIn(game(folderId, "a.sfc"), game(folderId, "notgame.sfc"), game(folderId, "b.cue", systemId = "psx"))
        play(ids.getValue("a.sfc"), 60, removedFromRecent = true)
        play(ids.getValue("notgame.sfc"), 600)
        play(ids.getValue("b.cue"), 30)
        sql("UPDATE game SET is_not_game = 1 WHERE id = ${ids.getValue("notgame.sfc")}")
        assertEquals("systems mixed", listOf("a.sfc", "b.cue"), dao.observeMostPlayed().first().map { it.title })
    }

    @Test
    fun mostPlayedIsMostFirstWithoutZeroAndStopsAtFifty() = runTest {
        val games = (1..55).map { game(folderId, "g%02d.sfc".format(it)) }
        val ids = library.scanIn(*games.toTypedArray(), game(folderId, "zero.sfc"), game(folderId, "hidden.sfc"), game(folderId, "copy.sfc", isPrimaryCopy = false))
        (1..55).forEach { play(ids.getValue("g%02d.sfc".format(it)), it * 10L) }
        play(ids.getValue("zero.sfc"), 0)
        play(ids.getValue("hidden.sfc"), 100_000)
        db.gameDao().setHidden(ids.getValue("hidden.sfc"), true)
        play(ids.getValue("copy.sfc"), 50_000)

        val list = dao.observeMostPlayed().first()
        assertEquals(SmartListDao.MOST_PLAYED_LIMIT, list.size)
        assertEquals("a copy the person played counts, as in Recent", "copy.sfc", list.first().title)
        assertEquals(listOf("g55.sfc", "g54.sfc"), list.drop(1).take(2).map { it.title })
        assertFalse(list.any { it.title == "zero.sfc" || it.title == "hidden.sfc" })
        assertEquals(listOf("g01.sfc"), dao.observeMostPlayed(limit = 56).first().takeLast(1).map { it.title })
    }

    @Test
    fun recentlyAddedIsTheLastThirtyDaysNewestFirstAndNeverTheFirstScan() = runTest {
        library.scanIn(game(folderId, "first-scan.sfc"), addedAt = null)
        library.scanIn(game(folderId, "old.sfc"), addedAt = NOW - SmartListDao.RECENTLY_ADDED_MS - 1)
        library.scanIn(game(folderId, "edge.sfc"), addedAt = NOW - SmartListDao.RECENTLY_ADDED_MS)
        val ids = library.scanIn(
            game(folderId, "new.sfc"),
            game(folderId, "copy.sfc", isPrimaryCopy = false),
            game(folderId, "hidden.sfc"),
            game(folderId, "notgame.sfc"),
            addedAt = NOW - 1_000,
        )
        db.gameDao().setHidden(ids.getValue("hidden.sfc"), true)
        sql("UPDATE game SET is_not_game = 1 WHERE id = ${ids.getValue("notgame.sfc")}")
        assertEquals(listOf("new.sfc", "edge.sfc"), dao.observeRecentlyAdded(now = NOW).first().map { it.title })
        assertEquals("a month later nothing is new", emptyList<GameListRow>(), dao.observeRecentlyAdded(now = NOW + SmartListDao.RECENTLY_ADDED_MS + 1).first())

        // A scan still running: its rows wait for the scan's end.
        db.scanDao().applyBatch(ScanBatch(folderId, newGames = listOf(game(folderId, "pending.sfc")), addedAt = NOW))
        assertEquals(listOf("new.sfc", "edge.sfc"), dao.observeRecentlyAdded(now = NOW).first().map { it.title })
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
