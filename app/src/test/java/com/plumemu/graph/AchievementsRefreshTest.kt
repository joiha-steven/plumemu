package com.plumemu.graph

import com.plumemu.coredata.db.RaMatchEntity
import com.plumemu.coredata.db.RaProgressEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AchievementsHarness.Companion.COMPLETION
import com.plumemu.graph.AchievementsHarness.Companion.DAY
import com.plumemu.graph.AchievementsHarness.Companion.GAME_LIST
import com.plumemu.graph.AchievementsHarness.Companion.START
import com.plumemu.graph.AchievementsHarness.Companion.completion
import com.plumemu.graph.AchievementsHarness.Companion.completionRow
import com.plumemu.graph.AchievementsHarness.Companion.gameList
import com.plumemu.graph.AchievementsHarness.Companion.ok
import com.plumemu.graph.AchievementsHarness.Companion.status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The daily completion refresh: its gates, its marks, and placing mastered games by title. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AchievementsRefreshTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    private fun completionAnswers() {
        h.answers[COMPLETION] = { ok(completion(completionRow(11, "Zork", "mastered"), completionRow(12, "Doom", null, awarded = 1))) }
    }

    @Test
    fun theRefreshRunsWhenEveryGatePassesAndReplacesTheProgress() = runBlocking {
        h.signIn()
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(99, 1, 1, 1, null, 1)))
        completionAnswers()
        assertTrue(h.achievements.refreshIfDue())
        assertEquals(START, h.marks.lastSuccess())
        val rows = h.db.openHelper.readableDatabase.query("SELECT ra_game_id FROM ra_progress ORDER BY ra_game_id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals("the whole table is the completion list", listOf(11L, 12L), rows)
    }

    @Test
    fun noGatePassedMeansNoRun() = runBlocking {
        completionAnswers()
        assertFalse("signed out", h.achievements.refreshIfDue())
        h.signIn()
        h.playing = true
        assertFalse("a game is being played", h.achievements.refreshIfDue())
        h.playing = false
        h.unmetered = false
        assertFalse("mobile data", h.achievements.refreshIfDue())
        h.unmetered = true
        h.marks.markSuccess(START - DAY + 1)
        assertFalse("ran less than a day ago", h.achievements.refreshIfDue())
        assertEquals(0, h.server.requestCount)

        h.now.set(START + 1)
        assertTrue("a day has passed", h.achievements.refreshIfDue())
        assertEquals(1, h.server.requestCount)
    }

    @Test
    fun theFrontRunsItOnceADay() = runBlocking {
        h.signIn()
        completionAnswers()
        h.achievements.onFront().join()
        h.achievements.onFront().join()
        assertEquals(1, h.server.requestCount)
        h.now.addAndGet(DAY)
        h.achievements.onFront().join()
        assertEquals(2, h.server.requestCount)
    }

    @Test
    fun aFailureIsLoggedKeepsTheRowsAndWaitsADay() = runBlocking {
        h.signIn()
        h.db.raDao().upsertProgress(listOf(RaProgressEntity(99, 1, 1, 1, null, 1)))
        h.answers[COMPLETION] = { status(404) }
        assertFalse(h.achievements.refreshIfDue())
        assertEquals(START, h.marks.lastFailure())
        assertEquals(1, h.db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })
        assertTrue(h.logLines.any { it.contains("completion failed") })
        assertFalse("not again the same day", h.achievements.refreshIfDue())
        assertEquals(1, h.server.requestCount)
    }

    @Test
    fun aRefusedKeyMarksTheAccountAndStopsTheRefresh() = runBlocking {
        h.signIn()
        h.answers[COMPLETION] = { status(401) }
        assertFalse(h.achievements.refreshIfDue())
        assertTrue(h.store.account.first()!!.isKeyRefused)
        h.now.addAndGet(2 * DAY)
        assertFalse(h.achievements.refreshIfDue())
        assertEquals(1, h.server.requestCount)
    }

    @Test
    fun aMasteredGameIsPlacedByHashingOnlyLibraryGamesOfTheSameTitle() = runBlocking {
        val zork = h.addGame("Zork (USA).sfc", "Zork")
        h.addGame("Mario (USA).sfc", "Mario")
        h.addGame("zork.nes", "Zork", systemId = "nes")
        h.signIn()
        completionAnswers()
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        assertTrue(h.achievements.refreshIfDue())
        assertEquals("only the same-titled game of the console's systems", listOf("Zork (USA).sfc"), h.hashed.toList())
        assertEquals(RaMatchEntity.MATCHED, h.db.raDao().match(zork)?.status)
        assertEquals(setOf(zork), h.db.raDao().masteredGameIds().first())

        // Placed now: the next refresh hashes nothing.
        h.now.addAndGet(DAY)
        assertTrue(h.achievements.refreshIfDue())
        assertEquals(1, h.hashed.size)
    }

    @Test
    fun signOutAndRestoreClearTheMarksSoTheNextFrontRefreshes() = runBlocking {
        h.signIn()
        completionAnswers()
        assertTrue(h.achievements.refreshIfDue())
        h.achievements.afterRestore()
        assertTrue(h.achievements.refreshIfDue())
        h.achievements.signOut()
        assertNull(h.store.account.first())
        assertEquals(0L, h.marks.lastSuccess())
        assertEquals(0, h.db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })
    }

    @Test
    fun aSignOutDuringARefreshLeavesNoMarkSoTheNextSignInRefreshesAtOnce() = runBlocking {
        h.signIn()
        completionAnswers()
        h.hold(COMPLETION)
        val run = async(Dispatchers.Default) { h.achievements.refreshIfDue() }
        h.awaitArrived(COMPLETION)
        assertTrue(h.achievements.signOut())
        h.release(COMPLETION)
        assertFalse(run.await())
        assertEquals("no success mark", 0L, h.marks.lastSuccess())
        assertEquals("no failure mark", 0L, h.marks.lastFailure())
        assertEquals(0, h.db.openHelper.readableDatabase.query("SELECT * FROM ra_progress").use { it.count })

        h.signIn()
        assertTrue("the next account refreshes at once", h.achievements.refreshIfDue())
    }

    @Test
    fun hashingForMasteredGamesStopsWhenAGameStarts() = runBlocking {
        h.addGame("Zork (USA).sfc", "Zork")
        h.addGame("Zork (Europe).sfc", "Zork")
        h.signIn()
        completionAnswers()
        h.answers[GAME_LIST] = { ok(gameList(md5 = "ffffffffffffffffffffffffffffffff")) }
        h.onHash = { h.playing = true }
        assertTrue(h.achievements.refreshIfDue())
        assertEquals("one file, then the game started", 1, h.hashed.size)
    }

    @Test
    fun gamesAlreadyCheckedReadNoFileAndUseNoBudget() = runBlocking {
        val other = "ffffffffffffffffffffffffffffffff"
        repeat(Achievements.MAX_TITLE_HASHES) { n ->
            val id = h.addGame("Zork ($n).sfc", "Zork")
            h.db.raDao().saveMatch(RaMatchEntity(id, RaMatchEntity.NO_SET, null, other, null, "k$n", 4, 1, 1))
        }
        val noSetBefore = h.addGame("Zork (Rev 1).sfc", "Zork")
        h.db.raDao().saveMatch(RaMatchEntity(noSetBefore, RaMatchEntity.NO_SET, null, AchievementsHarness.MD5, null, "kr", 4, 1, 1))
        val cannot = h.addGame("Zork (Beta).7z", "Zork")
        h.db.raDao().saveMatch(RaMatchEntity(cannot, RaMatchEntity.CANNOT_HASH, null, null, "NeedsDecoder", "kb", 4, 1, 1))
        val fresh = h.addGame("Zork (USA).sfc", "Zork")
        h.signIn()
        completionAnswers()
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        assertTrue(h.achievements.refreshIfDue())
        assertEquals("only the never-checked file is read", listOf("Zork (USA).sfc"), h.hashed.toList())
        assertEquals("a stored no-set hash is looked up again", RaMatchEntity.MATCHED, h.db.raDao().match(noSetBefore)?.status)
        assertEquals(RaMatchEntity.CANNOT_HASH, h.db.raDao().match(cannot)?.status)
        assertEquals(RaMatchEntity.MATCHED, h.db.raDao().match(fresh)?.status)
    }
}
