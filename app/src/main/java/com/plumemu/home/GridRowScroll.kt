package com.plumemu.home

import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ListGlide

/**
 * Where each row of a grid stands, in whole px as the lazy grid lays it out: every row's height and its top from the first row's,
 * a row gap apart. Rows may differ in height (a justified row of mixed box art), so the scroll rules read these, never one row height.
 * Built once per packing; the arrays are never changed.
 */
internal class RowSpans(
    val tops: IntArray,
    val heights: IntArray,
) {
    val count: Int get() = tops.size

    fun bottom(row: Int): Int = tops[row] + heights[row]

    /** The first row to show so [row] ends a window of [viewport] px: the top when [row] fits there, else the first row that makes it the last whole one. */
    fun firstShowing(
        row: Int,
        viewport: Int,
    ): Int {
        if (count == 0) return 0
        val last = row.coerceIn(0, count - 1)
        val limit = bottom(last) - viewport
        var low = 0
        var high = last
        while (low < high) {
            val mid = (low + high) ushr 1
            if (tops[mid] >= limit) high = mid else low = mid + 1
        }
        return low
    }

    /** The first row of the window the end of the list rests in: the last rows that fit [viewport] whole. */
    fun lastFirst(viewport: Int): Int = firstShowing(count - 1, viewport)

    /** The height left under the last whole rows when the list is scrolled to its end ([lastFirst]), added as padding so the end rests on a row's top. */
    fun leftover(viewport: Int): Int = if (count == 0) 0 else (viewport - (bottom(count - 1) - tops[lastFirst(viewport)])).coerceAtLeast(0)

    companion object {
        /** The rows of [grid] a [rowGap] apart, each as the lazy grid rounds it to px. */
        fun of(
            grid: PackedGrid,
            rowGap: Dp,
            density: Density,
        ): RowSpans = build(grid.rows.size, with(density) { rowGap.roundToPx() }) { with(density) { Dp(grid.rows[it].height).roundToPx() } }

        /** [count] rows of one [height] px a [gap] px apart (Apps' grid). */
        fun uniform(
            count: Int,
            height: Int,
            gap: Int,
        ): RowSpans = build(count, gap) { height }

        private fun build(
            count: Int,
            gap: Int,
            heightOf: (Int) -> Int,
        ): RowSpans {
            val heights = IntArray(count) { heightOf(it) }
            val tops = IntArray(count)
            var top = 0
            for (i in 0 until count) {
                tops[i] = top
                top += heights[i] + gap
            }
            return RowSpans(tops, heights)
        }
    }
}

/**
 * The grid's window height in px (the grid without the glide's room at each end): measured once the grid has a layout, from the
 * strip's [stripHeight] until then. Read where a scroll rule needs it, not in composition.
 */
@Composable
internal fun rememberGridViewportPx(
    gridState: LazyGridState,
    stripHeight: Dp,
    /** What the grid's top padding holds besides its glide's room, and the same at its bottom without the leftover: rows run through both to the bars. */
    roomAbove: Dp = 0.dp,
    roomBelow: Dp = 0.dp,
): State<Int> {
    val density = LocalDensity.current
    val fallback = with(density) { stripHeight.roundToPx() }
    val abovePx = with(density) { roomAbove.roundToPx() }
    val belowPx = with(density) { roomBelow.roundToPx() }
    return remember(gridState, fallback, abovePx, belowPx) {
        derivedStateOf {
            val info = gridState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) fallback else info.viewportSize.height - 2 * (info.beforeContentPadding - abovePx) - abovePx - belowPx
        }
    }
}

/**
 * The height left under the last whole rows, added as padding under the last row so the grid can scroll to a position where its
 * top is a row's top (without it the end of the list stops with a cut row's tail at the top, which shows alone under the header).
 * Whole px from [rows], so dp rounded to px on a screen like the Thor's (2.29375) never leaves the last row cut by a px or three.
 * Changes only when the last rows or the window do, so a result landing elsewhere recomposes nothing.
 */
@Composable
internal fun rememberGridLeftover(
    rows: State<RowSpans>,
    viewport: State<Int>,
): Dp {
    val density = LocalDensity.current
    val px by remember(rows, viewport) { derivedStateOf { rows.value.leftover(viewport.value) } }
    return with(density) { px.toDp() }
}

/**
 * Keeps the focused cover's row on screen (docs/design/rules.md › Focus and current: grids scroll so its row shows), in the same
 * frame as the focus moves, and glides like the list (`KeepFocusedVisible`). [columns] covers make a lazy item (1 in the cover grid,
 * whose items are packed rows).
 */
@Composable
internal fun KeepFocusedCellVisible(
    gridState: LazyGridState,
    columns: Int,
    rows: State<RowSpans>,
    viewport: State<Int>,
    glide: ListGlide,
    focusedIndex: () -> Int,
) {
    val latest = rememberUpdatedState(focusedIndex)
    val index by remember(gridState) { derivedStateOf { latest.value() } }
    val keeper = remember(gridState, columns) { ScrollKeeper() }
    val target = index
    var kept: KeptScroll? = null
    val moved =
        Snapshot.withoutReadObservation {
            val items = gridState.layoutInfo.visibleItemsInfo
            keeper.update(target, hasLayout = items.isNotEmpty(), isLaidOut = items.any { it.index == target }) {
                gridState.keepVisible(target, columns, rows.value, viewport.value).also { kept = it }.moved
            }
        }
    // Also when nothing scrolled. The grid scrolls only when the row past the focus is not whole (the rule it
    // always had); when it does, the new row glides in from the grid's edge and is clipped by it.
    kept?.let { glide.jumped(moved.toFloat(), it.roomAbove, it.roomBelow) }
    if (keeper.isWaiting) gridState.layoutInfo
    KeepFocusedCellOnResize(gridState, columns, rows, viewport) { latest.value() }
}

/**
 * Room the grid's glide may draw rows past the grid's edge: unbounded, so the new row slides in from the edge and the grid's
 * clip cuts it while it glides ([ListGlide] caps the offset by the rows it composed). A held key still goes linear.
 */
private const val GRID_GLIDE_ROOM = Float.MAX_VALUE

/**
 * The grid got another height (a larger text size or font makes the bars taller and the window shorter) while the focus stood
 * still: the scroll is aligned to whole rows again with the focused row shown, without a glide. Only this small scope reads the
 * viewport's height.
 */
@Composable
private fun KeepFocusedCellOnResize(
    gridState: LazyGridState,
    columns: Int,
    rows: State<RowSpans>,
    viewport: State<Int>,
    focusedIndex: () -> Int,
) {
    // Read in a flow, not in composition: while the grid glides its layout info is rewritten every frame, and a
    // composition-time read recomposed this scope on a move (HomeRecompositionTest) though the height stood still.
    val focusNow = rememberUpdatedState(focusedIndex)
    LaunchedEffect(gridState, columns) {
        var seen: Pair<Int, Int>? = null
        snapshotFlow { gridState.layoutInfo.viewportSize.height to viewport.value }.collect { now ->
            val before = seen
            seen = now
            if (before != null && before.first != 0 && before != now) gridState.alignToRows(focusNow.value(), columns, rows.value, now.second)
        }
    }
}

/** The scroll to whole rows with row [index]'s shown: the first shown row stays when it can, else moves least. */
private fun LazyGridState.alignToRows(
    index: Int,
    columns: Int,
    rows: RowSpans,
    viewport: Int,
) {
    val total = layoutInfo.totalItemsCount
    if (total == 0 || index !in 0 until total || rows.count == 0) return
    val row = (index / columns).coerceAtMost(rows.count - 1)
    val first = (firstVisibleItemIndex / columns).coerceIn(0, rows.lastFirst(viewport))
    val aligned = if (row < first) row else if (rows.bottom(row) - rows.tops[first] > viewport) rows.firstShowing(row, viewport) else first
    requestScrollToItem(aligned * columns, 0)
}

/**
 * Touch flings end on a row: the grid snaps to the nearest row's start (foundation's snapping, as a pager's), so a
 * fling never leaves a row cut at the top. The room under the last row ([rememberGridLeftover]) lets the end be one.
 */
@Composable
internal fun rememberRowSnapFling(gridState: LazyGridState) = rememberSnapFlingBehavior(remember(gridState) { SnapLayoutInfoProvider(gridState, SnapPosition.Start) })

/**
 * The grid's `keepVisible` (KeepFocusedVisible.kt), by rows of [columns] covers: the scroll that shows the focused row whole, and the
 * room left above and below it for a glide. Row positions come from [rows], so rows of different heights are as good as equal ones.
 */
private fun LazyGridState.keepVisible(
    index: Int,
    columns: Int,
    rows: RowSpans,
    viewport: Int,
): KeptScroll {
    val info = layoutInfo
    val row = index / columns
    val firstRow = firstVisibleItemIndex / columns
    if (info.visibleItemsInfo.isEmpty() || index !in 0 until info.totalItemsCount || row >= rows.count || firstRow >= rows.count) return KeptScroll(0, 0f, 0f)
    val scrolled = firstVisibleItemScrollOffset
    // Where the row stands against the window's top now.
    val top = rows.tops[row] - rows.tops[firstRow] - scrolled
    // By rows when the grid stands on a row (never in px: the room under the rows is rounded from dp, a px or
    // three off the window's end), by px after a touch scroll left it between rows.
    val onRow = scrolled == 0
    val isBelow = if (onRow) rows.bottom(row) - rows.tops[firstRow] > viewport else top + rows.heights[row] > viewport
    val isAbove = if (onRow) row < firstRow else top < 0
    return when {
        isAbove -> {
            requestScrollToItem(row * columns, 0)
            KeptScroll(-top, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
        }
        isBelow -> {
            // Scrolls by whole rows: the focused row lands as the last whole row and the rows above it fill the window from its top,
            // so no row is cut at the top (a cut row's tail, an app's second name line or a text tile's title, drew alone under the
            // header). What does not fit is left under the last whole row, where a cut row reads as "more".
            val newFirst = rows.firstShowing(row, viewport)
            requestScrollToItem(newFirst * columns, 0)
            KeptScroll(rows.tops[row] - rows.tops[newFirst] - top, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
        }
        else -> KeptScroll(0, GRID_GLIDE_ROOM, GRID_GLIDE_ROOM)
    }
}
