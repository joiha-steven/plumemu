package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * Packed cover grids measured on screen: every box at the place and size the justified packing gives (a row's boxes share its height, the gaps are the board's, a full row spans the grid), no box passes the grid, boxes of different shapes have different widths, and
 * Up and Down land on the box whose centre is nearest. Super Nintendo (landscape and portrait boxes, a poster) and
 * Favorites (landscape, portrait-ish and square), at the Pocket 5's 768, the Nova's 576 and the Pocket 6's 832.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomePackedGridBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** The boxes composed now, by game index. */
    private fun shown(count: Int): Map<Int, DpRect> =
        (0 until count).filter { compose.onAllNodesWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.associateWith { bounds(HomeTags.cover(it)) }

    private fun rowsOf(boxes: Map<Int, DpRect>): List<List<Pair<Int, DpRect>>> =
        boxes.entries.map { it.key to it.value }.groupBy { Math.round(it.second.top.value * 2) }.toSortedMap().values.map { row -> row.sortedBy { it.second.left.value } }

    private fun assertPacked(
        label: String,
        count: Int,
        layout: PlumLayout,
        shape: com.plumemu.coreui.theme.CoverShape,
        aspectOf: (Int) -> Float,
    ) {
        val grid = bounds(HomeTags.GRID)
        val boxes = shown(count)
        assertTrue("$label: boxes are shown", boxes.size >= 3)
        val rows = rowsOf(boxes)
        val gap = layout.gridGap(shape)
        // The requirement changed: rows have their own heights (each is scaled to span the grid), so the expected boxes come from
        // the packing worked out in PackedExpect, not from one row height and widths of the picture's shape.
        val expected = expectedPacking((0 until count).map(aspectOf), layout, shape)
        for (row in rows) {
            for ((index, box) in row) {
                assertEquals("$label: box $index height", expected.heights[expected.rowOf(index)], (box.bottom - box.top).value, TOLERANCE)
                assertEquals("$label: box $index width", expected.widths[index], (box.right - box.left).value, TOLERANCE)
                assertEquals("$label: box $index left", grid.left.value + expected.lefts[index], box.left.value, TOLERANCE)
                assertEquals("$label: box $index on its row's line", row.first().second.top.value, box.top.value, TOLERANCE)
                assertTrue("$label: box $index inside the grid", box.left >= grid.left - TOL_DP && box.right <= grid.right + TOL_DP)
            }
            // Equal gaps, none under the board's, the last box of a full row on the grid's right edge.
            val gaps = row.zipWithNext { (a, left), (b, right) ->
                assertEquals("$label: $b follows $a in order", a + 1, b)
                (right.left - left.right).value
            }
            gaps.forEach {
                assertEquals("$label: gaps in a row are equal", gaps[0], it, TOLERANCE)
                assertTrue("$label: gap $it under the board's ${gap.value}", it >= gap.value - TOLERANCE)
            }
            if (row !== rows.last() && row.size > 1) {
                assertEquals("$label: row ends at the right edge", (grid.right).value, row.last().second.right.value, TOLERANCE)
            }
            assertEquals("$label: row starts at the left", grid.left.value, row.first().second.left.value, TOLERANCE)
        }
        for ((above, below) in rows.zipWithNext()) {
            assertEquals("$label: row gap", layout.gridRowGap(shape).value, (below.first().second.top - above.first().second.bottom).value, TOLERANCE)
        }
        assertTrue("$label: more than one row", rows.size > 1)
        assertTrue("$label: boxes of different shapes have different widths", boxes.values.map { Math.round((it.right - it.left).value) }.toSet().size > 1)
    }

    private fun showFavorites(size: GridSize = GridSize.Medium): StripState {
        var state: StripState? = null
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme {
                layout = PlumTheme.layout
                PackedFavoritesBoard(focused = 0, gridSize = size) { state = it }
            }
        }
        compose.waitForIdle()
        packedLayout = layout
        return checkNotNull(state)
    }

    private var packedLayout: PlumLayout? = null

    private fun favorites() = assertPacked("favorites at ${packedLayout?.width}", PackedFavoritesLibrary.entries[1].count, checkNotNull(packedLayout), MixedGridShape) { i -> FAVORITE_PICKS[i].let { (id, at) -> realAspect(id, at) } }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFavoritesIsPacked() {
        showFavorites()
        favorites()
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFavoritesIsPacked() {
        showFavorites()
        favorites()
    }

    @Test
    @Config(qualifiers = POCKET6)
    fun pocket6FavoritesIsPacked() {
        showFavorites()
        favorites()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun everyGridSizeStaysPackedAndKeepsTheFocusedGame() {
        var size by mutableStateOf(GridSize.Small)
        var state: StripState? = null
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme {
                com.plumemu.coreui.theme.ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    androidx.compose.runtime.key(size) { PackedFavoritesBoard(focused = 5, gridSize = size) { state = it } }
                }
            }
        }
        for (each in GridSize.entries) {
            size = each
            compose.waitForIdle()
            val focused = compose.runOnIdle { checkNotNull(state).focus.row }
            assertEquals("the focused game stays at $each", 5, focused)
            packedLayout = layout
            favorites()
            assertTrue("the focused game's box is shown at $each", shown(16).containsKey(5))
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun largestTextRepacksAndKeepsTheFocus() {
        var state: StripState? = null
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme(com.plumemu.coreui.theme.PlumThemeSettings().copy(textSize = TextSize.Largest)) {
                layout = PlumTheme.layout
                PackedFavoritesBoard(focused = 7) { state = it }
            }
        }
        compose.waitForIdle()
        assertEquals(7, compose.runOnIdle { checkNotNull(state).focus.row })
        packedLayout = layout
        favorites()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun superNintendoIsPackedWithPortraitBoxesNarrowerThanLandscapeOnes() {
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme {
                layout = PlumTheme.layout
                PackedSnesBoard(focused = 0)
            }
        }
        compose.waitForIdle()
        assertPacked("snes", 20, checkNotNull(layout), com.plumemu.coreui.theme.CoverShape.Landscape) { i -> if (i == PACKED_SNES_POSTER) com.plumemu.coreui.theme.CoverShape.Landscape.widthOverHeight else packedSnesAspect("snes", i) }
        val landscape = bounds(HomeTags.cover(0))
        val portrait = bounds(HomeTags.cover(1))
        assertTrue("a Japanese portrait box is narrower than a US box", (portrait.right - portrait.left) < (landscape.right - landscape.left))
    }

    @Test
    @Config(qualifiers = WIDE)
    fun upAndDownLandOnTheNearestCentre() {
        val state = showFavorites()
        var checked = 0
        for (start in 0 until 6) {
            compose.runOnIdle {
                state.focusRow(start)
                state.dispatch(Action.Move(Direction.Down))
            }
            compose.waitForIdle()
            val boxes = shown(PackedFavoritesLibrary.entries[1].count)
            val rows = rowsOf(boxes)
            val from = boxes.getValue(start)
            val fromRow = rows.indexOfFirst { row -> row.any { it.first == start } }
            val target = compose.runOnIdle { state.focus.row }
            if (fromRow == rows.size - 1) {
                assertEquals("no row below: stays", start, target)
                continue
            }
            val centre = (from.left.value + from.right.value) / 2f
            val best = rows[fromRow + 1].minOf { abs((it.second.left.value + it.second.right.value) / 2f - centre) }
            val got = boxes.getValue(target)
            assertTrue("Down from $start lands in the next row", rows[fromRow + 1].any { it.first == target })
            assertEquals("Down from $start lands on the nearest centre", best, abs((got.left.value + got.right.value) / 2f - centre), TOLERANCE)
            checked++
        }
        assertTrue("some Down moves were checked", checked >= 3)
        assertEquals(Column.Games, compose.runOnIdle { state.focus.column })
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val POCKET6 = "w832dp-h468dp-land-xhdpi"
        const val TOLERANCE = 0.75f
        const val WIDTH_TOLERANCE = 0.6f
        val TOL_DP = androidx.compose.ui.unit.Dp(0.75f)
    }
}
