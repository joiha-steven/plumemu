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
        ) = layout.copy(textScale = size.heightScale)
        for (screen in listOf(rp5, thor, rp4Pro, nova)) {
            assertEquals(164f, at(screen, TextSize.Medium).keyboardHeight.value, 0.001f)
            assertEquals(204f, at(screen, TextSize.Medium).keyboardTrayHeight.value, 0.001f)
            assertEquals(18f, at(screen, TextSize.Medium).keyboardIconSize.value, 0.001f)
            assertEquals("the field's caret", 1.5f, at(screen, TextSize.Medium).caretWidth.value, 0.001f)
            assertEquals(1.5f * TextSize.Largest.heightScale, at(screen, TextSize.Largest).caretWidth.value, 0.001f)
            for (size in TextSize.entries) {
                val l = at(screen, size)
                assertEquals("${screen.height} at $size", 36f, l.keyboardKeyHeight.value, 0.001f)
                assertEquals(8f + 4 * 36f + 12f, l.keyboardHeight.value, 0.001f)
                assertEquals(l.keyboardHeight.value + 6f + 22f * size.heightScale + 12f, l.keyboardTrayHeight.value, 0.001f)
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
        assertEquals(204f + 22f * (TextSize.Largest.heightScale - 1f), at(nova, TextSize.Largest).keyboardTrayHeight.value, 0.001f)
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
                        assertTrue("ring room", layout.gridGap(shape) >= PlumDimens.focusRingOutset * 2)
                    }
                }
            }
        }
        assertEquals(74.125f, thor.copy(gridSize = GridSize.Small).gridCoverWidth(CoverShape(0.73f)).value, 0.01f)
    }

    /** The narrowest grid on the Nova (576) still shows whole rows with titles: Small tall, five 78.4 wide. */
    @Test
    fun narrowGridsStayUsable() {
        val novaSmall = nova.copy(gridSize = GridSize.Small)
        assertEquals(78.4f, novaSmall.gridCoverWidth(CoverShape(0.73f)).value, 0.01f)
        assertEquals(142.6667f, novaSmall.gridCoverWidth(CoverShape.Landscape).value, 0.01f)
        for (size in GridSize.entries) {
            for (shape in listOf(CoverShape.Landscape, CoverShape(1.12f), CoverShape(0.575f))) {
                assertTrue(nova.copy(gridSize = size).gridRowsThatFit(shape) >= 1)
            }
        }
    }

    @Test
    fun gridCoversSpanTheAreaLessTheBoardsPadding() {
        // RP5-3 / RP5-4: `width: gridW; padding: 4px; box-sizing: border-box`, so covers span gridW − 8.
        assertEquals(listOf(664.dp, 733.dp, 563.dp, 472.dp), listOf(rp5, thor, rp4Pro, nova).map { it.gridCoversWidth })
    }

    @Test
    fun gridCoverWidthSharesTheSpanAfterTheGaps() {
        // The board's `colW = (gridW − 8 − gap × (cols − 1)) / cols`.
        // Gaps 22 / 20 (the board's 14 / 12 gave 212 / 235 / 274.5 / 229 and 123.2 / 112.17 / 131.75 / 109).
        val landscape = listOf(206.6667f, 229.6667f, 270.5f, 225f)
        val portrait = listOf(116.8f, 105.5f, 125.75f, 103f)
        boardLandscape.zip(listOf(rp5, thor, rp4Pro, nova)).forEachIndexed { i, (large, layout) ->
            assertEquals(landscape[i], large.gridCoverWidth(CoverShape.Landscape).value, 0.01f)
            assertEquals(portrait[i], layout.gridCoverWidth(CoverShape.Portrait).value, 0.01f)
        }
        assertEquals(listOf(22.dp, 20.dp), listOf(rp5.gridGap(CoverShape.Landscape), rp5.gridGap(CoverShape.Portrait)))
    }

    @Test
    fun gridRowsKeepTheCoverShape() {
        // The board's `Math.round(colW / 1.4)` and `Math.round(colW / 0.78)`, with the gaps of 22 / 20 (
        // at the board's 14 / 12 they were 151 / 168 / 196 / 164 and 158 / 144 / 169 / 140).
        assertEquals(listOf(148.dp, 164.dp, 193.dp, 161.dp), boardLandscape.map { it.gridRowHeight(CoverShape.Landscape) })
        assertEquals(listOf(150.dp, 135.dp, 161.dp, 132.dp), listOf(rp5, thor, rp4Pro, nova).map { it.gridRowHeight(CoverShape.Portrait) })
    }

    @Test
    fun stripHeightAndGridRowsThatFit() {
        assertEquals(listOf(346.dp, 385.dp, 289.dp, 346.dp), listOf(rp5, thor, rp4Pro, nova).map { it.stripHeight })
        // The cover grid is 12 dp shorter (its top gap is 22: 20 before the 10 % lift): the Nova's landscape grid (two rows of 161
        // with a 22 gap need 344, 336 are left) shows one whole row and a second one cut, where it showed two.
        assertEquals(listOf(334.dp, 373.dp, 277.dp, 334.dp), listOf(rp5, thor, rp4Pro, nova).map { it.gridStripHeight })
        assertEquals(listOf(2, 2, 1, 1), boardLandscape.map { it.gridRowsThatFit(CoverShape.Landscape) })
        assertEquals(listOf(2, 2, 1, 2), listOf(rp5, thor, rp4Pro, nova).map { it.gridRowsThatFit(CoverShape.Portrait) })
        assertEquals(listOf(3, 2), listOf(boardLandscape[0].gridColumns(CoverShape.Landscape), boardLandscape[3].gridColumns(CoverShape.Landscape)))
        assertEquals(listOf(5, 4), listOf(rp5.gridColumns(CoverShape.Portrait), nova.gridColumns(CoverShape.Portrait)))
    }

    /** The focused cover grows about its centre (half of the growth goes up) and is raised: ring outset, raise and growth of the tallest row stay inside the grid's top gap. */
    @Test
    fun theGridTopGapHoldsTheRingAndTheGrowthOfEveryRow() {
        for (size in GridSize.entries) {
            for (layout in at(size)) {
                for (shape in listOf(CoverShape(0.575f), CoverShape.Portrait, CoverShape(1f), CoverShape(1.12f), CoverShape.Landscape)) {
                    val growth = layout.gridRowHeight(shape).value * (PlumDimens.FOCUSED_COVER_SCALE - 1f) / 2f + PlumDimens.focusedLift.value
                    val needed = PlumDimens.focusRingOutset.value + growth
                    assertTrue("$size ${layout.width} $shape needs $needed dp above the top row", needed <= PlumDimens.gridTopGap.value)
                    // Between rows the ring of the lifted cover must not reach the cover above it.
                    assertTrue("$size ${layout.width} $shape needs $needed dp above its row, the gap to the row above is ${layout.gridRowGap(shape).value}", needed <= layout.gridRowGap(shape).value)
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
        assertEquals(20.dp, rp5.gridGap(nds))
        val widths = listOf(151f, 130.6f, 125.75f, 144f)
        layouts.forEachIndexed { i, layout -> assertEquals(widths[i], layout.gridCoverWidth(nds).value, 0.01f) }
        // Rows keep the system's own ratio: DS 1.12 here, Game Boy 1.0 square.
        assertEquals(listOf(135.dp, 117.dp, 112.dp, 129.dp), layouts.map { it.gridRowHeight(nds) })
        assertEquals(listOf(151.dp, 131.dp, 126.dp, 144.dp), layouts.map { it.gridRowHeight(CoverShape(1f)) })
        assertEquals(listOf(2, 2, 2, 2), layouts.map { it.gridRowsThatFit(nds) })
    }

    @Test
    fun tallCoversKeepTheirOwnRatioInThePortraitColumns() {
        val psp = CoverShape(0.575f)
        assertEquals(5, rp5.gridColumns(psp))
        assertEquals(116.8f / 0.575f, rp5.gridRowHeight(psp).value, 0.5f)
    }

    @Test
    fun settingsOptionsWidth() {
        assertEquals(448.dp, rp5.settingsOptionsWidth)
        assertEquals(517.dp, thor.settingsOptionsWidth)
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

    @Test
    fun rowsAndBarsGrowWithTheTextAndFewerRowsFit() {
        val medium = PlumLayout(768.dp, 432.dp)
        val largest = PlumLayout(768.dp, 432.dp, textScale = TextSize.Largest.heightScale)
        assertEquals(40.dp, medium.rowHeight)
        assertEquals(58.dp, largest.rowHeight)
        // Game rows: 36 designed, never under 38 drawn (40 with the gap, the touch minimum).
        assertEquals(38.dp, medium.gameRowHeight)
        assertEquals(38.dp, PlumLayout(768.dp, 432.dp, textScale = TextSize.Small.heightScale).gameRowHeight)
        assertEquals(36.dp * 1.1f, PlumLayout(837.dp, 471.dp, textScale = TextSize.Large.heightScale).gameRowHeight)
        assertEquals(36.dp * 1.45f, largest.gameRowHeight)
        assertEquals(34.dp * 1.45f, largest.systemRowHeight)
        // System rows: 34 designed, never under 40 drawn.
        assertEquals(40.dp, medium.systemRowHeight)
        assertEquals(40.dp, PlumLayout(768.dp, 432.dp, textScale = TextSize.Large.heightScale).systemRowHeight)
        assertEquals(18.dp * 1.45f, largest.topBarHeight)
        assertEquals(22.dp * 1.45f, largest.hintBarHeight)
        assertEquals(200.dp * 1.45f, largest.systemsColumnWidth)
        // The bars take more of the screen, and each row more still.
        assertEquals(346.dp, medium.stripHeight)
        assertEquals(432.dp - 14.dp - 12.dp - 18.dp * 1.45f - 22.dp * 1.45f - 20.dp, largest.stripHeight)
        assertEquals(8, medium.listRowsThatFit)
        assertEquals(6, largest.listRowsThatFit)
        assertEquals(9, PlumLayout(837.dp, 471.dp, textScale = TextSize.Large.heightScale).listRowsThatFit)
        assertEquals(6, PlumLayout(837.dp, 471.dp, textScale = TextSize.Largest.heightScale).listRowsThatFit)
        // Smaller text never shrinks a box: its text already fits.
        assertEquals(medium.rowHeight, PlumLayout(768.dp, 432.dp, textScale = TextSize.Small.heightScale).rowHeight)
    }

    @Test
    fun peekSettingShiftsTheWideList() {
        assertEquals(448.dp, PlumLayout(768.dp, 432.dp, peek = 0.dp).gamesListWidth)
        assertEquals(720.dp, PlumLayout(768.dp, 432.dp, peek = 0.dp).gridAreaWidth)
    }
}
