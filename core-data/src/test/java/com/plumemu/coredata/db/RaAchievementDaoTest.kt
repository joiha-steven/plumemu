package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 17's RetroAchievements reads and writes: a game's stored achievement list
 * ([RaDao.saveGameProgress], [RaDao.observeRecentAchievements]), the points the completion refresh
 * keeps ([RaDao.replaceProgress]), sign-out ([RaDao.clearProgress]) and the totals per system
 * ([RaDao.observeSystemTotals]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RaAchievementDaoTest {
    private lateinit var db: PlumDatabase
    private lateinit var dao: RaDao
    private lateinit var ids: Map<String, Long>

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.raDao()
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/1", displayName = "ROMs", addedAt = 1))
        val games = listOf(
            game(folder, "zork.sfc"),
            game(folder, "mario.sfc"),
            game(folder, "mario (copy).sfc", isPrimaryCopy = false),
            game(folder, "hidden.sfc"),
            game(folder, "metroid.gba", systemId = "gba"),
        )
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "Super Nintendo"), SystemEntity("gba", "GBA")), games))
        ids = db.scanDao().gamesInFolder(folder).associate { it.documentId to it.id }
    }

    @After
    fun tearDown() = db.close()

    private fun id(document: String) = ids.getValue(document)

    private suspend fun match(document: String, raGameId: Long) {
        dao.saveMatch(RaMatchEntity(id(document), RaMatchEntity.MATCHED, raGameId, "0123456789abcdef0123456789abcdef", null, document, 1, 1, 5))
    }

    private fun progress(raGameId: Long, total: Int, soft: Int, award: String? = null, earned: Int? = null, points: Int? = null) =
        RaProgressEntity(raGameId, total, soft, 0, award, 7, pointsEarned = earned, pointsTotal = points)

    private fun achievement(raGameId: Long, id: Long, earned: Long? = null, hardcore: Long? = null, points: Int = 5) =
        RaAchievementEntity(raGameId, id, "A$id", "b$id", points, earned, hardcore)

    private suspend fun recent(raGameId: Long, limit: Int = 5) = dao.observeRecentAchievements(raGameId, limit).first().map { it.achievementId }

    private suspend fun totals(systemId: String) = dao.observeSystemTotals(systemId).first()

    @Test
    fun noStoredListShowsNoRecentAchievement() = runTest {
        assertEquals(emptyList<Long>(), recent(11))
    }

    @Test
    fun recentAchievementsAreTheUnlockedOnesOfThatGameLatestFirst() = runTest {
        dao.saveGameProgress(
            progress(11, 6, 4),
            listOf(
                achievement(11, 1, earned = 100),
                achievement(11, 2, earned = 300, hardcore = 300),
                achievement(11, 3),
                // A hardcore time later than the softcore one: the later one places it.
                achievement(11, 4, earned = 200, hardcore = 400),
                achievement(11, 5, hardcore = 250),
                achievement(11, 6, earned = 100),
            ),
        )
        dao.saveGameProgress(progress(12, 1, 1), listOf(achievement(12, 9, earned = 999)))

        assertEquals(listOf(4L, 2L, 5L, 1L, 6L), recent(11))
        assertEquals("the limit keeps the latest", listOf(4L, 2L), recent(11, limit = 2))
        assertEquals(listOf(9L), recent(12))
    }

    @Test
    fun savingAGamesListReplacesItAndANullListKeepsIt() = runTest {
        dao.saveGameProgress(progress(11, 2, 1), listOf(achievement(11, 1, earned = 100), achievement(11, 2)))
        dao.saveGameProgress(progress(11, 2, 2), listOf(achievement(11, 2, earned = 200), achievement(12, 7, earned = 1)))
        assertEquals("the old row is gone; another game's row is ignored", listOf(2L), recent(11))
        assertEquals(emptyList<Long>(), recent(12))

        dao.saveGameProgress(progress(11, 2, 2, earned = 10, points = 10), null)
        assertEquals(listOf(2L), recent(11))
        match("zork.sfc", 11)
        assertEquals(10 to 10, dao.observeProgress(id("zork.sfc")).first()?.let { it.pointsEarned to it.pointsTotal })
    }

    @Test
    fun signOutDeletesTheStoredAchievementsWithTheProgress() = runTest {
        dao.saveGameProgress(progress(11, 1, 1), listOf(achievement(11, 1, earned = 100)))
        dao.clearProgress()
        assertEquals(emptyList<Long>(), recent(11))
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ra_achievement").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
    }

    @Test
    fun theCompletionRefreshKeepsKnownPointsOnlyWhileTheCountsHold() = runTest {
        match("zork.sfc", 11)
        match("mario.sfc", 12)
        match("metroid.gba", 13)
        dao.saveGameProgress(progress(11, 10, 4, earned = 40, points = 100), listOf(achievement(11, 1, earned = 1)))
        dao.saveGameProgress(progress(12, 10, 4, earned = 40, points = 100), null)
        dao.saveGameProgress(progress(13, 10, 4, earned = 40, points = 100), null)
        dao.upsertProgress(listOf(progress(14, 3, 1, earned = 5, points = 15)))

        dao.replaceProgress(listOf(progress(11, 10, 4), progress(12, 10, 5), progress(13, 12, 4)))

        val stored = listOf("zork.sfc", "mario.sfc", "metroid.gba").map { dao.observeProgress(id(it)).first() }
        assertEquals("same counts keep both", 40 to 100, stored[0]?.let { it.pointsEarned to it.pointsTotal })
        assertEquals("more unlocked: the earned points are not known", null to 100, stored[1]?.let { it.pointsEarned to it.pointsTotal })
        assertEquals("a bigger set: neither is known", null to null, stored[2]?.let { it.pointsEarned to it.pointsTotal })
        assertEquals(listOf(1L), recent(11))

        dao.replaceProgress(listOf(progress(12, 10, 5)))
        assertEquals("a game that left the progress takes its achievements with it", emptyList<Long>(), recent(11))
    }

    @Test
    fun systemTotalsAreZeroWithNothingStored() = runTest {
        assertEquals(RaSystemTotals(0, 0, 0, 0), totals("snes"))
        match("zork.sfc", 11)
        assertEquals("a match without progress", RaSystemTotals(0, 0, 0, 0), totals("snes"))
    }

    @Test
    fun systemTotalsCountEachGameOnceOnItsSystemAndSumOnlyKnownPoints() = runTest {
        match("zork.sfc", 11)
        match("mario.sfc", 12)
        match("mario (copy).sfc", 12)
        match("hidden.sfc", 15)
        match("metroid.gba", 13)
        db.gameDao().setHidden(id("hidden.sfc"), true)
        dao.upsertProgress(
            listOf(
                progress(11, 10, 10, award = RaProgressEntity.MASTERED, earned = 100, points = 100),
                // Two library copies, one game: counted once.
                progress(12, 10, 10, award = RaProgressEntity.MASTERED, earned = null, points = null),
                progress(15, 5, 5, award = RaProgressEntity.MASTERED, earned = 50, points = 50),
                progress(13, 8, 2, award = "beaten-softcore", earned = 20, points = 80),
                // Progress of a game no library game is matched to.
                progress(99, 1, 1, award = RaProgressEntity.MASTERED, earned = 1, points = 1),
            ),
        )

        assertEquals(RaSystemTotals(masteredGames = 2, gamesWithProgress = 2, gamesWithPoints = 1, pointsEarned = 100), totals("snes"))
        assertEquals(RaSystemTotals(masteredGames = 0, gamesWithProgress = 1, gamesWithPoints = 1, pointsEarned = 20), totals("gba"))
        assertEquals(RaSystemTotals(0, 0, 0, 0), totals("psx"))
    }
}
