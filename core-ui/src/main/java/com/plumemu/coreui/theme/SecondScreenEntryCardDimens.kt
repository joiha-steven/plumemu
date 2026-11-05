package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sizes of the second screen's system, Recent and Favorites cards (boards `SecondSystem`, `SecondRecent`,
 * `SecondFavorites`, drawn at 1.5 px per dp: every value is the board's px / 1.5). The cards keep these
 * at both sizes and drop whole parts from the bottom when the height runs out. A text size that is not a whole
 * dp is written as an exact third: 9.33 sp draws at 13.995 px, which the renderer cuts to 13.
 */
object EntryCardDimens {
    /** The smallest text: the ramp's 11.33 (the top screen's info line, list tag), an exact third; the boards' 8 to 10 sp are raised to it. */
    private val SMALL_STEP = (34f / 3).sp

    /** The screen's margin: left and right, top, bottom (the board's 36, 30 and 32 px). */
    val marginSide = 24.dp
    val marginTop = 20.dp
    val marginBottom = 21.33.dp

    /** Header: the name (26 px, 600), the count (16 px), the gap between them and the gap under it. */
    val headerTitle = (52f / 3).sp
    val headerMeta = SMALL_STEP
    val headerGap = 10.67.dp
    val headerBottom = 12.dp

    /** The small capital label ("LAST PLAYED"): 12 px, 600, letter-spaced 1.6 px; its gap to what follows. */
    val label = SMALL_STEP
    val labelSpacing = 1.07.sp
    val labelToRow = 6.67.dp

    /** A cover's caption: the time it was played (14 px, 18 px line) and the system under it (13 px, 16 px line); 6 px between the cover and each line. */
    val captionWhen = SMALL_STEP
    val captionSystem = SMALL_STEP
    val captionGap = 4.dp
    val captionInset = 2.67.dp

    /** The covers' height (inside the collar) on each card, and the gap between covers. */
    val systemCoverHeight = 69.33.dp
    val systemCoverGap = 6.dp
    val recentCoverHeight = 93.33.dp
    val recentCoverGap = 8.dp
    val favoritesCoverHeight = 100.dp
    val favoritesCoverGap = 6.dp
    val favoritesRowGap = 8.dp
    const val SYSTEM_COVERS = 6
    const val RECENT_COVERS = 5
    const val FAVORITES_ROWS = 3

    /** A tile: padding (top, sides, bottom), gap between its lines; the gap between tiles and above them. */
    val tilePadTop = 14.67.dp
    val tilePadSide = 13.33.dp
    val tilePadBottom = 17.33.dp
    val tileLineGap = 5.33.dp
    val tileGap = 8.dp
    val tilesTop = 18.67.dp

    /** A tile's big figure (30 px on a 34 px line), its unit (16 px, 500), the "/ 248" (18 px) and the small line (15 px on 20 px). */
    val big = 20.sp
    val unit = SMALL_STEP
    val unitGap = 2.67.dp
    val of = 12.sp
    val small = SMALL_STEP
    val meterHeight = 4.dp

    /** The status line (16 px on 22 px) and its gap above on the system card and on Favorites. */
    val status = SMALL_STEP
    val statusTop = 17.33.dp
    val statusTopFavorites = 10.67.dp

    /** The system card's foot: rule, padding under it, the two lines' gap, the text lines (15 px on 22 px, at most two). */
    val aboutRule = 0.67.dp
    val aboutPadTop = 10.67.dp
    val aboutGap = 2.67.dp
    val aboutText = SMALL_STEP
    const val ABOUT_MAX_LINES = 2

    /** Recent's week tile: padding (top and bottom, sides), the chart's height, the gap above it, between bars, and its bar and label sizes. */
    val weekPadVertical = 12.dp
    val weekPadSide = 13.33.dp
    val weekChartHeight = 85.33.dp
    val weekChartTop = 4.dp
    val weekBarGap = 2.67.dp
    const val WEEK_BAR_WIDTH = 0.62f
    val weekBarCorner = 2.67.dp
    val weekBarFootCorner = 1.33.dp
    val weekBarMin = 2.dp
    val weekBarEmpty = 1.33.dp
    val weekDay = SMALL_STEP
    val weekDayGap = 4.dp

    /**
     * Each text's line in Jost, in em of its size: the board's `line-height` where it sets one, else Jost's own line
     * ([JOST_OWN_LINE]), as the browser draws `normal`. A line shorter than the font's own lets marks reach past its box, as on the board.
     */
    const val JOST_OWN_LINE = 1.445f

    /** Jost's ascent in em: where the browser puts the baseline inside a line box (half the leading above it). */
    const val JOST_ASCENT = 1.07f
    const val LINE_BIG = 34f / 30f
    const val LINE_SMALL = 20f / 15f
    const val LINE_STATUS = 22f / 16f
    const val LINE_FOOT = 22f / 15f
    const val LINE_CAPTION_WHEN = 18f / 14f
    const val LINE_CAPTION_SYSTEM = 16f / 13f
}
