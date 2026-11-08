package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.AccentChoice
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.FocusLift
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.PlumThemeSettings
import com.plumemu.coreui.theme.Rgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The focused cover of the grid is easy to see (docs/design/rules.md > Focus and current; `FocusLift`): it grows 3.5 % about a pivot that keeps it
 * inside the grid (right column its right edge, left column its left edge, top row its top edge, else its centre), wears a 2 dp rim glued on its
 * frame, near-white on a dark page and the ink on a light one, with a tight glow, and nothing else moves. Measured from pixels and layout bounds at
 * 576x432, 640x480, 768x432, 832x468 and 853x480, Graphite and Paper, on a Super Nintendo grid (one shape) and on Recent (mixed shapes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = SIZE_640)
abstract class GridCoverFocusBase {
    protected val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    protected var state: StripState? = null
    protected var accent = Color.Unspecified
    protected var background = Color.Unspecified
    protected var dark = true
    protected var ink = Color.Unspecified
    protected var gridSize by mutableStateOf(GridSize.Default)

    protected fun show(
        settings: PlumThemeSettings,
        entry: Int = FakeHomeContent.BOARD_SNES_INDEX,
        id: String = "snes",
    ) {
        compose.setContent {
            PlumTheme(settings.copy(gridSize = gridSize)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                dark = PlumTheme.colors.isDark
                ink = PlumTheme.colors.text
                key(gridSize) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(entry, 0), gridIds = setOf(id), onState = { state = it }) }
            }
        }
        compose.waitForIdle()
    }

    protected val d get() = compose.density.density

    protected fun focus(index: Int) {
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, index)) }
        compose.mainClock.advanceTimeBy(1000)
        compose.waitForIdle()
        // The lifted cover is drawn from the layer its cell records while drawing (a draw-phase write): apply it and draw the frame, as a device's next frame does.
        repeat(2) {
            compose.runOnIdle { Snapshot.sendApplyNotifications() }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
        }
    }

    protected fun cell(index: Int): Rect {
        val b = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
    }

    /** The collar as laid out (px): the picture's bounds plus the lip and groove. */
    protected fun collar(index: Int): Rect {
        val node = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode()
        // A picture's bounds, else the drawn box front fitted to the system's shape (mixed grids).
        val picture = node.children.first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE || it.value == HomeTags.COVER_OWN_SHAPE } }
        val box = Rect(picture.positionInRoot, Size(picture.size.width.toFloat(), picture.size.height.toFloat()))
        val c = CollarSize.Cover
        return Rect(box.left - c.sidePadding.value * d, box.top - c.topPadding.value * d, box.right + c.sidePadding.value * d, box.bottom + c.bottomPadding.value * d)
    }

    protected fun pixels(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    protected fun near(
        a: Color,
        b: Color,
        tolerance: Float = 0.07f,
    ) = abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    /** The rim's colour as it shows on the page. */
    protected fun rimOnPage(): Color = FocusLift.rimColor(dark, ink).compositeOver(background)

    /** The box of the rim-coloured pixels around [around] (px): the rim's outer edge, the outermost such pixel there is. */
    protected fun rimBox(
        shot: PixelMap,
        around: Rect,
    ): Rect? {
        // Room for the lift's growth and the rim, inside the gaps.
        val room = (FocusLift.rimWidth.value + 6f) * d
        val growX = around.width * (FocusLift.SCALE - 1f)
        val growY = around.height * (FocusLift.SCALE - 1f)
        val l = max(0, (around.left - room - growX).roundToInt())
        val t = max(0, (around.top - room - growY).roundToInt())
        val r = min(shot.width - 1, (around.right + room + growX).roundToInt())
        val b = min(shot.height - 1, (around.bottom + room + growY).roundToInt())
        val rim = rimOnPage()
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in t..b) for (x in l..r) {
            if (!near(shot[x, y], rim)) continue
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
        }
        return if (right < 0) null else Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }

    protected fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    protected fun distance(
        a: Color,
        b: Color,
    ) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    /** Which edges of the grid cover [index] stands on: first and last of its row, the grid's first row. */
    internal fun edgesOf(index: Int): LiftEdges {
        val me = cell(index)
        // The covers composed now (a scrolled grid has dropped the first rows).
        val shown = (maxOf(0, index - MAX_SHOWN)..index + MAX_SHOWN).filter { exists(it) }
        val row = shown.filter { cell(it).top == me.top }
        return LiftEdges(atLeft = index == row.first(), atRight = index == row.last(), atTop = exists(0) && cell(0).top == me.top)
    }

    /** Where the rim's outer edge must be (px) for cover [index] whose collar rests at [rest]: the collar grown about its pivot, plus the rim. */
    protected fun expectedRim(
        index: Int,
        rest: Rect,
    ): Rect {
        val edges = edgesOf(index)
        val pivot = FocusLift.pivot(rest, edges.atLeft, edges.atRight, edges.atTop)
        val s = FocusLift.SCALE
        val rim = FocusLift.rimWidth.value * d

        fun grown(
            v: Float,
            about: Float,
        ) = about + (v - about) * s
        return Rect(grown(rest.left, pivot.x) - rim, grown(rest.top, pivot.y) - rim, grown(rest.right, pivot.x) + rim, grown(rest.bottom, pivot.y) + rim)
    }

    /** The rim of the focused cover [index] (px), or a failure that says what was found. */
    protected fun drawnRim(
        label: String,
        index: Int,
    ): Rect = checkNotNull(rimBox(pixels(), collar(index))) { "$label: no rim is drawn round cover $index" }

    protected fun assertRimMatches(
        label: String,
        index: Int,
    ) {
        val rest = collar(index)
        val drawn = drawnRim(label, index)
        val want = expectedRim(index, rest)
        val tol = 2f * d
        assertEquals("$label: rim left", want.left, drawn.left, tol)
        assertEquals("$label: rim top", want.top, drawn.top, tol)
        assertEquals("$label: rim right", want.right, drawn.right, tol)
        assertEquals("$label: rim bottom", want.bottom, drawn.bottom, tol)
    }

    protected fun assertOwnSize(
        label: String,
        index: Int,
        neighbour: Int,
    ) {
        focus(neighbour)
        val others = (0..3).associateWith { cell(it) }
        focus(index)
        // Layout is untouched: every cover's cell stands where it stood with the focus on the neighbour, and the focused cover grows only in the draw.
        for ((i, before) in others) assertEquals("$label: cell $i did not move or resize", before, cell(i))
        assertRimMatches(label, index)
    }

    protected fun assertRimContrast(label: String) {
        focus(0)
        val drawn = drawnRim(label, 0)
        val shot = pixels()
        val onRim = shot[drawn.center.x.roundToInt(), (drawn.top + 1.5f * d).roundToInt()]
        assertTrue("$label: the rim pixel is the rim colour ($onRim vs ${rimOnPage()})", near(onRim, rimOnPage()))
        assertEquals("$label: near-white on a dark page, the ink on a light one", dark, onRim.luminance() > 0.5f)
        assertTrue("$label: rim against the page colour ${contrast(onRim, background)}", contrast(onRim, background) >= 3f)
        assertTrue("$label: no accent colour in the focus (rim $onRim, accent $accent)", !near(onRim, accent, 0.1f))
    }

    /** The glow is tight: just outside the rim the page is tinted, past a few sigmas it is the page again. */
    protected fun assertTightGlow(label: String) {
        focus(0)
        val drawn = drawnRim(label, 0)
        val shot = pixels()
        val x = drawn.center.x.roundToInt()
        val glow = shot[x, (drawn.top - 1f * d).roundToInt()]
        assertTrue("$label: a glow just outside the rim ($glow vs $background)", distance(glow, background) > 0.01f)
        val past = shot[x, (drawn.top - (FocusLift.reach.value + 2f) * d).roundToInt()]
        assertTrue("$label: the pixel past the glow is the page ($past vs $background)", near(past, background, 0.03f))
    }

    protected fun exists(index: Int) = compose.onAllNodesWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The right edge (px) of the column left of the grid: the letter rail, else the systems column; 0 when there is none. */
    protected fun leftColumnRight(): Float =
        listOf(HomeTags.RAIL, HomeTags.SYSTEMS).maxOf { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().maxOfOrNull { it.boundsInRoot.right } ?: 0f
        }

    /**
     * The focused cover and its mark stay in their own cell and gaps: the cover between two others is drawn the same, pixel for
     * pixel, whichever side the focus is on, its rounded corners left out (the page shows there, and the halo over it).
     */
    /** The covers worth testing: both ends of the first row and of the bottom row that shows whole, and the last game. */
    protected fun edgeIndices(last: Int): List<Int> {
        focus(0)
        val hints = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * d
        val shown = (0..last).takeWhile { it < 80 && exists(it) }
        val firstRow = shown.filter { cell(it).top == cell(0).top }
        val whole = shown.filter { cell(it).bottom <= hints }
        val bottomTop = whole.maxOf { cell(it).top }
        val bottomRow = whole.filter { cell(it).top == bottomTop }
        return listOf(firstRow.first(), firstRow.last(), bottomRow.first(), bottomRow.last(), last).distinct()
    }

    /**
     * The lifted cover at the grid's edges, at every Size step: the rim is whole and where the pivot puts it (an edge column grows inward, the top
     * row down), it is clear of the top bar, the hint bar, the left column (letter rail or systems) and the screen edge, and on each side its pixel
     * is the rim colour with contrast against the page.
     */
    protected fun assertWholeAtEdges(
        label: String,
        entry: Int = FakeHomeContent.BOARD_SNES_INDEX,
    ) {
        val last = FakeHomeContent.withArtwork().entries[entry].rows.lastIndex
        for (size in GridSize.entries) {
            gridSize = size
            compose.waitForIdle()
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val top = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value * d
            val hints = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * d
            val minLeft = max(leftColumnRight(), (grid.left.value - PlumDimens.stripMarginSide.value) * d)
            // The rim may enter the right margin (24 dp) but stays clear of the screen edge by 4 dp.
            val maxRight = (grid.right.value + PlumDimens.stripMarginSide.value - 4f) * d
            for (index in edgeIndices(last)) {
                focus(index)
                val at = "$label $size cover $index"
                val rest = collar(index)
                val shot = pixels()
                val rim = checkNotNull(rimBox(shot, rest)) { "$at: no rim" }
                assertRimMatches(at, index)
                assertTrue("$at: rim under the top bar (${rim.top} vs $top)", rim.top >= top - 0.5f)
                assertTrue("$at: rim above the hint bar (${rim.bottom} vs $hints)", rim.bottom <= hints + 1.5f)
                assertTrue("$at: rim clear of the left column (${rim.left} vs $minLeft)", rim.left >= minLeft - 0.5f)
                assertTrue("$at: rim clear of the screen edge (${rim.right} vs $maxRight)", rim.right <= maxRight + 1.5f)
                // The cover itself never leaves its rest collar toward the grid's margin: an edge column grows inward.
                val edges = edgesOf(index)
                val inset = FocusLift.rimWidth.value * d
                if (edges.atRight) assertTrue("$at: the last column grows inward (right ${rim.right - inset} vs ${rest.right})", rim.right - inset <= rest.right + 1f)
                if (edges.atLeft) assertTrue("$at: the first column grows inward (left ${rim.left + inset} vs ${rest.left})", rim.left + inset >= rest.left - 1f)
                if (edges.atTop) assertTrue("$at: the top row grows downward (top ${rim.top + inset} vs ${rest.top})", rim.top + inset >= rest.top - 1f)
                // On each side the rim's band (its outer edge in to its width) holds its colour, whole, whatever the px rounding.
                val band = (0..(FocusLift.rimWidth.value * d).roundToInt()).toList()
                val sides =
                    listOf<(Int) -> Color>(
                        { o -> shot[rim.center.x.roundToInt(), (rim.top + o).roundToInt()] },
                        { o -> shot[rim.center.x.roundToInt(), (rim.bottom - 1f - o).roundToInt()] },
                        { o -> shot[(rim.left + o).roundToInt(), rim.center.y.roundToInt()] },
                        { o -> shot[(rim.right - 1f - o).roundToInt(), rim.center.y.roundToInt()] },
                    )
                for ((i, at1) in sides.withIndex()) {
                    val best = band.map { at1(it) }.maxBy { contrast(it, background) }
                    assertTrue("$at: side $i of the rim is drawn in the rim colour ($best)", near(best, rimOnPage(), 0.1f))
                    assertTrue("$at: side $i against the page colour ${contrast(best, background)}", contrast(best, background) >= 3f)
                }
            }
        }
    }

    /** At rest the grid is flush to the right margin: the first row's last cell reaches the grid's right edge, and no cell moves when the last column is focused. */
    protected fun assertFlushToTheRightMargin(label: String) {
        focus(0)
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val right = grid.right.value * d
        val shown = (0..MAX_SHOWN).takeWhile { exists(it) }
        val firstRow = shown.filter { cell(it).top == cell(0).top }
        assertEquals("$label: the first row reaches the right margin", right, cell(firstRow.last()).right, 1.5f)
        val before = shown.associateWith { cell(it) }
        focus(firstRow.last())
        for ((i, rect) in before) assertEquals("$label: cell $i does not move when the last column is focused", rect, cell(i))
    }

    @Test
    fun unfocusedCoversStayAsTheyAre() {
        show(TableText.Graphite)
        focus(0)
        val far = collar(5)
        val one = pixels()
        focus(1)
        val two = pixels()
        var differing = 0
        for (y in far.top.roundToInt() until far.bottom.roundToInt()) for (x in far.left.roundToInt() until far.right.roundToInt()) {
            if (distance(one[x, y], two[x, y]) > 0.01f) differing++
        }
        assertEquals("a far cover is drawn the same whichever cover has the focus", 0, differing)
    }
}

internal const val SIZE_640 = "w640dp-h480dp-land-xhdpi"
internal const val SIZE_576 = "w576dp-h432dp-land-xhdpi"
internal const val SIZE_853 = "w853dp-h480dp-land-xxhdpi"
internal const val SIZE_768 = "w768dp-h432dp-land-xhdpi"
internal const val MAX_SHOWN = 40
internal const val SIZE_832 = "w832dp-h468dp-land-xxhdpi"
