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
import com.plumemu.coreui.theme.FocusLift
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
 * The grid runs from bar to bar and its rows go through the gaps and under both bars (design/rules.md >
 * Lists that scroll); it never draws into a bar (seen on an AYN Thor: the second line of an app's name
 * showed under the header), and a row cut at a bar shows at least a third of itself or is left out whole.
 * Measured on the pixels: scrolling down the Apps grid, Recent, Favorites and a system's grid, and back up,
 * the top bar and the hint bar are the pixels they were at the start at every step where a row is cut, and a
 * cut row showing under a third of itself leaves nothing in the grid.
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

    /** The pixels of a row [cell] (px: top, bottom) in the grid's column span, inside the run [from]..[to], that are not the page. */
    private fun inkOf(
        pixels: androidx.compose.ui.graphics.PixelMap,
        from: Float,
        to: Float,
        ringColumns: ClosedFloatingPointRange<Float> = 1f..0f,
    ): Int {
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val left = (grid.left.value * compose.density.density).toInt()
        val right = (grid.right.value * compose.density.density).toInt() - 2
        val page = pixels[2, 2]
        val own = compose.barsOwnBounds()
        var n = 0
        for (y in from.toInt().coerceAtLeast(0) until to.toInt().coerceAtMost(pixels.height)) for (x in left until right) {
            // The focused cover's ring and glow reach past its cell: its columns are left out.
            if (x.toFloat() in ringColumns) continue
            // What a bar draws itself (a title's line box, a key's collar reaching up) shares the strip's edge rows.
            if (own.any { it.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())) }) continue
            val p = pixels[x, y]
            if (abs(p.red - page.red) > 0.04f || abs(p.green - page.green) > 0.04f || abs(p.blue - page.blue) > 0.04f) n++
        }
        return n
    }

    /** The pixel columns the focused cover's ring, lift and glow may reach (a lifted cover's collar grown, plus the halo). */
    private fun ringColumns(entryId: String): ClosedFloatingPointRange<Float> {
        val strip = checkNotNull(state)
        val index = compose.runOnIdle { strip.focus.rowOf(entryId) }
        val node = compose.onAllNodes(isCover, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull { it.config.first { c -> c.key.name == "TestTag" }.value.toString() == HomeTags.cover(index) } ?: return 1f..0f
        val reach = FocusLift.reach.value * compose.density.density
        return (node.positionInRoot.x - reach)..(node.positionInRoot.x + node.size.width + reach)
    }

    /** Rows cut at the grid's top that show a third of themselves or more are fine; one showing less must leave no ink (messages; the grid's own top, px). */
    private fun topCuts(
        label: String,
        entryId: String,
    ): List<String> {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(2000)
        compose.waitForIdle()
        val density = compose.density.density
        val top = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * density
        val sliver = cells().filterValues { it.first < top - 1 && it.second > top + 1 && it.second - top < (it.second - it.first) / 3f }.values.distinctBy { it.first }
        if (sliver.isEmpty()) return emptyList()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        return sliver.mapNotNull { if (inkOf(pixels, top, it.second, ringColumns(entryId)) > 0) "$label: a row ${(it.second - top) / density} dp of ${(it.second - it.first) / density} shows at the grid's top, under a third" else null }
    }

    /** Pixels of the bar tagged [tag] that are not the page and lie outside everything the bars draw themselves ([barsOwnBounds]). */
    private fun foreignInBar(
        pixels: androidx.compose.ui.graphics.PixelMap,
        tag: String,
    ): Int {
        val own = compose.barsOwnBounds()
        val page = pixels[2, 2]
        var n = 0
        val box = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        for (y in box.top.toInt() until box.bottom.toInt()) for (x in box.left.toInt() until box.right.toInt()) {
            if (own.any { it.contains(androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat())) }) continue
            val p = pixels[x, y]
            if (abs(p.red - page.red) > 0.04f || abs(p.green - page.green) > 0.04f || abs(p.blue - page.blue) > 0.04f) n++
        }
        return n
    }

    /** Down all the way, then up all the way; at each rest the bars and the cut rows are checked. Returns "cut at top" / "cut at bottom" step counts. */
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
            val nodes = compose.onAllNodes(isCover, useUnmergedTree = true).fetchSemanticsNodes()
            val covers = nodes.map { it.positionInRoot.y to it.positionInRoot.y + it.size.height }
            // Scrolled: the first cover has left the top of the grid's rows.
            val firstTop = nodes.firstOrNull { node -> node.config.first { it.key.name == "TestTag" }.value.toString() == HomeTags.cover(0) }?.positionInRoot?.y
            if (firstTop == null || firstTop < top - 1f) scrolledSteps++
            // Captured only in a step that cuts a row: the capture is most of this test's time.
            val pixels by lazy { compose.onRoot().captureToImage().toPixelMap() }
            val focusTop = focusedBounds(strip, entryId)
            val cutAbove = covers.any { it.first < top - 1 && it.second > top + 1 }
            val cutBelow = covers.any { it.second > bottom + 1 && it.first < bottom - 1 }
            if (cutAbove) cutTop++
            if (cutBelow) cutBottom++
            if (cutAbove || cutBelow) {
                // A cut row goes under the bar and no further: outside what the bar draws itself the bar is the page.
                for ((name, tag) in listOf("top bar" to HomeTags.TOP_BAR, "hint bar" to HomeTags.HINT_BAR)) {
                    val n = foreignInBar(pixels, tag)
                    if (n > 0) problems += "$step: $n pixels of the $name are not the page while a row runs under it"
                }
            }
            // A row cut at a bar that shows under a third of itself is left out, the focused one aside.
            for ((index, cover) in nodes.withIndex()) {
                val at = covers[index]
                val share = if (at.first < top - 1 && at.second > top + 1) (at.second - top) / (at.second - at.first) else if (at.second > bottom + 1 && at.first < bottom - 1) (bottom - at.first) / (at.second - at.first) else continue
                if (share >= 1f / 3f) continue
                val name = cover.config.first { it.key.name == "TestTag" }.value.toString()
                if (focusTop != null && focusTop.first == at.first) continue
                val ringColumns = ringColumns(entryId)
                val ink = if (at.first < top) inkOf(pixels, top, at.second, ringColumns) else inkOf(pixels, at.first, bottom, ringColumns)
                if (ink > 0) problems += "$step: $name shows $share of itself at a bar, under a third, yet $ink pixels of it stand there"
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
            // Whole and clear of the bottom fade: a row only partly under it is one the grid may scroll to.
            // The focus rim and glow of the row below reach past it: whole means clear of the fade with those too.
            val reach = FocusLift.reach.value * density
            val wasWhole = next != null && next.second + reach <= (gridBounds.bottom - PlumDimens.listFadeBand).value * density + 0.5f && next.first >= gridBounds.top.value * density
            val still = before[focusIndex]?.first == after[focusIndex]?.first
            if (wasWhole && !still) problems += "$label down $i: the row below stood whole in the grid and the grid scrolled anyway (${next?.first}..${next?.second}, reach $reach, grid to ${gridBounds.bottom.value * density}, before ${before[focusIndex]}, after ${after[focusIndex]})"
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
        needsCutRow: Boolean = true,
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
        assertTrue("$label: the walks never scrolled ($top) or cut a row at the bottom ($bottom), so they prove nothing", top > 0 && (bottom > 0 || !needsCutRow))
    }

    // The Apps grid's window is whole rows (AppsGridWholeCellsTest): no row is cut at the bottom, so only the top walk is proof.
    @Test
    fun appsGridCutsAtItsEdges() = everySize("Apps", HomeContent.APPS_ID, needsCutRow = false) { AppsGridBoard(focused = 0, onState = { state = it }) }

    private fun gamesGrid(
        label: String,
        entryIndex: Int,
        content: HomeContent,
        source: HomeArtSource,
        gridId: String,
        needsCutRow: Boolean = true,
    ) = everySize(label, content.entries[entryIndex].id, GridSize.entries, needsCutRow) {
        HomeWithArt(source, gridActions(entryIndex, 0), content, gridIds = setOf(gridId), onState = { state = it })
    }

    // Medium and Small show whole rows (the mixed board is short enough not to scroll at all there), so only Large can cut one; this board's Large rows end on the grid's edge.
    @Test
    fun recentGridCutsAtItsEdges() = gamesGrid("Recent", 0, MixedGridLibrary, mixedGridSource(), MixedGridLibrary.entries[0].id, needsCutRow = false)

    @Test
    fun favoritesGridCutsAtItsEdges() = gamesGrid("Favorites", 1, MixedGridLibrary, mixedGridSource(), MixedGridLibrary.entries[1].id, needsCutRow = false)

    @Test
    fun systemGridCutsAtItsEdges() = gamesGrid("Super Nintendo", FakeHomeContent.BOARD_SNES_INDEX, CoverArtLibrary, coverArtSource(), "snes")

    /** A touch fling (and a drag) leaves no sliver at the top: a cut row shows a third of itself or is left out. */
    private fun fling(
        label: String,
        entryId: String,
        board: @Composable () -> Unit,
    ) {
        compose.setContent { PlumTheme(settings) { ProvideGridSize(gridSize) { board() } } }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        var scrolled = false
        for (size in listOf(TextSize.Small, TextSize.Large, TextSize.Largest)) {
            settings = PlumThemeSettings.Graphite.copy(textSize = size)
            compose.waitForIdle()
            for (gesture in 0 until 6) {
                compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).performTouchInput {
                    val x = center.x
                    if (gesture % 2 == 0) swipe(androidx.compose.ui.geometry.Offset(x, bottom - 20f), androidx.compose.ui.geometry.Offset(x, top + 20f), durationMillis = 60 + 40L * gesture) else swipe(androidx.compose.ui.geometry.Offset(x, top + 20f), androidx.compose.ui.geometry.Offset(x, bottom - 20f), durationMillis = 80)
                }
                problems += topCuts("$label $size fling $gesture", entryId)
                if (HomeTags.cover(0) !in cells().keys.map { HomeTags.cover(it) }) scrolled = true
            }
        }
        assertTrue("${problems.size} flings left a sliver at the top:\n" + problems.take(4).joinToString("\n"), problems.isEmpty())
        assertTrue("$label: no fling scrolled the grid, so the test proves nothing", scrolled)
    }

    @Test
    fun aFlingInAppsLeavesNoSliver() = fling("Apps", HomeContent.APPS_ID) { AppsGridBoard(focused = 0, onState = { state = it }) }

    @Test
    fun aFlingInASystemGridLeavesNoSliver() = fling("Super Nintendo", "snes") { HomeWithArt(coverArtSource(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), CoverArtLibrary, gridIds = setOf("snes"), onState = { state = it }) }

    /** A text size change at the end of the list (the board is not rebuilt, only its sizes) realigns the grid: no sliver at the top, the last row whole above the fade. */
    @Test
    fun aTextSizeChangeAtTheEndOfTheListKeepsTheLastRowWhole() {
        compose.setContent { PlumTheme(settings) { AppsGridBoard(focused = AppsGridApps.lastIndex, onState = { state = it }) } }
        compose.waitForIdle()
        val problems = mutableListOf<String>()
        val order = listOf(TextSize.Small, TextSize.Large, TextSize.Small, TextSize.Largest, TextSize.Small, TextSize.Huge, TextSize.Small, TextSize.Large)
        for (size in order) {
            settings = PlumThemeSettings.Graphite.copy(textSize = size)
            problems += topCuts("end of Apps at $size", HomeContent.APPS_ID)
            val density = compose.density.density
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val last = cells()[AppsGridApps.lastIndex]
            // The last row of the list rests whole above the bottom fade.
            if (last == null || last.second > (grid.bottom - PlumDimens.listFadeBand).value * density + 0.5f) problems += "end of Apps at $size: the last app is not whole above the fade ($last)"
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
