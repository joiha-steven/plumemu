package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import com.plumemu.coreinput.Action
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.testing.ReferenceShots
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.GridSize
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.testing.TableText
import com.plumemu.coreui.theme.PlumTheme
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * The focused grid cover: not moved or grown, whole at every
 * edge of the grid (top row, first and last column, last row) at both reference widths, its collar's groove
 * lit around the picture, its neighbours never moved; and every tile names its game in
 * every state of its art (no result yet, none, a picture still decoding, a file that cannot be read).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ReferenceShots.ROBOLECTRIC_SDK])
class HomeFocusedCoverTest {
    private val compose = appComposeRule()

    @get:Rule
    val rules = rulesAround(compose)

    private var accent = Color.Unspecified
    private var columns = 0
    private var tileTitleSize = 0f
    private var background = Color.Unspecified

    private fun show(
        gridSize: GridSize = GridSize.Default,
        shape: CoverShape = CoverShape.Landscape,
        content: @Composable () -> Unit,
    ) = compose.setContent {
        PlumTheme(TableText.Default.copy(gridSize = gridSize)) {
            accent = PlumTheme.colors.accent
            columns = PlumTheme.layout.gridColumns(shape)
            tileTitleSize = PlumTheme.type.coverTitle(gridSize).fontSize.value
            background = PlumTheme.colors.bg
            content()
        }
    }

    private val snesRows get() = FakeHomeContent.withArtwork().entries[FakeHomeContent.BOARD_SNES_INDEX].rows

    private fun cell(index: Int): DpRect = compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).getUnclippedBoundsInRoot()

    /** Where the picture of cover [index] is, in px (the layout's: the focused one is drawn larger over it, see [liftedPicture]). */
    private fun restingPicture(index: Int): Rect {
        val node: SemanticsNode =
            compose.onNodeWithTag(HomeTags.cover(index), useUnmergedTree = true).fetchSemanticsNode().children
                .first { child -> child.config.any { it.value == HomeTags.COVER_PICTURE } }
        return Rect(node.positionInRoot, androidx.compose.ui.geometry.Size(node.size.width.toFloat(), node.size.height.toFloat()))
    }

    /** [picture] (as laid out) drawn [PlumDimens.FOCUSED_COVER_SCALE] larger about its collar's centre: where the focused cover's picture is on screen. */
    private fun liftedPicture(picture: Rect): Rect {
        val d = compose.density.density
        val collar = Rect(
            picture.left - CollarSize.Cover.sidePadding.value * d, picture.top - CollarSize.Cover.topPadding.value * d,
            picture.right + CollarSize.Cover.sidePadding.value * d, picture.bottom + CollarSize.Cover.bottomPadding.value * d,
        )
        val s = PlumDimens.FOCUSED_COVER_SCALE
        val c = collar.center
        return Rect(c.x + (picture.left - c.x) * s, c.y + (picture.top - c.y) * s, c.x + (picture.right - c.x) * s, c.y + (picture.bottom - c.y) * s)
    }

    /** The Super Nintendo grid with the game [pick] gives for the width's column count focused; that game's index. */
    private fun showGridFocusedOn(pick: (columns: Int) -> Int): Int {
        show { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, pick(PlumTheme.layout.gridColumns(CoverShape.Landscape))), gridIds = setOf("snes")) }
        compose.waitForIdle()
        return pick(columns)
    }

    /** One grid of the every-size check: its size, PSP (tall) or Super Nintendo (landscape), and the top row's first or last cover focused. */
    private data class SizeCase(
        val size: GridSize,
        val isPsp: Boolean,
        val isLast: Boolean,
    )

    /**
     * At every grid size the focused cover and its ring are whole in the first column and the last
     * on the top row, for landscape and tall covers (one composition; each case opens its grid anew).
     */
    private fun assertWholeAtEveryGridSize(
        label: String,
        pspShape: CoverShape = CoverShape.Portrait,
    ) {
        val library = CoverArtLibrary.let { board -> board.withEntries(board.entries.map { if (it.id == "psp") it.copy(coverShape = pspShape) else it }) }
        var case by mutableStateOf(SizeCase(GridSize.Small, isPsp = false, isLast = false))
        var focused = -1
        compose.setContent {
            PlumTheme(TableText.Default.copy(gridSize = case.size)) {
                accent = PlumTheme.colors.accent
                background = PlumTheme.colors.bg
                val shape = if (case.isPsp) pspShape else CoverShape.Landscape
                columns = PlumTheme.layout.gridColumns(shape)
                focused = if (case.isLast) columns - 1 else 0
                val system = if (case.isPsp) PSP_INDEX else FakeHomeContent.BOARD_SNES_INDEX
                key(case) { HomeWithArt(rememberSource { coverArtSource() }, gridActions(system, focused), library, gridIds = setOf(if (case.isPsp) "psp" else "snes")) }
            }
        }
        for (size in GridSize.entries) {
            for (isPsp in listOf(false, true)) {
                for (isLast in listOf(false, true)) {
                    case = SizeCase(size, isPsp, isLast)
                    compose.waitForIdle()
                    assertWholeWithItsRing("$label $size ${if (isPsp) "PSP" else "Super Nintendo"} game $focused of $columns columns", focused)
                }
            }
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFocusedCoverIsWholeAtEveryGridSize() = assertWholeAtEveryGridSize("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusedCoverIsWholeAtEveryGridSize() = assertWholeAtEveryGridSize("576x432")

    /** The Thor's width: Small tall (8 columns, 81 dp) exists only here, and PSP, the tallest tile (0.575), is 238 dp high at Large. */
    @Test
    @Config(qualifiers = THOR)
    fun thorFocusedCoverIsWholeAtEveryGridSize() = assertWholeAtEveryGridSize("837x471", CoverShape(0.575f))

    private fun PixelMap.at(point: Offset): Color = this[point.x.roundToInt().coerceIn(0, width - 1), point.y.roundToInt().coerceIn(0, height - 1)]

    private fun Color.near(other: Color): Boolean = abs(red - other.red) < 0.12f && abs(green - other.green) < 0.12f && abs(blue - other.blue) < 0.12f

    /**
     * The focused cover [index] is drawn whole (a pixel just inside each edge is the picture's, not the groove's), its collar's groove
     * is lit in the accent on every side, the box stands where it does at rest (centred, on its collar's bottom), and the collar with its
     * halo keeps clear of both bars.
     */
    private fun assertWholeWithItsRing(
        label: String,
        index: Int,
    ) {
        val density = compose.density.density
        val resting = restingPicture(index)
        val drawn = liftedPicture(resting)
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        // Past the picture's antialiased last pixel, still inside the art.
        val inside = 4f
        val groove = CollarDimens.coverGroove.value * density * PlumDimens.FOCUSED_COVER_SCALE
        val sides =
            mapOf(
                "top" to (Offset(drawn.center.x, drawn.top) to Offset(0f, -1f)),
                "bottom" to (Offset(drawn.center.x, drawn.bottom) to Offset(0f, 1f)),
                "left" to (Offset(drawn.left, drawn.center.y) to Offset(-1f, 0f)),
                "right" to (Offset(drawn.right, drawn.center.y) to Offset(1f, 0f)),
            )
        for ((side, edgeAndOut) in sides) {
            val (edge, out) = edgeAndOut
            // Not the bottom: the box's drop shadow falls on its groove there.
            if (side == "bottom") continue
            val picture = pixels.at(edge - out * inside)
            // The groove is about 3 px wide and shaded at its inner edge: the sample across it (and a little past) that is closest to a lit groove.
            val lit = (1..8).map { pixels.at(edge + out * (groove * it / 4f)) }.minBy { abs(it.red - litGroove(accent).red) + abs(it.green - litGroove(accent).green) + abs(it.blue - litGroove(accent).blue) }
            // Unconditional, and tight enough for a dark picture: only a picture that is really missing leaves the two pixels equal.
            assertTrue("$label: the picture reaches its $side edge (not cut): picture $picture, groove $lit", maxOf(abs(picture.red - lit.red), abs(picture.green - lit.green), abs(picture.blue - lit.blue)) > 0.02f)
            assertTrue("$label: the groove is lit in the accent on the $side side ($lit)", lit.near(litGroove(accent)))
        }
        // The lifted collar and its ring (the halo may fade into the bar gaps) keep clear of both bars.
        val reach = PlumDimens.focusRingOutset.value
        val top = compose.onNodeWithTag(HomeTags.TOP_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val hints = compose.onNodeWithTag(HomeTags.HINT_BAR, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("$label: the ring stays under the top bar", drawn.top / density - CollarSize.Cover.topPadding.value * PlumDimens.FOCUSED_COVER_SCALE - reach >= top.bottom.value - EPSILON)
        assertTrue("$label: the ring stays over the hint bar", drawn.bottom / density + CollarSize.Cover.bottomPadding.value * PlumDimens.FOCUSED_COVER_SCALE + reach <= hints.top.value + EPSILON)
        // Layout is unchanged by the lift: the box stands where it does at rest.
        val cell = cell(index)
        assertEquals("$label: centred on its cell", (cell.left.value + cell.right.value) / 2f, resting.center.x / density, EPSILON)
        assertEquals("$label: the collar stands on its cell's bottom", cell.bottom.value - CollarSize.Cover.bottomPadding.value, resting.bottom / density, EPSILON)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFocusedCoverIsWholeAtTheTopLeft() {
        showGridFocusedOn { 0 }
        assertWholeWithItsRing("768x432 game 0", 0)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFocusedCoverIsWholeAtTheTopRight() {
        val last = showGridFocusedOn { it - 1 }
        assertWholeWithItsRing("768x432 game $last", last)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideFocusedCoverIsWholeOnTheLastRow() {
        val last = showGridFocusedOn { snesRows.lastIndex }
        assertWholeWithItsRing("768x432 the last game", last)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusedCoverIsWholeAtTheTopLeft() {
        showGridFocusedOn { 0 }
        assertWholeWithItsRing("576x432 game 0", 0)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusedCoverIsWholeAtTheTopRight() {
        val last = showGridFocusedOn { it - 1 }
        assertWholeWithItsRing("576x432 game $last", last)
    }

    @Test
    @Config(qualifiers = NARROW)
    fun narrowFocusedCoverIsWholeOnTheLastRow() {
        val last = showGridFocusedOn { snesRows.lastIndex }
        assertWholeWithItsRing("576x432 the last game", last)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aFocusMoveMovesNoNeighbour() {
        lateinit var state: StripState
        show { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) }
        compose.waitForIdle()
        val shown = (0 until columns * 2).associateWith { cell(it) }
        compose.runOnIdle { state.dispatch(Action.TapItem(Column.Games, 1)) }
        compose.waitForIdle()
        for ((index, before) in shown) assertEquals("cover $index stays where it was", before, cell(index))
        val box = restingPicture(1)
        assertTrue("the focus moved: game 1's box does not grow (${box.height})", box.height / compose.density.density <= (cell(1).bottom - cell(1).top).value - CollarSize.Cover.topPadding.value - CollarSize.Cover.bottomPadding.value + EPSILON)
    }

    /**
     * No other cover draws outside the grid: the rows cut by the grid's top and bottom edges end
     * there (the gaps to the bars are the strip's background), at rest and while the rows glide.
     * The focused cover [focused] and its ring (with its glow) may: their columns are left out.
     */
    private fun assertOnlyTheFocusedCoverLeavesTheGrid(
        label: String,
        focused: Int,
    ) {
        val density = compose.density.density
        val grid = compose.onNodeWithTag(HomeTags.GRID, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val pixels = compose.onRoot().captureToImage().toPixelMap()
        val left = (grid.left.value * density).roundToInt()
        val right = (grid.right.value * density).roundToInt()
        val ring = cell(focused)
        val margin = (PlumDimens.focusRingOutset.value + GLOW_DP) * density
        val ringColumns = (ring.left.value * density - margin)..(ring.right.value * density + margin)
        for (outside in listOf(grid.top.value * density - 2f * density, grid.bottom.value * density + 2f * density)) {
            val y = outside.roundToInt()
            val foreign = (left until right).filterNot { it.toFloat() in ringColumns || pixels[it, y].near(background) }.map { "$it ${pixels[it, y]}" }
            assertEquals("$label: nothing drawn ${if (outside < grid.top.value * density) "above" else "below"} the grid at y=$y px", emptyList<String>(), foreign)
        }
    }

    @Test
    @Config(qualifiers = WIDE)
    fun wideRowsStayInsideTheGridAtRestAndGliding() = assertRowsStayInside("768x432")

    @Test
    @Config(qualifiers = NARROW)
    fun narrowRowsStayInsideTheGridAtRestAndGliding() = assertRowsStayInside("576x432")

    private fun assertRowsStayInside(label: String) {
        lateinit var state: StripState
        // Row 4 focused: the grid is scrolled, rows cut at both edges, the focused one in the middle.
        show { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, 0), gridIds = setOf("snes"), onState = { state = it }) }
        compose.waitForIdle()
        repeat(4) {
            compose.runOnIdle { state.dispatch(Action.Move(com.plumemu.coreinput.Direction.Down)) }
            compose.waitForIdle()
        }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        assertOnlyTheFocusedCoverLeavesTheGrid("$label at rest", state.focus.rowOf(state.focus.systemIndex.let { CoverArtLibrary.entries[it].id }))
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { state.dispatch(Action.Move(com.plumemu.coreinput.Direction.Down)) }
        compose.mainClock.advanceTimeBy(GLIDE_PART_MS)
        assertOnlyTheFocusedCoverLeavesTheGrid("$label while gliding", state.focus.rowOf(CoverArtLibrary.entries[state.focus.systemIndex].id))
        compose.mainClock.autoAdvance = true
    }

    /** Every cover cell on screen whose subtree carries its game's title. */
    private fun titledCells(): Map<Int, Boolean> {
        val rows = snesRows
        return (0 until columns * 2).associateWith { index ->
            val inCell = hasAnyAncestor(hasTestTag(HomeTags.cover(index))) or hasTestTag(HomeTags.cover(index))
            // A tile names its game in text; a poster cover (no picture for good) in its content description.
            compose.onAllNodes((hasText(rows[index].title) or hasContentDescription(rows[index].title)) and inCell, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The grid cells (of the first two rows) whose cover is a poster. */
    private fun posterCells(): Set<Int> =
        (0 until columns * 2).filter { index ->
            compose.onAllNodes(hasTestTag(HomeTags.POSTER_COVER) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }.toSet()

    private fun assertEveryTileTitled(label: String) {
        val untitled = titledCells().filterValues { !it }.keys
        assertEquals("$label: every tile names its game", emptySet<Int>(), untitled)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun everyTileIsTitledBeforeItsResultIsRead() {
        val never =
            object : HomeArtSource {
                override fun observe(keys: List<String>): Flow<Map<String, GameArt>> = flow { awaitCancellation() }

                override suspend fun extras(key: String): DetailsExtras = DetailsExtras.None

                override val images: CoverImages = FakeCoverImages()
            }
        show { HomeWithArt(never, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        assertEveryTileTitled("no result read yet")
    }

    @Test
    @Config(qualifiers = WIDE)
    fun everyTileIsTitledWithoutAResultOrAPicture() {
        val titles = snesRows.map { it.title }
        val noArt = titles.filterIndexed { i, _ -> i % 2 == 0 }.toSet()
        val absent = titles.filterIndexed { i, _ -> i % 2 == 1 }.toSet()
        show { HomeWithArt(rememberSource { coverArtSource(noArt = noArt, absent = absent) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        assertEveryTileTitled("no result, or a result without a picture")
        assertEquals("no picture for good: every one wears a poster", (0 until columns * 2).toSet(), posterCells())
    }

    @Test
    @Config(qualifiers = WIDE)
    fun everyTileIsTitledWhilePicturesDecode() {
        val paths = snesRows.map { "/media/${it.id}/box-front-grid.webp" }.toSet()
        show { HomeWithArt(rememberSource { coverArtSource(pending = paths) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        assertEveryTileTitled("pictures still decoding")
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aPictureThatCannotBeReadBecomesItsPoster() {
        val gone = "/media/${snesRows[DONKEY_KONG_COUNTRY_2].id}/box-front-grid.webp"
        show { HomeWithArt(rememberSource { coverArtSource(unreadable = setOf(gone)) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        val titled = titledCells()
        assertTrue("the unreadable cover keeps its name", titled.getValue(DONKEY_KONG_COUNTRY_2))
        assertTrue("and wears its poster", DONKEY_KONG_COUNTRY_2 in posterCells())
        val pictures = compose.onAllNodes(hasTestTag(HomeTags.COVER_PICTURE), useUnmergedTree = true).fetchSemanticsNodes()
        assertTrue("the others show their pictures", pictures.size >= columns * 2)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aPictureInMemoryIsDrawnAtOnceWithNoTitleUnderIt() {
        show { HomeWithArt(rememberSource { coverArtSource() }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        // No frame advanced: what a fast scroll shows for covers already decoded.
        val titled = titledCells().filterValues { it }.keys
        assertEquals("no title composed under a cover in memory (no flash of names on a fast scroll)", emptySet<Int>(), titled)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aPictureDecodedNowShowsTheTitleUntilItHasFadedIn() {
        val gate = CompletableDeferred<Unit>()
        val decoding =
            object : CoverImages {
                private val drawn = FakeCoverImages()

                override fun cached(
                    picture: ArtPicture,
                    widthPx: Int,
                    heightPx: Int,
                ): ImageBitmap? = null

                override suspend fun load(
                    picture: ArtPicture,
                    widthPx: Int,
                    heightPx: Int,
                ): ImageBitmap? {
                    gate.await()
                    return drawn.load(picture, widthPx, heightPx)
                }
            }
        val source = FakeArtSource(artFor(FakeHomeContent.withArtwork()), images = decoding)
        show { HomeWithArt(source, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        assertEveryTileTitled("while decoding")
        gate.complete(Unit)
        compose.mainClock.advanceTimeBy(SETTLE_MS)
        compose.waitForIdle()
        assertEquals("faded in: the titles under the pictures are gone", emptySet<Int>(), titledCells().filterValues { it }.keys)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun aTileWithoutAPictureHoldsItsTitleAndYearAtTheBottomInsideItsPadding() {
        assertTileLayout()
    }

    @Test
    @Config(qualifiers = NARROW)
    fun aNarrowTileWithoutAPictureHoldsItsTitleAndYearAtTheBottomInsideItsPadding() {
        assertTileLayout()
    }

    private fun assertTileLayout() {
        val index = EARTHBOUND
        val row = snesRows[index]
        // At Medium, the focus title's 13 sp (Small, the default, sets 11 sp).
        // Still decoding: the titled tile stands (a game with no picture for good wears a poster instead).
        val pending = snesRows.map { "/media/${it.id}/box-front-grid.webp" }.toSet()
        show(gridSize = GridSize.Medium) { HomeWithArt(rememberSource { coverArtSource(pending = pending) }, gridActions(FakeHomeContent.BOARD_SNES_INDEX, MEGA_MAN_X), gridIds = setOf("snes")) }
        val year = ArtworkFakes.detailsFor(row.title, index).year.toString()
        fun inCell(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val cell = cell(index)
        val title = inCell(row.title)
        val yearBox = inCell(year)
        val padding = PlumDimens.coverPaddingLandscape.value
        // The tile stands in its collar.
        val side = CollarSize.Cover.sidePadding.value
        // A packed cell may be wider than its tile (the tile and its collar stand centred in it), so the padding is measured from the tile.
        val tile = compose.onNode((hasTestTag(HomeTags.COVER_OWN_SHAPE) or hasTestTag(HomeTags.COVER_PICTURE)) and hasAnyAncestor(hasTestTag(HomeTags.cover(index))), useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertTrue("the tile is inside its cell's collar", tile.left.value >= cell.left.value + side - EPSILON && tile.right.value <= cell.right.value - side + EPSILON)
        assertEquals("the title starts at the tile's padding", tile.left.value + padding, title.left.value, EPSILON)
        assertTrue("the title ends inside the padding", title.right.value <= tile.right.value - padding + EPSILON)
        // In the tile, which is the cell less its collar; the tile is a little shorter than before, so the title may start a little above the middle.
        val tileTop = cell.top.value + CollarSize.Cover.topPadding.value
        val tileBottom = cell.bottom.value - CollarSize.Cover.bottomPadding.value
        // The title and the year stack from the bottom, so a taller line (Jost's 1.445 em) lifts the title's top; its middle is what stays low.
        val titleMiddle = (title.top.value + title.bottom.value) / 2
        assertTrue("the title is in the lower half of the tile (a label, not an empty box): title $title in cell $cell", titleMiddle >= tileTop + (tileBottom - tileTop) * TITLE_MIDDLE_FROM - EPSILON)
        assertTrue("the year under the title", yearBox.top >= title.bottom)
        assertEquals("the year starts on the title's line", title.left.value, yearBox.left.value, EPSILON)
        assertEquals("the year sits on the padding above the tile's bottom", cell.bottom.value - CollarSize.Cover.bottomPadding.value - padding, yearBox.bottom.value, EPSILON)
        assertEquals("the title is set in the focus title's 13 sp", 13f, tileTitleSize, 0.01f)
    }

    private companion object {
        const val EPSILON = 0.6f
        const val SETTLE_MS = 400L
        const val GLIDE_PART_MS = 48L
        const val GLOW_DP = 8f
        const val TITLE_MIDDLE_FROM = 0.5f
        const val DONKEY_KONG_COUNTRY_2 = 1
        const val EARTHBOUND = 2
        const val WIDE = "w768dp-h432dp-land-xhdpi"
        const val NARROW = "w576dp-h432dp-land-xhdpi"
        const val THOR = "w837dp-h471dp-land-xhdpi"
    }
}
