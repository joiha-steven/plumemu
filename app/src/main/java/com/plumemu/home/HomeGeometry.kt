package com.plumemu.home

import androidx.compose.ui.unit.Dp
import com.plumemu.coreinput.strip.StripContent
import com.plumemu.coreinput.strip.StripGeometry
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import com.plumemu.coreui.theme.TextSize
import com.plumemu.coreui.theme.WidthClass

/**
 * The strip's sizes for core-input, from the theme. The strip starts at the screen edge with the
 * 24 dp margin inside it (`HomeStrip`), as core-input's strip coordinates expect. [showsGrid]:
 * the focused system is a grid, so the games column is the grid area (the narrow details stop
 * slides it out by that width). [detailsOnSecondScreen]: the second screen's window is up, so a
 * list has no details column ([homeListWidth]).
 */
internal fun homeStripGeometry(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    showsGrid: Boolean = false,
    detailsOnSecondScreen: Boolean = false,
): StripGeometry =
    StripGeometry(
        isNarrow = layout.widthClass == WidthClass.Narrow,
        leadingMargin = PlumDimens.stripMarginSide,
        systemsWidth = layout.systemsColumnWidth,
        gamesListWidth = homeGamesWidth(layout, hasBoxArt, showsGrid, detailsOnSecondScreen),
        columnGap = PlumDimens.columnGap,
        peek = layout.peek,
        detailsStopListPeek = PlumDimens.detailsStopListPeek,
    )

/**
 * The games list: `width − 368` beside the details (400 on narrow screens, details the third
 * stop), `width − 96` without them. While the second screen's window is up
 * ([detailsOnSecondScreen], layout C) the details are on that screen and the list takes the
 * no-details width too.
 */
internal fun homeListWidth(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    detailsOnSecondScreen: Boolean = false,
) = if (listHasDetails(layout, hasBoxArt, detailsOnSecondScreen)) layout.gamesListWidth else layout.listWidthWithoutArt

/**
 * Whether a list has a details column (or the narrow details stop) beside it: with box art; without
 * it only on a wide screen, as the text-only column; never while the
 * second screen's window is up ([detailsOnSecondScreen], layout C). Apps never shows one
 * ([HomeEntry.hasDetails]), but its list keeps this width, as with box art.
 */
internal fun listHasDetails(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    detailsOnSecondScreen: Boolean,
): Boolean = !detailsOnSecondScreen && (hasBoxArt || layout.widthClass == WidthClass.Wide)

/**
 * Whether a single system's list with box art writes "year · region" at its right
 * rather than the year alone. The full-width list (`width − 96`, the second screen's window up) always
 * has the room for a 21-character title beside it, also at Largest on 576 (RowYearRegionFitTest).
 * The 400 dp list beside the details has it on a wide screen but not at Largest text (the systems
 * column grows with the text, the list shrinks and "Donkey Kong Country 2" would end in "…"), and
 * never on a narrow screen (cut by 14 dp at Largest). Decided by the list's kind, width class and
 * text size, never by measuring a row.
 */
internal fun listShowsPlace(
    layout: PlumLayout,
    detailsOnSecondScreen: Boolean,
) = detailsOnSecondScreen || (layout.widthClass == WidthClass.Wide && layout.textScale < TextSize.Largest.heightScale)

/** The games column: the grid area (`width − 96`) for a grid (also while the second screen is up), else the list. */
internal fun homeGamesWidth(
    layout: PlumLayout,
    hasBoxArt: Boolean,
    showsGrid: Boolean,
    detailsOnSecondScreen: Boolean = false,
): Dp = if (showsGrid) layout.gridAreaWidth else homeListWidth(layout, hasBoxArt, detailsOnSecondScreen)

/** The details column: 248 beside the list on wide screens, `width − 104` as the narrow details stop. */
internal fun homeDetailsWidth(layout: PlumLayout): Dp =
    if (layout.widthClass == WidthClass.Wide) PlumDimens.detailsColumnWidth else layout.detailsStopWidth

/**
 * [strip] while the second screen's window is up:
 * a list's details are on that screen, so no list has a details column or a narrow details stop
 * (Right from the list does nothing there, and a drag finds no stop). A grid keeps its stop. A
 * focus on the details stop goes back to the list on the same game (`StripState.content`).
 */
internal fun StripContent.withDetailsOnSecondScreen(
    content: HomeContent,
    gridIds: Set<String>,
): StripContent =
    copy(
        systems =
            systems.mapIndexed { index, system ->
                if (system.hasDetails && !content.showsGrid(content.entries.getOrNull(index), gridIds)) system.copy(hasDetails = false) else system
            },
    )
