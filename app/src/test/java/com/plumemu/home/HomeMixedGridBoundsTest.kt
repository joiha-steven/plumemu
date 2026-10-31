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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreui.testing.ReferenceShots
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
 * Recent and Favorites as grids, measured: the square class's columns
 * and cells at every grid size, the cells even and inside the grid, and in each cell the cover at
 * its own shape (the picture's, or its system's for a game without pictures), fitted, standing on the
 * cell's bottom edge and centred across it. At the RP5's 768, the Thor's 837 and the Nova's 576.
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
            val columns = sized.gridColumns(CoverClass.Square)
            val label = "${entry.id} at $each, ${sized.width}"
            assertEquals("$label: the square class's columns", columns, sized.gridColumns(MixedGridShape))
            val grid = bounds(HomeTags.GRID)
            val first = bounds(HomeTags.cover(0))
            val cellWidth = sized.gridCoverWidth(MixedGridShape)
            val cellHeight = sized.gridRowHeight(MixedGridShape)
            val gap = sized.gridGap(MixedGridShape)
            assertDp("$label: $columns columns end at the covers' span", first.left + sized.gridCoversWidth, bounds(HomeTags.cover(columns - 1)).right)
            for (index in 0 until minOf(columns * 2, entry.count)) {
                val cell = bounds(HomeTags.cover(index))
                val at = "$label: cell $index"
                assertDp("$at width", cellWidth, cell.right - cell.left)
                assertDp("$at height", cellHeight, cell.bottom - cell.top)
                if (index % columns > 0) assertDp("$at gap across", gap, cell.left - bounds(HomeTags.cover(index - 1)).right)
                if (index >= columns) assertDp("$at gap down", gap, cell.top - bounds(HomeTags.cover(index - columns)).bottom)
                assertTrue("$at inside the grid across", cell.left >= grid.left - TOLERANCE.dp() && cell.right <= grid.right + TOLERANCE.dp())
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
        val fillsAcross = (cell.right - cell.left).value / (cell.bottom - cell.top).value <= aspect
        if (fillsAcross) assertDp("$at: as wide as the cell", cell.right - cell.left, width) else assertDp("$at: as tall as the cell", cell.bottom - cell.top, height)
        assertDp("$at: stands on the cell's bottom", cell.bottom, cover.bottom)
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
     * The focused game without pictures (a tall NES tile in Recent): its ring is drawn around the tile's
     * own shape grown from the bottom centre, the shelf rect of the cell, not around the whole square cell.
     */
    @Test
    @Config(qualifiers = WIDE)
    fun theRingOfATextOnlyTallTileHugsTheTileNotTheCell() {
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
        val scale = PlumDimens.FOCUSED_COVER_SCALE
        val width = (tile.right - tile.left).value * density
        val height = (tile.bottom - tile.top).value * density
        val centreX = (tile.left.value + tile.right.value) / 2f * density
        val bottom = tile.bottom.value * density
        val centreY = bottom - height / 2f - PlumDimens.focusedLift.value * density
        val drawn = Rect(centreX - width * scale / 2f, centreY - height * scale / 2f, centreX + width * scale / 2f, centreY + height * scale / 2f)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val ring = (PlumDimens.focusRingGap.value + PlumDimens.focusRingWidth.value / 2f) * density
        val sides =
            mapOf(
                "top" to (Offset(drawn.center.x, drawn.top) to Offset(0f, -1f)),
                "left" to (Offset(drawn.left, drawn.center.y) to Offset(-1f, 0f)),
                "right" to (Offset(drawn.right, drawn.center.y) to Offset(1f, 0f)),
            )
        for ((side, edgeAndOut) in sides) {
            val (edge, out) = edgeAndOut
            assertTrue("the ring runs ${ring / density} dp outside the tile's $side edge", pixels.at(edge + out * ring).near(accent))
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
