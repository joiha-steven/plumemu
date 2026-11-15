package com.plumemu.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreui.theme.PlumDimens
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * While a system is shown as a grid, the list pages beside it are still built ahead (a hidden games list in the grid's slot:
 * measured, never placed, out of semantics), so a step from the grid to a list system places a built page instead of
 * composing a pages row and a dozen rows in one frame (44-48 ms on the Thor).
 */
@OptIn(InternalComposeTracingApi::class, ExperimentalFoundationApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1024dp-h576dp-land-xhdpi")
class HiddenListBesideGridTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val built = mutableListOf<String>()

    /** Every page composition, idle time included. */
    private val pagesBuilt = mutableListOf<String>()
    private var inIdle = false
    private val scheduler = ManualPagesPrefetch()
    private val board = FakeHomeContent.withArtwork()
    private val gridIndex = FakeHomeContent.BOARD_SNES_INDEX

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
                    if (name == "com.plumemu.home.EntryGames") pagesBuilt += name
                    if (!inIdle && (name == "com.plumemu.home.EntryGames" || name == "com.plumemu.home.GamesListItem")) built += name
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

    private var gridAreaPx = 0
    private var gapPx = 0f

    private fun show(
        actions: List<Action> = listOf(Action.TapItem(Column.Systems, gridIndex)),
        settings: PlumThemeSettings = PlumThemeSettings.Default,
    ): StripState {
        lateinit var state: StripState
        gamesPagesStateMade = scheduler::install
        compose.setContent {
            PlumTheme(settings) {
                with(LocalDensity.current) {
                    gridAreaPx = PlumTheme.layout.gridAreaWidth.roundToPx()
                    gapPx = PlumDimens.columnGap.toPx()
                }
                HomeAt(board, actions, gridIds = setOf(board.entries[gridIndex].id), onState = { state = it })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            inIdle = true
            scheduler.runIdle()
            inIdle = false
        }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        return state
    }

    @Test
    fun listPagesBesideAShownGridAreBuiltAheadAndTheSwitchBuildsNoRow() {
        val state = show()
        // The two list neighbours are built; the grid's own page emits no rows (a third EntryGames would be its).
        assertEquals("pages built beside the grid: $pagesBuilt", 2, pagesBuilt.size)
        built.clear()
        compose.runOnUiThread { state.dispatch(Action.Move(Direction.Down)) }
        repeat(2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
        assertEquals(gridIndex + 1, state.focus.systemIndex)
        assertEquals("the switch to the list built: $built", emptyList<String>(), built)
    }

    @Test
    fun theHiddenListIsNotInTheSemanticsAndTakesNoRoom() {
        show()
        compose.onAllNodes(hasTestTag(HomeTags.GAMES_PAGE)).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(HomeTags.game(0)), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(HomeTags.GAMES), useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodes(hasTestTag(HomeTags.GRID), useUnmergedTree = true).assertCountEquals(1)
        // The grid keeps the layout's grid area. This measures the grid itself, not the slot around it: a slot widened by the
        // hidden list is caught by theHiddenListMovesNothingBesideTheGrid (narrow screens, where the details column follows the grid).
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("the grid's width", gridAreaPx, grid.size.width)
    }

    /** The details column stands one column gap past the grid (narrow screens: the third stop), as with no list beside the grid. */
    @Test
    @Config(qualifiers = "w576dp-h432dp-land-xhdpi")
    fun theHiddenListMovesNothingBesideTheGrid() {
        show(listOf(Action.TapItem(Column.Systems, gridIndex), Action.Enter) + List(12) { Action.Move(Direction.Right) })
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val details = compose.onNodeWithTag(HomeTags.DETAILS, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals("details column starts a gap past the grid", grid.right + gapPx, details.left, 1f)
    }

    private companion object {
        const val FRAMES = 8
    }

    /** Games column focused, R1: the dimmest of the frames after the step, as a share of the new list's brightness at rest (1 = no fade). */
    private fun dimmestShare(from: Int): Float {
        val state = show(listOf(Action.TapItem(Column.Systems, from), Action.Enter))
        // The list's area, from the list at rest (measured after the settle; the same area is read in the first frame).
        var area = androidx.compose.ui.geometry.Rect.Zero
        fun light(): Float {
            val px = compose.onRoot().captureToImage().toPixelMap()
            var sum = 0f
            for (x in area.left.toInt() until area.right.toInt() step 2) for (y in area.top.toInt() until area.bottom.toInt() step 2) px[x, y].let { sum += it.red + it.green + it.blue }
            return sum
        }
        compose.runOnUiThread { state.dispatch(Action.NextSystem) }
        val frames = (1..FRAMES).map {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onRoot().captureToImage().toPixelMap()
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        area = compose.onNodeWithTag(HomeTags.GAMES, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rest = light()
        val shares = frames.map { image ->
            var sum = 0f
            for (x in area.left.toInt() until area.right.toInt() step 2) for (y in area.top.toInt() until area.bottom.toInt() step 2) image[x, y].let { sum += it.red + it.green + it.blue }
            sum / rest
        }
        // The first frame still shows the grid; the fade, if any, is in the frames after it.
        val dimmest = shares.drop(1).min()
        return dimmest
    }

    @Test
    fun aSwitchFromAGridToAListShowsItAtOnceWithNoFade() {
        val toList = dimmestShare(gridIndex)
        assertTrue("grid to list fades: $toList", toList > 0.97f)
    }

    @Test
    fun listToListStillFades() {
        val toList = dimmestShare(gridIndex + 1)
        assertTrue("list to list does not fade: $toList", toList < 0.9f)
    }
}
