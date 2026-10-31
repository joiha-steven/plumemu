package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.PrefetchRequest
import androidx.compose.foundation.lazy.layout.PrefetchRequestScope
import androidx.compose.foundation.lazy.layout.PrefetchScheduler
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A system step in the column never builds a games page inside a frame (the Retroid Pocket 6 lost
 * 15-56 ms to it, budget 8.3 ms at 120 Hz): the page that becomes a neighbour is built by the
 * prefetcher in idle time and pinned only once it is built. Robolectric gives Compose's own
 * prefetcher no idle time, so the test gives the lazy row a scheduler it runs itself: the real
 * prefetch handles, run by the test between frames, a slice at a time as the device does.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class SystemStepFramesTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Page compositions (the page and its list rows) with the test clock's time they ran at. */
    private val built = mutableListOf<Pair<String, Long>>()
    private var inIdle = false

    private class ManualScheduler : PrefetchScheduler {
        val requests = ArrayDeque<PrefetchRequest>()

        override fun schedulePrefetch(prefetchRequest: PrefetchRequest) {
            requests += prefetchRequest
        }
    }

    private val scheduler = ManualScheduler()

    @Before
    fun trace() {
        Composer.setTracer(
            object : CompositionTracer {
                override fun isTraceInProgress(): Boolean = true

                override fun traceEventStart(
                    key: Int,
                    dirty1: Int,
                    dirty2: Int,
                    info: String,
                ) {
                    val name = info.substringBefore(' ')
                    if (!inIdle && (name == "com.plumemu.home.EntryGames" || name == "com.plumemu.home.GamesListItem")) built += name to compose.mainClock.currentTime
                }

                override fun traceEventEnd() = Unit
            },
        )
    }

    @After
    fun stop() {
        Composer.setTracer(null)
        gamesPagesStateMade = null
    }

    private fun pages(): LazyListState = gamesProbe().pages

    private fun gamesProbe(): GamesPagesProbe =
        checkNotNull(compose.onNodeWithTag(HomeTags.GAMES, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(GamesPagesInfo))

    /** Gives the pages' lazy row [scheduler] for its prefetching (a foundation internal, set by reflection: no public way). */
    private fun useManualScheduler(list: LazyListState) {
        val prefetchState = LazyListState::class.java.getMethod("getPrefetchState\$foundation").invoke(list)
        prefetchState.javaClass.getMethod("setPrefetchScheduler\$foundation", PrefetchScheduler::class.java).invoke(prefetchState, scheduler)
    }

    /** Idle time: runs queued prefetch requests, each [slices] times at most with [timeNanos] to spend, or to the end with null. */
    private fun idle(slices: Int? = null) {
        compose.runOnUiThread {
            inIdle = true
            var left = slices ?: Int.MAX_VALUE
            while (left > 0 && scheduler.requests.isNotEmpty()) {
                val request = scheduler.requests.first()
                val more = with(request) { Scope(if (slices == null) Long.MAX_VALUE / 4 else 1L).execute() }
                if (!more) scheduler.requests.removeFirst()
                left--
            }
            inIdle = false
        }
    }

    private class Scope(
        val nanos: Long,
    ) : PrefetchRequestScope {
        override fun availableTimeNanos(): Long = nanos
    }

    /** Frames of [count] after the step; the compositions each ran in, by frame time. */
    private fun frames(count: Int): List<Int> {
        built.clear()
        val start = compose.mainClock.currentTime
        repeat(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return (1..count).map { f -> built.count { (_, at) -> at <= start + f * FRAME_MS && at > start + (f - 1) * FRAME_MS } }
    }

    @Test
    fun aSystemStepBuildsNoPageInAFrameWhenItsNeighboursWereBuiltInIdleTime() {
        lateinit var state: StripState
        gamesPagesStateMade = ::useManualScheduler
        compose.setContent { PlumTheme(PlumThemeSettings.Default) { HomeAt(FakeHomeContent.board(), listOf(Action.TapItem(Column.Systems, FakeHomeContent.BOARD_SNES_INDEX)), onState = { state = it }) } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        // Idle time after the first frame: the neighbours of the shown page are built to the end.
        idle()
        frames(2)
        // A tap, 250 ms later: the page it lands on was built in idle time.
        compose.mainClock.advanceTimeBy(250)
        compose.runOnUiThread { state.dispatch(Action.Move(Direction.Down)) }
        val stepFrames = frames(2)
        assertEquals("no page built in the frame of the step or the one after: $stepFrames", listOf(0, 0), stepFrames)
        // The prefetcher gets a slice of idle time too short to finish the new neighbour.
        idle(slices = 1)
        val halfBuilt = frames(2)
        assertEquals("a neighbour the prefetcher has not finished is not built in a frame: $halfBuilt", listOf(0, 0), halfBuilt)
        val next = FakeHomeContent.board().entries[FakeHomeContent.BOARD_SNES_INDEX + 2].id
        assertEquals("unfinished: not pinned yet", true, next in gamesProbe().pending())
        idle()
        val finished = frames(2)
        assertEquals("once finished it is pinned without building anything in a frame: $finished", listOf(0, 0), finished)
        assertEquals("finished: pinned", false, next in gamesProbe().pending())
        assertEquals(FakeHomeContent.BOARD_SNES_INDEX + 1, state.focus.systemIndex)
    }

    private companion object {
        const val FRAME_MS = 16
    }
}
