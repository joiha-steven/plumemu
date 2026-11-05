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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTimeTrackerExactTest {
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
    private val asked = mutableListOf<Triple<String, Long, Long>>()
    private var answer: Long? = null

    private val source = ForegroundTime { pkg, from, to ->
        asked.add(Triple(pkg, from, to))
        answer
    }

    private fun tracker() = PlayTimeTracker(
        SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
        db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(), foregroundTime = source,
    )

    private fun advance(ms: Long) {
        wall += ms
        uptime += ms
    }

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun lessInFrontThanAwakeCountsTheLower() = runTest {
        val t = tracker()
        val start = wall
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(100_000)
        answer = 60_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 60), t.onResume())
        assertEquals(listOf(Triple("com.emu", start, wall)), asked)
        assertEquals(60L, db.playDao().play(gameId)?.totalSeconds)
        val session = db.playDao().sessions(gameId).single()
        assertEquals(60_000L, session.endedAt - session.startedAt)
    }

    @Test
    fun anUnknownTimeKeepsTheAwakeCount() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(100_000)
        answer = null
        assertEquals(PlaySessionOutcome.Recorded(gameId, 100), t.onResume())
    }

    @Test
    fun moreInFrontThanAwakeIsCappedByTheAwakeTime() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(100_000)
        answer = 500_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 100), t.onResume())
    }

    @Test
    fun aNoteWithoutAPackageDoesNotAskTheSource() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId)
        advance(100_000)
        answer = 5_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 100), t.onResume())
        assertEquals(emptyList<Triple<String, Long, Long>>(), asked)
    }

    @Test
    fun aLowerExactTimeUnderTenSecondsIsStillARealStart() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(100_000)
        answer = 4_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 4), t.onResume())
        assertEquals(4L, db.playDao().play(gameId)?.totalSeconds)
    }

    @Test
    fun nothingInFrontAtAllIsARecordedStartWithNoRow() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(3_600_000)
        answer = 0
        assertEquals(PlaySessionOutcome.Recorded(gameId, 0), t.onResume())
        assertEquals(emptyList<Any>(), db.playDao().sessions(gameId))
    }

    @Test
    fun aShortAwakeTimeIsTooShortWhateverTheExactSourceSays() = runTest {
        val t = tracker()
        t.onGameLaunched(gameId, packageName = "com.emu")
        advance(4_000)
        answer = 0
        assertEquals(PlaySessionOutcome.TooShort(gameId, 4), t.onResume())
        assertNull(db.playDao().play(gameId))
    }
}
