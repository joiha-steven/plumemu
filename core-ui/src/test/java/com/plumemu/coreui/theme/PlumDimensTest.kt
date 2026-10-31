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
        // Game rows: designed 36, drawn at least 38 (the board had 40), gap 2, padding 0 14. System rows: height 34, padding 0 10, 4 after Apps.
        assertEquals(listOf(40.dp, 36.dp, 38.dp, 2.dp, 14.dp), listOf(PlumDimens.rowHeight, PlumDimens.gameRowHeight, PlumDimens.gameRowMinHeight, PlumDimens.listRowGap, PlumDimens.gameRowPadding))
        assertEquals(listOf(34.dp, 10.dp, 4.dp), listOf(PlumDimens.systemRowHeight, PlumDimens.systemRowPadding, PlumDimens.systemGroupGap))
        // Focus dot 6, then 10 to the text; row corners 8.
        assertEquals(listOf(6.dp, 10.dp, 8.dp), listOf(PlumDimens.focusDot, PlumDimens.focusDotGap, PlumDimens.cornerRow))
    }

    @Test
    fun barsMatchTheBoardMarkup() {
        // Top bar: clock and battery 14 apart. Hints: 20 apart, 6 from cap to label; caps
        // min-width 20, height 20, padding 0 5, border 1.5.
        assertEquals(14.dp, PlumDimens.topBarItemGap)
        assertEquals(listOf(20.dp, 6.dp), listOf(PlumDimens.hintGap, PlumDimens.hintKeyGap))
        assertEquals(listOf(20.dp, 20.dp, 5.dp, 1.5.dp), listOf(PlumDimens.keyCapMinWidth, PlumDimens.keyCapHeight, PlumDimens.keyCapPadding, PlumDimens.keyCapBorder))
    }

    @Test
    fun statisticsPanelCoverIsShorterThanTheDetailsCoverAndFitsTheSidePanel() {
        assertEquals(130.dp, PlumDimens.settingsStatisticsCoverHeight)
        assertTrue(PlumDimens.settingsStatisticsCoverHeight < PlumDimens.detailsCoverHeightWide)
    }

    @Test
    fun detailsMatchTheBoardMarkup() {
        // RP5-2-games: column 248, cover 248×178, gap 12, title block gap 4.
        assertEquals(listOf(248.dp, 178.dp, 12.dp, 4.dp), listOf(PlumDimens.detailsColumnWidth, PlumDimens.detailsCoverHeightWide, PlumDimens.detailsBlockGapWide, PlumDimens.titleLineGap))
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
        assertEquals(listOf(22.dp, 20.dp, 4.dp), listOf(PlumDimens.gridGapLandscape, PlumDimens.gridGapPortrait, PlumDimens.gridPadding))
        assertEquals(listOf(8.dp, 6.dp), listOf(PlumDimens.cornerCoverLandscape, PlumDimens.cornerCoverPortrait))
        assertEquals(listOf(12.dp, 10.dp, 16.dp), listOf(PlumDimens.coverPaddingLandscape, PlumDimens.coverPaddingPortrait, PlumDimens.gridHintGap))
        // Focus ring 2.5 wide, 3 away: it reaches 5.5 past the cover; glow blur 12, spread 1.
        assertEquals(listOf(2.5.dp, 3.dp, 5.5.dp), listOf(PlumDimens.focusRingWidth, PlumDimens.focusRingGap, PlumDimens.focusRingOutset))
        assertEquals(listOf(12.dp, 1.dp), listOf(PlumDimens.focusGlowBlur, PlumDimens.focusGlowSpread))
    }
}
