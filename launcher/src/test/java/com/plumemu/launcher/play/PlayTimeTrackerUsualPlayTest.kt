package com.plumemu.launcher.play

import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.addGame
import com.plumemu.launcher.inMemoryDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A play past the cap was a left-open emulator: it counts the game's usual play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTimeTrackerUsualPlayTest {
    private lateinit var db: PlumDatabase
    private var gameId = 0L
    private var wall = 1_700_000_000_000L
    private var uptime = 40_000L
    private val clock = object : PlayClock {
        override fun wallMs() = wall

        override fun realtimeMs() = uptime + 10_000

        override fun uptimeMs() = uptime

        override fun bootCount() = 7
    }
    private val log = RecordingLog()
    private var exact: Long? = null
    private val source = ForegroundTime { _, _, _ -> exact }

    private fun tracker() = PlayTimeTracker(
        SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
        db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(), foregroundTime = source,
    )

    private fun advance(ms: Long) {
        wall += ms
        uptime += ms
    }

    private suspend fun earlier(vararg minutes: Long) {
        minutes.forEachIndexed { i, m -> db.playDao().addSession(gameId, endedAt = wall - (i + 1) * 1_000L, seconds = m * 60) }
    }

    private suspend fun playHours(hours: Long): PlaySessionOutcome {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(hours * 3_600_000)
        return t.onResume()
    }

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aPlayPastTheCapCountsTheMedianOfEarlierPlays() = runTest {
        earlier(20, 40, 60)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 40 * 60), playHours(6))
        assertEquals(40 * 60L, db.playDao().sessions(gameId).last().seconds)
        assertTrue(log.lines.any { it.startsWith("I ") && it.contains("past the cap; counted as the usual play 2400 s") })
    }

    @Test
    fun anEvenCountTakesTheMeanOfTheMiddleTwo() = runTest {
        earlier(20, 40, 60, 80)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 50 * 60), playHours(5))
    }

    @Test
    fun underThreeEarlierPlaysCountHalfAnHour() = runTest {
        earlier(10, 90)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 30 * 60), playHours(6))
    }

    @Test
    fun noEarlierPlayCountsHalfAnHour() = runTest {
        assertEquals(PlaySessionOutcome.Recorded(gameId, 30 * 60), playHours(10))
    }

    @Test
    fun earlierPlaysOverTheCapAreLeftOutOfTheMedian() = runTest {
        earlier(20, 40, 60, 5 * 60, 6 * 60)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 40 * 60), playHours(7))
    }

    @Test
    fun onlyThreeOverTheCapLeavesTooFewForTheMedian() = runTest {
        earlier(20, 40, 5 * 60, 6 * 60, 7 * 60)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 30 * 60), playHours(7))
    }

    @Test
    fun aLowerExactTimeBeatsTheUsualPlay() = runTest {
        earlier(20, 40, 60)
        exact = 10 * 60_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 10 * 60), playHours(6))
    }

    @Test
    fun aHigherExactTimeDoesNotRaiseTheUsualPlay() = runTest {
        earlier(20, 40, 60)
        exact = 5 * 3_600_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 40 * 60), playHours(6))
    }

    @Test
    fun aPlayUnderTheCapIsUnchanged() = runTest {
        earlier(20, 40, 60)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 3 * 3_600), playHours(3))
    }

    @Test
    fun aFailedReadOfEarlierPlaysFallsBackAndWarns() = runTest {
        earlier(20, 40, 60)
        db.close()
        val t = tracker()
        // The database is closed: the outcome fails, but the warn names the exception kind and the fallback was not an error.
        t.onGameLaunched(gameId)
        advance(6 * 3_600_000)
        t.onResume()
        assertTrue(log.lines.any { it.startsWith("W ") && it.contains("not read") && it.contains("1800 s") })
    }
}
