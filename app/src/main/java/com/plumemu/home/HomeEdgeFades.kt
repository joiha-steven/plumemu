package com.plumemu.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.plumemu.coreui.components.EDGE_CLEAR_SHARE
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
    /** The bottom fade reaches at least this far up (px): a games list fades its whole last row, as the board draws it. */
    private val bottomReachPx: Float = 0f,
    /** A column of whole rows (the systems): the part of a row cut by an edge is hidden outright, not faded. */
    private val wholeRowsOnly: Boolean = false,
    /** The empty space an item carries above its row (the systems' group gap): a row below it is whole even when the item crosses the edge. */
    private val gapAbove: (index: Int) -> Float = { 0f },
    /** See [EdgeFades.clearShare]: a games list passes 0 so its last row fades whole, never cut at half its height. */
    override val clearShare: Float = EDGE_CLEAR_SHARE,
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
    ): Float = if (list.canScrollForward) minOf(maxOf(fadePx, bottomReachPx), roomBelowFocus(height)) else 0f

    // The grown layout composes rows past the window (the glide's room), so the cut row is the one that crosses the edge.
    // A list never goes blank: see [cutRowsToHide].
    override fun clearTop(height: Float): Float {
        if (!wholeRowsOnly) return 0f
        val items = list.layoutInfo.visibleItemsInfo
        val at = hiddenTop(cutRowsToHide(items.size, height, list.canScrollBackward, list.canScrollForward, { rowTop(items[it]) }, { rowEnd(items[it]) }, { items[it].index == focused() }))
        return if (at < 0) 0f else rowEnd(items[at])
    }

    override fun clearBottom(height: Float): Float {
        if (!wholeRowsOnly) return 0f
        val items = list.layoutInfo.visibleItemsInfo
        val at = hiddenBottom(cutRowsToHide(items.size, height, list.canScrollBackward, list.canScrollForward, { rowTop(items[it]) }, { rowEnd(items[it]) }, { items[it].index == focused() }))
        return if (at < 0) 0f else height - rowTop(items[at])
    }

    private fun rowTop(item: androidx.compose.foundation.lazy.LazyListItemInfo) = item.offset + gapAbove(item.index) + glide.offset

    private fun rowEnd(item: androidx.compose.foundation.lazy.LazyListItemInfo) = item.offset + item.size + glide.offset

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
    /**
     * The share of a cut row that must show for it to stay (faded); a row less than this is left out whole, so no sliver of icon tops or
     * names stands at the edge (the Apps grid: small tiles with a name under them). 0 keeps every cut row.
     */
    private val minShown: Float = 0f,
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
        if (items.none { it.offset.y + shift < -CUT_PX && it.offset.y + it.size.height + shift > CUT_PX && !leftOutAtTop(it) }) return 0f
        val item = items.firstOrNull { it.index == focused() } ?: return fadePx
        return minOf(fadePx, (item.offset.y + shift).coerceAtLeast(0f))
    }

    override fun bottom(
        fadePx: Float,
        height: Float,
    ): Float {
        val shift = glide.offset
        val items = grid.layoutInfo.visibleItemsInfo
        if (items.none { it.offset.y + shift < height - CUT_PX && it.offset.y + it.size.height + shift > height + CUT_PX && !leftOutAtBottom(it, height) }) return 0f
        val item = items.firstOrNull { it.index == focused() } ?: return fadePx
        return minOf(fadePx, (height - (item.offset.y + item.size.height + shift)).coerceAtLeast(0f))
    }

    override fun clearTop(height: Float): Float {
        val shift = glide.offset
        return grid.layoutInfo.visibleItemsInfo.filter { leftOutAtTop(it) }.maxOfOrNull { it.offset.y + it.size.height + shift } ?: 0f
    }

    override fun clearBottom(height: Float): Float {
        val shift = glide.offset
        return grid.layoutInfo.visibleItemsInfo.filter { leftOutAtBottom(it, height) }.maxOfOrNull { height - (it.offset.y + shift) } ?: 0f
    }

    /** Whether [item] is cut by the top edge and less than [minShown] of it shows (the focused one is never left out). */
    private fun leftOutAtTop(item: LazyGridItemInfo): Boolean {
        if (minShown <= 0f || item.index == focused()) return false
        val end = item.offset.y + item.size.height + glide.offset
        return item.offset.y + glide.offset < -CUT_PX && end > CUT_PX && end < minShown * item.size.height
    }

    /** As [leftOutAtTop], at the bottom edge of a grid [height] px tall. */
    private fun leftOutAtBottom(
        item: LazyGridItemInfo,
        height: Float,
    ): Boolean {
        if (minShown <= 0f || item.index == focused()) return false
        val top = item.offset.y + glide.offset
        return top < height - CUT_PX && top + item.size.height > height + CUT_PX && height - top < minShown * item.size.height
    }

    private companion object {
        /** A row this few px past an edge is not cut: rows stand on whole px, dp rounding can leave one over. */
        const val CUT_PX = 1f
    }
}

/**
 * Which rows a whole-rows list hides: the position of the row cut by the top edge and of the one cut by the bottom edge
 * (packed; read with [hiddenTop] / [hiddenBottom], -1 for none). A row cut at both edges, the focused row and a row that is
 * whole are never hidden. A list never goes blank: when every row in the window would be hidden, the cut row with the most
 * on screen is kept. [top] and [end] are a row's edges from the window's top; only edges the list can scroll past cut.
 */
internal inline fun cutRowsToHide(
    count: Int,
    height: Float,
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    top: (Int) -> Float,
    end: (Int) -> Float,
    isFocused: (Int) -> Boolean,
): Long {
    var topCut = -1
    var bottomCut = -1
    var keeps = false
    for (i in 0 until count) {
        val t = top(i)
        val e = end(i)
        if (e <= 0f || t >= height) continue
        val cutAtTop = canScrollBackward && t < 0f
        val cutAtBottom = canScrollForward && e > height
        if ((cutAtTop && cutAtBottom) || isFocused(i) || (!cutAtTop && !cutAtBottom)) {
            keeps = true
        } else if (cutAtTop) {
            if (topCut < 0) topCut = i
        } else if (bottomCut < 0) {
            bottomCut = i
        }
    }
    if (!keeps && topCut >= 0 && bottomCut >= 0) {
        if (end(topCut) >= height - top(bottomCut)) topCut = -1 else bottomCut = -1
    } else if (!keeps) {
        // A single cut row and nothing else in the window: it stays.
        topCut = -1
        bottomCut = -1
    }
    return ((bottomCut + 1).toLong() shl 32) or (topCut + 1).toLong()
}

internal fun hiddenTop(packed: Long): Int = packed.toInt() - 1

internal fun hiddenBottom(packed: Long): Int = (packed shr 32).toInt() - 1

/**
 * The games list's fades: the bottom fade reaches a whole row and its clear share is 0, so the last row fades whole and is never cut at half its
 * height (`GamesPageFadesTest`).
 */
internal fun gamesListFades(
    list: LazyListState,
    glide: ListGlide,
    rowStridePx: Float,
    focused: () -> Int,
): ListEdgeFades = ListEdgeFades(list, glide, bottomReachPx = rowStridePx, clearShare = 0f, focused = focused)
