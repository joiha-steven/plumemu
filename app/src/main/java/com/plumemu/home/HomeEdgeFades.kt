package com.plumemu.home

import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.plumemu.coreui.components.EdgeFades
import com.plumemu.coreui.theme.ListGlide

/**
 * Where a grid's rows stop under the bars, read while drawing: a row cut at the run's edge goes under the bar and the screen's fade, and a row showing less than [minShown] of itself
 * is left out whole, so no sliver of covers or names stands at a bar.
 */
internal class GridEdgeFades(
    private val grid: LazyGridState,
    private val glide: ListGlide,
    /** The grid's room above its first row, in whole px as the list lays it out: a fraction here starts the mask inside a row's first px and leaves a hairline of a left-out row. */
    private val topRoom: Float,
    private val minShown: Float = 1f / 3f,
    /** How far the focused cover's ring reaches past its row, as a share of the row's height and in px; negative for a grid that lifts nothing past its rows. */
    private val liftShare: Float = -1f,
    private val ringPx: Float = 0f,
    /** Room the fade has finished in below the run's end (home's footer gap): a bottom row is judged by [minShown] of it counting this room, or one a hair short vanishes. */
    private val bottomGapPx: Float = 0f,
    private val focused: () -> Int,
) : EdgeFades, BarRunner {
    override val canScroll: Boolean
        get() = grid.canScrollForward || grid.canScrollBackward

    override fun top(
        fadePx: Float,
        height: Float,
    ): Float = 0f

    override fun bottom(
        fadePx: Float,
        height: Float,
    ): Float = 0f

    override val runsUnderTop: Boolean
        get() = grid.canScrollBackward

    override fun focusUnderTop(bandPx: Float): Boolean = grid.layoutInfo.visibleItemsInfo.any { it.index == focused() && it.offset.y + topRoom + glide.offset < bandPx }

    override val roomUnderFocus: Float
        get() {
            if (liftShare < 0f) return Float.POSITIVE_INFINITY
            val info = grid.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.index == focused() } ?: return Float.POSITIVE_INFINITY
            val run = info.viewportSize.height - 2 * (info.beforeContentPadding - topRoom)
            return run - (item.offset.y + topRoom + glide.offset + item.size.height * (1f + liftShare) + ringPx)
        }

    override fun clearTop(height: Float): Float {
        val shift = topRoom + glide.offset
        return grid.layoutInfo.visibleItemsInfo.filter { leftOutAtTop(it) }.maxOfOrNull { it.offset.y + it.size.height + shift } ?: 0f
    }

    override fun clearBottom(height: Float): Float {
        val shift = topRoom + glide.offset
        return grid.layoutInfo.visibleItemsInfo.filter { leftOutAtBottom(it, height) }.maxOfOrNull { height - (it.offset.y + shift) } ?: 0f
    }

    /** Whether [item] is cut by the top edge and less than [minShown] of it shows. */
    private fun leftOutAtTop(item: LazyGridItemInfo): Boolean {
        if (minShown <= 0f) return false
        val end = item.offset.y + topRoom + item.size.height + glide.offset
        return item.offset.y + topRoom + glide.offset < -CUT_PX && end > CUT_PX && end < minShown * item.size.height
    }

    /** As [leftOutAtTop], at the bottom edge of a grid [height] px tall. */
    private fun leftOutAtBottom(
        item: LazyGridItemInfo,
        height: Float,
    ): Boolean {
        if (minShown <= 0f) return false
        val top = item.offset.y + topRoom + glide.offset
        return top < height - CUT_PX && top + item.size.height > height + CUT_PX && height + bottomGapPx - top < minShown * item.size.height
    }

    private companion object {
        /** A row this few px past an edge is not cut: rows stand on whole px, dp rounding can leave one over. */
        const val CUT_PX = 1f
    }
}
