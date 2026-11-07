package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The narrowest grid tile allowed (docs/design/rules.md › Grid): room for the Small title and the ring. */
private val MIN_TILE_WIDTH = 70.dp

/** The width formulas of docs/design/rules.md at the four reference widths. */
class PlumLayoutTest {
    private val rp5 = PlumLayout(768.dp, 432.dp, gridSize = GridSize.Medium)
    private val thor = PlumLayout(837.dp, 471.dp, gridSize = GridSize.Medium)
    private val rp4Pro = PlumLayout(667.dp, 375.dp, gridSize = GridSize.Medium)
    private val nova = PlumLayout(576.dp, 432.dp, gridSize = GridSize.Medium)

    /** The keyboard (design/rules.md › Keyboard): 10 columns of 44 with 4 dp gaps, a 476 block centred. */
    @Test
    fun keyboardColumnsAre44AndTheBlockIs476OnEveryReferenceWidth() {
        for (screen in listOf(rp5, thor, rp4Pro, nova)) {
            assertEquals(44f, screen.keyboardColumnWidth.value, 0.001f)
            assertEquals(476f, screen.keyboardBlockWidth.value, 0.001f)
            // The block plus the 24 margins fits, so it is centred with at least 24 each side.
            assertTrue((screen.width.value - 476f) / 2 >= 24f)
        }
        assertEquals(44f, PlumLayout.keyboardColumnWidth(524.dp).value, 0.001f)
        // Narrower than 476 + 48: the block shrinks to width − 48 as before.
        assertEquals(40f, PlumLayout.keyboardColumnWidth(484.dp).value, 0.001f)
        assertEquals(436f, PlumLayout.keyboardBlockWidth(484.dp).value, 0.001f)
        assertEquals(((400f - 48f - 36f) / 10f), PlumLayout.keyboardColumnWidth(400.dp).value, 0.001f)
        assertEquals(352f, PlumLayout.keyboardBlockWidth(400.dp).value, 0.001f)
    }

    /**
     * Keys are 36 at every text size (labels grow, keys do not); the tray is 8 above the keys, the keys,
     * a 6 gap, the hint bar (22 x the height factor) and the 12 margin; and it is well under half the screen.
     */
    @Test
    fun keyboardKeysAre36AtEverySizeAndTheTrayIsUnderHalfTheScreen() {
        fun at(
            layout: PlumLayout,
            size: TextSize,
        ) = layout.copy(textScale = size.heightScale, hintScale = size.hintHeightScale)
        for (screen in listOf(rp5, thor, rp4Pro, nova)) {
            assertEquals(164f, at(screen, TextSize.Small).keyboardHeight.value, 0.001f)
            assertEquals(201f, at(screen, TextSize.Small).keyboardTrayHeight.value, 0.001f)
            assertEquals(18f, at(screen, TextSize.Small).keyboardIconSize.value, 0.001f)
            assertEquals("the field's caret", 1.5f, at(screen, TextSize.Small).caretWidth.value, 0.001f)
            assertEquals(1.5f * TextSize.Largest.heightScale, at(screen, TextSize.Largest).caretWidth.value, 0.001f)
            for (size in TextSize.entries) {
                val l = at(screen, size)
                assertEquals("${screen.height} at $size", 36f, l.keyboardKeyHeight.value, 0.001f)
                assertEquals(8f + 4 * 36f + 12f, l.keyboardHeight.value, 0.001f)
                assertEquals(l.keyboardHeight.value + 6f + 22f * size.hintHeightScale + 9f, l.keyboardTrayHeight.value, 0.001f)
            }
        }
        // The half-screen cap no longer binds where it used to (the pinned 0.6 and 3.9 dp overshoots on 432 high
        // are gone): every size on 432 and 471 high is under half; the RP4 Pro (375, half 187.5) is not asked to be.
        for (screen in listOf(rp5, thor, nova)) {
            for (size in TextSize.entries) {
                val l = at(screen, size)
                assertTrue("${screen.height} at $size: ${l.keyboardTrayHeight}", l.keyboardTrayHeight.value <= screen.height.value / 2)
            }
        }
        assertEquals(201f + 22f * (TextSize.Largest.hintHeightScale - 1f), at(nova, TextSize.Largest).keyboardTrayHeight.value, 0.001f)
    }

    @Test
    fun widthClassSplitsAt768() {
        assertEquals(WidthClass.Wide, rp5.widthClass)
        assertEquals(WidthClass.Wide, thor.widthClass)
        assertEquals(WidthClass.Narrow, rp4Pro.widthClass)
        assertEquals(WidthClass.Narrow, nova.widthClass)
        assertEquals(WidthClass.Narrow, WidthClass.of(767.9.dp))
    }

    @Test
    fun gamesListTakesTheExtraWidthOnWideScreens() {
        assertEquals(400.dp, rp5.gamesListWidth)
        assertEquals(469.dp, thor.gamesListWidth)
        // From 640 dp the narrow list ends inside the right margin with the systems open: width − 272 at Medium, 400 at most.
        assertEquals(395.dp, rp4Pro.gamesListWidth)
        assertEquals(400.dp, nova.gamesListWidth)
    }

    @Test
    fun theNovasFourThreeScreenKeepsItsListInsideTheMargin() {
        val fourThree = PlumLayout(640.dp, 432.dp)
        assertEquals(WidthClass.Narrow, fourThree.widthClass)
        assertEquals(368.dp, fourThree.gamesListWidth)
        assertEquals(368.dp, fourThree.narrowListWidth)
        // Margin 24 + systems column 200 + gap 24 + list 368 + margin 24 = 640.
        assertEquals(640.dp, 224.dp + 24.dp + fourThree.gamesListWidth + 24.dp)
        assertEquals(368.dp, PlumLayout(640.dp, 480.dp).gamesListWidth)
        // A text size that would leave less than 320 keeps the list at 400 (its preview runs under the fade, as at 576).
        assertEquals(400.dp, PlumLayout(640.dp, 432.dp, textScale = 1.45f).gamesListWidth)
        assertEquals(false, PlumLayout(640.dp, 432.dp, textScale = 1.45f).isNarrowFitted)
        assertEquals(true, fourThree.isNarrowFitted)
        assertEquals(false, nova.isNarrowFitted)
    }

    @Test
    fun listWithoutArtTakesTheDetailsSpace() {
        // RP5-N-noart draws the list 672 wide at 768: from x 72 (peek 48 + gap 24) to the 24 margin.
        assertEquals(672.dp, rp5.listWidthWithoutArt)
        assertEquals(741.dp, thor.listWidthWithoutArt)
        assertEquals(571.dp, rp4Pro.listWidthWithoutArt)
        assertEquals(480.dp, nova.listWidthWithoutArt)
        assertEquals(696.dp, PlumLayout(768.dp, 432.dp, peek = 24.dp).listWidthWithoutArt)
    }

    @Test
    fun detailsStopIsWidthMinus104() {
        assertEquals(472.dp, nova.detailsStopWidth)
        assertEquals(563.dp, rp4Pro.detailsStopWidth)
        assertEquals(664.dp, rp5.detailsStopWidth)
    }

    @Test
    fun gridAreaIsWidthMinus96() {
        assertEquals(672.dp, rp5.gridAreaWidth)
        assertEquals(741.dp, thor.gridAreaWidth)
        assertEquals(571.dp, rp4Pro.gridAreaWidth)
        assertEquals(480.dp, nova.gridAreaWidth)
    }

    /** The four reference layouts with the grid at [size]; the fixtures above are at Medium (the default). */
    private fun at(size: GridSize) = listOf(rp5, thor, rp4Pro, nova).map { it.copy(gridSize = size) }

    /** The board's landscape grid (RP5-3, Nova-4) is the Large size; its portrait grid (RP5-4, Nova-5) the Medium one. */
    private val boardLandscape = at(GridSize.Large)

    @Test
    fun gridColumnsFollowTheCoverShape() {
        assertEquals(listOf(3, 3, 2, 2), boardLandscape.map { it.gridColumns(CoverClass.Landscape) })
        assertEquals(listOf(5, 6, 4, 4), listOf(rp5, thor, rp4Pro, nova).map { it.gridColumns(CoverClass.Tall) })
        assertEquals("the default", GridSize.Medium, PlumLayout(768.dp, 432.dp).gridSize)
    }

    /**
     * The grid sizes' columns, as listed in docs/design/rules.md › Grid: (RP5 768, Thor 837,
     * RP4 Pro 667, Nova 576) for each class at Small, Medium and Large. The Thor's are the
     * design targets; the RP5 loses one column at each larger size.
     */
    @Test
    fun gridSizesGiveTheTableOfColumns() {
        val table =
            mapOf(
                CoverClass.Landscape to listOf(listOf(5, 5, 4, 3), listOf(4, 4, 3, 3), listOf(3, 3, 2, 2)),
                CoverClass.Square to listOf(listOf(6, 6, 5, 4), listOf(4, 5, 4, 3), listOf(3, 4, 3, 2)),
                CoverClass.Tall to listOf(listOf(7, 8, 6, 5), listOf(5, 6, 4, 4), listOf(4, 5, 4, 3)),
            )
        for ((coverClass, bySize) in table) {
            GridSize.entries.forEachIndexed { i, size ->
                assertEquals("$coverClass at $size", bySize[i], at(size).map { it.gridColumns(coverClass) })
            }
        }
    }

    @Test
    fun theThorKeepsItsColumnsAtItsDefaultLargeText() {
        // The Thor starts at Large text (TextSize.Default): the peek grows to 52.8 and the area to 736.2.
        val thorLarge = PlumLayout(837.dp, 471.dp, peek = PlumDimens.peekWidth * TextSize.Large.heightScale, textScale = TextSize.Large.heightScale)
        val targets = mapOf(CoverClass.Landscape to listOf(5, 4, 3), CoverClass.Square to listOf(6, 5, 4), CoverClass.Tall to listOf(8, 6, 5))
        for ((coverClass, columns) in targets) {
            assertEquals(coverClass.name, columns, GridSize.entries.map { thorLarge.copy(gridSize = it).gridColumns(coverClass) })
        }
    }

    /**
     * Each count holds with a few dp of area more or less at every reference width: 5 dp narrower
     * (a peek one text size wider) and 5 dp wider, so a padding change moves no column.
     */
    @Test
    fun gridColumnsAreNotAtTheEdgeOfTheirCount() {
        for (size in GridSize.entries) {
            for (layout in at(size)) {
                for (coverClass in CoverClass.entries) {
                    val columns = layout.gridColumns(coverClass)
                    for (delta in listOf(-5, 5)) {
                        val moved = layout.copy(peek = layout.peek - delta.dp)
                        assertEquals("$coverClass at $size, ${layout.width} with ${delta}dp of area", columns, moved.gridColumns(coverClass))
                    }
                }
            }
        }
    }

    /**
     * No tile is too small for its title or its ring: at least 70 dp wide (Small tall: 74 on the Thor
     * at Medium text, 78 on the Nova), so the Small title keeps 50 dp between the 10 dp paddings and
     * steps down to its smallest size when a word does not fit (`TileTitleFit`). It was 78 with the
     * board's gaps of 12 / 14; the wider 20 / 22 gaps narrow the covers and
     * keep every column count. The ring reaches 5.5 dp out, inside the gap.
     */
    @Test
    fun everyTileHasRoomForATitleAndARing() {
        val shapes = listOf(CoverShape.Landscape, CoverShape(1.12f), CoverShape(0.575f), CoverShape(0.73f))
        val texts = TextSize.entries
        for (size in GridSize.entries) {
            for (base in listOf(rp5, thor, rp4Pro, nova)) {
                for (text in texts) {
                    val layout = base.copy(gridSize = size, peek = PlumDimens.peekWidth * text.heightScale, textScale = text.heightScale)
                    for (shape in shapes) {
                        val width = layout.gridCoverWidth(shape)
                        assertTrue("${shape.coverClass} at $size on ${base.width} at $text: $width", width >= MIN_TILE_WIDTH)
                    }
                }
            }
        }
        val thorSmall = thor.copy(gridSize = GridSize.Small)
        assertEquals(gapped(86.7917f, thorSmall.gridColumns(CoverShape(0.73f))), thorSmall.gridCoverWidth(CoverShape(0.73f)).value, 0.01f)
    }

    /** A cover width the board gave with gaps of 10 px (6.67 dp), as it is with the gaps now [PlumDimens.gridGapAcross]: the columns share what the wider gaps take. */
    private fun gapped(
        board: Float,
        columns: Int,
    ) = board - (PlumDimens.gridGapAcross.value - 10f / 1.5f) * (columns - 1) / columns

    /** The narrowest grid on the Nova (576) still shows whole rows with titles: Small tall, five 78.4 wide. */
    @Test
    fun narrowGridsStayUsable() {
        val novaSmall = nova.copy(gridSize = GridSize.Small)
        assertEquals(gapped(90.6667f, novaSmall.gridColumns(CoverShape(0.73f))), novaSmall.gridCoverWidth(CoverShape(0.73f)).value, 0.01f)
        assertEquals(gapped(155.5556f, novaSmall.gridColumns(CoverShape.Landscape)), novaSmall.gridCoverWidth(CoverShape.Landscape).value, 0.01f)
        for (size in GridSize.entries) {
            for (shape in listOf(CoverShape.Landscape, CoverShape(1.12f), CoverShape(0.575f))) {
                assertTrue(nova.copy(gridSize = size).gridRowsThatFit(shape) >= 1)
            }
        }
    }

    @Test
    fun gridCoversSpanTheWholeArea() {
        // The requirement changed (it was the area less the board's 4 dp padding each side): covers run from where the list starts
        // to the right margin the clock and the hint bar end on.
        assertEquals(listOf(672.dp, 741.dp, 571.dp, 480.dp), listOf(rp5, thor, rp4Pro, nova).map { it.gridCoversWidth })
        assertEquals(listOf(rp5, thor, rp4Pro, nova).map { it.gridAreaWidth }, listOf(rp5, thor, rp4Pro, nova).map { it.gridCoversWidth })
    }

    @Test
    fun gridCoverWidthSharesTheSpanAfterTheGaps() {
        // `colW = (gridW − gap × (cols − 1)) / cols`: the columns and gaps are exactly the grid's width.
        // The gap between collars is 18 dp for every class (the board's were 6.67 across, 9.33 down; the numbers below are the board's, less what the wider gap takes).
        val landscape = listOf(219.5556f, 242.5556f, 282.1667f, 236.6667f)
        val portrait = listOf(129.0667f, 117.9444f, 137.75f, 115f)
        boardLandscape.zip(listOf(rp5, thor, rp4Pro, nova)).forEachIndexed { i, (large, layout) ->
            assertEquals(gapped(landscape[i], large.gridColumns(CoverShape.Landscape)), large.gridCoverWidth(CoverShape.Landscape).value, 0.01f)
            assertEquals(gapped(portrait[i], layout.gridColumns(CoverShape.Portrait)), layout.gridCoverWidth(CoverShape.Portrait).value, 0.01f)
        }
        assertEquals(18f, rp5.gridGap(CoverShape.Landscape).value, 0.01f)
        assertEquals(18f, rp5.gridGap(CoverShape.Portrait).value, 0.01f)
        assertEquals(18f, rp5.gridRowGap(CoverShape.Portrait).value, 0.01f)
    }

    @Test
    fun gridRowsKeepTheCoverShape() {
        // At every size a row is as tall as a box (collar chrome 8 across, 8.67 down) one cover width wide is for the shape's picture:
        // `(cover - 8) / ratio + 8.67`, not rounded, whatever the grid's height (the requirement changed: Medium and Small used to
        // take three and four rows from the strip's height).
        for (size in GridSize.entries) {
            for (layout in listOf(rp5, thor, rp4Pro, nova).map { it.copy(gridSize = size) }) {
                for (shape in listOf(CoverShape.Landscape, CoverShape.Portrait, CoverShape(1f), CoverShape(0.575f))) {
                    val cover = layout.gridCoverWidth(shape).value
                    assertEquals("$size ${shape.widthOverHeight} on ${layout.width}", (cover - 8f) / shape.widthOverHeight + 8.6667f, layout.gridRowHeight(shape).value, 0.01f)
                }
            }
        }
    }

    @Test
    fun stripHeightAndGridRowsThatFit() {
        assertEquals(listOf(350.dp, 389.dp, 293.dp, 350.dp), listOf(rp5, thor, rp4Pro, nova).map { it.stripHeight })
        // The cover grid is 12 dp shorter (its top gap is 14, 4 over the bar gap, and 8 dp stay free at the bottom, each the lifted row's reach): the Nova's landscape grid (two rows of 161
        // with a 22 gap need 344, 336 are left) shows one whole row and a second one cut, where it showed two.
        assertEquals(listOf(338.dp, 377.dp, 281.dp, 338.dp), listOf(rp5, thor, rp4Pro, nova).map { it.gridStripHeight })
        assertEquals(listOf(2, 2, 1, 1), boardLandscape.map { it.gridRowsThatFit(CoverShape.Landscape) })
        // Medium (the default) follows the width: the rows are as tall as the columns make them.
        assertEquals(listOf(2, 2, 1, 2), listOf(rp5, thor, rp4Pro, nova).map { it.gridRowsThatFit(CoverShape.Portrait) })
        assertEquals(listOf(3, 2), listOf(boardLandscape[0].gridColumns(CoverShape.Landscape), boardLandscape[3].gridColumns(CoverShape.Landscape)))
        assertEquals(listOf(5, 4), listOf(rp5.gridColumns(CoverShape.Portrait), nova.gridColumns(CoverShape.Portrait)))
    }

    /**
     * The focused cover's collar halo (the cover itself does not move) reaches [PlumDimens.focusHaloReach] past its collar:
     * it stays inside the grid's top gap; it may overlap a neighbour's gap, which is 18 dp across and down
     * between collars, for every cover class.
     */
    @Test
    fun theGridGapsHoldTheHaloAndKeepCollarsApart() {
        val reach = PlumDimens.focusHaloReach.value
        assertTrue("the halo ($reach dp) fits the top gap", reach <= PlumDimens.gridTopGap.value)
        for (size in GridSize.entries) {
            for (layout in at(size)) {
                for (shape in listOf(CoverShape(0.575f), CoverShape.Portrait, CoverShape(1f), CoverShape(1.12f), CoverShape.Landscape)) {
                    assertEquals("$size ${layout.width} $shape: collars are 18 dp apart across", 18f, layout.gridGap(shape).value, 0.01f)
                    assertEquals("$size ${layout.width} $shape: collars are 18 dp apart down", 18f, layout.gridRowGap(shape).value, 0.01f)
                }
            }
        }
    }

    @Test
    fun coverClassesSplitAt085And12() {
        assertEquals(CoverClass.Landscape, CoverShape(1.2f).coverClass)
        assertEquals(CoverClass.Landscape, CoverShape(1.37f).coverClass)
        assertEquals(CoverClass.Square, CoverShape(1.19f).coverClass)
        assertEquals(CoverClass.Square, CoverShape(1.12f).coverClass)
        assertEquals(CoverClass.Square, CoverShape(0.85f).coverClass)
        assertEquals(CoverClass.Tall, CoverShape(0.849f).coverClass)
        assertEquals(CoverClass.Tall, CoverShape(0.575f).coverClass)
        // The board's two shapes keep their columns.
        assertEquals(CoverClass.Landscape, CoverShape.Landscape.coverClass)
        assertEquals(CoverClass.Tall, CoverShape.Portrait.coverClass)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aCoverShapeHasAPositiveRatio() {
        CoverShape(0f)
    }

    @Test
    fun squareCoversSitBetweenLandscapeAndTall() {
        val nds = CoverShape(1.12f)
        val layouts = listOf(rp5, thor, rp4Pro, nova)
        // `floor((area + 12) / 142)` at Medium: the Thor about 5 (by design), 3 to 6 elsewhere.
        assertEquals(listOf(4, 5, 4, 3), layouts.map { it.gridColumns(CoverClass.Square) })
        assertEquals(listOf(4, 5, 4, 3), layouts.map { it.gridColumns(nds) })
        assertEquals(18f, rp5.gridGap(nds).value, 0.01f)
        val widths = listOf(163f, 142.8667f, 137.75f, 155.5556f)
        layouts.forEachIndexed { i, layout -> assertEquals(gapped(widths[i], layout.gridColumns(nds)), layout.gridCoverWidth(nds).value, 0.01f) }
        // A row is as tall as its shape makes a box of that width: DS 1.12 is shorter than Game Boy 1.0.
        layouts.forEach { assertTrue(it.gridRowHeight(nds) < it.gridRowHeight(CoverShape(1f))) }
        val cover = rp5.gridCoverWidth(nds).value
        assertEquals(gapped(163f, rp5.gridColumns(nds)), cover, 0.01f)
        assertEquals((cover - 8f) / 1.12f + 8.6667f, rp5.gridRowHeight(nds).value, 0.01f)
    }

    @Test
    fun tallCoversKeepTheirOwnRatioInThePortraitColumns() {
        val psp = CoverShape(0.575f)
        assertEquals(5, rp5.gridColumns(psp))
        val large = rp5.copy(gridSize = GridSize.Large)
        // The box (collar included) of that height is exactly its column wide.
        val box = (large.gridRowHeight(psp).value - 8.6667f) * 0.575f + 8f
        assertEquals(large.gridCoverWidth(psp).value, box, 0.01f)
    }

    @Test
    fun settingsOptionsWidth() {
        // Two columns (where three do not fit the text size), then three columns on the wide screens.
        assertEquals(448.dp, rp5.copy(settingsThreeFit = false).settingsOptionsWidth)
        assertEquals(517.dp, thor.copy(settingsThreeFit = false).settingsOptionsWidth)
        assertEquals(298.17f, rp5.settingsOptionsWidth.value, 0.01f)
        assertEquals(367.17f, thor.settingsOptionsWidth.value, 0.01f)
        assertEquals(619.dp, rp4Pro.settingsOptionsWidth)
        assertEquals(528.dp, nova.settingsOptionsWidth)
    }

    @Test
    fun listRowsFollowTheScreenHeight() {
        // Rows 38 high with a 2 gap: 40 per row, the last row without its gap.
        assertEquals(8, rp5.listRowsThatFit)
        assertEquals(9, thor.listRowsThatFit)
        assertEquals(7, rp4Pro.listRowsThatFit)
        assertEquals(8, nova.listRowsThatFit)
    }

    /** Rows per screen at the board's 40 dp pitch, Medium and the default Large, on the four reference screens (before the 34 design height: the same counts, Large's pitch was 41.6; the 40 dp footer zone gave 640 x 480 a tenth row at Medium). */
    @Test
    fun rowsPerScreenAtTheBoardPitch() {
        val large = TextSize.Large.heightScale
        for ((size, rows) in listOf((576.dp to 432.dp) to (8 to 8), (640.dp to 480.dp) to (10 to 9), (768.dp to 432.dp) to (8 to 8), (832.dp to 468.dp) to (9 to 8))) {
            val (width, height) = size
            assertEquals("$width x $height Medium", rows.first, PlumLayout(width, height).listRowsThatFit)
            assertEquals("$width x $height Large", rows.second, PlumLayout(width, height, textScale = large).listRowsThatFit)
        }
    }

    @Test
    fun rowsAndBarsGrowWithTheTextAndFewerRowsFit() {
        val medium = PlumLayout(768.dp, 432.dp)
        val largest = PlumLayout(768.dp, 432.dp, textScale = TextSize.Largest.heightScale)
        assertEquals(40.dp, medium.rowHeight)
        assertEquals(60.dp, largest.rowHeight)
        // Game rows: 34 designed, never under 38 drawn (40 with the gap, the touch minimum and the board's pitch): Large's 40.8 is above it.
        assertEquals(38.dp, medium.gameRowHeight)
        assertEquals(38.dp, PlumLayout(768.dp, 432.dp, textScale = TextSize.Small.heightScale).gameRowHeight)
        assertEquals(34.dp * 1.2f, PlumLayout(837.dp, 471.dp, textScale = TextSize.Large.heightScale).gameRowHeight)
        assertEquals(34.dp * 1.5f, largest.gameRowHeight)
        assertEquals(34.dp * 1.5f, largest.systemRowHeight)
        // System rows: 34 designed, never under 40 drawn.
        assertEquals(40.dp, medium.systemRowHeight)
        assertEquals(34.dp * 1.2f, PlumLayout(768.dp, 432.dp, textScale = TextSize.Large.heightScale).systemRowHeight)
        assertEquals(18.dp * 1.5f, largest.topBarHeight)
        // The hint bar stops growing at Medium (1.1).
        assertEquals(22.dp * 1.1f, largest.hintBarHeight)
        assertEquals(200.dp * 1.5f, largest.systemsColumnWidth)
        // The bars take more of the screen, and each row more still.
        assertEquals(350.dp, medium.stripHeight)
        assertEquals(432.dp - 14.dp - 18.dp * 1.5f - 10.dp - (9.dp + 22.dp * 1.1f + 9.dp), largest.stripHeight)
        assertEquals(8, medium.listRowsThatFit)
        assertEquals(6, largest.listRowsThatFit)
        assertEquals(8, PlumLayout(837.dp, 471.dp, textScale = TextSize.Large.heightScale).listRowsThatFit)
        // 34 x 1.5 = 51 is under the 52.2 the 36 gave, so Largest on the Thor shows 7 rows where it showed 6.
        assertEquals(7, PlumLayout(837.dp, 471.dp, textScale = TextSize.Largest.heightScale).listRowsThatFit)
        // On the Thor's 367 dpi a 53 dp stride rounds up to 122 px: with a taller top bar seven rows fit in dp yet overflow the strip in pixels and cut the first row.
        val tallBar = PlumLayout(837.dp, 471.dp, textScale = TextSize.Largest.heightScale, topBarLine = 35.dp)
        assertEquals(7, tallBar.listRowsThatFit)
        assertEquals(6, tallBar.copy(pixelsPerDp = 367f / 160f).listRowsThatFit)
        // Smaller text never shrinks a box: its text already fits.
        assertEquals(medium.rowHeight, PlumLayout(768.dp, 432.dp, textScale = TextSize.Small.heightScale).rowHeight)
    }

    @Test
    fun peekSettingShiftsTheWideList() {
        assertEquals(448.dp, PlumLayout(768.dp, 432.dp, peek = 0.dp).gamesListWidth)
        assertEquals(720.dp, PlumLayout(768.dp, 432.dp, peek = 0.dp).gridAreaWidth)
    }

    /** A size picks a column count and the columns share the width exactly, whatever the shape, screen and text size. */
    @Test
    fun everySizeFillsTheWidthWithItsColumnsWhateverTheShape() {
        for (size in GridSize.entries) {
            for ((w, h) in listOf(768 to 432, 576 to 432, 832 to 468)) {
                for (text in TextSize.entries) {
                    val layout = PlumLayout(w.dp, h.dp, textScale = text.heightScale, gridSize = size)
                    for (aspect in listOf(1.4f, 0.71f, 1f, 0.575f, 1.78f)) {
                        val shape = CoverShape(aspect)
                        val columns = layout.gridColumns(shape)
                        val at = "$size $w x $h $text $aspect"
                        val spanned = layout.gridCoverWidth(shape) * columns + layout.gridGap(shape) * (columns - 1)
                        assertEquals("$at: the columns and gaps are the grid's width", layout.gridCoversWidth.value, spanned.value, 0.01f)
                        assertTrue("$at: at least one row fits", layout.gridRowsThatFit(shape) >= 1)
                    }
                }
            }
        }
    }

    /**
     * The top bar holds the line of its place name's 16 step in every font and size: it grows past the 18 dp base, never cuts it. The
     * 20 name's taller line reaches into the margins above and below by less than either (so the content keeps the boards' place).
     */
    @Test
    fun theTopBarGrowsToHoldThePlaceNameLineInEveryFontAndSize() {
        for (font in PlumFont.entries) {
            for (size in TextSize.entries) {
                val line = (TypeRole.PlaceName.lineHeightSp(font, size) * PLACE_NAME_STEP_SP / TypeRole.PlaceName.sizeSp).dp
                val bar = PlumLayout(768.dp, 432.dp, textScale = size.heightScale, topBarLine = line).topBarHeight
                assertTrue("$font $size: bar $bar holds the line $line", bar >= line)
                assertTrue("$font $size: bar $bar keeps the base grown by the text", bar >= PlumDimens.topBarHeight * size.heightScale)
                val reach = (TypeRole.PlaceName.lineHeightSp(font, size).dp - bar) / 2
                assertTrue("$font $size: the 20 name reaches $reach past the bar", reach < PlumDimens.barGap && reach < PlumDimens.stripMarginTop)
            }
        }
        // Jost at Medium: 16 sp on a 1.445 line is 23.1 dp, 5 dp over the base.
        val step = PLACE_NAME_STEP_SP / TypeRole.PlaceName.sizeSp
        val jost = PlumLayout(768.dp, 432.dp, topBarLine = (TypeRole.PlaceName.lineHeightSp(PlumFont.Jost, TextSize.Small) * step).dp)
        assertEquals(23.12f, jost.topBarHeight.value, 0.01f)
        // The default (Jost, Large): the strip starts 14 + bar + 10 down: the board's 49.3 dp (cover top, ThemePaper) was drawn at 1.1, the bar grows 2.4 dp at 1.2.
        val large = PlumLayout(768.dp, 432.dp, textScale = TextSize.Large.heightScale, topBarLine = (TypeRole.PlaceName.lineHeightSp(PlumFont.Jost, TextSize.Large) * step).dp)
        assertEquals(51.74f, (PlumDimens.stripMarginTop + large.topBarHeight + PlumDimens.barGap).value, 0.1f)
    }

    /** While a keyboard tray is up the top bar keeps the base height, whatever the place-name line (the field and the 36 dp keys need the room). */
    @Test
    fun theTopBarKeepsItsBaseHeightWhileATrayIsUp() {
        for (font in PlumFont.entries) {
            for (size in TextSize.entries) {
                val line = TypeRole.PlaceName.lineHeightSp(font, size).dp
                val layout = PlumLayout(667.dp, 375.dp, textScale = size.heightScale, topBarLine = line)
                assertEquals("$font $size", PlumDimens.topBarHeight * size.heightScale, layout.topBarHeightTyping)
                assertTrue("$font $size: typing bar never taller than the idle bar", layout.topBarHeightTyping <= layout.topBarHeight)
            }
        }
    }

    /** The games list is the rows that fit, whole, the last without its gap; at 768 x 432 that is 8 x 40 - 2. */
    @Test
    fun theGamesListIsAWholeNumberOfRowsTall() {
        assertEquals(318f, rp5.listRowsHeight.value, 0.01f)
        assertEquals(318f, nova.listRowsHeight.value, 0.01f)
        for (layout in listOf(rp5, thor, rp4Pro, nova)) {
            assertTrue(layout.listRowsHeight <= layout.stripHeight)
            assertTrue(layout.listRowsHeight + PlumDimens.listRowGap + layout.gameRowHeight > layout.stripHeight)
        }
    }
}
