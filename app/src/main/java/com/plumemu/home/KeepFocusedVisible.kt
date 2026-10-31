package com.plumemu.home

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumTheme

/**
 * Rows kept in view past the focused one while a [ListGlide] is on: a 200 ms glide draws the rows a
 * stride or more from their place, and a held key piles up a lag of about a row while its repeats quicken, so the list
 * scrolls early, keeping three rows past the focus (HeldScrollFramesTest: with two, a hold's first repeats cut a fifth of
 * a row). The settings columns keep the same ([GLIDE_CONTEXT_ROWS]).
 */
internal const val GLIDE_CONTEXT_ROWS = 3

/** Rows kept in view past the focused one towards each edge while the list can scroll that way (the focus never rides the edge): [GLIDE_CONTEXT_ROWS] with a glide, one row without (animations off). */
internal fun contextRowsFor(glide: ListGlide?): Int = if (glide != null && glide.extra > 0.dp) GLIDE_CONTEXT_ROWS else 1

/**
 * Keeps the focused item of a lazy column on screen (docs/design/rules.md › Focus and current):
 * when it comes within [contextRowsFor] rows of an edge the list scrolls just enough to keep that
 * many rows past it, in the same frame as the focus moves. Until the first move the list keeps no
 * context ([ScrollKeeper.contextFor]), so a list opens where it always did.
 * The scroll is requested while composing that frame (`requestScrollToItem`, no coroutine), so the
 * list is laid out at its new place before the frame is drawn; a scroll that waited for the next
 * frame showed the focus past the edge for one frame. With a [glide] the rows are then drawn where
 * they were and glide to their place, never further than the room the scroll leaves around the
 * focus ([KeptScroll]), also after a move that needed no scroll.
 */
@Composable
internal fun KeepFocusedVisible(
    listState: LazyListState,
    glide: ListGlide? = null,
    /** What the rows' heights depend on besides their text (a line that comes and goes above the focus): when it changes, the focus is brought into view again. */
    contentKey: Any? = null,
    focusedIndex: () -> Int,
) {
    val latest = rememberUpdatedState(focusedIndex)
    // Only this small scope recomposes when the focused index changes.
    val index by remember(listState) { derivedStateOf { latest.value() } }
    // A new keeper when the text size or font changes (the size follows the window width, so it can change under an open list): the focused row is brought into view again.
    val textScale = PlumTheme.layout.textScale
    // Also the font and the text mode: they change the rows' heights as the size does.
    val type = PlumTheme.type
    val openedWith = remember(listState) { type }
    val openedKey = remember(listState) { contentKey }
    val keeper = remember(listState, textScale, type, contentKey) { ScrollKeeper(isAfterResize = type !== openedWith || contentKey != openedKey) }
    val context = contextRowsFor(glide)
    val target = index
    var kept: KeptScroll? = null
    // Read without observing: a scroll must not recompose this.
    val moved =
        Snapshot.withoutReadObservation {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val rows = keeper.contextFor(target, context)
            val wanted = (target - rows).coerceAtLeast(0)..(target + rows).coerceAtMost(info.totalItemsCount - 1)
            val isLaidOut = items.any { it.index == wanted.first } && items.any { it.index == wanted.last }
            keeper.update(target, hasLayout = items.isNotEmpty(), isLaidOut = isLaidOut) {
                listState.keepVisible(target, rows).also { kept = it }.moved
            }
        }
    // Also when nothing scrolled: a glide still running from the last jump must leave the row past
    // the new focus whole.
    kept?.let { glide?.jumped(moved.toFloat(), it.roomAbove, it.roomBelow) }
    // Waiting for a layout (the list's first, or the one that places an estimated row): observe
    // it, so this runs again once it exists.
    if (keeper.isWaiting) listState.layoutInfo
}

/**
 * What a scroll keeper has done, written while composing and never observed: the index it last
 * brought into view, whether the focus has moved since the list opened, and whether it waits for a
 * layout to check again. It waits before the list's first layout (a list rebuilt with the focus far
 * down, e.g. after search closes) and once after a row that was not laid out was placed by estimate
 * (rows of different heights).
 */
internal class ScrollKeeper(
    /**
     * Made because the text size changed: the rows are laid out at their new size only after this
     * composition, so the first scroll is checked twice more against that layout.
     */
    isAfterResize: Boolean = false,
) {
    private var rechecks = if (isAfterResize) 2 else 0
    private var handled = NONE
    private var estimates = 0
    private var hasMoved = false

    var isWaiting = false
        private set

    /**
     * The rows of context to keep around [target]: none while the list only shows where it
     * opened (its first scroll and the check after it), so it opens as it always did, the board's
     * remembered row with the row after it cut; [context] from the first move on.
     */
    fun contextFor(
        target: Int,
        context: Int,
    ): Int = if (!hasMoved && (handled == NONE || target == handled)) 0 else context

    /**
     * Brings [target] into view with [scroll] when it is new or awaited; returns how far the rows
     * moved, to glide (0 for the scroll that opens the list: nothing was on screen to glide from).
     */
    fun update(
        target: Int,
        hasLayout: Boolean,
        isLaidOut: Boolean,
        scroll: () -> Int,
    ): Int {
        if (!hasLayout) {
            isWaiting = true
            return 0
        }
        val isNew = target != handled
        if (!isNew && !isWaiting) return 0
        val opens = handled == NONE
        if (isNew) {
            estimates = 0
            if (!opens) hasMoved = true
        }
        handled = target
        val moved = scroll()
        val isRechecking = rechecks > 0
        if (isRechecking) rechecks--
        isWaiting = isRechecking || (!isLaidOut && estimates++ < MAX_ESTIMATES)
        return if (opens) 0 else moved
    }

    private companion object {
        const val NONE = Int.MIN_VALUE

        /** One check after an estimate; a list that cannot place the row never loops. */
        const val MAX_ESTIMATES = 1
    }
}

/**
 * A scroll [LazyListState.keepVisible] requested: how far the rows move in px (positive: down; 0:
 * no scroll) and how far a glide may draw them above or below their new place before the row
 * above or below the focus (the focused row itself at the list's ends) would cross that edge.
 */
internal data class KeptScroll(
    val moved: Int,
    val roomAbove: Float,
    val roomBelow: Float,
)

/**
 * Requests the scroll that shows item [index] whole with up to [context] items on each side of it
 * (as many as the list has there, and as fit: the item itself first; one taller than the window
 * shows its top), for the next layout (this
 * frame's when called while composing). Before the first layout it does nothing ([ScrollKeeper]
 * waits for it). An item that is not laid out (a jump of many rows, a wrap to the other end, a step
 * past a taller heading) is placed from the rows' average stride, and [ScrollKeeper] checks it again
 * against the next layout.
 */
internal fun LazyListState.keepVisible(
    index: Int,
    context: Int = 0,
): KeptScroll {
    val info = layoutInfo
    val count = info.totalItemsCount
    if (info.visibleItemsInfo.isEmpty() || index !in 0 until count) return KeptScroll(0, Float.MAX_VALUE, Float.MAX_VALUE)
    val rows = LaidOutRows(info)
    val windowEnd = info.viewportEndOffset - info.afterContentPadding
    var before = minOf(context, index)
    var after = minOf(context, count - 1 - index)
    // Less context where the item and its context do not fit (tall rows, a short list window).
    while (before + after > 0 && rows.bottomOf(index + after) - rows.topOf(index - before) > windowEnd) {
        if (before >= after) before-- else after--
    }
    val top = rows.topOf(index - before)
    val bottom = rows.bottomOf(index + after)
    val moved =
        when {
            top < 0 -> {
                requestScrollToItem(index - before)
                -top
            }
            // Never past the item's own top: an item taller than the window (a typing panel's
            // list above the keyboard tray at the largest sizes) shows its first line, not its last.
            bottom > windowEnd && rows.topOf(index) > 0 -> {
                val by = minOf(bottom - windowEnd, rows.topOf(index))
                requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset + by)
                -by
            }
            else -> 0
        }
    // Where the rows around the focus land after the scroll: the glide may not draw them past an edge.
    val roomAbove = rows.topOf((index - 1).coerceAtLeast(0)) + moved
    val roomBelow = windowEnd - (rows.bottomOf((index + 1).coerceAtMost(count - 1)) + moved)
    return KeptScroll(moved, roomAbove.coerceAtLeast(0).toFloat(), roomBelow.coerceAtLeast(0).toFloat())
}

/** Rows' places in a lazy list's last layout; a row not laid out is placed from the average stride. */
private class LaidOutRows(
    info: LazyListLayoutInfo,
) {
    private val items = info.visibleItemsInfo
    private val first = items.first()
    private val last = items.last()
    private val stride = if (last.index > first.index) (last.offset - first.offset) / (last.index - first.index) else first.size + info.mainAxisItemSpacing

    fun topOf(i: Int): Int {
        items.firstOrNull { it.index == i }?.let { return it.offset }
        val anchor = if (i < first.index) first else last
        return anchor.offset + (i - anchor.index) * stride
    }

    fun bottomOf(i: Int): Int {
        items.firstOrNull { it.index == i }?.let { return it.offset + it.size }
        val anchor = if (i < first.index) first else last
        return anchor.offset + (i - anchor.index) * stride + anchor.size
    }
}
