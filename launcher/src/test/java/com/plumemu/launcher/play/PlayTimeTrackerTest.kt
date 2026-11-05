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

private class FakeClock(
    var wall: Long = 1_700_000_000_000,
    var realtime: Long = 50_000,
    var boots: Int? = 7,
    var uptime: Long = 40_000,
) : PlayClock {
    override fun wallMs() = wall

    override fun realtimeMs() = realtime

    override fun uptimeMs() = uptime

    override fun bootCount() = boots

    fun advance(ms: Long) {
        wall += ms
        realtime += ms
        uptime += ms
    }

    /** Deep sleep: the awake clock stands still. */
    fun sleep(ms: Long) {
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
    private fun newProcessTracker(screenOff: ScreenOffTime = ScreenOffTime(clock)) = PlayTimeTracker(
        SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
        db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(), screenOff = screenOff,
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
        clock.uptime += 300_000
        assertEquals(PlaySessionOutcome.Recorded(gameId, 300), tracker.onResume())
    }

    @Test
    fun theStoredSessionSpansTheMeasuredTimeEndingNow() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall += 3 * 3_600_000 // corrected during the game: the span must not grow by three hours
        clock.realtime += 300_400
        clock.uptime += 300_400
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
        clock.uptime += 30_000
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
    fun aRebootInBetweenCountsNothing() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.wall += 1_800_000 // switched off, or the battery ran out: no clock tells how long was play
        clock.realtime = 20_000
        clock.uptime = 15_000
        clock.boots = 8
        assertEquals(PlaySessionOutcome.Unmeasurable(gameId), newProcessTracker().onResume())
        assertNull(db.playDao().play(gameId))
    }

    @Test
    fun aRebootWithALongerUptimeIsToldApartByTheBootCount() = runTest {
        newProcessTracker().onGameLaunched(gameId) // started 50 s after boot
        clock.wall += 3_600_000
        clock.realtime = 600_000 // ten minutes after the next boot
        clock.uptime = 590_000
        clock.boots = 8
        assertEquals(PlaySessionOutcome.Unmeasurable(gameId), newProcessTracker().onResume())
    }

    @Test
    fun deepSleepWithTheGameInFrontIsNotCounted() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.advance(600_000)
        clock.sleep(2 * 3_600_000) // folded shut for two hours
        clock.advance(300_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 900), tracker.onResume())
        val session = db.playDao().sessions(gameId).single()
        assertEquals(clock.wall - 900_000, session.startedAt)
    }

    @Test
    fun screenOffWhileKeptAwakeIsNotCounted() = runTest {
        val screen = ScreenOffTime(clock)
        val tracker = newProcessTracker(screen)
        tracker.onGameLaunched(gameId)
        clock.advance(600_000)
        screen.screenChanged(on = false)
        clock.advance(30 * 60_000) // screen off, but something holds the device awake
        screen.screenChanged(on = true)
        clock.advance(300_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 900), tracker.onResume())
    }

    @Test
    fun screenOffBeforeTheLaunchIsNotSubtracted() = runTest {
        val screen = ScreenOffTime(clock)
        screen.screenChanged(on = false)
        clock.advance(3_600_000)
        screen.screenChanged(on = true)
        val tracker = newProcessTracker(screen)
        tracker.onGameLaunched(gameId)
        clock.advance(600_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 600), tracker.onResume())
    }

    @Test
    fun aSessionLongerThanTheLimitCountsTheLimit() = runTest {
        val tracker = newProcessTracker()
        tracker.onGameLaunched(gameId)
        clock.advance(10 * 3_600_000) // the emulator left open overnight, screen kept on
        val limit = PlayTimeTracker.MAX_SESSION_SECONDS
        assertEquals(PlaySessionOutcome.Recorded(gameId, limit), tracker.onResume())
        assertEquals(clock.wall - limit * 1_000, db.playDao().sessions(gameId).single().startedAt)
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
        val start = PendingSession(1, startedAtWallMs = 1_000_000, startedAtRealtimeMs = 100_000, bootCount = 3, startedAtUptimeMs = 90_000)
        // Same boot: the awake clock decides, whatever the wall clock and deep sleep did.
        assertEquals(20_000L, PlaySessions.elapsedMs(start, 1_000_000 + 10_820_000, 120_000, 110_000, 3))
        assertEquals(20_000L, PlaySessions.elapsedMs(start, 1_000_000 - 3_600_000, 120_000, 110_000, 3))
        assertEquals(5_000L, PlaySessions.elapsedMs(start, 1_020_000, 120_000, 95_000, 3))
        // A note without the awake clock (written by an older version): the elapsed-since-boot clock.
        assertEquals(20_000L, PlaySessions.elapsedMs(start.copy(startedAtUptimeMs = null), 1_020_000, 120_000, 95_000, 3))
        // Another boot cannot be measured.
        assertNull(PlaySessions.elapsedMs(start, 1_500_000, 200_000, 150_000, 4))
        assertNull(PlaySessions.elapsedMs(start, 900_000, 50_000, 40_000, 4))
        // Android 6, no boot count: the boot moment seen through both clocks decides.
        val old = start.copy(bootCount = null)
        assertEquals(20_000L, PlaySessions.elapsedMs(old, 1_000_000 + 20_500, 120_000, 110_000, null))
        assertNull(PlaySessions.elapsedMs(old, 1_500_000, 200_000, 150_000, null))
    }

    @Test
    fun theAwakeStartSurvivesProcessDeath() = runTest {
        newProcessTracker().onGameLaunched(gameId)
        clock.advance(300_000)
        clock.sleep(3_600_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 300), newProcessTracker().onResume())
    }
}
