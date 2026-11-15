package com.plumemu.home

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The focus ring and glow of a cover in the grid's first or last column are drawn whole: the grids' row keeps [GRID_PAGE_ROOM_DP] past each
 * side, and the ring of an edge cover reaches as far outward as that of a cover in the middle (a row clipping it would shorten it).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w1024dp-h576dp-land-xhdpi")
class GridPageRoomTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private val board = FakeHomeContent.withArtwork()
    private val snes = FakeHomeContent.BOARD_SNES_INDEX
    private lateinit var state: StripState
    private var density = 1f

    private fun focus(index: Int) {
        compose.runOnUiThread { state.dispatch(Action.TapItem(Column.Games, index)) }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
    }

    /** How far, in px, the ring of cover [index] differs from the screen with the focus on [rested], outward past the cover's [left] or right edge, on its own row. */
    private fun reach(
        index: Int,
        rested: Int,
        left: Boolean,
    ): Int {
        focus(index)
        val cover = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val withFocus = compose.onRoot().captureToImage().toPixelMap()
        focus(rested)
        val without = compose.onRoot().captureToImage().toPixelMap()
        val y = cover.center.y.toInt()
        val edge = (if (left) cover.left else cover.right).toInt()
        var far = 0
        for (d in 0..200) {
            val x = if (left) edge - d else edge + d
            if (x !in 0 until withFocus.width) continue
            val a = withFocus[x, y]
            val b = without[x, y]
            if (Math.abs(a.red - b.red) + Math.abs(a.green - b.green) + Math.abs(a.blue - b.blue) > 0.004f) far = d
        }
        println("GRID_ROOM cover $index left=$left: ring reaches $far px past the cover")
        return far
    }

    private fun coverIndexes(): Map<Int, androidx.compose.ui.geometry.Rect> =
        (0 until 40)
            .filter { compose.onAllNodes(hasTestTag(HomeTags.cover(it)), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            .associateWith { compose.onNodeWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot }

    private fun check(left: Boolean) {
        compose.setContent {
            PlumTheme {
                density = androidx.compose.ui.platform.LocalDensity.current.density
                HomeAt(board, listOf(Action.TapItem(Column.Systems, snes), Action.Enter), gridIds = setOf(board.entries[snes].id), onState = { state = it })
            }
        }
        compose.waitForIdle()
        val covers = coverIndexes()
        val edge = if (left) covers.minBy { it.value.left }.key else covers.maxBy { it.value.right }.key
        // A cover standing in the middle of its row, and one in another row to compare against: the ring of the focused cover alone differs.
        val middle = covers.entries.sortedBy { it.value.left }[covers.size / 2].key
        val elsewhere = covers.entries.maxBy { Math.abs(it.value.center.y - covers.getValue(edge).center.y) }.key
        val atEdge = reach(edge, elsewhere, left)
        val inMiddle = reach(middle, elsewhere, left)
        // Measured: an edge cover's ring reaches about four fifths as far as a middle cover's (nothing beside it to overlap); a row clipping it at 8 dp reaches under two thirds.
        assertTrue("the edge cover's ring reaches $atEdge px outward, a middle cover's $inMiddle", atEdge * 4 >= inMiddle * 3)
    }

    @Test
    fun theRingOfTheLastColumnIsNotCutAtTheRowsSide() = check(left = false)

    @Test
    fun theRingOfTheFirstColumnIsNotCutAtTheRowsSide() = check(left = true)
}
