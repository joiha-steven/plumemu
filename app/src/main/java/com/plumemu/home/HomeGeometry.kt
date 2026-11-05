package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripGeometry
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumFont
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass

/**
 * The strip's sizes for core-input, from the theme. The strip starts at the screen edge with the
 * 24 dp margin inside it (`HomeStrip`), as core-input's strip coordinates expect. [showsGrid]:
 * the focused system is a grid, so the games column is the grid area (the narrow details stop
 * slides it out by that width).
 */
internal fun homeStripGeometry(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    showsGrid: Boolean = false,
): StripGeometry =
    StripGeometry(
        isNarrow = layout.widthClass == WidthClass.Narrow,
        leadingMargin = PlumDimens.stripMarginSide,
        systemsWidth = layout.systemsColumnWidth,
        gamesListWidth = homeGamesWidth(layout, hasBoxArt, showsGrid),
        columnGap = PlumDimens.columnGap,
        peek = layout.peek,
        detailsStopListPeek = PlumDimens.detailsStopListPeek,
    )

/**
 * The games list: `width − 368` beside the details (400 on narrow screens, details the third
 * stop; to the right margin where the details are left out, [detailsLeftOutAtGamesStop]), `width − 96` without them.
 */
internal fun homeListWidth(
    layout: PlumLayout,
    hasBoxArt: Boolean,
) = if (listHasDetails(layout, hasBoxArt) && !detailsLeftOutAtGamesStop(layout)) layout.gamesListWidth else layout.listWidthWithoutArt

/**
 * Whether a narrow screen has under [PlumDimens.detailsCoverMinWide] of room for the details column beside the 400 dp list at the games
 * stop (as at 576): the column is left out there, so the list takes its room up to the right margin (no blank gap). Decided from the
 * layout alone, so the list keeps its width at every stop.
 */
private fun detailsLeftOutAtGamesStop(layout: PlumLayout): Boolean {
    if (layout.widthClass == WidthClass.Wide) return false
    val systems = layout.systemsColumnWidth
    val gamesOffset = layout.peek.coerceIn(0.dp, systems) - (PlumDimens.stripMarginSide + systems)
    val beginsAt = PlumDimens.stripMarginSide + systems + PlumDimens.columnGap + layout.gamesListWidth + PlumDimens.columnGap + gamesOffset
    return layout.width - PlumDimens.stripMarginSide - beginsAt < PlumDimens.detailsCoverMinWide
}

/**
 * Whether a list has a details column (or the narrow details stop) beside it: with box art; without
 * it only on a wide screen, as the text-only column. Apps' list has one too (its icon and package, [AppDetails]),
 * so its list keeps this width, as with box art.
 */
internal fun listHasDetails(
    layout: PlumLayout,
    hasBoxArt: Boolean,
): Boolean = hasBoxArt || layout.widthClass == WidthClass.Wide

/**
 * Whether a single system's list with box art writes "year · region" at its right
 * rather than the year alone. The 400 dp list beside the details has it on a wide screen but not at Huge or Largest text (the systems
 * column grows with the text, the list shrinks and "Donkey Kong Country 2" would end in "…"), and
 * never on a narrow screen (cut by 14 dp at Largest). Mono's wide letters leave it from Large: a 25-letter title is 23 dp short beside both columns. Decided by the list's kind, width class and
 * text size, never by measuring a row.
 */
internal fun listShowsPlace(
    layout: PlumLayout,
    font: PlumFont,
): Boolean = layout.widthClass == WidthClass.Wide && layout.textScale < (if (font == PlumFont.Mono) TextSize.Large else TextSize.Huge).heightScale

/** The games column: the grid area (`width − 96`) for a grid else the list. */
internal fun homeGamesWidth(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    showsGrid: Boolean,
): Dp = if (showsGrid) layout.gridAreaWidth else homeListWidth(layout, hasBoxArt)

/**
 * How much of the wide details column is inside the right margin while the systems have the focus: the strip rests at its start, so the
 * column begins where the list ends and only the rest of the screen is its. The list keeps its width; the cover is sized to this.
 */
internal fun detailsRestWidth(
    layout: PlumLayout,
    hasBoxArt: Boolean,
): Dp =
    (layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap * 2 - homeGamesWidth(layout, hasBoxArt, showsGrid = false))
        .coerceIn(0.dp, homeDetailsWidth(layout))

/**
 * Whether the wide details column's cover can be shown while the systems have the focus: only if the whole column ends inside the right
 * margin. A cover is whole inside the margins or left out, never cut by the screen's edge and never a small thumbnail.
 */
internal fun detailsCoverFitsWithSystemsOpen(
    layout: PlumLayout,
    hasBoxArt: Boolean,
): Boolean = detailsRestWidth(layout, hasBoxArt) >= homeDetailsWidth(layout)

/** The details column: 248 beside the list on wide screens, `width − 104` as the narrow details stop. */
internal fun homeDetailsWidth(layout: PlumLayout): Dp =
    if (layout.widthClass == WidthClass.Wide) PlumDimens.detailsColumnWidth else layout.detailsStopWidth

/**
 * The width the details column takes at the strip's stop [column]: the room from where it begins on screen to the right margin, at most
 * [homeDetailsWidth], so it is never cut by the screen's edge. A narrow screen's third stop ([Column.Details]) is the full
 * `width − 104` that ends at the margin already; at its other stops the column takes the room left, or none when that is under
 * [PlumDimens.detailsCoverMinWide] (a cover is whole inside the margins or left out, never a sliver).
 * A wide screen's column with the systems open keeps the room it has (its cover shows alone, [detailsRestWidth]).
 */
internal fun homeDetailsFit(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    showsGrid: Boolean,
    column: Column,
): Dp {
    val full = homeDetailsWidth(layout)
    val isNarrow = layout.widthClass != WidthClass.Wide
    if (isNarrow && column == Column.Details) return full
    val beginsAt =
        PlumDimens.stripMarginSide + layout.systemsColumnWidth + PlumDimens.columnGap + homeGamesWidth(layout, hasBoxArt, showsGrid) + PlumDimens.columnGap +
            homeStripGeometry(layout, hasBoxArt, showsGrid).offsetOf(column)
    val room = (layout.width - PlumDimens.stripMarginSide - beginsAt).coerceIn(0.dp, if (isNarrow) PlumDimens.detailsColumnWidth else full)
    return if (isNarrow && room < PlumDimens.detailsCoverMinWide) 0.dp else room
}

/**
 * The games list as the board places it: [PlumDimens.gamesListBleed] further left than its box (64 dp from the edge, 408 wide, ending where
 * the box ends, so nothing to its right moves), and on a dark theme the hairline between the systems' numbers and the list, drawn
 * [PlumDimens.listDividerOffset] left of the box and inset from the strip's top and bottom. A light theme has no hairline on the board.
 */
@Composable
internal fun Modifier.boardListStart(): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
            if (colors.bg.luminance() < LIGHT_THEME_LUMINANCE) {
                val inset = PlumDimens.listDividerInset.toPx()
                val width = PlumDimens.listDividerWidth.toPx()
                drawRect(
                    color = colors.text.copy(alpha = DIVIDER_ALPHA),
                    topLeft = Offset(-PlumDimens.listDividerOffset.toPx(), inset),
                    size = Size(width, size.height - inset * 2),
                )
            }
        }.layout { measurable, constraints ->
            val bleed = PlumDimens.gamesListBleed.roundToPx()
            val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth + bleed, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(-bleed, 0) }
        }
}

private const val DIVIDER_ALPHA = 0.08f
private const val LIGHT_THEME_LUMINANCE = 0.5f
