package com.plumemu.coredata.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RaDaoTest {
    private lateinit var db: PlumDatabase
    private lateinit var dao: RaDao
    private var zork = 0L
    private var mario = 0L
    private var marioCopy = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        dao = db.raDao()
        val folder = db.scanDao().insertFolder(RomFolderEntity(treeUri = "content://tree/1", displayName = "ROMs", addedAt = 1))
        val games = listOf(game(folder, "zork.sfc"), game(folder, "mario.sfc"), game(folder, "mario (copy).sfc", isPrimaryCopy = false))
        db.scanDao().applyBatch(ScanBatch(folder, listOf(SystemEntity("snes", "Super Nintendo")), games))
        val ids = db.scanDao().gamesInFolder(folder).associate { it.documentId to it.id }
        zork = ids.getValue("zork.sfc")
        mario = ids.getValue("mario.sfc")
        marioCopy = ids.getValue("mario (copy).sfc")
    }

    @After
    fun tearDown() = db.close()

    private fun matched(gameId: Long, raGameId: Long, key: String = "k$gameId", size: Long = 1024, time: Long = 99) =
        RaMatchEntity(gameId, RaMatchEntity.MATCHED, raGameId, "0123456789abcdef0123456789abcdef", null, key, size, time, 5)

    private fun progress(raGameId: Long, total: Int, soft: Int, hard: Int, award: String?) =
        RaProgressEntity(raGameId, total, soft, hard, award, 7)

    @Test
    fun aStoredMatchIsFreshOnlyForTheSameFileSizeAndTime() = runTest {
        assertTrue(dao.saveMatch(matched(zork, 11, key = "/roms/zork.sfc", size = 1024, time = 99)))
        assertEquals(11L, dao.freshMatch(zork, "/roms/zork.sfc", 1024, 99)?.raGameId)
        assertNull(dao.freshMatch(zork, "/roms/zork.sfc", 1025, 99))
        assertNull(dao.freshMatch(zork, "/roms/zork.sfc", 1024, 100))
        assertNull(dao.freshMatch(zork, "/roms/other.sfc", 1024, 99))
        assertNull(dao.freshMatch(mario, "/roms/zork.sfc", 1024, 99))
        // The stale row is still readable as the last answer.
        assertEquals(11L, dao.match(zork)?.raGameId)
    }

    @Test
    fun savingAgainReplacesTheGamesMatch() = runTest {
        dao.saveMatch(matched(zork, 11))
        val noSet = RaMatchEntity(zork, RaMatchEntity.NO_SET, null, "ffffffffffffffffffffffffffffffff", null, "k", 2048, 100, 6)
        dao.saveMatch(noSet)
        assertEquals(noSet, dao.match(zork))
    }

    @Test
    fun aMatchForAGameThatIsGoneIsNotWritten() = runTest {
        assertFalse(dao.saveMatch(matched(9999, 11)))
        assertNull(dao.match(9999))
    }

    @Test
    fun deletingTheGameDeletesItsMatchAndNoOtherRow() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.saveMatch(matched(mario, 12))
        dao.upsertProgress(listOf(progress(11, 10, 10, 10, "mastered")))
        db.openHelper.writableDatabase.execSQL("DELETE FROM game WHERE id = $zork")
        assertNull(dao.match(zork))
        assertEquals(12L, dao.match(mario)?.raGameId)
        // Progress belongs to the RetroAchievements game, not the library row.
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })
    }

    @Test
    fun matchedIdsListOnlyMatchedGamesAmongThoseAsked() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.saveMatch(RaMatchEntity(mario, RaMatchEntity.CANNOT_HASH, null, null, "NeedsDecoder", "k", 1, 1, 1))
        dao.saveMatch(matched(marioCopy, 12))
        assertEquals(mapOf(zork to 11L), dao.matchedRaGameIds(listOf(zork, mario, 4242)))
        assertEquals(mapOf(zork to 11L, marioCopy to 12L), dao.matchedRaGameIds(listOf(zork, mario, marioCopy)))
        assertEquals(emptyMap<Long, Long>(), dao.matchedRaGameIds(emptyList()))
    }

    @Test
    fun matchedIdsAcceptMoreIdsThanOneStatementTakes() = runTest {
        dao.saveMatch(matched(marioCopy, 12))
        val many = (100_000L until 102_500L).toList() + marioCopy
        assertEquals(mapOf(marioCopy to 12L), dao.matchedRaGameIds(many))
    }

    @Test
    fun matchedIdsFromSeveralChunksAreJoined() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.saveMatch(matched(mario, 12))
        dao.saveMatch(matched(marioCopy, 12))
        // Chunks of 3: [zork, 900, 901], [mario, 902, 903], [marioCopy] (a repeated id is asked once).
        val ids = listOf(zork, 900L, 901L, mario, 902L, 903L, marioCopy, zork)
        assertEquals(mapOf(zork to 11L, mario to 12L, marioCopy to 12L), dao.matchedRaGameIdsInChunks(ids, chunkSize = 3))
        assertEquals(mapOf(mario to 12L), dao.matchedRaGameIdsInChunks(listOf(900L, mario), chunkSize = 1))
    }

    @Test
    fun replaceProgressLeavesExactlyTheGivenRows() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.upsertProgress(listOf(progress(11, 10, 10, 10, "mastered"), progress(99, 1, 1, 0, null)))
        val fresh = listOf(progress(11, 10, 3, 0, null), progress(12, 4, 4, 4, "mastered"))
        dao.replaceProgress(fresh)
        val stored = db.openHelper.readableDatabase.query("SELECT ra_game_id FROM ra_progress ORDER BY 1").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(11L, 12L), stored)
        assertEquals(fresh[0], dao.observeProgress(zork).first())
        assertEquals(emptySet<Long>(), dao.masteredGameIds().first())
        dao.replaceProgress(emptyList())
        assertNull(dao.observeProgress(zork).first())
    }

    @Test
    fun masteredIsTheMasteredAwardForEveryMatchedCopy() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.saveMatch(matched(mario, 12))
        dao.saveMatch(matched(marioCopy, 12))
        dao.upsertProgress(
            listOf(
                progress(11, 10, 10, 10, "completed"),
                progress(12, 20, 20, 20, "mastered"),
                // Mastered, but no library game is matched to it.
                progress(13, 5, 5, 5, "mastered"),
            ),
        )
        assertEquals(setOf(mario, marioCopy), dao.masteredGameIds().first())
        // Every achievement unlocked in counts but no mastered award: not marked.
        dao.upsertProgress(listOf(progress(11, 10, 10, 10, "beaten-hardcore")))
        assertEquals(setOf(mario, marioCopy), dao.masteredGameIds().first())
        dao.upsertProgress(listOf(progress(11, 12, 12, 12, "mastered")))
        assertEquals(setOf(zork, mario, marioCopy), dao.masteredGameIds().first())
    }

    @Test
    fun aGameMatchedNoLongerIsNotMarkedMastered() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.upsertProgress(listOf(progress(11, 10, 10, 10, "mastered")))
        dao.saveMatch(RaMatchEntity(zork, RaMatchEntity.NO_SET, null, "ffffffffffffffffffffffffffffffff", null, "k", 1, 1, 1))
        assertEquals(emptySet<Long>(), dao.masteredGameIds().first())
    }

    @Test
    fun progressOfOneGameFollowsItsMatch() = runTest {
        assertNull(dao.observeProgress(zork).first())
        dao.saveMatch(matched(zork, 11))
        assertNull(dao.observeProgress(zork).first())
        val row = progress(11, 10, 4, 2, null)
        dao.upsertProgress(listOf(row, progress(12, 3, 3, 3, "mastered")))
        assertEquals(row, dao.observeProgress(zork).first())
        assertNull(dao.observeProgress(mario).first())
    }

    @Test
    fun clearProgressDeletesProgressAndKeepsMatches() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.upsertProgress(listOf(progress(11, 10, 10, 10, "mastered"), progress(12, 1, 0, 0, null)))
        assertEquals(setOf(zork), dao.masteredGameIds().first())
        dao.clearProgress()
        assertEquals(emptySet<Long>(), dao.masteredGameIds().first())
        assertNull(dao.observeProgress(zork).first())
        assertEquals(0, db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })
        assertEquals(11L, dao.match(zork)?.raGameId)
    }

    @Test
    fun unmatchedGamesLeaveOutMatchedOnesAndOtherSystems() = runTest {
        dao.saveMatch(matched(zork, 11))
        dao.saveMatch(matched(mario, 12).copy(status = RaMatchEntity.NO_SET, raGameId = null))
        val unmatched = dao.unmatchedGames(listOf("snes")).associateBy { it.gameId }
        assertEquals(setOf(mario, marioCopy), unmatched.keys)
        assertEquals(RaMatchEntity.NO_SET, unmatched.getValue(mario).matchStatus)
        assertEquals("0123456789abcdef0123456789abcdef", unmatched.getValue(mario).md5)
        assertNull(unmatched.getValue(marioCopy).matchStatus)
        assertTrue(dao.unmatchedGames(listOf("nes")).isEmpty())
        assertEquals(listOf(11L), dao.matchedRaGameIdList())
    }
}
