package com.plumemu.coreui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.CollarSize
import kotlin.math.floor
import kotlin.math.roundToInt

/** Wide from 768 dp (the Retroid Pocket 5), narrow below (design/rules.md › The strip). */
enum class WidthClass {
    Wide,
    Narrow,
    ;

    companion object {
        val WIDE_MIN_WIDTH: Dp = 768.dp

        /**
         * From here a narrow screen has room to keep the games list whole inside the right margin while the
         * systems column is open (the Retroid Pocket Nova's 640 dp, docs/design/rules.md › Narrow, 640 and up);
         * below it (the Nova's 576 dp) the list stays 400 and runs under the edge fade.
         */
        val NARROW_FITTED_MIN_WIDTH: Dp = 640.dp

        /** The narrowest the fitted list may get: a bigger text size leaves it 400 and the preview runs under the fade, as at 576. */
        val NARROW_FITTED_MIN_LIST: Dp = 320.dp

        fun of(width: Dp): WidthClass = if (width >= WIDE_MIN_WIDTH) Wide else Narrow
    }
}

/**
 * How a grid lays out covers of a [CoverShape] (docs/design/rules.md › Grid): its columns, gap,
 * corner and padding. Landscape from 1.2 (SNES, N64), square from 0.85 below 1.2 (DS, Game Boy,
 * PlayStation), tall below 0.85 (NES, Mega Drive, PS2, PSP).
 */
enum class CoverClass {
    Landscape,
    Square,
    Tall,
    ;

    companion object {
        const val LANDSCAPE_MIN = 1.2f
        const val SQUARE_MIN = 0.85f

        fun of(widthOverHeight: Float): CoverClass =
            when {
                widthOverHeight >= LANDSCAPE_MIN -> Landscape
                widthOverHeight >= SQUARE_MIN -> Square
                else -> Tall
            }
    }
}

/**
 * The grid's three sizes (L1 / R1 and the Size hint), one for every
 * system; each class of cover takes its own columns from it ([PlumLayout.gridColumns]). [Medium] is
 * the default (Small was a little small). The board's
 * landscape grid is [Large], its portrait grid [Medium].
 */
enum class GridSize {
    Small,
    Medium,
    Large,
    ;

    companion object {
        val Default = Medium
    }
}

/**
 * A system's box-art shape, width over height: a tile and a box front's slot in the
 * grid have it; its [coverClass] picks the columns. The app gives each system its own (measured,
 * or from the person's covers); [Landscape] and [Portrait] are the board's two shapes.
 */
@Immutable
data class CoverShape(
    val widthOverHeight: Float,
) {
    init {
        require(widthOverHeight > 0f && widthOverHeight.isFinite()) { "width over height must be positive, was $widthOverHeight" }
    }

    val coverClass: CoverClass = CoverClass.of(widthOverHeight)

    companion object {
        /** The board's landscape covers (SNES, N64), about 1.4:1; a system the data does not know. */
        val Landscape = CoverShape(1.4f)

        /** The board's portrait covers (PSP, PS2, GameCube), about 0.78:1. */
        val Portrait = CoverShape(0.78f)
    }
}

/**
 * Widths that follow the screen, from the formulas in design/rules.md. [peek] is the system peek
 * setting (48 by default). Read in composables via `PlumTheme.layout`.
 */
@Immutable
data class PlumLayout(
    val width: Dp,
    val height: Dp,
    val peek: Dp = PlumDimens.peekWidth,
    /** [TextSize.heightScale] of the text size in use: every box that holds a line of text grows by it. */
    val textScale: Float = 1f,
    /** [TextSize.hintHeightScale]: the hint bar and its key caps stop growing at Medium. */
    val hintScale: Float = maxOf(1f, minOf(textScale, TextSize.HINT_FACTOR_CAP)),
    /** The top bar's place name line in the font and size in use: the bar grows to hold it, never cuts it. */
    val topBarLine: Dp = 0.dp,
    /** L1 / R1 and the Size hint, one size for every system: picks the grid's cells. */
    val gridSize: GridSize = GridSize.Default,
    /** Whether Settings' three columns fit at the text size and font in use ([SettingsColumns.fits]); true where nothing says otherwise. */
    val settingsThreeFit: Boolean = true,
    /** Screen pixels per dp: rows lay out in rounded pixels, so what fits is counted there ([listRowsThatFit]). */
    val pixelsPerDp: Float = 1f,
) {
    val widthClass: WidthClass = WidthClass.of(width)

    private val narrowFitWidth: Dp =
        width - PlumDimens.stripMarginSide - PlumDimens.systemsColumnWidth * textScale - PlumDimens.columnGap - PlumDimens.stripMarginSide

    /** A narrow screen of [WidthClass.NARROW_FITTED_MIN_WIDTH] or more whose text size leaves the fitted list at least [WidthClass.NARROW_FITTED_MIN_LIST] wide. */
    val isNarrowFitted: Boolean = width >= WidthClass.NARROW_FITTED_MIN_WIDTH && width < WidthClass.WIDE_MIN_WIDTH && narrowFitWidth >= WidthClass.NARROW_FITTED_MIN_LIST

    /**
     * Narrow lists: 400, but from [WidthClass.NARROW_FITTED_MIN_WIDTH] up no wider than what is left after the systems
     * column, the gap and the right margin (`width − 272` at Medium: 368 on the Nova's 640), so with the systems
     * open the rows end inside the margin, with their year or region whole, and no fade lies over them.
     */
    val narrowListWidth: Dp = if (isNarrowFitted) minOf(PlumDimens.gamesListMinWidth, narrowFitWidth) else PlumDimens.gamesListMinWidth

    /**
     * Games list. Wide: everything left after margin, peek, two column gaps and details
     * (`width − 368` at the default peek: 400 on the RP5, 469 on the Thor). Narrow: 400.
     */
    val gamesListWidth: Dp =
        if (widthClass == WidthClass.Wide) {
            width - PlumDimens.stripMarginSide - peek - PlumDimens.columnGap * 2 - PlumDimens.detailsColumnWidth
        } else {
            narrowListWidth
        }

    /**
     * Games list without box art: the list takes the details column's space, from the
     * peek's gap to the right margin. `width − 96` at the default peek: 672 on the RP5 (the
     * board's no-art list), 741 on the Thor, 571 on the RP4 Pro, 480 on the Nova.
     */
    val listWidthWithoutArt: Dp = width - PlumDimens.stripMarginSide - peek - PlumDimens.columnGap

    /** Details in the narrow details stop: `width − 104` (margin, list peek, gap); 472 on the Nova. */
    val detailsStopWidth: Dp = width - PlumDimens.stripMarginSide - PlumDimens.detailsStopListPeek - PlumDimens.columnGap

    /** Grid area with games focused: `width − 96` (margin, peek, gap) at the default peek. */
    val gridAreaWidth: Dp = width - PlumDimens.stripMarginSide - peek - PlumDimens.columnGap

    /** Columns of the grid for [shape]'s class at [gridSize]. */
    fun gridColumns(shape: CoverShape): Int = gridColumns(shape.coverClass)

    /**
     * Columns for covers of [coverClass] at [gridSize]: `floor((area + gap) / (cell + gap))` with
     * the cell of [gridCell] (docs/design/rules.md › Grid has the table at the reference widths).
     * The gap here is the one the table was cut with (14 / 12), not the wider drawn gap (22 / 20): the wider gap shrinks the covers, it does not take away a column.
     */
    fun gridColumns(coverClass: CoverClass): Int = gridColumns(gridCell(coverClass, gridSize), gridColumnsGap(coverClass))

    private fun gridColumnsGap(coverClass: CoverClass): Dp =
        when (coverClass) {
            CoverClass.Landscape -> PlumDimens.gridColumnsGapLandscape
            CoverClass.Square, CoverClass.Tall -> PlumDimens.gridColumnsGapPortrait
        }

    /** Between neighbouring collars of [shape]'s covers across ([PlumDimens.gridGapAcross]). Nothing grows on focus, so no room for growth. */
    fun gridGap(shape: CoverShape): Dp = PlumDimens.gridGapAcross

    /** Between covers of [coverClass] across and down in Apps' grid, whose focused tile still grows: 22 landscape, 20 square and tall. */
    fun gridGap(coverClass: CoverClass): Dp =
        when (coverClass) {
            CoverClass.Landscape -> PlumDimens.gridGapLandscape
            CoverClass.Square, CoverClass.Tall -> PlumDimens.gridGapPortrait
        }

    /** Between neighbouring collars of [shape]'s covers down ([PlumDimens.gridGapDown]), for every cover class (the tall-cover gap only made room for growth). */
    fun gridRowGap(shape: CoverShape): Dp = PlumDimens.gridGapDown

    /**
     * What the covers span across: the whole grid area, from where the list starts to the right margin the clock and the hint bar end on
     * (672 on the RP5, 741 on the Thor, 480 on the Nova). Every full row meets both ends; nothing is left over on the right.
     */
    val gridCoversWidth: Dp = gridAreaWidth

    /** One cover's width: the covers' span shared by the columns after the gaps, exactly (a size picks a column count, never a width). */
    fun gridCoverWidth(shape: CoverShape): Dp {
        val columns = gridColumns(shape)
        return (gridCoversWidth - gridGap(shape) * (columns - 1)) / columns
    }

    /**
     * The height of a box (collar included) of [shape] one [gridCoverWidth] wide: its picture is exactly the shape. It is the target
     * height of a mixed grid's rows and the height of every row of a one-shape grid. Not rounded: rows snap to whole px as they lay out.
     * How many rows show follows from it ([gridRowsThatFit]); a row cut at the strip's edge reads as "more".
     */
    fun gridRowHeight(shape: CoverShape): Dp {
        val chromeX = CollarSize.Cover.sidePadding * 2
        val chromeY = CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding
        return (gridCoverWidth(shape) - chromeX) / shape.widthOverHeight + chromeY
    }

    /** Grid rows of [shape] that fit whole in the grid (at least one). */
    fun gridRowsThatFit(shape: CoverShape): Int =
        floor((gridStripHeight + gridRowGap(shape)) / (gridRowHeight(shape) + gridRowGap(shape))).toInt().coerceAtLeast(1)

    /**
     * Settings in three columns (sections, options, right column) on a wide screen at the text sizes where they fit, else
     * two (docs/design/rules.md › Settings). Decided from the width class, the text size and the font only ([SettingsColumns]).
     */
    val isSettingsThreeColumns: Boolean = widthClass == WidthClass.Wide && settingsThreeFit

    /**
     * Settings options column with the sections slid away. Wide: `width − 320` (margin, panel,
     * gap, margin; 448 on the RP5). Narrow: `width − 48` (both margins; 528 on the Nova).
     */
    val settingsOptionsWidth: Dp =
        if (isSettingsThreeColumns) {
            width - SettingsThreeDimens.sectionsWidth - SettingsThreeDimens.optionsGap - SettingsThreeDimens.panelGap -
                SettingsThreeDimens.panelWidth - PlumDimens.stripMarginSide
        } else if (widthClass == WidthClass.Wide) {
            width - PlumDimens.stripMarginSide * 2 - PlumDimens.columnGap - PlumDimens.settingsPanelWidth
        } else {
            width - PlumDimens.stripMarginSide * 2
        }

    /** The widest a value gets in an options row. */
    val settingsValueMaxWidth: Dp =
        if (isSettingsThreeColumns) settingsOptionsWidth * SettingsThreeDimens.VALUE_SHARE else settingsOptionsWidth / 2

    /**
     * The options column while the sections are focused (narrow, 640 and up): it is a preview under the sections, so
     * it is as wide as the room left of the right margin (`width − 272`: 368 on the Nova's 640), its values whole;
     * once an option is focused it takes [settingsOptionsWidth]. Elsewhere it is [settingsOptionsWidth].
     */
    val settingsOptionsPreviewWidth: Dp =
        if (widthClass == WidthClass.Narrow && width >= WidthClass.NARROW_FITTED_MIN_WIDTH) {
            width - PlumDimens.stripMarginSide - PlumDimens.settingsSectionsWidth - PlumDimens.columnGap - PlumDimens.stripMarginSide
        } else {
            settingsOptionsWidth
        }

    // Heights of boxes around text: the table's dp at Medium, growing in step with the text above
    // Medium so a line never clips (design/rules.md › Type). Below Medium they stay (the text fits).
    val rowHeight: Dp = PlumDimens.rowHeight * textScale
    val gameRowHeight: Dp = maxOf(PlumDimens.gameRowHeight * textScale, PlumDimens.gameRowMinHeight)
    val systemRowHeight: Dp = maxOf(PlumDimens.systemRowHeight * textScale, PlumDimens.systemRowMinHeight)

    /** The letter rail's least room per letter, growing with the text like the rows. */
    val railLetterMinHeight: Dp = PlumDimens.railLetterMinHeight * textScale
    val topBarHeight: Dp = maxOf(PlumDimens.topBarHeight * textScale, topBarLine)

    /** The top bar's height while a keyboard tray is up: the base height whatever the place-name line (the field and the keys need the room). */
    val topBarHeightTyping: Dp = PlumDimens.topBarHeight * textScale

    val hintBarHeight: Dp = PlumDimens.hintBarHeight * hintScale

    /** The footer zone every screen with hint keys keeps under its content: [PlumDimens.footerGap], the bar, [PlumDimens.footerBelow] (40 at Small). */
    val footerZone: Dp = PlumDimens.footerGap + hintBarHeight + PlumDimens.footerBelow

    /** The systems column widens with the text so a name keeps its line. */
    val systemsColumnWidth: Dp = PlumDimens.systemsColumnWidth * textScale
    val keyCapHeight: Dp = PlumDimens.keyCapHeight * hintScale
    val settingsHexRowHeight: Dp = PlumDimens.settingsHexRowHeight * textScale
    val settingsPreviewRowHeight: Dp = PlumDimens.settingsPreviewRowHeight * textScale
    val setupPointHeight: Dp = PlumDimens.setupPointHeight * textScale
    val setupButtonHeight: Dp = PlumDimens.setupButtonHeight * textScale
    val setupChoiceMinHeight: Dp = PlumDimens.setupChoiceMinHeight * textScale
    val setupFoundRowHeight: Dp = PlumDimens.setupFoundRowHeight * textScale

    /** Under the keys on the tray: the gap to the hint bar, the hint bar, the bottom margin. */
    private val keyboardBelowKeys: Dp = KeyboardDimens.keyboardHintGap + hintBarHeight + PlumDimens.footerBelow

    /**
     * One column of the on-screen keyboard: 44, whatever the width or the text size, until the screen
     * is narrower than the block plus the strip's margins (476 + 2 × 24); then the block shrinks to
     * `width − 48` (484 wide: 436; every reference screen keeps 476).
     */
    val keyboardColumnWidth: Dp = keyboardColumnWidth(width)

    /** The block of keys: 10 columns and 9 gaps (476), centred on the screen by [com.plumemu.coreui.components.keyboard.PlumKeyboard]. */
    val keyboardBlockWidth: Dp = keyboardBlockWidth(width)

    /**
     * A keyboard key: 36 at every text size (the labels grow, the keys do not). The old half-screen limit on
     * the key height is gone: with keys at 36 the tray ([keyboardTrayHeight]) is under half the screen on 432
     * and 471 high (`PlumLayoutTest`, `KeyboardFitTest` assert it) and over it on 375 high, where nothing can
     * shrink below the 40 dp touch area.
     */
    val keyboardKeyHeight: Dp = KeyboardDimens.keyboardKeyHeight

    /** The keyboard without what the caller puts under it: 8 dp of tray, four rows of keys, three gaps (164). */
    val keyboardHeight: Dp =
        KeyboardDimens.keyboardTopPadding + keyboardKeyHeight * KeyboardDimens.KEYBOARD_ROWS +
            KeyboardDimens.keyboardKeyGap * (KeyboardDimens.KEYBOARD_ROWS - 1)

    /**
     * The whole tray as a screen shows it: [keyboardHeight], then the 6 gap, the hint bar and the
     * 12 bottom margin, the tray running to the screen's bottom with the hint bar on it (204 at Medium).
     * The height a caller gives [com.plumemu.coreui.components.keyboard.PlumKeyboard].
     */
    val keyboardTrayHeight: Dp = keyboardHeight + keyboardBelowKeys

    /** Shift, delete and hide icons: 18 at Medium, growing with the text. */
    val keyboardIconSize: Dp = KeyboardDimens.keyboardIconSize * textScale

    /** The caret of a field typed with the keyboard: 1.5 at Medium, growing with the text (drawn, never blinking). */
    val caretWidth: Dp = PlumDimens.caretWidth * textScale

    /** How far the hint keys' collars reach above the hint bar (0 when the bar has room for them): a list's bottom keeps this much extra above the bar so the gap to the drawn keys is still [PlumDimens.barGap]. */
    val hintKeyReach: Dp = HintKeyDimens.reachAboveBar(hintBarHeight, keyCapHeight)

    /** The strip's height between the bars: 346 at 432 high at Medium. */
    val stripHeight: Dp =
        height - PlumDimens.stripMarginTop - topBarHeight - PlumDimens.barGap - footerZone

    /** The cover grid's height: the strip less what its wider top gap ([PlumDimens.gridTopGap], not [PlumDimens.barGap]) and its bottom room take. */
    val gridStripHeight: Dp = stripHeight - (PlumDimens.gridTopGap - PlumDimens.barGap) - PlumDimens.gridBottomRoom

    /** The Apps grid's height: the strip less its wider top gap ([PlumDimens.appsGridTopGap]). */
    val appsStripHeight: Dp = stripHeight - (PlumDimens.appsGridTopGap - PlumDimens.barGap)

    /**
     * Game rows that fit between the bars (the games list only), counting the gap after each; fewer at bigger text.
     * Counted in rounded pixels, as the rows lay out: a count that fits in dp can overflow by a pixel per row and cut the first row.
     */
    val listRowsThatFit: Int =
        floor(
            (floor(stripHeight.value * pixelsPerDp) + (PlumDimens.listRowGap.value * pixelsPerDp).roundToInt()) /
                ((gameRowHeight + PlumDimens.listRowGap).value * pixelsPerDp).roundToInt(),
        ).toInt().coerceAtLeast(0)

    /** The games list's height: the [listRowsThatFit] whole rows, the last without its gap. The list ends on a row (where the list fades) and the scroll keeper's window is this, so the two agree on the last row. */
    val listRowsHeight: Dp = ((gameRowHeight + PlumDimens.listRowGap) * listRowsThatFit - PlumDimens.listRowGap).coerceAtLeast(0.dp)

    private fun gridColumns(
        cell: Dp,
        gap: Dp,
    ): Int = floor((gridAreaWidth + gap) / (cell + gap)).toInt().coerceAtLeast(1)

    companion object {
        /** One keyboard column on a keyboard [width] wide (the keyboard measures with the width it is given). */
        fun keyboardColumnWidth(width: Dp): Dp =
            minOf(
                KeyboardDimens.keyboardColumnWidth,
                (width - PlumDimens.stripMarginSide * 2 - KeyboardDimens.keyboardKeyGap * (KeyboardDimens.KEYBOARD_COLUMNS - 1)) /
                    KeyboardDimens.KEYBOARD_COLUMNS,
            )

        /** The width of the keys' block on a keyboard [width] wide: 476, or `width − 48` on a narrower screen. */
        fun keyboardBlockWidth(width: Dp): Dp =
            keyboardColumnWidth(width) * KeyboardDimens.KEYBOARD_COLUMNS +
                KeyboardDimens.keyboardKeyGap * (KeyboardDimens.KEYBOARD_COLUMNS - 1)

        /**
         * The narrowest cover of [coverClass] at [size] (docs/design/rules.md › Grid): the cell is
         * what the column count is counted with (columns = floor((area + gap) / (cell + gap)) with the old 14 landscape / 12 square and tall gaps, so the columns kept their counts when the drawn gaps grew to 22 / 20);
         * the cover is the covers' span shared by the columns after their gaps, so it can be a little narrower than the cell
         * (Small tall on the Thor: cell 79, cover 74.1). Chosen so the
         * Thor's 741 dp area (736 at its default Large text, whose peek is wider) gets these
         * columns (Small / Medium / Large: landscape 5 / 4 / 3, square 6 / 5 / 4, tall 8 / 6 / 5),
         * the RP5 loses a column at each larger size, and no reference width sits within a few dp
         * of a change of count. The board's 200 (landscape, Large) and 106 (tall, Medium) and G1's
         * 130 (square, Medium) qualify and are kept, so those grids keep today's columns at every
         * text size. Square Large is 172 so 640 wide gets fewer columns at Large than at Medium (GridSizeStepsTest).
         */
        internal fun gridCell(
            coverClass: CoverClass,
            size: GridSize,
        ): Dp =
            when (coverClass) {
                CoverClass.Landscape ->
                    when (size) {
                        GridSize.Small -> 118.dp
                        GridSize.Medium -> 142.dp
                        GridSize.Large -> 200.dp
                    }
                CoverClass.Square ->
                    when (size) {
                        GridSize.Small -> 99.dp
                        GridSize.Medium -> 130.dp
                        GridSize.Large -> 172.dp
                    }
                CoverClass.Tall ->
                    when (size) {
                        GridSize.Small -> 79.dp
                        GridSize.Medium -> 106.dp
                        GridSize.Large -> 128.dp
                    }
            }
    }
}
