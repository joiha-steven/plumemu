package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp

/**
 * Settings › Statistics' page (docs/design/rules.md › Statistics page): figure tiles over two lists of
 * bars. Gaps between blocks are the strip's own ([PlumDimens.settingsPanelGap] between tiles and
 * between groups, [PlumDimens.columnGap] between the two lists); only what the page alone draws is here.
 */
object StatisticsDimens {
    /** Inside a figure tile, on every side: the rows' text line, so a tile's words start where a row's do. */
    val tilePadding = PlumDimens.gameRowPadding

    /** Above and below a tile's text where the page shows two tiles a row (narrow): Jost's tall lines need the room, so the first game stays whole under the tiles (the taller top bar leaves 5 dp less). */
    val tilePaddingVerticalNarrow = 6.dp

    /** A bar's thickness: the week's empty days, a game's and a system's share, the box art coverage. */
    val barHeight = 4.dp

    /** The week tile's chart: seven bars this high at most, side by side. */
    val weekChartHeight = 28.dp

    /** The same chart where the page shows two tiles a row: shorter, for the same reason as [tilePaddingVerticalNarrow]. */
    val weekChartHeightNarrow = 20.dp

    /** Between two of the week's bars. */
    val weekBarGap = 4.dp

    /** Between a row's text and its bar under it. */
    val barGap = 4.dp

    /**
     * The tiles share the page's width, this many to a row on wide screens; on narrow ones two
     * ([NARROW_TILES_PER_ROW]) so a figure keeps its size (docs/design/rules.md › Width classes).
     */
    const val WIDE_TILES_PER_ROW = 4
    const val NARROW_TILES_PER_ROW = 2
}
