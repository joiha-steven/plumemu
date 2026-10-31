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
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.testing.MediumText
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
 * columns 3 / 5 and 2 / 4; gaps 22 / 20 (the board's 14 / 12); the bar's title at 72).
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

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlumTheme(MediumText.Default) { content() } }

    @Test
    @Config(qualifiers = WIDE)
    fun wideDetailsSitBesideTheList() {
        show { ArtDetailsBoard() }
        val row = bounds(HomeTags.game(0))
        assertDp("list after the peek", 72.dp, row.left)
        assertDp("list width − 368 = 400", 400.dp, row.right - row.left)
        val cover = bounds(HomeTags.DETAILS_COVER)
        assertDp("details 24 after the list", 496.dp, cover.left)
        assertDp("cover at the strip's top", 42.dp, cover.top)
        assertDp("cover 248 wide", 248.dp, cover.right - cover.left)
        assertDp("cover 178 high", 178.dp, cover.bottom - cover.top)
        assertDp("details end at the right margin", 768.dp - 24.dp, cover.right)
        assertDp("title 12 under the cover", cover.bottom + 12.dp, bounds(HomeTags.DETAILS_TITLE).top)
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
        assertDp("cover at the strip's top", 42.dp, cover.top)
        assertDp("cover 228 wide", 228.dp, cover.right - cover.left)
        assertDp("cover 164 high", 164.dp, cover.bottom - cover.top)
        val title = bounds(HomeTags.DETAILS_TITLE)
        assertDp("title at the column's left, text first", 80.dp, title.left)
        assertTrue("20 between the text and the cover", title.right <= cover.left - 20.dp + TOLERANCE.dp)
        assertDp("title level with the cover", 42.dp, title.top)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowGamesStopShowsTheDetailsTextCutByTheScreen() {
        show { HomeAt(FakeHomeContent.withArtwork(), BoardGamesFocused) }
        assertDp("list stays 400", 400.dp, bounds(HomeTags.game(0)).right - bounds(HomeTags.game(0)).left)
        val title = bounds(HomeTags.DETAILS_TITLE)
        assertDp("text 24 after the list, at the column's left", 496.dp, title.left)
        assertDp("80 of the column on screen", 80.dp, 576.dp - bounds(HomeTags.DETAILS).left)
        assertDp("the cover is past the screen, on the column's right", 496.dp + 472.dp, bounds(HomeTags.DETAILS_COVER).right)
    }

    private fun assertGrid(
        columns: Int,
        coverWidth: Dp,
        rowHeight: Dp,
        gap: Dp,
        rowGap: Dp = gap,
    ) {
        val first = bounds(HomeTags.cover(0))
        assertDp("grid starts where the list does", 72.dp, first.left)
        // The cover grid sits PlumDimens.gridTopGap (20) under the top bar, the strip's other screens barGap (10), for the 5 % lift.
        assertDp("first row under the grid's top gap", 42.dp + (PlumDimens.gridTopGap - PlumDimens.barGap), first.top)
        assertDp("cover width", coverWidth, first.right - first.left)
        assertDp("row height keeps the shape", rowHeight, first.bottom - first.top)
        assertDp("gap across", first.right + gap, bounds(HomeTags.cover(1)).left)
        val nextRow = bounds(HomeTags.cover(columns))
        assertDp("$columns columns: the next row starts at the left", 72.dp, nextRow.left)
        assertDp("gap down", first.bottom + rowGap, nextRow.top)
        // The covers' span: columns × width + gaps, the grid area less the board's 4 + 4.
        assertDp("last column's end", first.left + coverWidth * columns + gap * (columns - 1), bounds(HomeTags.cover(columns - 1)).right)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideLandscapeGrid() {
        show { ArtGridLandscapeBoard() }
        assertGrid(columns = 3, coverWidth = 206.667.dp, rowHeight = 148.dp, gap = 22.dp)
        assertDp("covers span 664", 72.dp + 664.dp, bounds(HomeTags.cover(2)).right)
        val title = compose.onNodeWithText("Mega Man X · 1993 · Capcom", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertDp("the bar's title starts where the grid does", 72.dp, title.left)
        assertEquals("no details beside a wide grid", 0, compose.onAllNodesWithTag(HomeTags.DETAILS).fetchSemanticsNodes().size)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowLandscapeGrid() {
        // Focus on the first row: with the cover grid's 10 dp shorter strip (top gap 20) two 161 rows no longer both fit at
        // 576x432, so the board's focus on row 2 scrolls the grid and this test could not read the first row where it rests.
        show { ProvideGridSize(GridSize.Large) { HomeAt(FakeHomeContent.withArtwork(), gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes")) } }
        assertGrid(columns = 2, coverWidth = 225.dp, rowHeight = 161.dp, gap = 22.dp)
        assertDp("covers span 472", 72.dp + 472.dp, bounds(HomeTags.cover(1)).right)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun widePortraitGrid() {
        // The board's portrait grid and these numbers are Medium (the default is Small).
        show { ProvideGridSize(GridSize.Medium) { ArtGridPortraitBoard() } }
        assertGrid(columns = 5, coverWidth = 116.8.dp, rowHeight = 150.dp, gap = 20.dp, rowGap = 22.dp)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowPortraitGrid() {
        // The board's portrait grid and these numbers are Medium (the default is Small).
        show { ProvideGridSize(GridSize.Medium) { ArtGridPortraitBoard() } }
        assertGrid(columns = 4, coverWidth = 103.dp, rowHeight = 132.dp, gap = 20.dp, rowGap = 22.dp)
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
            assertDp("tile $index left", first.left + (width + gap) * column, tile.left)
            assertDp("tile $index top", first.top + (height + gap) * row, tile.top)
            assertEquals("tile $index inside the grid across", true, tile.left >= grid.left - TOLERANCE.dp && tile.right <= grid.right + TOLERANCE.dp)
        }
        assertEquals("the first row inside the grid", true, first.top >= grid.top - TOLERANCE.dp)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideSquareGrid() {
        // These numbers are Medium's (the default is Small).
        show { ProvideGridSize(GridSize.Medium) { ArtGridSquareBoard() } }
        // floor((672 + 12) / 142) = 4; (664 − 3 × 20) / 4 = 151 wide; 151 / 1.12 = 135 high (gap 20; the columns count with 12).
        assertGrid(columns = 4, coverWidth = 151.dp, rowHeight = 135.dp, gap = 20.dp)
        assertDp("covers span 664", 72.dp + 664.dp, bounds(HomeTags.cover(3)).right)
        assertEvenTilesInsideTheGrid(columns = 4, gap = 20.dp)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowSquareGrid() {
        // These numbers are Medium's (the default is Small).
        show { ProvideGridSize(GridSize.Medium) { ArtGridSquareBoard() } }
        // floor((480 + 12) / 142) = 3; (472 − 2 × 12) / 3 = 149.3 wide; 149.3 / 1.12 = 133 high.
        assertGrid(columns = 3, coverWidth = 144.dp, rowHeight = 129.dp, gap = 20.dp)
        assertDp("covers span 472", 72.dp + 472.dp, bounds(HomeTags.cover(2)).right)
        assertEvenTilesInsideTheGrid(columns = 3, gap = 20.dp)
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
            PlumTheme(MediumText.Default) {
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
            val columns = sized.gridColumns(shape)
            val label = "$id at $each, ${sized.width}"
            val first = bounds(HomeTags.cover(0))
            assertDp("$label: cover width", sized.gridCoverWidth(shape), first.right - first.left)
            assertDp("$label: $columns columns end at the covers' span", first.left + sized.gridCoversWidth, bounds(HomeTags.cover(columns - 1)).right)
            assertDp("$label: the next row starts at the left", first.left, bounds(HomeTags.cover(columns)).left)
            assertEvenRowsInsideTheGrid(label, columns, sized.gridCoverWidth(shape), sized.gridGap(shape), sized.gridRowGap(shape))
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
            if (index % columns > 0) assertDp("$at gap across", gap, tile.left - bounds(HomeTags.cover(index - 1)).right)
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
        const val TOLERANCE = 0.5f
    }
}
