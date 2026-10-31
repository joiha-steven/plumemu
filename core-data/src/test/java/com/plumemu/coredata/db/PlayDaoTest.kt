package com.plumemu.coredata.db

import android.database.SQLException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Play history and the second screen's "last played" reads, on an in-memory database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayDaoTest {
    private lateinit var db: PlumDatabase
    private val dao get() = db.playDao()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    /** Inserts [games] in one folder and returns their ids by document id. */
    private suspend fun insert(vararg games: GameEntity): Map<String, Long> {
        val folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
        db.scanDao().applyBatch(
            ScanBatch(
                folderId,
                listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("md", "Mega Drive")),
                games.map { it.copy(folderId = folderId) },
            ),
        )
        return db.scanDao().gamesInFolder(folderId).associate { it.documentId to it.id }
    }

    @Test
    fun aSessionIsAddedToTheTotalAndMovesTheLastPlay() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            assertNull("never played", dao.play(id))
            dao.addSession(id, endedAt = 1_000, seconds = 60)
            dao.addSession(id, endedAt = 5_000, seconds = 30)
            val play = dao.play(id)!!
            assertEquals(5_000L, play.lastPlayedAt)
            assertEquals(90L, play.totalSeconds)
        }

    @Test
    fun aSessionTakenBackLeavesTheTotalTheLastPlayAndTheSessionsAsBefore() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            dao.addSession(id, endedAt = 1_000, seconds = 60)
            val added = dao.recordSession(id, endedAt = 5_000, seconds = 30)!!
            assertEquals(AddedSession(added.sessionId, id, 5_000, 30, previousLastPlayedAt = 1_000), added)
            assertTrue(dao.takeBack(added))
            assertEquals(PlayEntity(id, lastPlayedAt = 1_000, totalSeconds = 60), dao.play(id))
            assertEquals(listOf(1_000L), dao.sessions(id).map { it.endedAt })
            assertFalse("a second answer changes nothing", dao.takeBack(added))
            assertEquals(PlayEntity(id, lastPlayedAt = 1_000, totalSeconds = 60), dao.play(id))
        }

    @Test
    fun aFirstPlayTakenBackLeavesTheGameNeverPlayedSoItLeavesRecent() =
        runTest {
            val ids = insert(game(0, "metroid"), game(0, "zelda"))
            val zelda = ids.getValue("zelda")
            dao.addSession(zelda, endedAt = 1_000, seconds = 60)
            val added = dao.recordSession(ids.getValue("metroid"), endedAt = 5_000, seconds = 30)!!
            assertNull(added.previousLastPlayedAt)
            assertTrue(dao.takeBack(added))
            assertNull(dao.play(ids.getValue("metroid")))
            assertEquals("another game's play is untouched", PlayEntity(zelda, 1_000, 60), dao.play(zelda))
            assertEquals(listOf(zelda), db.gameDao().observeRecent(10).first().map { it.id })
        }

    @Test
    fun aTakeBackAfterANewerPlayKeepsTheNewerLastPlay() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            val added = dao.recordSession(id, endedAt = 5_000, seconds = 30)!!
            dao.addSession(id, endedAt = 9_000, seconds = 40)
            assertTrue(dao.takeBack(added))
            assertEquals(PlayEntity(id, lastPlayedAt = 9_000, totalSeconds = 40), dao.play(id))
        }

    @Test
    fun theNewestPlayInASystemIsItsOwnAndSkipsWhatItsListDoesNotShow() =
        runTest {
            val ids =
                insert(
                    game(0, "metroid", title = "Super Metroid"),
                    game(0, "zelda", title = "Zelda"),
                    game(0, "hidden", title = "Hidden"),
                    game(0, "bios", title = "BIOS").copy(isNotGame = true),
                    game(0, "copy", title = "Second copy", isPrimaryCopy = false),
                    game(0, "sonic", systemId = "md", title = "Sonic"),
                )
            db.gameDao().setHidden(ids.getValue("hidden"), true)
            dao.addSession(ids.getValue("zelda"), endedAt = 1_000, seconds = 60)
            dao.addSession(ids.getValue("metroid"), endedAt = 2_000, seconds = 60)
            // Newer, but not in the Super Nintendo list: another system, hidden, not a game, a second copy.
            for (key in listOf("sonic", "hidden", "bios", "copy")) dao.addSession(ids.getValue(key), endedAt = 9_000, seconds = 1)
            assertEquals(LastPlay("Super Metroid", 2_000), dao.newestPlayInSystem("snes"))
            assertEquals(LastPlay("Sonic", 9_000), dao.newestPlayInSystem("md"))
        }

    @Test
    fun aSystemWithNoPlayHasNoNewestPlay() =
        runTest {
            insert(game(0, "metroid"))
            assertNull(dao.newestPlayInSystem("snes"))
            assertNull("no such system", dao.newestPlayInSystem("psx"))
        }

    @Test
    fun theNewestFavouritePlayIsAmongVisibleFavouritesOnly() =
        runTest {
            val ids =
                insert(
                    game(0, "metroid", title = "Super Metroid"),
                    game(0, "zelda", title = "Zelda"),
                    game(0, "hidden", title = "Hidden"),
                    game(0, "sonic", systemId = "md", title = "Sonic"),
                )
            assertNull("no favourites", dao.newestFavoritePlay())
            for (key in listOf("metroid", "sonic", "hidden")) db.gameDao().setFavorite(ids.getValue(key), true)
            db.gameDao().setHidden(ids.getValue("hidden"), true)
            assertNull("favourites never played", dao.newestFavoritePlay())
            dao.addSession(ids.getValue("metroid"), endedAt = 1_000, seconds = 60)
            dao.addSession(ids.getValue("sonic"), endedAt = 3_000, seconds = 60)
            // Newer, but not a favourite, or hidden.
            dao.addSession(ids.getValue("zelda"), endedAt = 9_000, seconds = 60)
            dao.addSession(ids.getValue("hidden"), endedAt = 9_000, seconds = 60)
            assertEquals(LastPlay("Sonic", 3_000), dao.newestFavoritePlay())
        }

    // Sessions. Times are wall-clock milliseconds near a real date, so the 400-day cut
    // is exercised with real-sized numbers.

    private fun sessionCount(): Int =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM play_session").use { c ->
            c.moveToFirst()
            c.getInt(0)
        }

    private fun refuseEverySession() =
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER refuse_sessions BEFORE INSERT ON play_session BEGIN SELECT RAISE(ABORT, 'refused'); END",
        )

    @Test
    fun aSessionWritesOneRowWithTheGamesSystemAndTheSameSecondsAsTheTotal() =
        runTest {
            val id = insert(game(0, "sonic", systemId = "md")).getValue("sonic")
            assertFalse(dao.hasAnySession())
            dao.addSession(id, endedAt = NOW, seconds = 600)
            val session = dao.sessions(id).single()
            assertEquals(PlaySessionEntity(session.id, id, "md", startedAt = NOW - 600_000, endedAt = NOW, seconds = 600), session)
            assertEquals(600L, dao.play(id)!!.totalSeconds)
            assertTrue(dao.hasAnySession())
        }

    @Test
    fun theMeasuredStartIsStoredWhenGiven() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            dao.addSession(id, endedAt = NOW, seconds = 59, startedAt = NOW - 59_400)
            assertEquals(NOW - 59_400, dao.sessions(id).single().startedAt)
        }

    @Test
    fun theSumOfNewSessionsIsTheGrowthOfTheTotal() =
        runTest {
            val ids = insert(game(0, "metroid"), game(0, "sonic", systemId = "md"))
            val metroid = ids.getValue("metroid")
            val sonic = ids.getValue("sonic")
            // A total from before schema 5: no session stands for it.
            db.openHelper.writableDatabase.execSQL("INSERT INTO play (game_id, last_played_at, total_seconds) VALUES ($metroid, 1, 7200)")
            val before = dao.totalSeconds()
            val added = listOf(metroid to 61L, sonic to 3_600L, metroid to 0L, metroid to 125L)
            added.forEachIndexed { i, (id, seconds) -> dao.addSession(id, endedAt = NOW + i * HOUR, seconds = seconds) }
            assertEquals(added.sumOf { it.second }, dao.totalSeconds() - before)
            assertEquals("a 0-second session is a row too", added.size, sessionCount())
            assertEquals(added.sumOf { it.second }, dao.secondsPlayedBetween(0, Long.MAX_VALUE))
            assertEquals(7_200L + 61 + 0 + 125, dao.play(metroid)!!.totalSeconds)
        }

    @Test
    fun aFailureWhileWritingTheSessionLeavesTheTotalAsItWas() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            dao.addSession(id, endedAt = NOW, seconds = 60)
            refuseEverySession()
            try {
                dao.addSession(id, endedAt = NOW + HOUR, seconds = 30)
                fail("the trigger should have refused the session")
            } catch (expected: SQLException) {
                // The whole transaction rolls back, the total's update with it.
            }
            assertEquals(PlayEntity(id, lastPlayedAt = NOW, totalSeconds = 60), dao.play(id))
            assertEquals(1, sessionCount())
        }

    @Test
    fun aFailureOnTheFirstSessionLeavesNoPlayRow() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            refuseEverySession()
            try {
                dao.addSession(id, endedAt = NOW, seconds = 30)
                fail("the trigger should have refused the session")
            } catch (expected: SQLException) {
                // The play row inserted first is rolled back too.
            }
            assertNull(dao.play(id))
            assertEquals(0, sessionCount())
        }

    @Test
    fun secondsBetweenCountOnlyThePartOfASessionInsideTheRange() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            // 1,000 seconds from START to START + 1,000,000 ms.
            dao.addSession(id, endedAt = START + 1_000_000, seconds = 1_000)
            assertEquals("inside", 1_000L, dao.secondsPlayedBetween(START - HOUR, START + 2 * HOUR))
            assertEquals("the range exactly", 1_000L, dao.secondsPlayedBetween(START, START + 1_000_000))
            assertEquals("before", 0L, dao.secondsPlayedBetween(START - 2 * HOUR, START - HOUR))
            assertEquals("after", 0L, dao.secondsPlayedBetween(START + 2 * HOUR, START + 3 * HOUR))
            assertEquals("ends where the range starts", 0L, dao.secondsPlayedBetween(START + 1_000_000, START + HOUR))
            assertEquals("starts where the range ends", 0L, dao.secondsPlayedBetween(START - HOUR, START))
            assertEquals("crosses the start", 400L, dao.secondsPlayedBetween(START + 600_000, START + HOUR))
            assertEquals("crosses the end", 250L, dao.secondsPlayedBetween(START - HOUR, START + 250_000))
            assertEquals("covers the range", 100L, dao.secondsPlayedBetween(START + 300_000, START + 400_000))
            assertEquals("empty range", 0L, dao.secondsPlayedBetween(START + 500_000, START + 500_000))
        }

    @Test
    fun aSessionWithNoLengthCountsWholeWhenItsEndIsInside() =
        runTest {
            val id = insert(game(0, "metroid")).getValue("metroid")
            // A start not before the end (a clock oddity): its seconds go where it ended.
            dao.addSession(id, endedAt = START, seconds = 30, startedAt = START)
            dao.addSession(id, endedAt = START + 10, seconds = 20, startedAt = START + 50)
            assertEquals(50L, dao.secondsPlayedBetween(START, START + 11))
            assertEquals(30L, dao.secondsPlayedBetween(START, START + 10))
            assertEquals(0L, dao.secondsPlayedBetween(START + 11, START + HOUR))
            assertEquals(0L, dao.secondsPlayedBetween(START - HOUR, START))
        }

    @Test
    fun sessionsOlderThan400DaysAreDeletedAndTotalsStay() =
        runTest {
            val ids = insert(game(0, "metroid"), game(0, "sonic", systemId = "md"))
            val metroid = ids.getValue("metroid")
            val sonic = ids.getValue("sonic")
            val retention = PlayDao.SESSION_RETENTION_MS
            assertEquals(400L * 24 * 60 * 60 * 1_000, retention)
            dao.addSession(metroid, endedAt = START, seconds = 100)
            dao.addSession(sonic, endedAt = START + 1, seconds = 200)
            dao.addSession(metroid, endedAt = START + 2, seconds = 300)
            // Exactly 400 days after the second: it stays (and the third); the first, a millisecond older, goes.
            dao.addSession(sonic, endedAt = START + 1 + retention, seconds = 10)
            assertEquals(listOf(START + 2), dao.sessions(metroid).map { it.endedAt })
            assertEquals(listOf(START + 1, START + 1 + retention), dao.sessions(sonic).map { it.endedAt })
            dao.addSession(sonic, endedAt = START + 3 + retention, seconds = 10)
            assertEquals(emptyList<PlaySessionEntity>(), dao.sessions(metroid))
            assertEquals(listOf(START + 1 + retention, START + 3 + retention), dao.sessions(sonic).map { it.endedAt })
            assertEquals("totals are never cut", 400L, dao.play(metroid)!!.totalSeconds)
            assertEquals(220L, dao.play(sonic)!!.totalSeconds)
            assertEquals(620L, dao.totalSeconds())
        }

    @Test
    fun aGameLeavingTheLibraryTakesItsSessionsOnly() =
        runTest {
            val ids = insert(game(0, "metroid"), game(0, "sonic", systemId = "md"))
            dao.addSession(ids.getValue("metroid"), endedAt = NOW, seconds = 60)
            dao.addSession(ids.getValue("sonic"), endedAt = NOW, seconds = 30)
            val folderId = db.scanDao().folders().single().id
            db.scanDao().finishScan(ScanFinish(folderId, removedGameIds = listOf(ids.getValue("metroid"))))
            assertEquals(emptyList<PlaySessionEntity>(), dao.sessions(ids.getValue("metroid")))
            assertEquals(1, sessionCount())
            assertEquals(30L, dao.secondsPlayedBetween(0, Long.MAX_VALUE))
        }

    private companion object {
        /** A fixed noon UTC, so the tests do not depend on the clock. */
        const val NOW = 1_792_756_800_000L
        const val START = NOW - 30L * 24 * 60 * 60 * 1_000
        const val HOUR = 60L * 60 * 1_000
    }
}
