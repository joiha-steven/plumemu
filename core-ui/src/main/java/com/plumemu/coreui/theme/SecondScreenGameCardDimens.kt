package com.plumemu.coreui.theme

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Text sizes here are at least the main screen's matching style (the info line's 11.33 for small text, capitals and running secondary text);
 * the window draws the second screen's text larger by [SecondScreenPhysical.thor] so that a dp is as big to the eye as on the top screen.
 *
 * The second screen's game card (boards `SecondGame*`, drawn at 1.5x: board px / 1.5 = dp). Sizes of its parts. In Jost the
 * tiles' texts sit on the board's CSS lines (`boardLine` in the app); the description and the foot keep the type styles' own.
 * A text size that is not a whole dp is an exact third: 17.33 sp draws at 25.995 px, which the renderer cuts to 25.
 */
object SecondScreenGameCardDimens {
    /** The smallest text: the ramp's 11.33 (the top screen's info line, list tag), an exact third. */
    private val SMALL_STEP = (34f / 3).sp

    /** The card's margins: the board's 30 / 36 / 32 px. */
    val marginTop = 20.dp
    val marginSide = 24.dp
    val marginBottom = 21.33.dp

    /** Header: the title (26 / 600), the system's short name (the board's 16, raised to the small step), and the gap under them. */
    val titleSize = (52f / 3).sp
    val systemSize = SMALL_STEP
    val headerGap = 12.dp
    val headerItemGap = 10.67.dp

    /** The tiles: between them, under them, inside them, between their parts. */
    val tileGap = 9.33.dp
    val tilesBottomGap = 13.33.dp
    val tilePadX = 12.dp
    val tilePadY = 10.67.dp
    val tilePartGap = 5.33.dp

    /** The achievements tile is a little wider than the play tile (the board's flex 1.05 against 1). */
    const val ACHIEVEMENTS_WEIGHT = 1.05f
    const val PLAY_WEIGHT = 1f

    /** Tile label (the board's 12 px / 600, letter-spacing 1.6 px, capitals), raised from 8 sp to the small step. */
    val labelSize = SMALL_STEP
    val labelSpacing = 1.07.sp

    /** The big figure (30 / 600) and its unit (16 / 500), and the small lines (15). */
    val bigSize = 20.sp
    val unitSize = SMALL_STEP
    val unitGap = 2.67.dp
    val smallSize = SMALL_STEP

    /** The meter: thickness (pill). */
    val meterHeight = 5.33.dp

    /** Badges: side, corner, gap, and the space above the row (the board's margin-top 6 px). */
    val badgeSize = 29.33.dp
    val badgeCorner = 6.dp
    val badgeGap = 5.33.dp
    val badgesTop = 4.dp
    const val BADGES = 5

    /** The ghost on an empty badge face: the board's 11x11 pixel glyph drawn in half of the badge. */
    const val BADGE_GHOST_SHARE = 0.5f

    /** Play bars: chart height, gap between columns, bar share of its column, stub heights, corners, and the label's gap. */
    val barsHeight = 46.67.dp
    val barsGap = 2.67.dp
    const val BAR_WIDTH_SHARE = 0.62f
    val barMin = 2.dp
    val barEmpty = 1.33.dp
    val barCornerTop = 2.67.dp
    val barCornerBottom = 1.33.dp
    val barsLabelGap = 4.dp
    val barsLabelSize = SMALL_STEP
    const val WEEKS_SHOWN = 8

    /** Description (19), and the share of its box the bottom fade takes (the board fades from 82 %). */
    val descriptionSize = (38f / 3).sp
    /** Room above the first line inside the description's clip, for the tallest marks (Vietnamese capitals); the box starts this far up. */
    val descriptionInkTop = 4.dp
    const val DESCRIPTION_FADE_SHARE = 0.18f

    /** Foot: above it, between the battery and the file line; the battery glyph is the board's 24 x 12 px. */
    val footTop = 12.dp
    val footGap = 4.dp
    val footSize = SMALL_STEP

    /** The mono face is drawn at its x-height factor (0.96), so this is a little above the small step. */
    val fileSize = 12.sp
    val fileSpacing = (-0.13).sp
}
