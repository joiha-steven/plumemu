package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.testing.MediumText
import com.plumemu.coreui.theme.PlumTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings › Appearance › Grid size on the main screen: the grid follows the
 * setting while it is on screen, the focused game stays focused and whole across a change, and
 * each cell draws the saved picture whose height covers what it draws.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeGridSizeTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    private var gridSize by mutableStateOf(GridSize.Medium)
    private var layout: PlumLayout? = null
    private var strip: StripState? = null

    /** System [id]'s grid with game [focused] focused, in a theme whose grid size is [gridSize] (the stored setting's way in). */
    private fun show(
        id: String,
        focused: Int,
        content: HomeContent = CoverArtLibrary,
        source: FakeArtSource = coverArtSource(),
    ) {
        val system = content.entries.indexOfFirst { it.id == id }
        compose.setContent {
            PlumTheme(MediumText.Graphite.copy(gridSize = gridSize)) {
                layout = PlumTheme.layout
                HomeWithArt(source, gridActions(system, focused), content, gridIds = setOf(id), onState = { strip = it })
            }
        }
        compose.waitForIdle()
    }

    private fun assertColumns(
        label: String,
        columns: Int,
        tileWidth: Float,
    ) {
        val first = bounds(HomeTags.cover(0))
        assertEquals("$label: tile width", tileWidth, (first.right - first.left).value, 0.5f)
        assertEquals("$label: cover ${columns - 1} ends the first row", first.top.value, bounds(HomeTags.cover(columns - 1)).top.value, 0.5f)
        assertEquals("$label: cover $columns starts the second row", first.left.value, bounds(HomeTags.cover(columns)).left.value, 0.5f)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun theGridFollowsTheSettingWhileItIsShown() {
        show("snes", 0)
        // RP5: Medium 4 landscape columns (149.5 wide), Small 5 (115.2), Large 3 (206.7); gaps 22, the counts are the board's.
        assertColumns("Medium", 4, 149.5f)
        gridSize = GridSize.Small
        compose.waitForIdle()
        assertColumns("Small", 5, 115.2f)
        gridSize = GridSize.Large
        compose.waitForIdle()
        assertColumns("Large", 3, 206.7f)
    }

    /**
     * The focused game keeps the focus across every change of size, and the grid opens again at
     * its row: the focused cover is whole inside the grid, however far its row moved.
     */
    private fun assertFocusKeptAcrossSizes(label: String) {
        val focused = 20
        gridSize = GridSize.Small
        show("psp", focused)
        for (size in listOf(GridSize.Large, GridSize.Medium, GridSize.Small, GridSize.Large)) {
            gridSize = size
            compose.waitForIdle()
            val state = strip!!
            assertEquals("$label $size: still in the games", Column.Games, state.focus.column)
            assertEquals("$label $size: the same game", focused, state.focus.rowOf("psp"))
            val grid = bounds(HomeTags.GRID)
            val cover = bounds(HomeTags.cover(focused))
            val at = "$label $size (${layout!!.gridColumns(CoverShape.Portrait)} columns): cover $cover in grid $grid"
            assertTrue("$at: whole below the grid's top", cover.top >= grid.top - 0.5.dp)
            assertTrue("$at: whole above the grid's bottom", cover.bottom <= grid.bottom + 0.5.dp)
            assertTrue("$at: inside across", cover.left >= grid.left - 0.5.dp && cover.right <= grid.right + 0.5.dp)
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFocusIsKeptAcrossASizeChange() = assertFocusKeptAcrossSizes("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusIsKeptAcrossASizeChange() = assertFocusKeptAcrossSizes("576x432")

    /** The board with PSP at [shape] (as the library gives it from the platform data). */
    private fun pspAt(shape: CoverShape): HomeContent =
        CoverArtLibrary.let { board -> board.withEntries(board.entries.map { if (it.id == "psp") it.copy(coverShape = shape) else it }) }

    private fun decodedSizes(source: FakeArtSource): Set<String> =
        (source.images as FakeCoverImages).decoded.map { (path, _, _) -> path.substringAfterLast("box-front-").substringBefore('.') }.toSet()

    @Test
    @Config(qualifiers = THOR)
    fun aTallPspTileAtLargeOnTheThorDrawsTheLargeFile() {
        gridSize = GridSize.Large
        val source = coverArtSource(content = pspAt(PSP))
        show("psp", 0, pspAt(PSP), source)
        // 5 columns of 137 dp, 238 dp high at 0.575: more than the grid file's 400 px.
        assertEquals(5, layout!!.gridColumns(PSP))
        assertEquals(setOf("large"), decodedSizes(source))
    }

    @Test
    @Config(qualifiers = THOR)
    fun aMediumNesTileOnTheThorDrawsTheGridFile() {
        gridSize = GridSize.Medium
        val source = coverArtSource(content = pspAt(NES))
        show("psp", 0, pspAt(NES), source)
        // 6 columns of 112 dp, 154 dp high at 0.73: within the grid file's 400 px.
        assertEquals(6, layout!!.gridColumns(NES))
        assertEquals(setOf("grid"), decodedSizes(source))
    }

    @Test
    fun thePictureIsChosenByTheHeightItIsDrawnAt() {
        // The Thor's upper screen: 1920 px over 837 dp.
        val thor = Density(1920f / 837f)
        val thorLayout = PlumLayout(837.dp, 471.dp)
        fun cell(
            size: GridSize,
            shape: CoverShape,
        ): DpSize = thorLayout.copy(gridSize = size).let { DpSize(it.gridCoverWidth(shape), it.gridRowHeight(shape)) }
        // As saved: the grid file fits 560x400, the large one 1240x1080 with a box front at most 720 tall.
        val psp = GameArt(grid = ArtPicture("/psp/grid", 230, 400), large = ArtPicture("/psp/large", 414, 720), details = NO_DETAILS)
        val nes = GameArt(grid = ArtPicture("/nes/grid", 292, 400), large = ArtPicture("/nes/large", 525, 720), details = NO_DETAILS)
        assertSame("PSP at Large: 238 dp, 546 px", psp.large, psp.gridPicture(cell(GridSize.Large, PSP), thor))
        assertSame("PSP at Small: 141 dp, 324 px", psp.grid, psp.gridPicture(cell(GridSize.Small, PSP), thor))
        assertSame("NES at Medium: 154 dp, 352 px", nes.grid, nes.gridPicture(cell(GridSize.Medium, NES), thor))
        assertSame("NES at Large: 188 dp, 431 px", nes.large, nes.gridPicture(cell(GridSize.Large, NES), thor))
        // Either file stands in for the other.
        assertSame(psp.large, psp.copy(grid = null).gridPicture(cell(GridSize.Small, PSP), thor))
        assertSame(psp.grid, psp.copy(large = null).gridPicture(cell(GridSize.Large, PSP), thor))
        assertNull(GameArt(grid = null, large = null, details = NO_DETAILS).gridPicture(cell(GridSize.Medium, PSP), thor))
    }

    private companion object {
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"

        /** PSP's and NES's measured box fronts (platform data). */
        val PSP = CoverShape(0.575f)
        val NES = CoverShape(0.73f)

        val NO_DETAILS = GameDetails(year = null, publisher = "", genre = "", players = "", description = "")
    }
}
