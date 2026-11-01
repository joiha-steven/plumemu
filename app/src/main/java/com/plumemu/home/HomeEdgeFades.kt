package com.plumemu.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.plumemu.coreui.components.EdgeFades
import com.plumemu.coreui.theme.ListGlide

/**
 * The home screen's scroll-edge fades (design/rules.md › Lists that scroll), positions read while drawing. Rows are
 * placed where the glide draws them ([ListGlide.offset]); offsets are from the list's visible top. A fade never
 * reaches the focused item: it stops short of it (a list opened with the focus as its last whole row).
 */
internal class ListEdgeFades(
    private val list: LazyListState,
    private val glide: ListGlide,
    private val focused: () -> Int,
) : EdgeFades {
    override val canScroll: Boolean
        get() = list.canScrollForward || list.canScrollBackward

    // While rows continue past that edge, as the game panel's lists.
    override fun top(
        fadePx: Float,
        height: Float,
    ): Float = if (list.canScrollBackward) minOf(fadePx, roomAboveFocus()) else 0f

    override fun bottom(
        fadePx: Float,
        height: Float,
    ): Float = if (list.canScrollForward) minOf(fadePx, roomBelowFocus(height)) else 0f

    private fun roomAboveFocus(): Float {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focused() } ?: return Float.MAX_VALUE
        return (item.offset + glide.offset).coerceAtLeast(0f)
    }

    private fun roomBelowFocus(height: Float): Float {
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focused() } ?: return Float.MAX_VALUE
        return (height - (item.offset + item.size + glide.offset)).coerceAtLeast(0f)
    }
}

/**
 * A grid's fades: only where a row is cut at that edge. Standing on rows the top is never cut and the room under
 * the last whole row may show no next row at all, so there is nothing to fade; a cut row and the gap before it
 * (20 dp or more, as wide as the fade) are what fades, never a whole row.
 */
internal class GridEdgeFades(
    private val grid: LazyGridState,
    private val glide: ListGlide,
    private val focused: () -> Int,
) : EdgeFades {
    override val canScroll: Boolean
        get() = grid.canScrollForward || grid.canScrollBackward

    override fun top(
        fadePx: Float,
        height: Float,
    ): Float {
        val shift = glide.offset
        val items = grid.layoutInfo.visibleItemsInfo
        if (items.none { it.offset.y + shift < -CUT_PX && it.offset.y + it.size.height + shift > CUT_PX }) return 0f
        val item = items.firstOrNull { it.index == focused() } ?: return fadePx
        return minOf(fadePx, (item.offset.y + shift).coerceAtLeast(0f))
    }

    override fun bottom(
        fadePx: Float,
        height: Float,
    ): Float {
        val shift = glide.offset
        val items = grid.layoutInfo.visibleItemsInfo
        if (items.none { it.offset.y + shift < height - CUT_PX && it.offset.y + it.size.height + shift > height + CUT_PX }) return 0f
        val item = items.firstOrNull { it.index == focused() } ?: return fadePx
        return minOf(fadePx, (height - (item.offset.y + item.size.height + shift)).coerceAtLeast(0f))
    }

    private companion object {
        /** A row this few px past an edge is not cut: rows stand on whole px, dp rounding can leave one over. */
        const val CUT_PX = 1f
    }
}
