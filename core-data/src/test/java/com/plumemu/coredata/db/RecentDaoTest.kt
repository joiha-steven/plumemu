package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Recent's Remove from Recent and Clear Recent: a game leaves Recent until it is played
 * again, and its play time, sessions and statistics stay as they were.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecentDaoTest {
    private lateinit var db: PlumDatabase
    private val play get() = db.playDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    /** Inserts [count] games and plays game i (1-based) ending at i * 1000 for i * 10 seconds; returns the ids in play order. */
    private suspend fun played(count: Int): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(folderId, listOf(SystemEntity("snes", "Super Nintendo")), (1..count).map { game(folderId, "g%03d".format(it)) }),
        )
        val ids = db.scanDao().gamesInFolder(folderId).sortedBy { it.documentId }.map { it.id }
        ids.forEachIndexed { i, id -> play.addSession(id, endedAt = (i + 1) * 1_000L, seconds = (i + 1) * 10L) }
        return ids
    }

    private suspend fun recent(limit: Int = 20): List<Long> = db.gameDao().observeRecent(limit).first().map { it.id }

    /** Everything Statistics and the game's facts read, to compare before and after. */
    private suspend fun playFacts(ids: List<Long>) =
        listOf(
            ids.map { play.play(it)?.let { p -> p.lastPlayedAt to p.totalSeconds } },
            ids.map { play.sessions(it) },
            ids.map { play.sessionCount(listOf(it)) },
            play.totalSeconds(),
            play.secondsPlayedBetween(0, Long.MAX_VALUE),
            play.playedCopies(),
        )

    @Test
    fun aRemovedGameLeavesRecentAndComesBackWhenPlayedAgain() =
        runTest {
            val (a, b, c) = played(3)
            assertEquals(listOf(c, b, a), recent())

            assertEquals(1, play.removeFromRecent(b))
            assertEquals(listOf(c, a), recent())

            play.addSession(b, endedAt = 9_000, seconds = 5)
            assertEquals("played again: back on top", listOf(b, c, a), recent())
        }

    @Test
    fun aRemovedGameComesBackEvenWhenTheClockWasSetBack() =
        runTest {
            val (a, b) = played(2)
            play.removeFromRecent(b)
            play.addSession(b, endedAt = 500, seconds = 5)
            assertEquals(listOf(a, b), recent())
        }

    @Test
    fun clearRecentEmptiesItAndEachGameComesBackOnlyWhenPlayed() =
        runTest {
            val (a, b, c) = played(3)
            assertEquals(3, play.clearRecent())
            assertEquals(emptyList<Long>(), recent())
            assertEquals("a second clear changes nothing", 0, play.clearRecent())

            play.addSession(a, endedAt = 10_000, seconds = 5)
            assertEquals(listOf(a), recent())
            play.addSession(c, endedAt = 11_000, seconds = 5)
            assertEquals(listOf(c, a), recent())
            assertTrue(b !in recent())
        }

    @Test
    fun removeAndClearLeavePlayTimeSessionsAndStatisticsUntouched() =
        runTest {
            val ids = played(3)
            val before = playFacts(ids)
            play.removeFromRecent(ids[1])
            assertEquals(before, playFacts(ids))
            play.clearRecent()
            assertEquals(before, playFacts(ids))
        }

    @Test
    fun removingANeverPlayedGameChangesNothing() =
        runTest {
            val (a) = played(1)
            assertEquals(0, play.removeFromRecent(a + 100))
            assertEquals(listOf(a), recent())
        }

    @Test
    fun aTakenBackPlayPutsARemovedGameOutOfRecentAgain() =
        runTest {
            val (a, b) = played(2)
            play.removeFromRecent(a)
            val added = play.recordSession(a, endedAt = 9_000, seconds = 5)!!
            assertEquals(listOf(a, b), recent())
            assertTrue(play.takeBack(added))
            assertEquals("as before the play: still removed", listOf(b), recent())
        }

    @Test
    fun clearRecentWhileAPlayIsHeldThenTakingThePlayBackKeepsTheGameOut() =
        runTest {
            val (a, b) = played(2)
            val added = play.recordSession(a, endedAt = 9_000, seconds = 5)!!
            play.clearRecent()
            assertTrue(play.takeBack(added))
            assertEquals("the clear made after the play stays", emptyList<Long>(), recent())
            play.addSession(b, endedAt = 10_000, seconds = 5)
            assertEquals(listOf(b), recent())
        }

    /**
     * Old rows, a scan batch of new rows for the same files, and the moves that fold each new row into
     * its old one (`ScanDao.mergePersonData`). Row i is played (and maybe removed from Recent) on
     * each side as the lists say. Returns the kept (old) ids.
     */
    private suspend fun movedOnto(playOld: List<Boolean>, removeOld: List<Boolean>, playNew: List<Boolean>, removeNew: List<Boolean>): List<Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "m", displayName = "Moved", addedAt = 0))
        val systems = listOf(SystemEntity("snes", "Super Nintendo"))
        db.scanDao().applyBatch(ScanBatch(folderId, systems, removeOld.indices.map { game(folderId, "old$it", relativePath = "A/$it.sfc") }))
        val old = db.scanDao().gamesInFolder(folderId).sortedBy { it.documentId }
        old.forEachIndexed { i, row ->
            if (playOld[i]) play.addSession(row.id, endedAt = 1_000L + i, seconds = 10)
            if (removeOld[i]) play.removeFromRecent(row.id)
        }
        val inserted = db.scanDao().applyBatch(ScanBatch(folderId, systems, removeOld.indices.map { game(folderId, "new$it", relativePath = "B/$it.sfc") }))
        val moves = removeOld.indices.map { i ->
            val new = inserted.single { it.fields.documentId == "new$i" }
            if (playNew[i]) play.addSession(new.id, endedAt = 2_000L + i, seconds = 10)
            if (removeNew[i]) play.removeFromRecent(new.id)
            GameMove(old[i].id, new)
        }
        db.scanDao().finishScan(ScanFinish(folderId, moves = moves))
        return old.map { it.id }
    }

    @Test
    fun aMergedGameStaysOutOfRecentOnlyWhenBothRowsWereOut() =
        runTest {
            val (both, one) = movedOnto(
                playOld = listOf(true, true), removeOld = listOf(true, true),
                playNew = listOf(true, true), removeNew = listOf(true, false),
            )
            assertEquals("both removed: out; only the old one removed: shown", listOf(one), recent())
            assertEquals("the play time still adds up", 20L, play.play(both)?.totalSeconds)
        }

    @Test
    fun aPlayRowMovedToAKeptGameWithoutOneKeepsItsMark() =
        runTest {
            val (removed, shown) = movedOnto(
                playOld = listOf(false, false), removeOld = listOf(false, false),
                playNew = listOf(true, true), removeNew = listOf(true, false),
            )
            assertEquals("the moved play row keeps its mark on the kept id", listOf(shown), recent())
            assertEquals(10L, play.play(removed)?.totalSeconds)
        }

    @Test
    fun theLimitIsHonouredAtTenAndFifty() =
        runTest {
            val ids = played(60)
            assertEquals(ids.reversed().take(10), recent(10))
            assertEquals(ids.reversed().take(20), recent(20))
            assertEquals(ids.reversed().take(50), recent(50))
        }

    @Test
    fun aRemovedGameDoesNotTakeAPlaceOfTheLimit() =
        runTest {
            val ids = played(12)
            play.removeFromRecent(ids.last())
            assertEquals(ids.reversed().drop(1).take(10), recent(10))
        }
}
