package com.plumemu.launcher.play

import androidx.test.core.app.ApplicationProvider
import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.coredata.db.SessionBattery
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.addGame
import com.plumemu.launcher.inMemoryDatabase
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

/** A meter that hands out [next] once and counts the starts it was told of. */
private class FakePlayBattery(var next: SessionBattery? = null) : PlayBattery {
    var starts = 0
    var takes = 0
    val usuals = mutableListOf<Long?>()

    override fun onPlayStarting(usualDrawMicroamps: Long?) {
        starts++
        usuals += usualDrawMicroamps
        next = null
    }

    override fun takeMeasured(): SessionBattery? {
        takes++
        return next.also { next = null }
    }
}

private class StepClock(var wall: Long = 1_700_000_000_000, var realtime: Long = 50_000) : PlayClock {
    override fun wallMs() = wall

    override fun realtimeMs() = realtime

    override fun bootCount() = 7

    fun advance(ms: Long) {
        wall += ms
        realtime += ms
    }
}

/** The battery measured during a play is stored with its session, once, and only its own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTimeTrackerBatteryTest {
    private lateinit var db: PlumDatabase
    private val clock = StepClock()
    private val battery = FakePlayBattery()
    private var gameId = 0L

    private fun tracker() = PlayTimeTracker(
        SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
        db.playDao(), db.gameDao(), clock, RecordingLog(), UnconfinedTestDispatcher(), battery as PlayBattery,
    )

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun whatThePlayMeasuredIsStoredInItsSessionRow() = runTest {
        val tracker = tracker()
        tracker.onGameLaunched(gameId)
        assertEquals("the launch starts a new measurement", 1, battery.starts)
        battery.next = SessionBattery(540, 2_000_000, 1_950_000, 333_000)
        clock.advance(600_000)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 600), tracker.onResume())
        val row = db.playDao().sessions(gameId).single()
        assertEquals(540L, row.batterySeconds)
        assertEquals(333_000L, row.batteryDrawMicroamps)
        assertEquals(2_000_000L, row.chargeStartMicroampHours)
        assertEquals(1_950_000L, row.chargeEndMicroampHours)
    }

    @Test
    fun aPlayWithNothingMeasuredStoresNoFigures() = runTest {
        val tracker = tracker()
        tracker.onGameLaunched(gameId)
        clock.advance(600_000)
        tracker.onResume()
        assertNull(db.playDao().sessions(gameId).single().batterySeconds)
    }

    @Test
    fun aTooShortPlayStillTakesItsFiguresSoTheNextPlayDoesNotInheritThem() = runTest {
        val tracker = tracker()
        tracker.onGameLaunched(gameId)
        battery.next = SessionBattery(5, null, null, 300_000)
        clock.advance(5_000)
        assertEquals(PlaySessionOutcome.TooShort(gameId, 5), tracker.onResume())
        assertEquals(1, battery.takes)
        assertNull(battery.next)
    }

    @Test
    fun aSessionUnderTenSecondsNamesItsEmulatorAndTenSecondsIsAlreadyRecorded() = runTest {
        val tracker = tracker()
        tracker.onGameLaunched(gameId, emulatorId = "snes9x")
        clock.advance((PlayTimeTracker.MIN_SESSION_SECONDS - 1) * 1_000)
        assertEquals(PlaySessionOutcome.TooShort(gameId, 9, "snes9x"), tracker.onResume())
        tracker.onGameLaunched(gameId, emulatorId = "snes9x")
        clock.advance(PlayTimeTracker.MIN_SESSION_SECONDS * 1_000)
        assertEquals("a normal session says nothing", PlaySessionOutcome.Recorded(gameId, 10, "snes9x"), tracker.onResume())
    }

    @Test
    fun theMeterIsToldTheGamesUsualPaceAtTheStart() = runTest {
        val tracker = tracker()
        tracker.onGameLaunched(gameId)
        clock.advance(600_000)
        battery.next = SessionBattery(600, null, null, 400_000)
        tracker.onResume()
        tracker.onGameLaunched(gameId)
        assertEquals("never measured, then the stored play's pace (ROADMAP S6b)", listOf(null, 400_000L), battery.usuals)
    }

    @Test
    fun aUsualPaceThatCannotBeReadStartsThePlayWithoutOne() = runTest {
        val log = RecordingLog()
        val tracker =
            PlayTimeTracker(
                SharedPreferencesSessionStore(ApplicationProvider.getApplicationContext()),
                db.playDao(), db.gameDao(), clock, log, UnconfinedTestDispatcher(), battery as PlayBattery,
            )
        db.close()
        tracker.onGameLaunched(gameId)
        assertEquals(listOf<Long?>(null), battery.usuals)
        assertTrue(log.lines.toString(), log.lines.any { it.startsWith("W ") && "usual battery pace of game $gameId not read" in it })
    }

    @Test
    fun aResumeWithNothingPendingTakesNothing() = runTest {
        battery.next = SessionBattery(600, null, null, 300_000)
        assertEquals(PlaySessionOutcome.NothingPending, tracker().onResume())
        assertEquals("an app of the Apps entry: its reads wait for the next game's start, which clears them", 0, battery.takes)
    }
}
