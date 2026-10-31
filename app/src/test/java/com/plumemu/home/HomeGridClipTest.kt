package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.graphics.toPixelMap
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Direction
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.ProvideGridSize
import com.plumemu.coreui.theme.TextSize
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * A grid row scrolled partly off the grid's edge is cut at the grid's edge (seen on an AYN Thor:
 * the second line of an app's name showed under the header). The strip lets the focus ring into the 10 dp
 * bar gaps above and below the grid; tile content must not follow it. Measured on the pixels: scrolling
 * down the Apps grid, Recent, Favorites and a system's grid, and back up, the gap above and below the grid
 * holds nothing but the background at every step where a row is cut and the focused tile (whose ring may
 * be there) is not the one at that edge.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
abstract class HomeGridClipBase {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null

    private val isCover =
        SemanticsMatcher("a cover cell") { node ->
            node.config.any { (key, value) -> key.name == "TestTag" && (value as? String)?.let { it.startsWith("home_cover_") && it.removePrefix("home_cover_").all(Char::isDigit) } == true }
        }

    /** The composed grid cells by index: top and bottom in px. */
    private fun cells(): Map<Int, Pair<Float, Float>> =
        compose.onAllNodes(isCover, useUnmergedTree = true).fetchSemanticsNodes().associate { node ->
            node.config.first { it.key.name == "TestTag" }.value.toString().removePrefix("home_cover_").toInt() to (node.positionInRoot.y to node.positionInRoot.y + node.size.height)
        }

    /** Row cut at the grid's top, as messages (the grid's own top, px). */
    private fun topCuts(label: String): List<String> {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        val density = compose.density.density
        val top = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * density
        return cells().filterValues { it.first < top - 1 && it.second > top + 1 }.entries.distinctBy { it.value.first }.map { "$label: a row starts ${(top - it.value.first) / density} dp above the grid's top" }
    }

    /** Down all the way, then up all the way; at each rest the gaps are checked. Returns "cut at top" / "cut at bottom" step counts. */
    private fun walk(label: String, entryId: String): Pair<Int, Int> {  // steps scrolled off the first row, steps with a row cut at the bottom
        val strip = checkNotNull(state)
        val density = compose.density.density
        var cutTop = 0
        var cutBottom = 0
        var scrolledSteps = 0
        val problems = mutableListOf<String>()
        fun check(step: String) {
            compose.waitForIdle()
            compose.mainClock.advanceTimeBy(1000)
            compose.waitForIdle()
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val top = grid.top.value * density
            val bottom = grid.bottom.value * density
            val gap = PlumDimens.barGap.value * density
            val ring = PlumDimens.focusRingOutset.value * density * 2
            val nodes = compose.onAllNodes(isCover, useUnmergedTree = true).fetchSemanticsNodes()
            val covers = nodes.map { it.positionInRoot.y to it.positionInRoot.y + it.size.height }
            // Scrolled: the cell at the grid's top is not the first one.
            val atTop = nodes.filter { abs(it.positionInRoot.y - top) <= 1f }.map { node -> node.config.first { it.key.name == "TestTag" }.value.toString() }
            if (atTop.isNotEmpty() && HomeTags.cover(0) !in atTop) scrolledSteps++
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val left = (grid.left.value * density).toInt()
            val right = (grid.right.value * density).toInt() - 2
            val background = pixels[right + 1, (top - gap / 2).toInt()]

            fun dirty(y0: Int, y1: Int): Int {
                var n = 0
                for (y in y0 until y1) for (x in left until right) {
                    val p = pixels[x, y]
                    if (abs(p.red - background.red) > 0.04f || abs(p.green - background.green) > 0.04f || abs(p.blue - background.blue) > 0.04f) n++
                }
                return n
            }

            val focusTop = focusedBounds(strip, entryId)
            // A row cut at the grid's top shows its tail alone (a name's second line, a text tile's title).
            covers.filter { it.first < top - 1 && it.second > top + 1 }.forEach { problems += "$step: a row starts ${(top - it.first) / density} dp above the grid's top and is cut there" }
            val cutAbove = covers.any { it.first < top - 1 && it.second > top }
            val cutBelow = covers.any { it.second > bottom + 1 && it.first < bottom }
            if (cutAbove && (focusTop == null || focusTop.first - ring >= top)) {
                cutTop++
                val n = dirty((top - gap).toInt(), top.toInt())
                if (n > 0) problems += "$step: $n pixels of the gap above the grid are not background"
            }
            if (cutBelow && (focusTop == null || focusTop.second + ring <= bottom)) {
                cutBottom++
                val n = dirty(bottom.toInt(), (bottom + gap).toInt())
                if (n > 0) problems += "$step: $n pixels of the gap below the grid are not background"
            }
        }
        check("$label at the start")
        repeat(40) { i ->
            // A Down onto a row that is whole in the grid must not scroll it (the rows are whole by the rows that fit, not by px).
            val before = cells()
            val focusIndex = compose.runOnIdle { strip.focus.rowOf(entryId) }
            val gridBounds = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val columns = before[focusIndex]?.let { mine -> before.values.count { it.first == mine.first } } ?: 0
            val next = before[focusIndex + columns]
            compose.runOnIdle { strip.dispatch(Action.Move(Direction.Down)) }
            check("$label down $i")
            val after = cells()
            val wasWhole = next != null && next.second <= gridBounds.bottom.value * density + 0.5f && next.first >= gridBounds.top.value * density
            val still = before[focusIndex]?.first == after[focusIndex]?.first
            if (wasWhole && !still) problems += "$label down $i: the row below stood whole in the grid and the grid scrolled anyway"
            val moved = compose.runOnIdle { strip.focus.rowOf(entryId) }
            if (columns > 0 && moved != focusIndex && moved != focusIndex + columns) problems += "$label down $i: focus went from $focusIndex to $moved, not one row of $columns"
            val landed = after[moved]
            if (landed != null && (landed.first < gridBounds.top.value * density - 0.5f || landed.second > gridBounds.bottom.value * density + 0.5f)) problems += "$label down $i: the focused cell is not whole in the grid"
        }
        repeat(40) { i ->
            compose.runOnIdle { strip.dispatch(Action.Move(Direction.Up)) }
            check("$label up $i")
        }
        assertTrue("${problems.size} steps draw outside the grid:\n" + problems.take(3).joinToString("\n"), problems.isEmpty())
        return scrolledSteps to cutBottom
    }

    private fun focusedBounds(
        strip: StripState,
        entryId: String,
    ): Pair<Float, Float>? {
        val index = compose.runOnIdle { strip.focus.rowOf(entryId) }
        val node = compose.onAllNodes(androidx.compose.ui.test.hasTestTag(HomeTags.cover(index)), useUnmergedTree = true).fetchSemanticsNodes().firstOrNull() ?: return null
        return node.positionInRoot.y to node.positionInRoot.y + node.size.height
    }

    private var settings by mutableStateOf(PlumThemeSettings.Graphite)
    private var gridSize by mutableStateOf(GridSize.Small)

    /** [board] on the Thor at each text size (and grid size when [gridSizes]); the walk's cut rows counted over all of them. */
    private fun everySize(
        label: String,
        entryId: String,
        gridSizes: List<GridSize> = listOf(GridSize.Small),
        board: @Composable () -> Unit,
    ) {
        compose.setContent { PlumTheme(settings) { ProvideGridSize(gridSize) { key(settings, gridSize) { board() } } } }
        var top = 0
        var bottom = 0
        val problems = mutableListOf<String>()
        for (grid in gridSizes) {
            for (size in TextSize.entries) {
                gridSize = grid
                settings = PlumThemeSettings.Graphite.copy(textSize = size)
                compose.waitForIdle()
                try {
                    val (t, b) = walk("$label $size $grid", entryId)
                    top += t
                    bottom += b
                } catch (e: AssertionError) {
                    problems += e.message.orEmpty()
                }
            }
        }
        assertTrue("$label: ${problems.size} of the sizes draw outside the grid:\n" + problems.take(4).joinToString("\n"), problems.isEmpty())
        assertTrue("$label: the walks never scrolled ($top) or cut a row at the bottom ($bottom), so they prove nothing", top > 0 && bottom > 0)
    }

    @Test
    fun appsGridCutsAtItsEdges() = everySize("Apps", HomeContent.APPS_ID) { AppsGridBoard(focused = 0, onState = { state = it }) }

    private fun gamesGrid(
        label: String,
        entryIndex: Int,
        content: HomeContent,
        source: HomeArtSource,
        gridId: String,
    ) = everySize(label, content.entries[entryIndex].id, GridSize.entries) {
        HomeWithArt(source, gridActions(entryIndex, 0), content, gridIds = setOf(gridId), onState = { state = it })
    }

    @Test
    fun recentGridCutsAtItsEdges() = gamesGrid("Recent", 0, MixedGridLibrary, mixedGridSource(), MixedGridLibrary.entries[0].id)

    @Test
    fun favoritesGridCutsAtItsEdges() = gamesGrid("Favorites", 1, MixedGridLibrary, mixedGridSource(), MixedGridLibrary.entries[1].id)

    @Test
    fun systemGridCutsAtItsEdges() = gamesGrid("Super Nintendo", FakeHomeContent.BOARD_SNES_INDEX, CoverArtLibrary, coverArtSource(), "snes")

    /** A touch fling (and a drag) ends on a row: no row is left cut at the top. */
    private fun fling(
        label: String,
        board: @Composable () -> Unit,
    ) {
        compose.setContent { PlumTheme(settings) { ProvideGridSize(gridSize) { board() } } }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        var scrolled = false
        for (size in listOf(TextSize.Medium, TextSize.Large, TextSize.Largest)) {
            settings = PlumThemeSettings.Graphite.copy(textSize = size)
            compose.waitForIdle()
            for (gesture in 0 until 6) {
                compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).performTouchInput {
                    val x = center.x
                    if (gesture % 2 == 0) swipe(androidx.compose.ui.geometry.Offset(x, bottom - 20f), androidx.compose.ui.geometry.Offset(x, top + 20f), durationMillis = 60 + 40L * gesture) else swipe(androidx.compose.ui.geometry.Offset(x, top + 20f), androidx.compose.ui.geometry.Offset(x, bottom - 20f), durationMillis = 80)
                }
                problems += topCuts("$label $size fling $gesture")
                if (HomeTags.cover(0) !in cells().keys.map { HomeTags.cover(it) }) scrolled = true
            }
        }
        assertTrue("${problems.size} flings left a cut top row:\n" + problems.take(4).joinToString("\n"), problems.isEmpty())
        assertTrue("$label: no fling scrolled the grid, so the test proves nothing", scrolled)
    }

    @Test
    fun aFlingInAppsEndsOnARow() = fling("Apps") { AppsGridBoard(focused = 0, onState = { state = it }) }

    @Test
    fun aFlingInASystemGridEndsOnARow() = fling("Super Nintendo") { HomeWithArt(coverArtSource(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), CoverArtLibrary, gridIds = setOf("snes"), onState = { state = it }) }

    /** A text size change at the end of the list (the board is not rebuilt, only its sizes) realigns the grid to whole rows. */
    @Test
    fun aTextSizeChangeAtTheEndOfTheListRealignsToWholeRows() {
        compose.setContent { PlumTheme(settings) { AppsGridBoard(focused = AppsGridApps.lastIndex, onState = { state = it }) } }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        val order = listOf(TextSize.Medium, TextSize.Large, TextSize.Small, TextSize.Largest, TextSize.Medium, TextSize.Huge, TextSize.Small, TextSize.Large)
        for (size in order) {
            settings = PlumThemeSettings.Graphite.copy(textSize = size)
            problems += topCuts("end of Apps at $size")
            val density = compose.density.density
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val last = cells()[AppsGridApps.lastIndex]
            if (last == null || last.second > grid.bottom.value * density + 0.5f) problems += "end of Apps at $size: the last app is not whole in the grid ($last)"
        }
        assertTrue("${problems.size} problems:\n" + problems.take(4).joinToString("\n"), problems.isEmpty())
    }
}

/** The xhdpi reference density. */
@Config(qualifiers = "w837dp-h471dp-land-xhdpi")
class HomeGridClipXhdpiTest : HomeGridClipBase()

/** The Thor's top screen as it is: 1920x1080 px at 367 dpi, 837 x 471 dp. */
@Config(qualifiers = "w837dp-h471dp-land-367dpi")
class HomeGridClipThorDensityTest : HomeGridClipBase()
