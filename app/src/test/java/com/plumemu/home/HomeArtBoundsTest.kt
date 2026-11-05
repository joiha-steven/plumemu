package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumDimens
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
 * Where the box-art parts land, measured against the boards'
 * own numbers: RP5-2-games (details 248 beside the 400 list, cover 248×178, gap 12),
 * Nova-3-details (strip at `56 − 648`, details `width − 104` = 472, cover 228×164, text 20 to its
 * right), RP5-3 / RP5-4 and their Nova versions (grid `width − 96` less the 4 dp padding;
 * columns 3 / 5 and 2 / 4; gaps 6.67 across and 9.33 down between collars; the bar's title at 72).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeArtBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlumTheme(TableText.Default) { content() } }

    @Test
    @Config(qualifiers = WIDE)
    fun wideDetailsSitBesideTheList() {
        show { ArtDetailsBoard() }
        val row = bounds(HomeTags.game(0))
        assertDp("list after the peek (less the 8 bleed)", 72.dp - PlumDimens.gamesListBleed, row.left)
        assertDp("list width − 368 = 400 (plus the 8 bleed)", 400.dp + PlumDimens.gamesListBleed, row.right - row.left)
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertDp("details 24 after the list", 496.dp, cover.left)
        assertDp("cover at the strip's top", TableText.StripTop, cover.top)
        assertDp("cover 248 wide", 248.dp, cover.right - cover.left)
        // The cover is up to 178 high; the title block and the whole facts block come first, so it gives up what they need. The facts are in two columns
        // here (a label never wraps, so LAST PLAYED does not fit a third), a row taller than three columns: the cover keeps 140.
        val coverHeight = (cover.bottom - cover.top).value
        assertTrue("cover at most 178 and still a cover: $coverHeight", coverHeight in 140f..178.5f)
        assertDp("details end at the right margin", 768.dp - 24.dp, cover.right)
        assertDp("title one block gap (16) under the cover", cover.bottom + PlumDimens.detailsBlockGapWide, bounds(HomeTags.DETAILS_TITLE).top)
        assertDp("title at the column's left", 496.dp, bounds(HomeTags.DETAILS_TITLE).left)
        val facts = bounds(HomeTags.DETAILS_FACTS)
        assertDp("facts at the column's left", 496.dp, facts.left)
        val years = compose.onAllNodes(hasText("1994", substring = true) and hasAnyAncestor(hasTestTag(HomeTags.GAMES)), useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals("the list shows years with box art (EarthBound, Final Fantasy VI, Super Metroid)", true, years.size >= 3)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowDetailsStopTakesWidthMinus104() {
        show { ArtDetailsBoard() }
        val row = bounds(HomeTags.game(0))
        assertDp("the list slid out until 56 show", 56.dp, row.right)
        val details = bounds(HomeTags.DETAILS)
        assertDp("details 24 after the list peek", 80.dp, details.left)
        assertDp("details width − 104 = 472", 472.dp, details.right - details.left)
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertDp("cover at the column's right edge", 552.dp, cover.right)
        assertDp("cover at the strip's top", TableText.StripTop, cover.top)
        assertDp("cover 228 wide", 228.dp, cover.right - cover.left)
        assertDp("cover 164 high", 164.dp, cover.bottom - cover.top)
        val title = bounds(HomeTags.DETAILS_TITLE)
        assertDp("title at the column's left, text first", 80.dp, title.left)
        assertTrue("20 between the text and the cover", title.right <= cover.left - 20.dp + TOLERANCE.dp)
        assertDp("title level with the cover", TableText.StripTop, title.top)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowGamesStopLeavesTheDetailsOutWhenTheMarginLeavesTooLittleRoom() {
        // a column is never cut by the screen's edge. At 576 the room left is under the cover's 140, so the column is left out and the list takes its room up to the right margin.
        show { HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused) }
        assertDp("list reaches the right margin: 576 - 24 - 72 = 480 (plus the 8 bleed)", 480.dp + PlumDimens.gamesListBleed, bounds(HomeTags.game(0)).right - bounds(HomeTags.game(0)).left)
        assertEquals("no details column cut by the edge", 0, compose.onAllNodesWithTag(HomeTags.DETAILS).fetchSemanticsNodes().size)
    }

    /** A packed box: as tall as the row, the picture inside the collar as wide as its [aspect] makes it. */
    private fun packedWidth(
        rowHeight: Dp,
        aspect: Float,
    ): Dp = Dp((rowHeight - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding).value * aspect) + CollarSize.Cover.sidePadding * 2

    /** How many boxes stand on the first row: packed rows hold what their widths let them. */
    private fun firstRowCount(): Int = generateSequence(0) { it + 1 }.takeWhile { runCatching { bounds(HomeTags.cover(it)).top == bounds(HomeTags.cover(0)).top }.getOrDefault(false) }.count()

    /** How many boxes of [width] fit a row of [span] with [gap] between them: the Size step's column count, worked out from the box the step makes. */
    private fun boxesPerRow(span: Dp, width: Dp, gap: Dp): Int {
        val natural = minOf(width.value, span.value)
        return floor((span.value + gap.value + 0.5f) / (natural + gap.value)).toInt()
    }

    /** The one box width a single-shape grid has: the columns and gaps fill [span] exactly (the requirement changed: no cap, no natural width left at the right). */
    private fun tileWidth(span: Dp, width: Dp, gap: Dp): Dp {
        val columns = boxesPerRow(span, width, gap)
        return ((span.value - (columns - 1) * gap.value) / columns).dp
    }

    /** The first row is exactly what the packing rule gives for [span] (the covers' width), so an early wrap fails. */
    private fun assertGrid(
        span: Dp,
        aspect: Float,
        rowHeight: Dp,
        gap: Dp,
        rowGap: Dp = gap,
    ) {
        val coverWidth = tileWidth(span, packedWidth(rowHeight, aspect), gap)
        val columns = firstRowCount()
        assertEquals("boxes on the first row", boxesPerRow(span, packedWidth(rowHeight, aspect), gap), columns)
        val first = bounds(HomeTags.cover(0))
        assertTrue("grid starts where the list does", first.left >= 72.dp - TOLERANCE.dp)
        // The cover grid sits PlumDimens.gridTopGap under the top bar, the strip's other screens barGap (10), for the 5 % lift.
        assertDp("first row under the grid's top gap", TableText.StripTop + (PlumDimens.gridTopGap - PlumDimens.barGap), first.top)
        assertDp("cover width", coverWidth, first.right - first.left)
        assertDp("row height keeps the shape", rowHeight, first.bottom - first.top)
        // A row another follows is justified (the requirement changed from a fixed gap): the gap is the board's or wider.
        assertTrue("gap across is the board's or wider", bounds(HomeTags.cover(1)).left.value >= (first.right + gap).value - GAP_TOLERANCE)
        val nextRow = bounds(HomeTags.cover(columns))
        assertDp("$columns columns: the next row starts where the first does", first.left, nextRow.left)
        assertDp("gap down", first.bottom + rowGap, nextRow.top)
        // A full row starts at the left edge and ends on the covers' span, or (a box would gain too much) keeps natural widths and board gaps.
        assertDp("grid starts at the list's edge", 72.dp, first.left)
        val end = bounds(HomeTags.cover(columns - 1)).right
        val naturalEnd = first.left + coverWidth * columns + gap * (columns - 1)
        assertEquals("last column's end", naturalEnd.value, end.value, GAP_TOLERANCE * columns)
        assertTrue("it ends on the span or the boxes are natural", end.value >= (72.dp + span).value - GAP_TOLERANCE * columns || kotlin.math.abs(coverWidth.value - packedWidth(rowHeight, aspect).value) < TOLERANCE)
        assertTrue("the columns end inside the span", bounds(HomeTags.cover(columns - 1)).right <= bounds(HomeTags.GRID).right + TOLERANCE.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideLandscapeGrid() {
        show { ArtGridLandscapeBoard() }
        assertGrid(span = 672.dp, aspect = 1.4f, rowHeight = 159.77.dp, gap = 6.667.dp, rowGap = 9.333.dp)
        assertTrue("three boxes inside the covers' span 672", bounds(HomeTags.cover(2)).right <= 72.dp + 672.dp + TOLERANCE.dp)
        val title = compose.onNodeWithText("Mega Man X 1993 · Capcom", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertDp("the bar's title starts where the grid does", 72.dp, title.left)
        assertEquals("no details beside a wide grid", 0, compose.onAllNodesWithTag(HomeTags.DETAILS).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLandscapeGrid() {
        // Focus on the first row: with the cover grid's 10 dp shorter strip (top gap 20) two 161 rows no longer both fit at
        // 576x432, so the board's focus on row 2 scrolls the grid and this test could not read the first row where it rests.
        show { ProvideGridSize(GridSize.Large) { HomeAt(FakeHomeContent.withArtwork(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) } }
        assertGrid(span = 480.dp, aspect = 1.4f, rowHeight = 172.dp, gap = 6.667.dp, rowGap = 9.333.dp)
        assertTrue("two boxes inside the covers' span 480", bounds(HomeTags.cover(1)).right <= 72.dp + 480.dp + TOLERANCE.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun widePortraitGrid() {
        // Medium takes five tall columns at 768 (672 wide): a box is 129.07 wide, 163.88 tall (the shape's picture inside the collar).
        show { ProvideGridSize(GridSize.Medium) { HomeAt(FakeHomeContent.withArtwork(), gridActions(PSP_INDEX, 0), gridIds = setOf("psp")) } }
        assertGrid(span = 672.dp, aspect = 0.78f, rowHeight = 163.88.dp, gap = 6.667.dp, rowGap = 9.333.dp)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowPortraitGrid() {
        // Medium takes four tall columns at 576 (480 wide): 115 wide, 145.85 tall. Focus on the first row, so the grid rests there.
        show { ProvideGridSize(GridSize.Medium) { HomeAt(FakeHomeContent.withArtwork(), gridActions(PSP_INDEX, 0), gridIds = setOf("psp")) } }
        assertGrid(span = 480.dp, aspect = 0.78f, rowHeight = 145.85.dp, gap = 6.667.dp, rowGap = 9.333.dp)
    }

    /**
     * Every cover of the first two rows: one size, evenly spaced across and down, inside the
     * grid's bounds (the square class has no mockup to compare with).
     */
    private fun assertEvenTilesInsideTheGrid(
        columns: Int,
        gap: Dp,
        rowGap: Dp = gap,
    ) {
        val grid = bounds(HomeTags.GRID)
        val first = bounds(HomeTags.cover(0))
        val width = first.right - first.left
        val height = first.bottom - first.top
        for (index in 0 until columns * 2) {
            val tile = bounds(HomeTags.cover(index))
            val (row, column) = index / columns to index % columns
            assertDp("tile $index width", width, tile.right - tile.left)
            assertDp("tile $index height", height, tile.bottom - tile.top)
            // Justified rows (the requirement changed from a fixed gap): the pitch is the board's or wider, the same across the row;
            // gaps are whole px: up to one px off per gap crossed.
            val pitch = bounds(HomeTags.cover(1)).left - first.left
            assertTrue("tile $index pitch is the board's or wider", pitch.value >= (width + gap).value - TOLERANCE)
            assertEquals("tile $index left", (first.left + pitch * column).value, tile.left.value, TOLERANCE + 0.5f * column)
            assertEquals("tile $index top", (first.top + (height + rowGap) * row).value, tile.top.value, TOLERANCE + 0.5f * row)
            assertEquals("tile $index inside the grid across", true, tile.left >= grid.left - TOLERANCE.dp && tile.right <= grid.right + TOLERANCE.dp)
        }
        assertEquals("the first row inside the grid", true, first.top >= grid.top - TOLERANCE.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSquareGrid() {
        show { ProvideGridSize(GridSize.Medium) { ArtGridSquareBoard() } }
        // Medium takes four square columns at 768 (672 wide): 163 wide, (163 − 8) / 1.12 + 8.667 = 147.06 tall.
        assertGrid(span = 672.dp, aspect = 1.12f, rowHeight = 147.06.dp, gap = 6.667.dp, rowGap = 9.333.dp)
        assertTrue("four boxes inside the covers' span 672", bounds(HomeTags.cover(3)).right <= 72.dp + 672.dp + TOLERANCE.dp)
        assertEvenTilesInsideTheGrid(columns = 4, gap = 6.667.dp, rowGap = 9.333.dp)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSquareGrid() {
        show { ProvideGridSize(GridSize.Medium) { ArtGridSquareBoard() } }
        // Medium takes three square columns at 576 (480 wide): 155.56 wide, 140.41 tall, filling the span.
        assertGrid(span = 480.dp, aspect = 1.12f, rowHeight = 140.41.dp, gap = 6.667.dp, rowGap = 9.333.dp)
        assertTrue("three boxes inside the covers' span 480", bounds(HomeTags.cover(2)).right <= 72.dp + 480.dp + TOLERANCE.dp)
        assertEvenTilesInsideTheGrid(columns = 3, gap = 6.667.dp, rowGap = 9.333.dp)
    }

    /**
     * At each grid size, system [id]'s grid (its first cover focused) has the layout's columns,
     * cover width and gap, and its tiles are the same size, evenly spaced and inside the grid.
     */
    private fun assertEveryGridSize(
        id: String,
        content: HomeContent = FakeHomeContent.withArtwork(),
    ) {
        var size by mutableStateOf(GridSize.Small)
        var layout: PlumLayout? = null
        val system = content.entries.indexOfFirst { it.id == id }
        val shape = content.entries[system].coverShape
        compose.setContent {
            PlumTheme(TableText.Default) {
                ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    key(size) { HomeAt(content, gridActions(system, 0), gridIds = setOf(id)) }
                }
            }
        }
        for (each in GridSize.entries) {
            size = each
            compose.waitForIdle()
            val sized = checkNotNull(layout)
            val label = "$id at $each, ${sized.width}"
            val first = bounds(HomeTags.cover(0))
            val columns = firstRowCount()
            val natural = packedWidth(sized.gridRowHeight(shape), shape.widthOverHeight)
            val width = tileWidth(sized.gridCoversWidth, natural, sized.gridGap(shape))
            assertEquals("$label: boxes on the first row", boxesPerRow(sized.gridCoversWidth, natural, sized.gridGap(shape)), columns)
            assertDp("$label: box width", width, first.right - first.left)
            assertTrue("$label: $columns columns end inside the covers' span", bounds(HomeTags.cover(columns - 1)).right <= first.left + sized.gridCoversWidth + TOLERANCE.dp)
            assertDp("$label: the next row starts at the left", first.left, bounds(HomeTags.cover(columns)).left)
            assertEvenRowsInsideTheGrid(label, columns, width, sized.gridGap(shape), sized.gridRowGap(shape))
        }
    }

    /**
     * Two rows of tiles, [width] wide (the lazy grid hands out the odd pixels of a width that is not
     * whole, so a tile may be one pixel off, never more), [gap] apart across and down, inside the grid.
     */
    private fun assertEvenRowsInsideTheGrid(
        label: String,
        columns: Int,
        width: Dp,
        gap: Dp,
        rowGap: Dp = gap,
    ) {
        val grid = bounds(HomeTags.GRID)
        val first = bounds(HomeTags.cover(0))
        val height = first.bottom - first.top
        for (index in 0 until columns * 2) {
            val tile = bounds(HomeTags.cover(index))
            val at = "$label: tile $index"
            assertDp("$at width", width, tile.right - tile.left)
            assertDp("$at height", height, tile.bottom - tile.top)
            if (index % columns > 0) {
                val across = (tile.left - bounds(HomeTags.cover(index - 1)).right).value
                assertTrue("$at gap across $across is the board's ${gap.value} or wider", across >= gap.value - GAP_TOLERANCE)
                assertEquals("$at gaps in a row are equal", (bounds(HomeTags.cover(1)).left - first.right).value, across, GAP_TOLERANCE)
            }
            if (index >= columns) assertDp("$at gap down", rowGap, tile.top - bounds(HomeTags.cover(index - columns)).bottom)
            assertEquals("$at inside the grid across", true, tile.left >= grid.left - TOLERANCE.dp && tile.right <= grid.right + TOLERANCE.dp)
        }
        assertEquals("$label: the first row inside the grid", true, first.top >= grid.top - TOLERANCE.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("snes")
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideTallGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("psp")
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSquareGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("nds", squareContent())
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("snes")
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowTallGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("psp")
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSquareGridsAtEverySizeAreEvenAndInside() {
        assertEveryGridSize("nds", squareContent())
    }

    /** The Thor's width: Small tall (8 columns, 81 dp) exists only here; PSP at 0.575 is the tallest tile. */
    @Test
    @Config(qualifiers = THOR)
    fun thorTallGridsAtEverySizeAreEvenAndInside() {
        val board = FakeHomeContent.withArtwork()
        assertEveryGridSize("psp", board.withEntries(board.entries.map { if (it.id == "psp") it.copy(coverShape = CoverShape(0.575f)) else it }))
    }

    /** The board's content with the DS at its measured square shape (as [ArtGridSquareBoard]). */
    private fun squareContent(): HomeContent =
        FakeHomeContent.withArtwork().let { board -> board.withEntries(board.entries.map { if (it.id == "nds") it.copy(coverShape = NDS_SHAPE) else it }) }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"
        // A px at xhdpi is 0.5 dp: a gap of 6.67 dp is 13.3 px, so a tile lands one px off its ideal at most.
        const val TOLERANCE = 0.55f
        // A packed box's left edge and width round to whole px separately, so a gap between two can be 1.5 px (0.75 dp) off.
        const val GAP_TOLERANCE = 0.8f
    }
}
