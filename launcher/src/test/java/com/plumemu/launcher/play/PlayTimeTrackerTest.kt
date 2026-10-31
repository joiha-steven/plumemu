package com.plumemu.launcher.play

import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.addGame
import com.plumemu.launcher.inMemoryDatabase
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeClock(var wall: Long = 1_700_000_000_000, var realtime: Long = 50_000, var boots: Int? = 7) : PlayClock {
    override fun wallMs() = wall

    override fun realtimeMs() = realtime

    override fun bootCount() = boots

    fun advance(ms: Long) {
        wall += ms
        realtime += ms
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTimeTrackerTest {
    private lateinit var db: PlumDatabase
    private val clock = FakeClock()
    private val log = RecordingLog()
    private var gameId = 0L

    /** A tracker as a fresh process would build it: nothing in memory, only what is on disk. */
    private fun newProcessTracker() = PlayTimeTracker(
        SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
        db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(),
    )

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun theEmulatorStartedComesBackWithItsSessionAfterProcessDeath() = runTest {
        newProcessTracker().onGameLaunched(gameId, emulatorId = "snes9x")
        clock.advance(61_000)
        // A new process (Android killed Plumemu while the game ran) still knows which emulator played.
        assertEquals(PlaySessionOutcome.Recorded(gameId, 61, "snes9x"), newProcessTracker().onResume())
    }

    @Test
    fun aSessionIsAddedOnTheNextResume() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.advance(95_400)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 95), tracker.onResume())
        val play = db.playDao().play(gameId)
        assertEquals(95L, play?.totalSeconds)
        assertEquals(clock.wall, play?.lastPlayedAt)

        tracker.onGameLaunched(gameId)
        clock.advance(10_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 10), tracker.onResume())
        assertEquals(105L, db.playDao().play(gameId)?.totalSeconds)
    }

    @Test
    fun underTenSecondsIsIgnored() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.advance(9_999)
        assertEquals(PlaySessionOutcome.TooShort(gameId, 9), tracker.onResume())
        assertNull(db.playDao().play(gameId))
    }

    @Test
    fun theStartSurvivesProcessDeath() = runTest {
        newProcessTracker().onGameLaunched(gameId)
        clock.advance(600_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 600), newProcessTracker().onResume())
        assertEquals(600L, db.playDao().play(gameId)?.totalSeconds)
    }

    @Test
    fun aResumeWithoutALaunchAndASecondResumeAddNothing() = runTest {
        val tracker = newProcessTracker()
        assertEquals(PlaySessionOutcome.NothingPending, tracker.onResume())
        tracker.onGameLaunched(gameId)
        clock.advance(60_000)
        val outcomes = listOf(async { tracker.onResume() }, async { newProcessTracker().onResume() }).awaitAll()
        assertEquals(1, outcomes.count { it is PlaySessionOutcome.Recorded })
        assertEquals(60L, db.playDao().play(gameId)?.totalSeconds)
    }

    @Test
    fun aCancelledLaunchCountsNothing() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        tracker.cancelPending()
        clock.advance(60_000)
        assertEquals(PlaySessionOutcome.NothingPending, tracker.onResume())
    }

    @Test
    fun aGameRemovedMeanwhileIsReportedNotCrashed() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(9_999)
        clock.advance(60_000)
        assertEquals(PlaySessionOutcome.GameGone(9_999), tracker.onResume())
    }

    @Test
    fun aNetworkTimeJumpWithinOneBootCountsOnlyTheElapsedClock() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall += 3 * 3_600_000 // the clock was three hours off and got corrected
        clock.realtime += 300_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 300), tracker.onResume())
    }

    @Test
    fun theStoredSessionSpansTheMeasuredTimeEndingNow() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall += 3 * 3_600_000 // corrected during the game: the span must not grow by three hours
        clock.realtime += 300_400
        assertEquals(PlaySessionOutcome.Recorded(gameId, 300), tracker.onResume())
        val session = db.playDao().sessions(gameId).single()
        assertEquals(clock.wall, session.endedAt)
        assertEquals(clock.wall - 300_400, session.startedAt)
        assertEquals(300L, session.seconds)
        assertEquals("snes", session.systemId)
    }

    @Test
    fun aBackwardsTimeChangeWithinOneBootCountsOnlyTheElapsedClock() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall -= 3_600_000 // the person set the clock an hour back
        clock.realtime += 30_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 30), tracker.onResume())
    }

    @Test
    fun aClosedDatabaseIsReportedNotThrown() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.advance(60_000)
        db.close()
        val outcome = tracker.onResume()
        assertTrue("$outcome", outcome is PlaySessionOutcome.Failed)
    }

    @Test
    fun aRebootInBetweenFallsBackToTheWallClock() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall += 1_800_000
        clock.realtime = 20_000 // the elapsed-since-boot clock restarted
        clock.boots = 8
        assertEquals(PlaySessionOutcome.Recorded(gameId, 1_800), newProcessTracker().onResume())
    }

    @Test
    fun aRebootWithALongerUptimeIsToldApartByTheBootCount() = runTest {
        newProcessTracker().onGameLaunched(gameId) // started 50 s after boot
        clock.wall += 3_600_000
        clock.realtime = 600_000 // ten minutes after the next boot
        clock.boots = 8
        assertEquals(PlaySessionOutcome.Recorded(gameId, 3_600), newProcessTracker().onResume())
    }

    @Test
    fun onAndroidSixWithoutBootCountAClockSetBackCannotBeMeasured() = runTest {
        clock.boots = null
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall -= 3_600_000
        clock.realtime += 30_000
        assertEquals(PlaySessionOutcome.Unmeasurable(gameId), tracker.onResume())
    }

    @Test
    fun theSystemClockReadsTheBootCount() {
        val resolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver
        android.provider.Settings.Global.putInt(resolver, android.provider.Settings.Global.BOOT_COUNT, 42)
        assertEquals(42, SystemPlayClock(resolver).bootCount())
    }

    @Test
    fun elapsedTimeRules() {
        val start = PendingSession(1, startedAtWallMs = 1_000_000, startedAtRealtimeMs = 100_000, bootCount = 3)
        // Same boot: the elapsed-since-boot clock decides, whatever the wall clock did.
        assertEquals(20_000L, PlaySessions.elapsedMs(start, 1_000_000 + 10_820_000, 120_000, 3))
        assertEquals(20_000L, PlaySessions.elapsedMs(start, 1_000_000 - 3_600_000, 120_000, 3))
        // Another boot: the wall clock decides; negative cannot be measured.
        assertEquals(500_000L, PlaySessions.elapsedMs(start, 1_500_000, 200_000, 4))
        assertNull(PlaySessions.elapsedMs(start, 900_000, 50_000, 4))
        // Android 6, no boot count: the boot moment seen through both clocks decides.
        val old = start.copy(bootCount = null)
        assertEquals(20_000L, PlaySessions.elapsedMs(old, 1_000_000 + 20_500, 120_000, null))
        assertEquals(500_000L, PlaySessions.elapsedMs(old, 1_500_000, 200_000, null))
    }
}
