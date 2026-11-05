package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The main screen's fixed sizes, read from the board's markup (the RP5-1 systems
 * and RP5-2 games boards) and docs/design/rules.md › The strip.
 */
class PlumDimensTest {
    @Test
    fun stripMatchesTheDesignRules() {
        assertEquals(listOf(24.dp, 14.dp, 12.dp), listOf(PlumDimens.stripMarginSide, PlumDimens.stripMarginTop, PlumDimens.stripMarginBottom))
        assertEquals(listOf(18.dp, 22.dp, 10.dp), listOf(PlumDimens.topBarHeight, PlumDimens.hintBarHeight, PlumDimens.barGap))
        assertEquals(listOf(200.dp, 24.dp, 48.dp, 32.dp), listOf(PlumDimens.systemsColumnWidth, PlumDimens.columnGap, PlumDimens.peekWidth, PlumDimens.edgeFade))
    }

    @Test
    fun rowsMatchTheBoardMarkup() {
        // Game rows: designed 34 (so the default Large, 37.4, is drawn at the 38 minimum: 40 per row with the gap, the board's 60 px pitch), gap 2, padding 0 23.33 (the board's text start, 35 px in from the slab's left edge). System rows: height 34, padding 0 10, 4 after Apps.
        assertEquals(listOf(40.dp, 34.dp, 38.dp, 2.dp, 23.33.dp), listOf(PlumDimens.rowHeight, PlumDimens.gameRowHeight, PlumDimens.gameRowMinHeight, PlumDimens.listRowGap, PlumDimens.gameRowPadding))
        assertEquals(listOf(34.dp, 10.dp, 4.dp), listOf(PlumDimens.systemRowHeight, PlumDimens.systemRowPadding, PlumDimens.systemGroupGap))
        // Focus dot 6, then 10 to the text; row corners 8.
        assertEquals(listOf(6.dp, 10.dp, 8.dp), listOf(PlumDimens.focusDot, PlumDimens.focusDotGap, PlumDimens.cornerRow))
    }

    @Test
    fun barsMatchTheBoardMarkup() {
        // Top bar: clock and battery 14 apart. Hints: 20 apart, 6 from cap to label; caps
        // min-width 22.67, height 20.67 (the board's hint key is 44 x 43 px with its collar), padding 0 5, border 1.5.
        assertEquals(14.dp, PlumDimens.topBarItemGap)
        assertEquals(listOf(9.dp, (16f / 3f).dp), listOf(PlumDimens.hintGap, PlumDimens.hintKeyGap))
        assertEquals(17.33f, PlumDimens.hintGapBoard.value, 0.01f)
        assertEquals(listOf(22.67.dp, 20.67.dp, 5.dp, 1.5.dp), listOf(PlumDimens.keyCapMinWidth, PlumDimens.keyCapHeight, PlumDimens.keyCapPadding, PlumDimens.keyCapBorder))
    }

    @Test
    fun statisticsPageTilesStartTheirWordsOnTheRowsTextLineAndBarsStayThin() {
        assertEquals(PlumDimens.gameRowPadding, StatisticsDimens.tilePadding)
        assertEquals(listOf(4.dp, 28.dp, 4.dp, 4.dp), listOf(StatisticsDimens.barHeight, StatisticsDimens.weekChartHeight, StatisticsDimens.weekBarGap, StatisticsDimens.barGap))
        assertEquals(listOf(4, 2), listOf(StatisticsDimens.WIDE_TILES_PER_ROW, StatisticsDimens.NARROW_TILES_PER_ROW))
        // Seven bars and their gaps fit a 2-per-row tile at 576 (528 page: (528 - 12) / 2 - 2 x 14 = 230).
        assertTrue(StatisticsDimens.weekBarGap * 6 < 230.dp)
    }

    @Test
    fun detailsMatchTheBoardMarkup() {
        // RP5-2-games: column 248, cover 248×178, title block gap 4; the block gap is the Depth board's 24 px (16; 14.67 before, from 22 px).
        assertEquals(listOf(248.dp, 178.dp, 16.dp, 4.dp), listOf(PlumDimens.detailsColumnWidth, PlumDimens.detailsCoverHeightWide, PlumDimens.detailsBlockGapWide, PlumDimens.titleLineGap))
        // Nova-3-details: cover 228×164, 20 to the text, lines 6 apart, a 4 spacer, 14 to the description.
        assertEquals(
            listOf(228.dp, 164.dp, 20.dp, 6.dp, 4.dp, 14.dp),
            listOf(
                PlumDimens.detailsStopCoverWidth,
                PlumDimens.detailsStopCoverHeight,
                PlumDimens.detailsStopCoverGap,
                PlumDimens.detailsStopLineGap,
                PlumDimens.detailsStopFactsSpacer,
                PlumDimens.detailsStopBlockGap,
            ),
        )
        // Facts: `column-gap: 12px; row-gap: 4px`.
        assertEquals(listOf(12.dp, 4.dp), listOf(PlumDimens.factsColumnGap, PlumDimens.factsRowGap))
    }

    @Test
    fun gridMatchesTheBoardMarkup() {
        // RP5-3 / RP5-4: gaps 22 / 20 (the board's were 14 / 12), padding 4, corners 8 / 6, cover padding 12 / 10, bar gap 16.
        assertEquals(listOf(22.dp, 20.dp), listOf(PlumDimens.gridGapLandscape, PlumDimens.gridGapPortrait))
        assertEquals(listOf(8.dp, 6.dp), listOf(PlumDimens.cornerCoverLandscape, PlumDimens.cornerCoverPortrait))
        assertEquals(listOf(12.dp, 10.dp, 9.dp), listOf(PlumDimens.coverPaddingLandscape, PlumDimens.coverPaddingPortrait, PlumDimens.gridHintGap))
        // Focus ring 2.5 wide, 3 away: it reaches 5.5 past the cover; the focus halo reaches 10 more in 7 bands.
        assertEquals(listOf(2.5.dp, 3.dp, 5.5.dp), listOf(PlumDimens.focusRingWidth, PlumDimens.focusRingGap, PlumDimens.focusRingOutset))
        assertEquals(listOf(10.dp, 7), listOf(PlumDimens.focusHaloReach, PlumDimens.FOCUS_HALO_STEPS))
    }

    @Test
    fun listColumnsDividerAndDetailsBlockMatchTheBoardMarkup() {
        // The board's px / 1.5: year column 64 px, region 48 px; the list reaches 8 dp left of its box (64 dp from the edge, 408 wide).
        assertEquals(listOf(42.67.dp, 38.dp, 8.dp), listOf(PlumDimens.rowYearColumn, PlumDimens.rowRegionColumn, PlumDimens.gamesListBleed))
        // Hairline: 1 px wide, 16 dp left of the list box (x 56), inset 5.33 from the strip's top and bottom.
        assertEquals(listOf(16.dp, 0.67.dp, 5.33.dp), listOf(PlumDimens.listDividerOffset, PlumDimens.listDividerWidth, PlumDimens.listDividerInset))
        // Details block: label to value 4 px, hairline to block 14 px, title to its line 4 px; dots 6 px, 3 px apart, 8 px from the number.
        assertEquals(listOf(2.67.dp, 9.33.dp, 2.67.dp), listOf(PlumDimens.detailsLabelGap, PlumDimens.detailsDividerGap, PlumDimens.detailsSubtitleGap))
        assertEquals(listOf(4.dp, 2.dp, 5.33.dp), listOf(PlumDimens.ratingDotSize, PlumDimens.ratingDotGap, PlumDimens.ratingNumberGap))
        // Top bar: name to count 16 px (the Covers bar).
        assertEquals(10.67.dp, PlumDimens.placeCountGap)
    }
}
