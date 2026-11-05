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

/** The read-only figures of the second screen's system and Recent cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemCardQueriesTest {
    private lateinit var db: PlumDatabase
    private var folderId = 0L
    private val games get() = db.gameDao()
    private val plays get() = db.playDao()

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        folderId = db.scanDao().insertFolder(RomFolderEntity(treeUri = "t", displayName = "ROMs", addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun library(vararg games: GameEntity) {
        db.scanDao().applyBatch(ScanBatch(folderId, listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("nes", "NES")), games.toList()))
    }

    private suspend fun idOf(documentId: String) = db.scanDao().gamesInFolder(folderId).first { it.documentId == documentId }.id

    private suspend fun systemPlays(system: String) = games.observeSystemPlays(system).first().associate { it.gameId to it.lastPlayedAt }

    private var leftOutCount = 0

    private suspend fun leftOut(system: String?, path: String?, more: Int = 0) {
        val directory = "d${leftOutCount++}"
        db.scanDao().finishScan(
            ScanFinish(
                folderId = folderId,
                leftOut = LeftOutWrite(false, listOf(directory), listOf(LeftOutFileEntity(folderId = folderId, directoryPath = directory, relativePath = path, reason = "junk", systemId = system, more = more))),
            ),
        )
    }

    @Test
    fun anEmptyLibraryAnswersZeroAndNothing() = runTest {
        assertTrue(systemPlays("snes").isEmpty())
        assertTrue(games.observeRecentPlayed(5).first().isEmpty())
        assertEquals(0L, plays.totalSecondsInSystem("snes"))
        assertTrue(plays.gameIdsPlayedBetween(0, Long.MAX_VALUE).isEmpty())
        assertEquals(0, db.leftOutFileDao().countInSystem("snes"))
    }

    @Test
    fun withNoPlaysThereAreNoLastPlayedRowsAndNoTimeButTheGamesAreThere() = runTest {
        library(game(folderId, "a"), game(folderId, "b"))
        assertTrue(systemPlays("snes").isEmpty())
        assertEquals(0L, plays.totalSecondsInSystem("snes"))
        assertEquals(2, games.observeSystemsWithGames().first().single().gameCount)
    }

    @Test
    fun systemPlaysHoldEveryPlayedRowOfTheSystemWhateverItsCopyOrVisibilityAndRowsOfGivesTheCoverFields() = runTest {
        library(game(folderId, "a", title = "Actraiser"), game(folderId, "e", title = "Actraiser", isPrimaryCopy = false), game(folderId, "h", title = "Hidden"), game(folderId, "n", systemId = "nes", title = "Metroid"), game(folderId, "u", title = "Unplayed"))
        plays.addSession(idOf("a"), endedAt = 100, seconds = 10)
        plays.addSession(idOf("e"), endedAt = 300, seconds = 10)
        plays.addSession(idOf("h"), endedAt = 200, seconds = 10)
        plays.addSession(idOf("n"), endedAt = 900, seconds = 10)
        assertEquals(mapOf(idOf("a") to 100L, idOf("e") to 300L, idOf("h") to 200L), systemPlays("snes"))
        val rows = games.rowsOf(listOf(idOf("a"), idOf("n")))
        assertEquals(setOf("Actraiser", "Metroid"), rows.map { it.title }.toSet())
        assertEquals(games.observeSystemGames("snes").first().first { it.title == "Actraiser" }.scrapeKey, rows.first { it.title == "Actraiser" }.scrapeKey)
    }

    @Test
    fun recentPlayedMatchesRecentWithTheLastPlay() = runTest {
        library(game(folderId, "a"), game(folderId, "b"), game(folderId, "n", systemId = "nes"))
        plays.addSession(idOf("a"), endedAt = 100, seconds = 5)
        plays.addSession(idOf("n"), endedAt = 300, seconds = 5)
        plays.addSession(idOf("b"), endedAt = 200, seconds = 5)
        val recent = games.observeRecentPlayed(2).first()
        assertEquals(games.observeRecent(2).first().map { it.id }, recent.map { it.id })
        assertEquals(listOf(300L, 200L), recent.map { it.lastPlayedAt })
    }

    @Test
    fun playTimeOfASystemSumsItsGamesAndCountsHiddenOnesLikeTheTotal() = runTest {
        library(game(folderId, "a"), game(folderId, "b"), game(folderId, "n", systemId = "nes"))
        plays.addSession(idOf("a"), endedAt = 100, seconds = 30)
        plays.addSession(idOf("a"), endedAt = 200, seconds = 40)
        plays.addSession(idOf("b"), endedAt = 300, seconds = 50)
        plays.addSession(idOf("n"), endedAt = 400, seconds = 1_000)
        games.setHidden(idOf("b"), true)
        assertEquals(120L, plays.totalSecondsInSystem("snes"))
        assertEquals(1_000L, plays.totalSecondsInSystem("nes"))
        assertEquals(1_120L, plays.totalSeconds())
    }

    @Test
    fun leftOutFilesOfASystemCountFileRowsAndTheMoreRowsAndSkipSystemlessOnes() = runTest {
        library(game(folderId, "a"))
        leftOut("snes", "snes/a.txt")
        leftOut("snes", "snes/b.txt")
        leftOut("snes", null, more = 7)
        leftOut("nes", "nes/c.txt")
        leftOut(null, "x.txt")
        assertEquals(9, db.leftOutFileDao().countInSystem("snes"))
        assertEquals(1, db.leftOutFileDao().countInSystem("nes"))
        assertEquals("a system with none", 0, db.leftOutFileDao().countInSystem("psx"))
    }

    @Test
    fun gameIdsPlayedInARangeFollowTheOverlapRuleOfSecondsPlayedBetween() = runTest {
        library(game(folderId, "a"), game(folderId, "b"), game(folderId, "c"))
        val day = 86_400_000L
        plays.addSession(idOf("a"), endedAt = 10 * day, seconds = 60)
        plays.addSession(idOf("a"), endedAt = 11 * day, seconds = 60)
        plays.addSession(idOf("b"), endedAt = 12 * day, seconds = 60)
        plays.addSession(idOf("c"), endedAt = 3 * day, seconds = 60)
        suspend fun between(from: Long, to: Long) = plays.gameIdsPlayedBetween(from, to).toSet()
        assertEquals("a twice is one id", setOf(idOf("a"), idOf("b")), between(10 * day - 1_000, 13 * day))
        assertEquals(3, between(0, Long.MAX_VALUE).size)
        assertEquals("a session ending at `to` is outside", setOf(idOf("a")), between(10 * day - 1_000, 11 * day - 60_000))
        assertEquals("a session ending exactly at `from` has no overlap", emptySet<Long>(), between(10 * day, 10 * day + day / 2))
        assertEquals(emptySet<Long>(), between(20 * day, 30 * day))
    }
}
