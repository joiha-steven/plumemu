package com.plumemu.graph

import com.plumemu.coredata.db.RaAchievementEntity
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.graph.AchievementsHarness.Companion.COMPLETION
import com.plumemu.graph.AchievementsHarness.Companion.GAME_LIST
import com.plumemu.graph.AchievementsHarness.Companion.GAME_PROGRESS
import com.plumemu.graph.AchievementsHarness.Companion.completion
import com.plumemu.graph.AchievementsHarness.Companion.completionRow
import com.plumemu.graph.AchievementsHarness.Companion.gameList
import com.plumemu.graph.AchievementsHarness.Companion.ok
import com.plumemu.graph.AchievementsHarness.Companion.progress
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The game progress answer (`API_GetGameInfoAndUserProgress`, read by Info and after a game) is
 * stored with its points and its achievement list (schema 17); the completion refresh keeps known
 * points and sends nothing more; sign-out deletes the list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class AchievementsStoredListTest {
    @get:org.junit.Rule val timeout = graphTestTimeout()

    @get:Rule
    val temp = TemporaryFolder()

    private val h by lazy { AchievementsHarness(temp.newFolder("ra")) }

    @After
    fun tearDown() = h.close()

    private suspend fun stored(raGameId: Long): List<RaAchievementEntity> = h.db.raDao().observeRecentAchievements(raGameId, 10).first()

    private suspend fun points(gameId: Long) = h.db.raDao().observeProgress(gameId).first()?.let { it.pointsEarned to it.pointsTotal }

    private fun achievementRows(): Int =
        h.db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM ra_achievement").use { it.moveToFirst(); it.getInt(0) }

    @Test
    fun infoStoresThePointsAndTheListWithTheUnlockTimes() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false // no daily refresh in this test
        h.answers[GAME_LIST] = { ok(gameList(raId = 11)) }
        h.answers[GAME_PROGRESS] = { ok(progress(raId = 11, awarded = 2)) }

        h.achievements.progressFor(zork)

        assertEquals("earned 5 + 10 of 5 + 10 + 15", 15 to 30, points(zork))
        assertEquals(3, achievementRows())
        assertEquals(
            listOf(
                RaAchievementEntity(11, 2, "A2", "102", 10, raTimeMillis("2026-01-02 10:00:00"), null),
                RaAchievementEntity(11, 1, "A1", "101", 5, 1_767_261_600_000, null),
            ),
            stored(11),
        )
    }

    @Test
    fun afterAGameTheListAndPointsFollowTheNewAnswer() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 1)) }
        h.achievements.progressFor(zork)
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 3)) }

        h.achievements.afterGame(zork)

        assertEquals(30 to 30, points(zork))
        assertEquals(listOf(3L, 2L, 1L), stored(11).map { it.achievementId })
    }

    @Test
    fun anAnswerListingFewerAchievementsThanItCountsStoresNoPointsAndKeepsTheList() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 1)) }
        h.achievements.progressFor(zork)
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 2).replace("\"NumAchievements\":3", "\"NumAchievements\":4")) }

        h.achievements.progressFor(zork)

        assertEquals(null to null, points(zork))
        assertEquals("the earlier list stays", listOf(1L), stored(11).map { it.achievementId })
    }

    @Test
    fun theCompletionRefreshKeepsThePointsWhileTheCountsHoldAndAsksNothingElse() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false
        h.answers[GAME_LIST] = { ok(gameList()) }
        val progressCalls = java.util.concurrent.atomic.AtomicInteger()
        h.answers[GAME_PROGRESS] = { progressCalls.incrementAndGet(); ok(progress(awarded = 2)) }
        h.achievements.progressFor(zork)
        h.unmetered = true
        h.answers[COMPLETION] = { ok(completion(completionRow(11, "Zork", null, awarded = 2))) }

        assertTrue(h.achievements.refreshIfDue())

        assertEquals(15 to 30, points(zork))
        assertEquals(listOf(2L, 1L), stored(11).map { it.achievementId })
        assertEquals("the refresh asks no game's list", 1, progressCalls.get())
    }

    @Test
    fun signOutDeletesTheStoredList() = runBlocking {
        val zork = h.addGame("zork.sfc", "Zork")
        h.signIn()
        h.unmetered = false
        h.answers[GAME_LIST] = { ok(gameList()) }
        h.answers[GAME_PROGRESS] = { ok(progress(awarded = 2)) }
        h.achievements.progressFor(zork)
        assertEquals(3, achievementRows())

        assertTrue(h.achievements.signOut())

        assertEquals(0, achievementRows())
        assertNull(h.db.raDao().observeProgress(zork).first())
    }

    @Test
    fun unlockTimesReadAsUtcAndTextThatDoesNotReadIsNull() {
        assertEquals(1_767_261_600_000L, raTimeMillis("2026-01-01 10:00:00"))
        assertNull(raTimeMillis("yesterday"))
        assertNull(raTimeMillis("2026-13-45 10:00:00"))
    }
}
