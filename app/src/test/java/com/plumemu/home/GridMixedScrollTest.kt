package com.plumemu.home

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.ProvideGridSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Down and Up through a justified grid whose rows differ in height, on screen: after each move the focused cover's whole box is
 * inside the grid's window (the scroll keeps its row whole), and a move lands on the box whose x centre is nearest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class GridMixedScrollTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun show(size: GridSize): StripState {
        lateinit var state: StripState
        val aspects = listOf(1.4f, 0.9f, 1.0f, 0.7f, 1.5f, 1.4f, 1.0f, 0.9f, 0.7f, 1.5f, 1.0f)
        compose.setContent {
            PlumTheme {
                ProvideGridSize(size) {
                    HomeWithArt(rememberSource { realCoverSource(aspectOf = { _, i -> aspects[i % aspects.size] }) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it })
                }
            }
        }
        compose.waitForIdle()
        return state
    }

    private fun assertFocusedBoxWhole(
        what: String,
        state: StripState,
    ) {
        val focus = compose.runOnIdle { state.focus.row }
        assertTrue("$what: the focused game $focus is composed", compose.onAllNodesWithTag(HomeTags.cover(focus), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        val grid = bounds(HomeTags.GRID)
        val box = bounds(HomeTags.cover(focus))
        assertTrue("$what: game $focus top ${box.top} is inside the grid from ${grid.top}", box.top.value >= grid.top.value - TOLERANCE)
        assertTrue("$what: game $focus bottom ${box.bottom} is inside the grid to ${grid.bottom}", box.bottom.value <= grid.bottom.value + TOLERANCE)
    }

    private fun walk(size: GridSize) {
        val state = show(size)
        val heights = HashSet<Int>()
        // Down through the whole list, then Up back to the start: the focused box is whole after every move.
        repeat(40) { step ->
            val before = compose.runOnIdle { state.focus.row }
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Down)) }
            compose.waitForIdle()
            assertFocusedBoxWhole("$size Down $step from $before", state)
            heights += Math.round(bounds(HomeTags.cover(compose.runOnIdle { state.focus.row })).let { it.bottom.value - it.top.value })
        }
        assertTrue("rows of different heights were crossed: $heights", heights.size >= 2)
        repeat(40) { step ->
            compose.runOnIdle { state.dispatch(Action.Move(Direction.Up)) }
            compose.waitForIdle()
            assertFocusedBoxWhole("$size Up $step", state)
        }
        // Up keeps the column, so the focus is a box of the first row, which stands at the window's top.
        val top = bounds(HomeTags.cover(compose.runOnIdle { state.focus.row })).top.value - bounds(HomeTags.GRID).top.value
        assertTrue("back on the first row, whose top is ${top} dp under the window's", top < FIRST_ROW_TOP)
    }

    @Test
    fun theFocusedRowStaysWholeAtSmall() = walk(GridSize.Small)

    @Test
    fun theFocusedRowStaysWholeAtMedium() = walk(GridSize.Medium)

    @Test
    fun theFocusedRowStaysWholeAtLarge() = walk(GridSize.Large)

    private companion object {
        const val TOLERANCE = 0.75f

        /** How far under the window's top the first row's boxes stand (the grid's top gap and the glide's room). */
        const val FIRST_ROW_TOP = 40f
    }
}
