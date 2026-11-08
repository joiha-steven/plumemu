package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Apps grid shows whole columns only, at rest: with the systems focused (its preview beside the column) no column is cut by the right margin or the
 * fade, and so with the games focused, at the top of the list and after scrolling to the last app. Rows run under the bars and may be cut there, by a third
 * or more of their height showing (a row showing less is left out: AppsGridCutRowTest). "Cut" is measured on the cell: its bounds clipped by every ancestor
 * against its own size.
 */
abstract class AppsGridWholeCellsBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    /** Two-line names in every row, the last visible one included. */
    private fun content(): HomeContent {
        val board = FakeHomeContent.board()
        val apps = board.entries.first { it.id == HomeContent.APPS_ID }
        val named = apps.rows.mapIndexed { i, row -> row.copy(title = "Nightly Emulator ${i + 1}") }
        return board.withEntries(board.entries.map { if (it === apps) it.copy(rows = named) else it })
    }

    private var textSize by mutableStateOf(TextSize.Small)

    private fun show(actions: List<Action>) {
        val appsIndex = FakeHomeContent.board().entries.indexOfFirst { it.id == HomeContent.APPS_ID }
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite.copy(textSize = textSize)) {
                HomeAt(content(), listOf(Action.TapItem(Column.Systems, appsIndex)) + actions, gridIds = setOf(HomeContent.APPS_ID))
            }
        }
        compose.waitForIdle()
    }

    private fun assertWhole(where: String) {
        val density = compose.density
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        val margin = with(density) { PlumDimens.stripMarginSide.toPx() }
        val cells = (0 until 60).mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { i to it } }
        var shown = 0
        for ((i, node) in cells) {
            val clipped = node.boundsInRoot
            val whole = Rect(node.positionInRoot, node.size.toSize())
            if (clipped.width < 1f || clipped.height < 1f) continue
            // A row at a bar showing under a third of itself is left out by the grid's mask (its bounds still stand).
            if (clipped.height < whole.height * (1f / 3f) - 1f) continue
            shown++
            val cut = whole.width - clipped.width > 1f
            assertTrue("$where: cell $i is cut: shows $clipped of $whole", !cut)
            assertTrue("$where: cell $i ends at ${clipped.right}, past the right margin ${rootWidth - margin}", clipped.right <= rootWidth - margin + 1f)
        }
        assertTrue("$where: no cell is shown", shown > 0)
    }

    protected fun preview() {
        show(emptyList())
        assertWhole("systems focused")
    }

    /**
     * With the systems focused the preview's last whole column ends at the right margin, the clock's and the hint bar's: no empty strip at any text size (the size sets the
     * column count). A grid that has room for one column only cannot be spread.
     */
    protected fun previewReachesTheMargin() {
        show(emptyList())
        val problems = mutableListOf<String>()
        for (size in TextSize.entries) {
            textSize = size
            compose.waitForIdle()
            val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
            val margin = with(compose.density) { PlumDimens.stripMarginSide.toPx() }
            val shown = (0 until 60).mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() }.filter { it.boundsInRoot.width >= 1f }
            val columns = shown.map { it.positionInRoot.x.toInt() }.distinct().size
            val right = shown.maxOf { it.boundsInRoot.right }
            val whole = shown.all { Rect(it.positionInRoot, it.size.toSize()).let { w -> w.width - it.boundsInRoot.width <= 1f } }
            if (columns >= 2 && kotlin.math.abs(right - (rootWidth - margin)) > 1f) problems += "${size.name}: $columns columns end at $right, the right margin is ${rootWidth - margin}"
            if (!whole) problems += "${size.name}: a column is cut"
            // Even spacing: the gaps between the columns' cells are equal.
            val lefts = shown.map { it.positionInRoot.x }.distinct().sorted()
            val gaps = lefts.zipWithNext { a, b -> b - a - shown.first().size.width }
            if (gaps.isNotEmpty() && gaps.max() - gaps.min() > 2f) problems += "${size.name}: uneven gaps $gaps"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** A cell drawn spread out takes the touch where it is drawn: a tap on the last shown column's cell focuses that very cell. */
    protected fun aSpreadCellTakesTheTouchWhereItIsDrawn() {
        val appsIndex = FakeHomeContent.board().entries.indexOfFirst { it.id == HomeContent.APPS_ID }
        var strip: StripState? = null
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                HomeAt(content(), listOf(Action.TapItem(Column.Systems, appsIndex)), gridIds = setOf(HomeContent.APPS_ID), onState = { strip = it }, onAction = { strip?.dispatch(it) })
            }
        }
        compose.waitForIdle()
        val cells = (0 until 60).mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { i to it } }.filter { it.second.boundsInRoot.width >= 1f }
        val last = cells.maxBy { it.second.boundsInRoot.right }
        compose.onNodeWithTag(HomeTags.cover(last.first), useUnmergedTree = true).performTouchInput { click(center) }
        compose.waitForIdle()
        val focus = compose.runOnIdle { strip!!.focus }
        assertTrue("a tap on cell ${last.first}, drawn at ${last.second.boundsInRoot}, focused ${focus.column} row ${focus.row}", focus.column == Column.Games && focus.row == last.first)
    }

    /** While a finger drags the strip the spread follows it: the gaps stay even and the spread closes with it, never a jump. */
    protected fun theSpreadFollowsTheStrip() {
        show(emptyList())
        fun cells() = (0 until 60).mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { i to it.positionInRoot.x } }
        val before = cells().filter { (i, _) -> i < 3 }
        val drag = with(compose.density) { 40.dp.toPx() }
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput {
            down(center)
            moveBy(Offset(-drag, 0f))
        }
        compose.waitForIdle()
        val during = cells().filter { (i, _) -> i < 3 }
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { up() }
        val moved = before.indices.map { before[it].second - during[it].second }
        val gapsBefore = before.zipWithNext { a, b -> b.second - a.second }
        val gapsDuring = during.zipWithNext { a, b -> b.second - a.second }
        assertTrue("the first cells moved $moved px for a drag of $drag", moved[0] > 1f)
        assertTrue("the spread did not close while dragging: gaps $gapsBefore then $gapsDuring", gapsDuring.zip(gapsBefore).all { (d, b) -> d < b - 0.5f })
        assertTrue("uneven gaps while dragging: $gapsDuring", gapsDuring.max() - gapsDuring.min() <= 2f)
    }

    /**
     * The focus rim stands on the cell it is drawn for while the strip is dragged from the games back toward the systems (the preview's spread opens again): the ring's
     * centre is the focused cell's, not where the unspread grid would have put it.
     */
    protected fun theRingStandsOnItsSpreadCell() {
        val appsIndex = FakeHomeContent.board().entries.indexOfFirst { it.id == HomeContent.APPS_ID }
        var rim = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent {
            PlumTheme(PlumThemeSettings.Graphite) {
                rim = com.plumemu.coreui.theme.FocusLift.rimColor(PlumTheme.colors.isDark, PlumTheme.colors.text).compositeOver(PlumTheme.colors.bg)
                HomeAt(content(), listOf(Action.TapItem(Column.Systems, appsIndex), Action.Enter, Action.TapItem(Column.Games, 3)), gridIds = setOf(HomeContent.APPS_ID))
            }
        }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1000)
        // Where the ring's left edge stands against the cell's left edge, at rest and with the strip dragged: the same.
        fun ringLeftOfCell(): Float {
            val cell = compose.onAllNodesWithTag(HomeTags.cover(3), useUnmergedTree = true).fetchSemanticsNodes().first()
            val grid = compose.onAllNodesWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNodes().first()
            val image = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).captureToImage().toPixelMap()
            val xs = mutableListOf<Int>()
            for (y in 0 until image.height) for (x in 0 until image.width) {
                val c = image[x, y]
                if (kotlin.math.abs(c.red - rim.red) + kotlin.math.abs(c.green - rim.green) + kotlin.math.abs(c.blue - rim.blue) < 0.06f) xs += x
            }
            assertTrue("no rim pixels found", xs.size > 20)
            return grid.positionInRoot.x + xs.min() - cell.positionInRoot.x
        }
        val resting = ringLeftOfCell()
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput {
            down(center)
            moveBy(Offset(with(compose.density) { 120.dp.toPx() }, 0f))
        }
        compose.waitForIdle()
        val dragged = ringLeftOfCell()
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { up() }
        assertTrue("the ring's left edge is $resting px from its cell's at rest and $dragged px while the strip is dragged", kotlin.math.abs(dragged - resting) <= 3f)
    }

    /** A finger drags the strip toward the games, still holding: the column changes only on release, yet the grid is then not clipped to the preview. */
    protected fun draggedTowardTheGames() {
        show(emptyList())
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput {
            down(center)
            moveBy(Offset(-with(compose.density) { 120.dp.toPx() }, 0f))
        }
        compose.waitForIdle()
        val rootWidth = compose.onRoot().fetchSemanticsNode().size.width
        val grid = compose.onAllNodesWithTag(HomeTags.GRID, useUnmergedTree = true).fetchSemanticsNodes().first()
        val gridTop = grid.positionInRoot.y
        val gridBottom = gridTop + grid.size.height
        val blank =
            (0 until 60).mapNotNull { i -> compose.onAllNodesWithTag(HomeTags.cover(i), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()?.let { i to it } }.filter { (_, node) ->
                val whole = Rect(node.positionInRoot, node.size.toSize())
                val clipped = node.boundsInRoot
                whole.left >= 0f && whole.right <= rootWidth && whole.top >= gridTop - 1f && whole.bottom <= gridBottom + 1f && (clipped.width < 1f || clipped.height < 1f)
            }
        compose.onNodeWithTag(HomeTags.STRIP).performTouchInput { up() }
        assertTrue("cells ${blank.map { it.first }} stand on the screen mid-drag but are clipped away (blank on the right)", blank.isEmpty())
    }

    protected fun gamesAtTop() {
        show(listOf(Action.Enter))
        assertWhole("games focused, top")
    }

    protected fun gamesAtEnd() {
        show(listOf(Action.Enter, Action.TapItem(Column.Games, 40)))
        assertWhole("games focused, last app")
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w768dp-h432dp-land-xhdpi")
class AppsGridWholeCells768Test : AppsGridWholeCellsBase() {
    @Test fun preview768() = preview()

    @Test fun margin768() = previewReachesTheMargin()

    @Test fun touch768() = aSpreadCellTakesTheTouchWhereItIsDrawn()

    @Test fun follow768() = theSpreadFollowsTheStrip()

    @Test fun ring768() = theRingStandsOnItsSpreadCell()

    @Test fun top768() = gamesAtTop()

    @Test fun dragged768() = draggedTowardTheGames()

    @Test fun end768() = gamesAtEnd()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w576dp-h432dp-land-xhdpi")
class AppsGridWholeCells576Test : AppsGridWholeCellsBase() {
    @Test fun preview576() = preview()

    @Test fun margin576() = previewReachesTheMargin()

    @Test fun top576() = gamesAtTop()

    @Test fun end576() = gamesAtEnd()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w832dp-h468dp-land-xhdpi")
class AppsGridWholeCellsThorTest : AppsGridWholeCellsBase() {
    @Test fun previewThor() = preview()

    @Test fun marginThor() = previewReachesTheMargin()

    @Test fun topThor() = gamesAtTop()

    @Test fun endThor() = gamesAtEnd()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w640dp-h480dp-land-xhdpi")
class AppsGridWholeCells640Test : AppsGridWholeCellsBase() {
    @Test fun preview640() = preview()

    @Test fun margin640() = previewReachesTheMargin()

    @Test fun top640() = gamesAtTop()
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = "w960dp-h540dp-land-xhdpi")
class AppsGridWholeCells960Test : AppsGridWholeCellsBase() {
    @Test fun preview960() = preview()

    @Test fun margin960() = previewReachesTheMargin()

    @Test fun top960() = gamesAtTop()
}
