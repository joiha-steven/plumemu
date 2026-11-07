package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
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
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.focusedCoverScale
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
 * The focused cover of the grid is easy to see (docs/design/rules.md › Focus and current): its own size, a little larger with an accent ring in the gaps
 * around it, no halo, and nothing else moves. Measured from pixels and layout bounds at 640x480 and 832x468, Graphite, Paper and
 * Paper with a dark teal accent, on a Super Nintendo grid (one shape) and on Recent (mixed shapes).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK], qualifiers = SIZE_640)
class GridCoverFocusTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var state: StripState? = null
    private var accent = Color.Unspecified
    private var background = Color.Unspecified
    private var gridSize by mutableStateOf(GridSize.Default)

    private fun show(
        settings: PlumThemeSettings,
        entry: Int = FakeHomeContent.BOARD_SNES_INDEX,
        id: String = "snes",
    ) {
        compose.setContent {
            PlumTheme(settings.copy(gridSize = gridSize)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                key(gridSize) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(entry, 0), gridIds = setOf(id), onState = { state = it }) }
            }
        }
        compose.waitForIdle()
    }

    private val d get() = compose.density.density

    private fun focus(index: Int) {
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

    private fun cell(index: Int): Rect {
        val b = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getUnclippedBoundsInRoot()
        return Rect(b.left.value * d, b.top.value * d, b.right.value * d, b.bottom.value * d)
    }

    /** The collar as laid out (px): the picture's bounds plus the lip and groove. */
    private fun collar(index: Int): Rect {
        val node = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode()
        // A picture's bounds, else the drawn box front fitted to the system's shape (mixed grids).
        val picture = node.children.first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE || it.value == HomeTags.COVER_OWN_SHAPE } }
        val box = Rect(picture.positionInRoot, Size(picture.size.width.toFloat(), picture.size.height.toFloat()))
        val c = CollarSize.Cover
        return Rect(box.left - c.sidePadding.value * d, box.top - c.topPadding.value * d, box.right + c.sidePadding.value * d, box.bottom + c.bottomPadding.value * d)
    }

    private fun pixels(): PixelMap = compose.onRoot().captureToImage().toPixelMap()

    private fun near(
        a: Color,
        b: Color,
        tolerance: Float = 0.07f,
    ) = abs(a.red - b.red) < tolerance && abs(a.green - b.green) < tolerance && abs(a.blue - b.blue) < tolerance

    /** The box of the accent-coloured pixels around [around] (px): the ring's outer edge, the outermost accent there is. */
    private fun accentBox(
        shot: PixelMap,
        around: Rect,
    ): Rect? {
        // Room for the lift (half of the growth each side) and the ring.
        val room = (PlumDimens.focusRingOutset.value + 6f) * d
        val growX = around.width * (PlumDimens.FOCUSED_COVER_SCALE - 1f) / 2f
        val growY = around.height * (PlumDimens.FOCUSED_COVER_SCALE - 1f) / 2f
        val l = max(0, (around.left - room - growX).roundToInt())
        val t = max(0, (around.top - room - growY).roundToInt())
        val r = min(shot.width - 1, (around.right + room + growX).roundToInt())
        val b = min(shot.height - 1, (around.bottom + room + growY).roundToInt())
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = -1
        var bottom = -1
        for (y in t..b) for (x in l..r) {
            if (!near(shot[x, y], accent)) continue
            left = min(left, x)
            right = max(right, x)
            top = min(top, y)
            bottom = max(bottom, y)
        }
        return if (right < 0) null else Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }

    private fun contrast(
        a: Color,
        b: Color,
    ): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun distance(
        a: Color,
        b: Color,
    ) = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    /** The drawn collar of the focused cover (px), from the ring's outer edge, or a failure that says what was found. */
    private fun drawnCollar(
        label: String,
        index: Int,
    ): Rect {
        val ring = accentBox(pixels(), collar(index))
        assertTrue("$label: no accent ring is drawn round cover $index", ring != null)
        val out = PlumDimens.focusRingOutset.value * d
        return Rect(ring!!.left + out, ring.top + out, ring.right - out, ring.bottom - out)
    }

    /** How much larger the focused cover whose resting collar is [rest] is drawn: up to the constant, less for a very large cover (`focusedCoverScale`). */
    private fun scaleOf(rest: Rect) = focusedCoverScale(max(rest.width, rest.height) / d)

    private fun assertOwnSize(
        label: String,
        index: Int,
        neighbour: Int,
    ) {
        focus(neighbour)
        val others = (0..3).associateWith { cell(it) }
        focus(index)
        val rest = collar(index)
        val drawn = drawnCollar(label, index)
        val ratioW = drawn.width / rest.width
        val ratioH = drawn.height / rest.height
        val scale = scaleOf(rest)
        assertEquals("$label: the focused cover is drawn $scale larger in width", scale, ratioW, 0.02f)
        assertEquals("$label: the focused cover is drawn $scale larger in height", scale, ratioH, 0.02f)
        assertEquals("$label: the mark is about the collar's centre (x)", rest.center.x, drawn.center.x, 1.5f)
        assertEquals("$label: the mark is about the collar's centre (y)", rest.center.y, drawn.center.y, 1.5f)
        // Layout is untouched: every cover's cell stands where it stood with the focus on the neighbour.
        for ((i, before) in others) assertEquals("$label: cell $i did not move or resize", before, cell(i))
    }

    private fun assertRingContrast(label: String) {
        focus(0)
        val drawn = drawnCollar(label, 0)
        val out = PlumDimens.focusRingOutset.value * d
        val ringPx = PlumDimens.focusRingWidth.value * d
        val shot = pixels()
        val x = drawn.center.x.roundToInt()
        val ringAt = shot[x, (drawn.top - out + ringPx / 2).roundToInt()]
        val inside = shot[x, (drawn.top - out + ringPx + 1.5f * d).roundToInt()]
        assertTrue("$label: the ring pixel is the accent ($ringAt vs $accent)", near(ringAt, accent))
        assertTrue("$label: the band inside the ring is the page colour ($inside vs $background)", near(inside, background, 0.02f))
        assertTrue("$label: ring against the page just inside it ${contrast(ringAt, inside)}", contrast(ringAt, inside) >= 3f)
        assertTrue("$label: ring against the page colour ${contrast(ringAt, background)}", contrast(ringAt, background) >= 3f)
        // Outside the ring: the keyline, the page colour whatever the halo or a neighbour cover is.
        val outside = shot[x, (drawn.top - out - PlumDimens.focusRingKeyline.value * d / 2).roundToInt()]
        assertTrue("$label: ring against the pixel just outside it ${contrast(ringAt, outside)}", contrast(ringAt, outside) >= 3f)
    }

    /** The ring's outside is the page: no halo, so the mark is the same on every side. */
    private fun assertNoHalo(label: String) {
        focus(0)
        val drawn = drawnCollar(label, 0)
        val out = PlumDimens.focusRingOutset.value * d
        val above = pixels()[drawn.center.x.roundToInt(), (drawn.top - out - (PlumDimens.focusRingKeyline.value + 2f) * d).roundToInt()]
        assertTrue("$label: the pixel past the ring's band is the page ($above vs $background)", near(above, background, 0.02f))
    }

    private fun exists(index: Int) = compose.onAllNodesWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The right edge (px) of the column left of the grid: the letter rail, else the systems column; 0 when there is none. */
    private fun leftColumnRight(): Float =
        listOf(HomeTags.RAIL, HomeTags.SYSTEMS).maxOf { tag ->
            compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().maxOfOrNull { it.boundsInRoot.right } ?: 0f
        }

    /**
     * The focused cover and its mark stay in their own cell and gaps: the cover between two others is drawn the same, pixel for
     * pixel, whichever side the focus is on, its rounded corners left out (the page shows there, and the halo over it).
     */
    @Test
    fun aNeighbourStaysWholeWhicheverSideTheFocusIsOn() {
        show(PlumThemeSettings.Graphite)
        focus(0)
        val middle = collar(1)
        assertEquals("three covers in the first row", cell(0).top, cell(2).top)
        val focusLeft = pixels()
        focus(2)
        val focusRight = pixels()
        val corner = (CollarDimens.coverBoxCorner.value + CollarSize.Cover.sidePadding.value) * d
        var differing = 0
        var first = ""
        // One pixel in: the collar's outermost is anti-aliased, the page or the halo under it shows through.
        for (x in middle.left.roundToInt() + 1 until middle.right.roundToInt() - 1) {
            for (y in middle.top.roundToInt() + 1 until middle.bottom.roundToInt() - 1) {
                val inCornerX = x < middle.left + corner || x > middle.right - corner
                val inCornerY = y < middle.top + corner || y > middle.bottom - corner
                if (inCornerX && inCornerY) continue
                if (!near(focusLeft[x, y], focusRight[x, y], 8f / 255f)) {
                    if (differing == 0) first = "${x - middle.left}, ${y - middle.top} of ${middle.width} x ${middle.height}: ${focusLeft[x, y]} ${focusRight[x, y]}"
                    differing++
                }
            }
        }
        assertEquals("pixels of the neighbour that the focus changes, first at $first", 0, differing)
    }

    /** Landing on a cover, once focus has rested, one glint crosses it and is gone. */
    @Test
    fun aCoverTheFocusRestsOnGlintsOnce() {
        show(PlumThemeSettings.Graphite)
        focus(0)
        val box = collar(1)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { checkNotNull(state).dispatch(Action.TapItem(Column.Games, 1)) }
        compose.mainClock.advanceTimeBy(PlumMotion.FOCUS_SWEEP_REST_MS + PlumMotion.FOCUS_SWEEP_MS / 2L)
        val crossing = pixels()
        compose.mainClock.advanceTimeBy(1000)
        val after = pixels()
        compose.mainClock.autoAdvance = true
        var lighter = 0
        for (x in box.left.roundToInt() until box.right.roundToInt() step 4) {
            for (y in box.top.roundToInt() until box.bottom.roundToInt() step 4) {
                if (crossing[x, y].red > after[x, y].red + 0.04f) lighter++
            }
        }
        assertTrue("the glint lit only $lighter samples", lighter > 100)
    }

    /** The covers worth testing: both ends of the first row and of the bottom row that shows whole, and the last game. */
    private fun edgeIndices(last: Int): List<Int> {
        focus(0)
        val hints = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * d
        val shown = (0..last).takeWhile { it < 80 && exists(it) }
        val firstRow = shown.filter { cell(it).top == cell(0).top }
        val whole = shown.filter { cell(it).bottom <= hints }
        val bottomTop = whole.maxOf { cell(it).top }
        val bottomRow = whole.filter { cell(it).top == bottomTop }
        return listOf(firstRow.first(), firstRow.last(), bottomRow.first(), bottomRow.last(), last).distinct()
    }

    /** The pixel [along] px out from the lifted collar's [side] middle (negative: inside). */
    private fun PixelMap.beside(
        rest: Rect,
        side: Int,
        along: Float,
    ): Color {
        val s = scaleOf(rest)
        val c = rest.center
        val x = c.x + if (side == LEFT) -(rest.width * s / 2 + along) else if (side == RIGHT) rest.width * s / 2 + along else 0f
        val y = c.y + if (side == TOP) -(rest.height * s / 2 + along) else if (side == BOTTOM) rest.height * s / 2 + along else 0f
        return this[x.roundToInt(), y.roundToInt()]
    }

    /**
     * The lifted cover at the grid's edges, at every Size step: the ring is whole (all four sides drawn, the whole outset), it is clear of
     * the top bar, the hint bar, the left column (letter rail or systems) and the next column, and on each side the pixel just outside the
     * ring is the page colour, so the ring's contrast is measured against what really lies next to it (never the halo's tint or a
     * neighbour cover's colour).
     */
    private fun assertWholeAtEdges(
        label: String,
        entry: Int = FakeHomeContent.BOARD_SNES_INDEX,
    ) {
        val last = FakeHomeContent.withArtwork().entries[entry].rows.lastIndex
        val out = PlumDimens.focusRingOutset.value * d
        val onRing = PlumDimens.focusRingGap.value * d + PlumDimens.focusRingWidth.value * d / 2
        for (size in GridSize.entries) {
            gridSize = size
            compose.waitForIdle()
            val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val top = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().bottom.value * d
            val hints = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot().top.value * d
            val minLeft = max(leftColumnRight(), (grid.left.value - PlumDimens.stripMarginSide.value) * d)
            // The lifted ring may enter the right margin (24 dp) but stays clear of the screen edge by 4 dp.
            val maxRight = (grid.right.value + PlumDimens.stripMarginSide.value - 4f) * d
            for (index in edgeIndices(last)) {
                focus(index)
                val at = "$label $size cover $index"
                val rest = collar(index)
                val shot = pixels()
                val ring = checkNotNull(accentBox(shot, rest)) { "$at: no ring" }
                val expectW = rest.width * scaleOf(rest) + 2 * out
                val expectH = rest.height * scaleOf(rest) + 2 * out
                assertTrue("$at: the ring is whole (${ring.width} x ${ring.height} of $expectW x $expectH; ring $ring)", ring.width >= expectW - 4f && ring.height >= expectH - 4f)
                assertTrue("$at: ring under the top bar (${ring.top} vs $top)", ring.top >= top - 0.5f)
                assertTrue("$at: ring above the hint bar (${ring.bottom} vs $hints)", ring.bottom <= hints + 1.5f)
                assertTrue("$at: ring clear of the left column (${ring.left} vs $minLeft)", ring.left >= minLeft - 0.5f)
                assertTrue("$at: ring clear of the screen edge (${ring.right} vs $maxRight)", ring.right <= maxRight + 1.5f)
                for ((side, name) in listOf(TOP to "top", BOTTOM to "bottom", LEFT to "left", RIGHT to "right")) {
                    // The ring's outer edge is found along the side's normal (layout rounding moves it a px or two), then the pixel in the middle of the keyline beyond it is read.
                    val edge = shot.outerRingEdge(rest, side, onRing, d * 3f)
                    assertTrue("$at: the $name of the ring is drawn in the accent", edge != null)
                    val outside = shot.beside(rest, side, edge!! + PlumDimens.focusRingKeyline.value * d / 2)
                    val onIt = shot.beside(rest, side, edge - PlumDimens.focusRingWidth.value * d / 2)
                    // Under the last row the hint keys' collars stand just below the ring: there the pixel outside is theirs, not the page's.
                    if (side == BOTTOM && ring.bottom > hints - 8f * d) continue
                    assertTrue("$at: the pixel outside the $name of the ring is the page colour ($outside vs $background)", near(outside, background, 0.03f))
                    assertTrue("$at: the $name ring against the pixel just outside it ${contrast(onIt, outside)}", contrast(onIt, outside) >= 3f)
                }
            }
        }
    }

    /** How far out (px past the lifted collar's [side]) the outermost accent pixel lies within [slack] px of [expected]; null when there is none. */
    private fun PixelMap.outerRingEdge(
        rest: Rect,
        side: Int,
        expected: Float,
        slack: Float,
    ): Float? {
        var found: Float? = null
        var along = expected - slack
        while (along <= expected + slack) {
            if (near(beside(rest, side, along), accent)) found = along + 0.5f
            along += 0.5f
        }
        return found
    }

    @Test
    fun focusedCoverIsDrawnLargerAndNothingMoves() {
        show(TableText.Graphite)
        assertOwnSize("640x480 snes", 1, 0)
    }

    @Test
    fun mixedSystemCoverIsDrawnLargerToo() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertOwnSize("640x480 recent", 1, 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun focusedCoverIsDrawnLargerAt832() {
        show(TableText.Graphite)
        assertOwnSize("832x468 snes", 1, 0)
    }

    @Test
    fun ringContrastGraphite() {
        show(TableText.Graphite)
        assertRingContrast("graphite")
    }

    @Test
    fun ringContrastPaper() {
        show(TableText.Paper)
        assertRingContrast("paper")
    }

    @Test
    fun ringContrastPaperWithDarkTeal() {
        show(TableText.Paper.copy(accent = AccentChoice.Custom(Rgb(0x0F5C5C))))
        assertRingContrast("paper teal")
    }

    @Test
    fun noHaloGraphite() {
        show(TableText.Graphite)
        assertNoHalo("graphite")
    }

    @Test
    fun noHaloPaper() {
        show(TableText.Paper)
        assertNoHalo("paper")
    }

    @Test
    fun noHaloPaperWithDarkTeal() {
        show(TableText.Paper.copy(accent = AccentChoice.Custom(Rgb(0x0F5C5C))))
        assertNoHalo("paper teal")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt640() {
        show(TableText.Graphite)
        assertWholeAtEdges("640 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt640() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("640 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesSinglePaperAt640() {
        show(TableText.Paper)
        assertWholeAtEdges("640 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_640)
    fun liftIsWholeAtTheEdgesMixedPaperAt640() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("640 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt576() {
        show(TableText.Graphite)
        assertWholeAtEdges("576 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt576() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("576 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesSinglePaperAt576() {
        show(TableText.Paper)
        assertWholeAtEdges("576 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_576)
    fun liftIsWholeAtTheEdgesMixedPaperAt576() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("576 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt832() {
        show(TableText.Graphite)
        assertWholeAtEdges("832 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt832() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("832 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesSinglePaperAt832() {
        show(TableText.Paper)
        assertWholeAtEdges("832 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_832)
    fun liftIsWholeAtTheEdgesMixedPaperAt832() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("832 mixed paper", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesSingleGraphiteAt853() {
        show(TableText.Graphite)
        assertWholeAtEdges("853 single graphite")
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesMixedGraphiteAt853() {
        show(TableText.Graphite, entry = 0, id = "recent")
        assertWholeAtEdges("853 mixed graphite", entry = 0)
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesSinglePaperAt853() {
        show(TableText.Paper)
        assertWholeAtEdges("853 single paper")
    }

    @Test
    @Config(qualifiers = SIZE_853)
    fun liftIsWholeAtTheEdgesMixedPaperAt853() {
        show(TableText.Paper, entry = 0, id = "recent")
        assertWholeAtEdges("853 mixed paper", entry = 0)
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

private const val SIZE_640 = "w640dp-h480dp-land-xhdpi"
private const val SIZE_576 = "w576dp-h432dp-land-xhdpi"
private const val SIZE_853 = "w853dp-h480dp-land-xxhdpi"
private const val TOP = 0
private const val BOTTOM = 1
private const val LEFT = 2
private const val RIGHT = 3
private const val SIZE_832 = "w832dp-h468dp-land-xxhdpi"
