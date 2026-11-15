package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.WARM_DELAY_MS
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The grids of the systems either side of the shown one are built ahead in idle time (pages of grids, [GridPages]), kept while they are
 * neighbours: a switch onto a grid system places a built grid instead of composing its covers inside one frame (46-49 ms on the Thor).
 * Hidden grids are measured at their size, never placed or drawn, and add no tags to the semantics tree.
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1024dp-h576dp-land-xhdpi")
class GridsBuiltAheadTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Cover compositions outside idle time, and in idle time. */
    private val coversInFrames = mutableListOf<String>()
    private val coversInIdle = mutableListOf<String>()
    private var inIdle = false
    private val scheduler = ManualPagesPrefetch()
    private val board = FakeHomeContent.withArtwork()
    private val snes = FakeHomeContent.BOARD_SNES_INDEX
    private val next = snes + 1
    private val drawn = mutableListOf<Pair<String, Int>>()
    private var gridAreaPx = 0
    private var gapPx = 0f

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
                    if (info.substringBefore(' ') == "com.plumemu.home.GridCoverAt") (if (inIdle) coversInIdle else coversInFrames) += info
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

    private fun show(
        grids: Set<Int>,
        actions: List<Action>,
        idle: Boolean = true,
    ): StripState {
        lateinit var state: StripState
        gamesPagesStateMade = scheduler::install
        val probe = GridDrawProbe { kind, index -> drawn += kind to index }
        compose.setContent {
            PlumTheme {
                with(LocalDensity.current) {
                    gridAreaPx = PlumTheme.layout.gridAreaWidth.roundToPx()
                    gapPx = PlumDimens.columnGap.toPx()
                }
                CompositionLocalProvider(LocalGridDrawProbe provides probe) {
                    HomeAt(board, actions, gridIds = grids.map { board.entries[it].id }.toSet(), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        if (idle) {
            // The grids beside the shown one are asked for once the focus has rested.
            restFor(WARM_DELAY_MS + 100)
            compose.waitForIdle()
            compose.runOnUiThread {
                inIdle = true
                scheduler.runIdle()
                inIdle = false
            }
        }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return state
    }

    /** Time passes for the rest timer, a delayed post on the main looper (Compose's clock does not run it). */
    private fun restFor(ms: Long) = compose.runOnUiThread { org.robolectric.shadows.ShadowLooper.idleMainLooper(ms, java.util.concurrent.TimeUnit.MILLISECONDS) }

    private fun step(
        state: StripState,
        action: Action,
    ) {
        compose.runOnUiThread { state.dispatch(action) }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    /** The screen on [grids] (board indexes) with no idle run yet and the clock held: the rest timer is a delayed post, moved by [restFor]. */
    private fun startHeld(
        grids: List<Int>,
        actions: List<Action> = listOf(Action.TapItem(Column.Systems, snes), Action.Enter),
    ): StripState {
        lateinit var state: StripState
        gamesPagesStateMade = scheduler::install
        compose.mainClock.autoAdvance = false
        compose.setContent {
            PlumTheme { HomeAt(board, actions, gridIds = grids.map { board.entries[it].id }.toSet(), onState = { state = it }) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        return state
    }

    private fun idle() =
        compose.runOnUiThread {
            inIdle = true
            scheduler.runIdle()
            inIdle = false
        }

    /** Time passes by [ms] with a frame every 50 ms at most, the idle time of each frame used. */
    private fun passTime(ms: Long) {
        var left = ms
        while (left > 0) {
            val slice = minOf(50L, left)
            compose.mainClock.advanceTimeBy(slice)
            restFor(slice)
            compose.waitForIdle()
            idle()
            left -= slice
        }
    }

    private fun dispatch(
        state: StripState,
        action: Action,
    ) {
        compose.runOnUiThread { state.dispatch(action) }
        compose.mainClock.advanceTimeBy(50)
        restFor(50)
        compose.waitForIdle()
        idle()
    }

    /** Nothing is built beside the shown grid while the focus moves every 50 ms (a held key); it is built once the focus has rested [WARM_DELAY_MS]. */
    @Test
    fun aGridIsNotBuiltWhileTheFocusMovesAndIsBuiltOnceItHasRested() {
        val state = startHeld(listOf(snes, next))
        repeat(20) { i -> dispatch(state, Action.Move(if (i % 2 == 0) Direction.Right else Direction.Left)) }
        assertEquals("a grid was built while the focus moved: $coversInIdle", emptyList<String>(), coversInIdle)
        passTime(WARM_DELAY_MS + 100)
        assertTrue("the grid was built once the focus rested", coversInIdle.isNotEmpty())
    }

    /** L1/R1 held across several systems (each a grid) builds none of their neighbours until the rest. */
    @Test
    fun aHeldSystemSwitchBuildsNothingUntilItRests() {
        val state = startHeld(listOf(snes, next, next + 1, next + 2, next + 3, next + 4))
        repeat(4) { dispatch(state, Action.NextSystem) }
        assertEquals(next + 3, state.focus.systemIndex)
        assertEquals("a grid was built while the systems switched: $coversInIdle", emptyList<String>(), coversInIdle)
        passTime(WARM_DELAY_MS + 100)
        assertTrue("the neighbours were built once the switching rested", coversInIdle.isNotEmpty())
    }

    /** The rest ended and the request is made, then the focus moves before the idle time comes: the request is cancelled, nothing is built. */
    @Test
    fun aRequestPendingWhenTheFocusMovesIsCancelled() {
        val state = startHeld(listOf(snes, next))
        // The rest ends without an idle run: the request waits in the scheduler.
        compose.mainClock.advanceTimeBy(WARM_DELAY_MS + 100)
        restFor(WARM_DELAY_MS + 100)
        compose.waitForIdle()
        assertTrue("a request is waiting for idle time", scheduler.requests.isNotEmpty())
        compose.runOnUiThread { state.dispatch(Action.Move(Direction.Right)) }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        idle()
        assertEquals("the cancelled request built a grid: $coversInIdle", emptyList<String>(), coversInIdle)
    }

    /** A drag or fling of the grid (no focus change) holds the building off for as long as it lasts, past the rest delay. */
    @Test
    fun aTouchScrollOfTheGridBuildsNothingWhileItLasts() {
        val state = startHeld(listOf(snes, next))
        compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, -200f))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        passTime(WARM_DELAY_MS * 3)
        assertEquals("a grid was built during a scroll: $coversInIdle", emptyList<String>(), coversInIdle)
        compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        passTime(WARM_DELAY_MS + 100)
        assertTrue("the grid was built once the scroll ended and the focus rested", coversInIdle.isNotEmpty())
        assertEquals(snes, state.focus.systemIndex)
    }

    @Test
    fun aGridBesideAShownGridIsBuiltAheadAndTheSwitchComposesNoCover() {
        val state = show(setOf(snes, next), listOf(Action.TapItem(Column.Systems, snes)))
        assertTrue("the neighbour grid was built in idle time: $coversInIdle", coversInIdle.isNotEmpty())
        coversInFrames.clear()
        step(state, Action.Move(Direction.Down))
        assertEquals(next, state.focus.systemIndex)
        assertEquals("the switch composed covers: $coversInFrames", emptyList<String>(), coversInFrames)
        compose.onAllNodes(hasTestTag(HomeTags.GRID), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aGridBesideAShownListIsBuiltAheadAndTheSwitchComposesNoCover() {
        val state = show(setOf(snes), listOf(Action.TapItem(Column.Systems, next)))
        assertTrue("the grid beside the list was built in idle time: $coversInIdle", coversInIdle.isNotEmpty())
        coversInFrames.clear()
        step(state, Action.Move(Direction.Up))
        assertEquals(snes, state.focus.systemIndex)
        assertEquals("the switch composed covers: $coversInFrames", emptyList<String>(), coversInFrames)
        compose.onAllNodes(hasTestTag(HomeTags.GRID), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun aHiddenGridIsNotDrawnTaggedOrFound() {
        show(setOf(snes), listOf(Action.TapItem(Column.Systems, next)))
        assertTrue("the grid was built", coversInIdle.isNotEmpty())
        assertEquals("a hidden grid drew: $drawn", emptyList<Pair<String, Int>>(), drawn)
        compose.onAllNodes(hasTestTag(HomeTags.GRID), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(HomeTags.cover(0)), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(HomeTags.GRID)).assertCountEquals(0)
    }

    @Test
    fun aHiddenGridKeepsTheShownGridsWidth() {
        show(setOf(snes, next), listOf(Action.TapItem(Column.Systems, snes)))
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("the grid's width", gridAreaPx, grid.size.width)
    }

    /** The details column stands one column gap past the grid (narrow screens: the third stop) whatever is built beside the grid. */
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun hiddenGridsMoveNothingBesideTheGrid() {
        show(setOf(snes, next), listOf(Action.TapItem(Column.Systems, snes), Action.Enter) + List(12) { Action.Move(Direction.Right) })
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val details = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("details column starts a gap past the grid", grid.right + gapPx, details.left, 1f)
    }
}
