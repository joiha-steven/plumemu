package com.plumemu.launcher.play

import com.plumemu.coredata.db.PlumDatabase
import com.plumemu.launcher.RecordingLog
import com.plumemu.launcher.addGame
import com.plumemu.launcher.inMemoryDatabase
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A start given up while it runs leaves no half-recorded play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayHandOffTest {
    private class MemoryStore : PendingSessionStore {
        var session: PendingSession? = null
        var onWrite: () -> Unit = {}
        var clears = true

        override fun read(): PendingSession? = session

        override fun write(session: PendingSession): Boolean {
            this.session = session
            onWrite()
            return true
        }

        override fun clear(): Boolean {
            if (clears) session = null
            return clears
        }
    }

    private object Clock : PlayClock {
        override fun wallMs() = 1_700_000_000_000

        override fun realtimeMs() = 50_000L

        override fun bootCount() = 7
    }

    private lateinit var db: PlumDatabase
    private val store = MemoryStore()
    private var gameId = 0L

    @Before
    fun setUp() = runTest {
        db = inMemoryDatabase()
        gameId = db.addGame("snes", "primary:ROMs/snes/a.sfc")
    }

    @After
    fun tearDown() = db.close()

    private fun kotlinx.coroutines.test.TestScope.tracker() =
        PlayTimeTracker(store, db.playDao(), db.gameDao(), Clock, RecordingLog(), UnconfinedTestDispatcher(testScheduler))

    @Test
    fun cancelledWhileTheNoteIsWrittenStartsNothingAndTakesTheNoteBack() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        var started = false
        val job = launch {
            val me = coroutineContext.job
            store.onWrite = { me.cancel() }
            tracker.handOff(gameId, "snes9x") { started = true }
        }
        job.join()
        assertFalse(started)
        assertNull("no half-recorded play", store.session)
        assertFalse(tracker.isPending())
    }

    @Test
    fun cancelledDuringTheStartKeepsTheNoteOfAGameHandedOver() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        var started = false
        val job = launch {
            val me = coroutineContext.job
            tracker.handOff(gameId, "snes9x") {
                me.cancel()
                started = true
            }
        }
        job.join()
        assertTrue(started)
        assertNotNull("the game was handed over: its play counts", store.session)
        assertTrue(tracker.isPending())
    }

    @Test
    fun cancelledBeforeNotesNothing() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        var started = false
        val job = launch {
            coroutineContext.job.cancel()
            tracker.handOff(gameId) { started = true }
        }
        job.join()
        assertFalse(started)
        assertNull(store.session)
    }

    @Test
    fun takingTheNoteBackWorksFromACancelledCaller() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        tracker.onGameLaunched(gameId, "snes9x")
        val job = launch {
            coroutineContext.job.cancel()
            tracker.cancelPending()
        }
        job.join()
        assertNull(store.session)
        assertEquals(false, tracker.isPending())
    }

    @Test
    fun aWaitGivenUpBeforeTheHandOffStartsNothingAndTakesTheNoteBack() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        val watch = HandOffWatch()
        var started = false
        store.onWrite = { assertTrue(watch.giveUp()) } // the 20 s limit lands while the note is written
        launch(watch) { runCatching { tracker.handOff(gameId, "snes9x") { started = true } } }.join()
        assertFalse(started)
        assertNull("no half-recorded play", store.session)
    }

    @Test
    fun aHandOffThatBeganCannotBeGivenUpAndIsWaitedFor() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        val watch = HandOffWatch()
        var giveUpAccepted: Boolean? = null
        launch(watch) { tracker.handOff(gameId, "snes9x") { giveUpAccepted = watch.giveUp() } }.join()
        assertEquals("the emulator is being opened: the wait goes on", false, giveUpAccepted)
        assertNotNull(store.session)
    }

    @Test
    fun aNoteThatCannotBeClearedStaysPending() = runTest(UnconfinedTestDispatcher()) {
        // Why the app's held-for-play also asks whether Plumemu is in front (HeldForPlayTest).
        val tracker = tracker()
        tracker.onGameLaunched(gameId, "snes9x")
        store.clears = false
        tracker.cancelPending()
        assertTrue(tracker.isPending())
    }

    @Test
    fun aGivenUpStartTakesBackOnlyItsOwnNoteNotTheNextStarts() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        val secondGame = db.addGame("snes", "primary:ROMs/snes/b.sfc")
        val firstWatch = HandOffWatch()
        var secondStarted = false
        var writes = 0
        // Slow storage: the 20 s limit lands while start #1 writes its note; the person presses OK then A,
        // and start #2 queues for the lock, so it writes its note before #1 can take its own back.
        store.onWrite = {
            if (writes++ == 0) {
                assertTrue(firstWatch.giveUp())
                launch { tracker.handOff(secondGame, "snes9x") { secondStarted = true } }
            }
        }
        launch(firstWatch) { runCatching { tracker.handOff(gameId, "snes9x") { error("given up: must not start") } } }.join()
        assertTrue(secondStarted)
        assertEquals("the second game's note is kept: its play time counts", secondGame, store.session?.gameId)
        assertTrue(tracker.isPending())
    }

    @Test
    fun aGivenUpStartWithNoLaterStartTakesItsNoteBack() = runTest(UnconfinedTestDispatcher()) {
        val tracker = tracker()
        val watch = HandOffWatch()
        store.onWrite = { watch.giveUp() }
        launch(watch) { runCatching { tracker.handOff(gameId) { error("must not start") } } }.join()
        assertNull(store.session)
    }
}
