package com.plumemu.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.theme.CoverClass
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.graphicsLayer

/*
 * The Apps grid's window: only whole cells show. Rows: the window is a whole number of rows high, so no row is cut by its end at rest (the scroll rules
 * already rest on a row's top). Columns: while the systems are focused the grid previews beside them and its right part is outside the margin;
 * the preview is as wide as the whole columns that fit, and cells past it are neither drawn nor touchable until the games are focused. The whole columns are spread so the last one
 * ends at the right margin (the first stays; the room left is shared between the gaps), drawn that way per cell ([AppsSpread]): every size reaches the margin, no empty strip.
 */

/** The largest height of whole rows ([rowPx] tall, [gapPx] between) that fits in [availablePx]; [availablePx] itself when not even one row does. */
internal fun wholeRowsPx(
    availablePx: Int,
    rowPx: Int,
    gapPx: Int,
): Int {
    val rows = (availablePx + gapPx) / (rowPx + gapPx)
    return if (rows < 1) availablePx else rows * rowPx + (rows - 1) * gapPx
}

/**
 * The columns of [columns] that end within [limitPx], cells laid out as `GridCells.Fixed` does over [fullPx] with [gapPx] between: how many and how wide they are together.
 * All of them ([fullPx]) when they all fit; none (0 and 0) when not even one does.
 */
internal fun wholeColumns(
    fullPx: Int,
    columns: Int,
    gapPx: Int,
    limitPx: Int,
): Pair<Int, Int> {
    if (fullPx <= limitPx) return columns to fullPx
    val room = fullPx - gapPx * (columns - 1)
    var end = 0
    var count = 0
    var shown = 0
    for (c in 0 until columns) {
        val cell = room / columns + if (c < room % columns) 1 else 0
        val next = end + cell
        if (next > limitPx) break
        end = next + gapPx
        shown = next
        count++
    }
    return count to shown
}

/** The width of the first columns of [columns] that end within [limitPx] ([wholeColumns]). */
internal fun wholeColumnsPx(
    fullPx: Int,
    columns: Int,
    gapPx: Int,
    limitPx: Int,
): Int = wholeColumns(fullPx, columns, gapPx, limitPx).second

/**
 * How far each column moves right of the one before it so the last whole column inside [limitPx] ends exactly there: the room the whole columns leave is shared
 * between their gaps. 0 when every column fits or only one does (one cannot be spread).
 */
internal fun spreadPerColumnPx(
    fullPx: Int,
    columns: Int,
    gapPx: Int,
    limitPx: Int,
): Float {
    val (count, shown) = wholeColumns(fullPx, columns, gapPx, limitPx)
    return if (fullPx <= limitPx || count < 2) 0f else (limitPx - shown).toFloat() / (count - 1)
}

/**
 * The Apps preview's spread: column c of the grid is drawn [shiftOf] px right of its place, so the whole columns beside the systems reach the right margin. Read while drawing
 * (a cell's `graphicsLayer`, so it moves a touch target with it and recomposes nothing). It follows the strip's own position ([StripState.systemNamesAlpha]: 1 at the
 * systems' stop, 0 at the games', in between while it slides or a finger drags it), so the cells return to the full grid's places with the strip's motion, never in a jump.
 */
@Stable
internal class AppsSpread(
    private val follow: () -> Float,
) {
    /** The shift of one column step in px, set while the grid is measured; 0 for a grid that is not an Apps preview. */
    var perColumnPx by mutableFloatStateOf(0f)

    // Whole pixels: a cell and its text stay crisp at rest and while the strip moves.
    fun shiftOf(column: Int): Float = if (perColumnPx == 0f) 0f else kotlin.math.round(perColumnPx * column * follow())
}

internal val LocalAppsSpread = staticCompositionLocalOf<AppsSpread?> { null }

/**
 * While [active] (read while measuring and drawing) the grid is as wide as the margin allows and clips there, its whole columns spread over that width ([spread]); its cells are laid out as
 * before, so nothing moves when the games take the focus. [isAppsPreview] (read while measuring) says whether the entry shown is one that is spread at all.
 */
internal fun Modifier.wholeColumnsWhile(
    columns: Int,
    gapPx: Int,
    limitPx: Int,
    spread: AppsSpread,
    isAppsPreview: () -> Boolean,
    active: () -> Boolean,
): Modifier =
    graphicsLayer { clip = active() }
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val each = if (isAppsPreview()) spreadPerColumnPx(placeable.width, columns, gapPx, limitPx) else 0f
            if (each != spread.perColumnPx) spread.perColumnPx = each
            val width =
                when {
                    !active() -> placeable.width
                    // Spread, the last whole column ends at the limit: the window is as wide as that.
                    each > 0f -> limitPx
                    else -> wholeColumnsPx(placeable.width, columns, gapPx, limitPx)
                }
            layout(width, placeable.height) { placeable.place(0, 0) }
        }

/** The Apps preview beside the systems: the [modifier] for the strip's grid and its [spread] for the grid's cells (provided as [LocalAppsSpread]). */
@Stable
internal class AppsPreview(
    val modifier: Modifier,
    val spread: AppsSpread,
)

/**
 * [wholeColumnsWhile] for the strip's grid, on while an Apps grid is previewed beside the systems and the strip rests there (read when measuring and drawing, not composing):
 * the preview ends at the right margin, [PlumDimens.stripMarginSide]. Goes before the grid's own width, which would centre a narrower grid in it.
 */
@Composable
internal fun rememberAppsPreview(
    content: HomeContent,
    state: StripState,
    layout: PlumLayout,
): AppsPreview {
    val density = LocalDensity.current
    return remember(content, state, layout, density) {
        val gapPx = with(density) { layout.gridGap(CoverClass.Square).roundToPx() }
        // The grid's left is where the strip puts it at rest: after the margin, the systems column and the gap.
        val limitPx = with(density) { (layout.width - PlumDimens.stripMarginSide * 2 - layout.systemsColumnWidth - PlumDimens.columnGap).roundToPx() }
        val spread = AppsSpread { state.systemNamesAlpha }
        val isApps = { content.entries.getOrNull(state.focus.systemIndex)?.let { it.kind == EntryKind.Apps || it.isAppGamesGrid } == true }
        AppsPreview(
            Modifier.wholeColumnsWhile(appsGridColumns(layout), gapPx, limitPx, spread, isApps) {
                // Not while the strip moves (a finger drag changes the column only on release): the grid then shows what the move brings in.
                state.focus.column == Column.Systems && !state.isMoving && isApps()
            },
            spread,
        )
    }
}
