package com.plumemu.launcher.play

import android.content.Context
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

/** Screen-off time is kept with the pending note, so a killed Plumemu still leaves it out of the play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayTimeTrackerScreenOffStoreTest {
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
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A tracker of one process: its own [ScreenOffTime], the one store on disk. */
    private fun process(screen: ScreenOffTime) = PlayTimeTracker(
        SharedPreferencesSessionStore(context), db.playDao(), db.gameDao(), clock, RecordingLog(), UnconfinedTestDispatcher(),
        screenOff = screen,
    )

    private fun advanceMin(minutes: Long) {
        wall += minutes * 60_000
        uptime += minutes * 60_000
    }

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun screenOffBeforeProcessDeathIsLeftOut() = runTest {
        val screen = ScreenOffTime(clock)
        process(screen).onGameLaunched(gameId)
        advanceMin(10)
        screen.screenChanged(on = false)
        advanceMin(5)
        screen.screenChanged(on = true)
        advanceMin(10)
        // Plumemu was killed here; the next process knows only the disk.
        val outcome = process(ScreenOffTime(clock)).onResume()
        assertEquals(PlaySessionOutcome.Recorded(gameId, 20 * 60), outcome)
    }

    @Test
    fun aStretchStillOpenAtProcessDeathIsLeftOutWhole() = runTest {
        val screen = ScreenOffTime(clock)
        process(screen).onGameLaunched(gameId)
        advanceMin(10)
        screen.screenChanged(on = false)
        advanceMin(5)
        // Killed with the screen off; the new process starts watching with the screen still off.
        val next = ScreenOffTime(clock).also { it.screenChanged(on = false) }
        val tracker = process(next)
        advanceMin(5)
        next.screenChanged(on = true)
        advanceMin(10)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 20 * 60), tracker.onResume())
    }

    @Test
    fun aStretchOpenAtDeathEndsWhenTheNewProcessStartsWatchingIfTheScreenIsOn() = runTest {
        val screen = ScreenOffTime(clock)
        process(screen).onGameLaunched(gameId)
        advanceMin(10)
        screen.screenChanged(on = false)
        advanceMin(5)
        val next = ScreenOffTime(clock) // screen on when watching starts
        val tracker = process(next)
        advanceMin(10)
        assertEquals(PlaySessionOutcome.Recorded(gameId, 20 * 60), tracker.onResume())
    }

    @Test
    fun twoDeathsInARowKeepEveryStretchOnce() = runTest {
        val first = ScreenOffTime(clock)
        process(first).onGameLaunched(gameId)
        advanceMin(10)
        first.screenChanged(on = false)
        advanceMin(5)
        first.screenChanged(on = true)
        val second = ScreenOffTime(clock)
        process(second)
        advanceMin(5)
        second.screenChanged(on = false)
        advanceMin(5)
        second.screenChanged(on = true)
        advanceMin(5)
        // Third process: 30 min elapsed, 10 min with the screen off.
        assertEquals(PlaySessionOutcome.Recorded(gameId, 20 * 60), process(ScreenOffTime(clock)).onResume())
    }

    @Test
    fun aScreenChangeAfterTheNoteWasClearedWritesNothing() = runTest {
        val screen = ScreenOffTime(clock)
        val tracker = process(screen)
        tracker.onGameLaunched(gameId)
        advanceMin(5)
        tracker.onResume()
        screen.screenChanged(on = false)
        advanceMin(1)
        screen.screenChanged(on = true)
        val prefs = context.getSharedPreferences("plumemu_play_session", Context.MODE_PRIVATE)
        assertTrue("no note may reappear: ${prefs.all}", prefs.all.isEmpty())
        assertEquals(PlaySessionOutcome.NothingPending, tracker.onResume())
    }

    @Test
    fun aNoteWithoutScreenOffKeysBehavesAsBefore() = runTest {
        val store = SharedPreferencesSessionStore(context)
        store.write(PendingSession(gameId, wall, uptime + 10_000, 7, startedAtUptimeMs = uptime))
        advanceMin(15)
        val outcome = process(ScreenOffTime(clock)).onResume()
        assertEquals(PlaySessionOutcome.Recorded(gameId, 15 * 60), outcome)
    }
}
