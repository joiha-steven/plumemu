package com.plumemu.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumLayout
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Recent and Favorites as packed grids, measured: at every grid size the square class's row
 * height and gaps, the boxes inside the grid, and in each box the cover at its own shape
 * (the picture's, or its system's for a game without pictures), standing on the box's bottom edge and centred across it. At the RP5's 768, the Thor's 837 and the Nova's 576.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeMixedGridBoundsTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private fun bounds(tag: String): DpRect = compose.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** What is drawn in cell [index]: the decoded picture, or the placeholder in its system's shape. */
    private fun coverIn(
        index: Int,
        tag: String,
    ): DpRect = compose.onNode(hasTestTag(tag) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).getUnclippedBoundsInRoot()

    private fun assertDp(
        what: String,
        expected: Dp,
        actual: Dp,
    ) = assertEquals(what, expected.value, actual.value, TOLERANCE)

    private fun assertEveryGridSize(entryIndex: Int) {
        val source = mixedGridSource()
        val entry = MixedGridLibrary.entries[entryIndex]
        var size by mutableStateOf(GridSize.Small)
        var layout: PlumLayout? = null
        compose.setContent {
            PlumTheme {
                ProvideGridSize(size) {
                    layout = PlumTheme.layout
                    key(size) { HomeWithArt(source, gridActions(entryIndex, 0), MixedGridLibrary, gridIds = setOf(entry.id)) }
                }
            }
        }
        for (each in GridSize.entries) {
            size = each
            compose.waitForIdle()
            val sized = checkNotNull(layout)
            val label = "${entry.id} at $each, ${sized.width}"
            val grid = bounds(HomeTags.GRID)
            val gap = sized.gridGap(MixedGridShape)
            // The requirement changed: every row is scaled to span the grid at its own height, so cells are measured against
            // the packing worked out in PackedExpect, not against one cell height.
            val expected = expectedPacking(entry.rows.map { source.results.value[it.scrapeKey]?.grid?.aspect ?: entry.shapeOf(it).widthOverHeight }, sized, MixedGridShape)
            val rowGap = sized.gridRowGap(MixedGridShape)
            val shown = (0 until entry.count).filter { compose.onAllNodesWithTag(HomeTags.cover(it), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }.take(8)
            assertTrue("$label: covers are shown", shown.size >= 4)
            var before: DpRect? = null
            for (index in shown) {
                val cell = bounds(HomeTags.cover(index))
                val at = "$label: cell $index"
                assertEquals("$at height", expected.heights[expected.rowOf(index)], (cell.bottom - cell.top).value, TOLERANCE)
                assertEquals("$at width", expected.widths[index], (cell.right - cell.left).value, TOLERANCE)
                assertEquals("$at left", grid.left.value + expected.lefts[index], cell.left.value, TOLERANCE)
                assertTrue("$at inside the grid across", cell.left >= grid.left - TOLERANCE.dp() && cell.right <= grid.right + TOLERANCE.dp())
                before?.let { last ->
                    if (abs(cell.top.value - last.top.value) < TOLERANCE) {
                        // Boxes in a row are the board's gap apart (to the pixel's rounding).
                        assertEquals("$at gap across is the board's", gap.value, (cell.left - last.right).value, TOLERANCE + 0.5f)
                    } else {
                        assertEquals("$at starts a row at the grid's left", grid.left.value, cell.left.value, TOLERANCE)
                        assertDp("$at gap down", rowGap, cell.top - last.bottom)
                    }
                }
                before = cell
                assertCoverOnTheShelf(at, entry, index, cell, source)
            }
        }
    }

    /** The cover in [cell]: its own shape, as large as the cell allows, on the bottom edge, centred across. */
    private fun assertCoverOnTheShelf(
        at: String,
        entry: HomeEntry,
        index: Int,
        cell: DpRect,
        source: FakeArtSource,
    ) {
        val row = entry.rows[index]
        val picture = source.results.value[row.scrapeKey]?.grid
        val aspect = picture?.aspect ?: entry.shapeOf(row).widthOverHeight
        val cover = coverIn(index, if (picture != null) HomeTags.COVER_PICTURE else HomeTags.COVER_OWN_SHAPE)
        val width = cover.right - cover.left
        val height = cover.bottom - cover.top
        assertEquals("$at: its own shape", aspect, width.value / height.value, ASPECT_TOLERANCE)
        // The cover stands in its collar, which takes its padding of the cell.
        val side = CollarSize.Cover.sidePadding
        val innerWidth = cell.right - cell.left - side * 2
        val innerHeight = cell.bottom - cell.top - CollarSize.Cover.topPadding - CollarSize.Cover.bottomPadding
        val fillsAcross = innerWidth.value / innerHeight.value <= aspect
        if (fillsAcross) assertDp("$at: as wide as the cell's inner room", innerWidth, width) else assertDp("$at: as tall as the cell's inner room", innerHeight, height)
        assertDp("$at: its collar stands on the cell's bottom", cell.bottom - CollarSize.Cover.bottomPadding, cover.bottom)
        assertDp("$at: centred across", cover.left - cell.left, cell.right - cover.right)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideRecentGrid() = assertEveryGridSize(RECENT)

    @Test
    @Config(qualifiers = WIDE)
    fun wideFavoritesGrid() = assertEveryGridSize(FAVORITES)

    @Test
    @Config(qualifiers = THOR)
    fun thorRecentGrid() = assertEveryGridSize(RECENT)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowRecentGrid() = assertEveryGridSize(RECENT)

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFavoritesGrid() = assertEveryGridSize(FAVORITES)

    /** The fixture mixes the three classes and keeps one game without pictures in the cells measured. */
    @Test
    fun theFixtureMixesShapesAndHasAGameWithoutPictures() {
        val recent = MixedGridLibrary.entries[RECENT]
        val classes = recent.rows.map { recent.shapeOf(it).coverClass }.toSet()
        assertEquals(CoverClass.entries.toSet(), classes)
        val source = mixedGridSource()
        assertEquals(null, source.results.value[recent.rows[MIXED_NO_ART_INDEX].scrapeKey]?.grid)
        assertEquals(CoverClass.Tall, recent.shapeOf(recent.rows[MIXED_NO_ART_INDEX]).coverClass)
    }

    /**
     * The focused game without pictures (a tall NES tile in Recent): its collar hugs the tile's own shape (the shelf rect of the cell),
     * not the whole square cell: the lit groove is just outside the tile's top, left and right edges.
     */
    @Test
    @Config(qualifiers = WIDE)
    fun theCollarOfATextOnlyTallTileHugsTheTileNotTheCell() {
        var accent = Color.Unspecified
        compose.setContent {
            PlumTheme {
                accent = PlumTheme.colors.accent
                MixedGridBoard(RECENT, MIXED_NO_ART_INDEX)
            }
        }
        compose.waitForIdle()
        val density = compose.density.density
        val tile = coverIn(MIXED_NO_ART_INDEX, HomeTags.COVER_OWN_SHAPE)
        val cell = bounds(HomeTags.cover(MIXED_NO_ART_INDEX))
        assertTrue("a tall tile in a square cell is narrower than it", (tile.right - tile.left).value < (cell.right - cell.left).value - 1f)
        val drawn = Rect(tile.left.value * density, tile.top.value * density, tile.right.value * density, tile.bottom.value * density)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // The focused collar is drawn larger about its centre (never laid out larger): its groove, hugging the tile, is scaled with it.
        val lift = PlumDimens.FOCUSED_COVER_SCALE
        val centre = Offset(drawn.center.x, (drawn.top - CollarSize.Cover.topPadding.value * density + drawn.bottom + CollarSize.Cover.bottomPadding.value * density) / 2f)
        fun lifted(edge: Offset) = centre + (edge - centre) * lift
        val groove = CollarDimens.coverGroove.value * density / 2f * lift
        val sides =
            mapOf(
                "top" to (lifted(Offset(drawn.center.x, drawn.top)) to Offset(0f, -1f)),
                "left" to (lifted(Offset(drawn.left, drawn.center.y)) to Offset(-1f, 0f)),
                "right" to (lifted(Offset(drawn.right, drawn.center.y)) to Offset(1f, 0f)),
            )
        for ((side, edgeAndOut) in sides) {
            val (edge, out) = edgeAndOut
            assertTrue("the groove is lit just outside the tile's $side edge (lifted with the collar)", pixels.at(edge + out * groove).near(litGroove(accent)))
        }
    }

    private fun PixelMap.at(point: Offset): Color = this[point.x.roundToInt().coerceIn(0, width - 1), point.y.roundToInt().coerceIn(0, height - 1)]

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    private fun Float.dp(): Dp = Dp(this)

    private companion object {
        const val RECENT = 0
        const val FAVORITES = 1
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val TOLERANCE = 0.5f

        /** A cover's sides are whole pixels (0.5 dp at xhdpi), so its shape is a little off on small tiles. */
        const val ASPECT_TOLERANCE = 0.02f
    }
}
