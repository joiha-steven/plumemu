package com.plumemu.home

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
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
internal fun contextRowsFor(glide: ListGlide?): Int = if (glide != null && glide.motionOn) GLIDE_CONTEXT_ROWS else 1

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
    /** The gap each item carries after itself (a games row is its pitch tall): the last row needs the room without it, as `PlumLayout.listRowsThatFit` counts. */
    trailingGap: Dp = 0.dp,
    /** The list draws a gliding pill: a move that scrolls nothing still counts as a step of a held key ([ListGlide.stepped]). */
    countsSteps: Boolean = false,
    /** Settings' panels: px of the next row shown past an edge the list continues over (see [restWithPeeks]); 0 for none. */
    peekPx: Int = 0,
    /** A list of whole rows (the game menu, Search): a scroll ends with a row's top at the window's top, never with a cut row there (see [keepVisible]). */
    snapsToRows: Boolean = false,
    /** Keep the context rows from the first scroll on, however the focus got there (the game menu's slab keeps its room below it when the menu opens on a row). */
    keepsContextAtOpen: Boolean = false,
    /** How far below the window's top a list that continues above rests its first whole row, the row before it going under the bar ([keepVisible] `band`). */
    bandPx: Int = 0,
    /** The items a scroll may rest on at the band's bottom (a heading is not one); every item by default. */
    isRestRow: (Int) -> Boolean = { true },
    /** Settings' panels: focused on this row the list rests at its very top when the row is whole there: what stands above it (a heading no cursor stops on) is never left under the fade. */
    restsAtTopOn: Int = -1,
    /** Settings' panels: the px of room the list keeps under its last row, written here to reach a rest past its end ([restWithPeeks]). */
    foot: MutableIntState? = null,
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
    val keeper = remember(listState, textScale, type, contentKey) { ScrollKeeper(isAfterResize = type !== openedWith || contentKey != openedKey, keepsContextAtOpen = keepsContextAtOpen) }
    val context = contextRowsFor(glide)
    val gapPx = with(LocalDensity.current) { trailingGap.roundToPx() }
    val target = index
    var kept: KeptScroll? = null
    // Read without observing: a scroll must not recompose this.
    val moved =
        Snapshot.withoutReadObservation {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            val rows = keeper.contextFor(target, context)
            // The row before the first context row too: a rest at the band scrolls past it.
            val wanted = (target - rows - 1).coerceAtLeast(0)..(target + rows).coerceAtMost(info.totalItemsCount - 1)
            val isLaidOut = items.any { it.index == wanted.first } && items.any { it.index == wanted.last }
            keeper.update(target, hasLayout = items.isNotEmpty(), isLaidOut = isLaidOut) {
                listState.keepVisible(target, rows, gapPx, snapsToRows, bandPx, isRestRow, peekPx, restsAtTopOn, foot).also { kept = it }.moved
            }
        }
    // Also when nothing scrolled: a glide still running from the last jump must leave the row past
    // the new focus whole.
    kept?.let {
        glide?.jumped(moved.toFloat(), it.roomAbove, it.roomBelow)
        if (countsSteps && moved == 0) glide?.stepped()
    }
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
    private val keepsContextAtOpen: Boolean = false,
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
    ): Int =
        if (!hasMoved && (handled == NONE || target == handled)) {
            // A list that keeps its room from the start keeps one row, not the glide's three: it opens where it always did unless that row is cut.
            if (keepsContextAtOpen) minOf(context, 1) else 0
        } else {
            context
        }

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
    trailingGap: Int = 0,
    /**
     * Scroll to a row's top rather than by the pixels needed, and back to the top of a first row that is cut, when a row ends up whole at the window's top and the item
     * and its context stay whole: a list of whole rows hides a row cut at its top, which left a blank band there. Not when the
     * list ends first (the scroll is then clamped and the band stays; nothing can be done).
     */
    snapsToRows: Boolean = false,
    /**
     * The first whole row's place below the window's top (px; 0 for none): with [snapsToRows] a list that continues above rests with a [isRestRow] row's top there (the row before it
     * lies above, going under the bar). The first row alone rests at the window's top: nothing continues above it.
     */
    band: Int = 0,
    isRestRow: (Int) -> Boolean = { true },
    /** Above 0: the list rests with this many px of the next row past each edge it continues over ([restWithPeeks]). */
    peek: Int = 0,
    /** See [KeepFocusedVisible]. */
    restsAtTopOn: Int = -1,
    /** See [KeepFocusedVisible]. */
    foot: MutableIntState? = null,
): KeptScroll {
    val info = layoutInfo
    val count = info.totalItemsCount
    if (info.visibleItemsInfo.isEmpty() || index !in 0 until count) return KeptScroll(0, Float.MAX_VALUE, Float.MAX_VALUE)
    val rows = LaidOutRows(info)
    // A list with room at its foot pads its top and foot alike but for that room, which is no part of what ends the window.
    val windowEnd = info.viewportEndOffset - (if (foot != null) info.beforeContentPadding else info.afterContentPadding) + trailingGap
    if (index == restsAtTopOn && rows.bottomOf(index) - rows.topOf(0) <= windowEnd) return restAtTop(index, count, windowEnd, rows, foot)
    var before = minOf(context, index)
    var after = minOf(context, count - 1 - index)
    // Less context where the item and its context do not fit (tall rows, a short list window).
    while (before + after > 0 && rows.bottomOf(index + after) - rows.topOf(index - before) > windowEnd) {
        if (before >= after) before-- else after--
    }
    if (peek > 0) return restShowingPeeks(index, before, after, windowEnd, peek, rows, foot)
    // The window a rest leaves: past the band when rows continue above it.
    val bands = snapsToRows && band > 0
    while (before + after > 0 && rows.bottomOf(index + after) - rows.topOf(index - before) > windowEnd - (if (bands && index - before > 0) band else 0)) {
        if (before >= after) before-- else after--
    }
    // With a band the first context row is one a scroll can rest on (the first item rests at the window's top whatever it is).
    while (bands && before > 0 && index - before > 0 && !isRestRow(index - before)) before--
    // The band only where the item and its context stay whole past it: a window too low for that keeps the focused row whole at the top.
    val bandNow = if (bands && index - before > 0 && rows.bottomOf(index + after) - rows.topOf(index - before) <= windowEnd - band) band else 0
    val top = rows.topOf(index - before)
    val bottom = rows.bottomOf(index + after)
    val moved =
        when {
            top < bandNow -> {
                val at = index - before
                if (bandNow > 0 && restOn(at, rows, band)) {
                    band - top
                } else {
                    requestScrollToItem(at)
                    -top
                }
            }
            // Never past the item's own top: an item taller than the window (a typing panel's
            // list above the keyboard tray at the largest sizes) shows its first line, not its last.
            bottom > windowEnd && rows.topOf(index) > 0 -> {
                val needed = minOf(bottom - windowEnd, rows.topOf(index))
                // The first scroll off the list's start moves a whole stride or more: a shorter one would end before a held key's next step, and the rows would stall.
                val fromStart = if (bandNow > 0 && firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0) rows.topOf(1) - rows.topOf(0) else 0
                val snapped = if (snapsToRows) rows.firstTopAtLeast(maxOf(bottom - windowEnd, fromStart) + bandNow, rows.topOf(index - before), if (bandNow > 0) isRestRow else { _ -> true }) else null
                if (snapped != null && bandNow > 0 && restOn(snapped.first, rows, band)) {
                    band - snapped.second
                } else if (snapped != null && bandNow == 0) {
                    requestScrollToItem(snapped.first, 0)
                    -snapped.second
                } else {
                    requestScrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset + needed)
                    -needed
                }
            }
            // A list of whole rows opened or clamped with its first row cut at the top (a short list that fits but for one row): that row is hidden, a
            // blank band. Back to its top when the item and its context stay whole; the room left then is at the bottom, as the games list ends.
            snapsToRows && firstVisibleItemScrollOffset > 0 && (!bands || firstVisibleItemIndex == 0) && bottom + firstVisibleItemScrollOffset <= windowEnd -> {
                val back = firstVisibleItemScrollOffset
                requestScrollToItem(firstVisibleItemIndex, 0)
                back
            }
            // A scrolled list whose first shown row stands lower than the band (a rest the layout could not give before: the room below the last row came after): up to the band
            // while the context stays whole and the list can still scroll that far.
            bandNow > 0 && (firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0) && hole(rows, isRestRow, band, top, windowEnd) != null -> {
                val row = hole(rows, isRestRow, band, top, windowEnd)!!
                if (restOn(row.first, rows, band)) band - row.second else 0
            }
            else -> 0
        }
    // Where the rows around the focus land after the scroll: the glide may not draw them past an edge.
    val roomAbove = rows.topOf((index - 1).coerceAtLeast(0)) + moved
    val roomBelow = windowEnd - (rows.bottomOf((index + 1).coerceAtMost(count - 1)) + moved)
    return KeptScroll(moved, roomAbove.coerceAtLeast(0).toFloat(), roomBelow.coerceAtLeast(0).toFloat())
}

/** The first rest row standing lower than [band] (its index and top) that a scroll to the band keeps [contextTop] whole for and that the list can still scroll to, or null. */
private fun LazyListState.hole(
    rows: LaidOutRows,
    isRestRow: (Int) -> Boolean,
    band: Int,
    contextTop: Int,
    windowEnd: Int,
): Pair<Int, Int>? {
    val info = layoutInfo
    val row = rows.firstTopAtLeast(band, contextTop, isRestRow) ?: return null
    if (row.second <= band || row.first == 0) return null
    val last = info.visibleItemsInfo.lastOrNull() ?: return null
    // How far the list can still scroll: what lies below the window, room included.
    val remaining = if (last.index == info.totalItemsCount - 1) last.offset + last.size - (info.viewportEndOffset - info.afterContentPadding) else Int.MAX_VALUE
    return row.takeIf { it.second - band <= remaining }
}

/**
 * Requests the scroll that puts row [at]'s top at [band] px (the row before it, [at] - 1, stands first in the list's window, scrolled up past it); false when that row's
 * stride is shorter than the band (nothing is requested).
 */
private fun LazyListState.restOn(
    at: Int,
    rows: LaidOutRows,
    band: Int,
): Boolean {
    val past = rows.topOf(at) - rows.topOf(at - 1) - band
    if (past < 0) return false
    requestScrollToItem(at - 1, past)
    return true
}

/** [keepVisible] for a list resting at its very top on row [index] ([KeepFocusedVisible] `restsAtTopOn`). */
private fun LazyListState.restAtTop(
    index: Int,
    count: Int,
    windowEnd: Int,
    rows: LaidOutRows,
    foot: MutableIntState?,
): KeptScroll {
    val moved = -rows.topOf(0)
    foot?.intValue = 0
    if (moved != 0) requestScrollToItem(0)
    return KeptScroll(moved, rows.topOf(0).plus(moved).toFloat(), (windowEnd - (rows.bottomOf((index + 1).coerceAtMost(count - 1)) + moved)).coerceAtLeast(0).toFloat())
}

/** [keepVisible] for a list that rests showing the next rows ([restWithPeeks]): context gives way to the peeks, down to one row on each side. */
private fun LazyListState.restShowingPeeks(
    index: Int,
    contextBefore: Int,
    contextAfter: Int,
    windowEnd: Int,
    peek: Int,
    rows: LaidOutRows,
    foot: MutableIntState?,
): KeptScroll {
    val info = layoutInfo
    val items = info.visibleItemsInfo
    val first = items.first()
    val last = items.last()
    val count = info.totalItemsCount
    val kept = { delta: Int ->
        val roomAbove = rows.topOf((index - 1).coerceAtLeast(0)) - delta
        val roomBelow = windowEnd - (rows.bottomOf((index + 1).coerceAtMost(count - 1)) - delta)
        KeptScroll(-delta, roomAbove.coerceAtLeast(0).toFloat(), roomBelow.coerceAtLeast(0).toFloat())
    }
    // Resting on the room at the foot with the row whole and none cut at the top: the row that went is no longer laid out, so nothing here could find this rest again.
    if ((foot?.intValue ?: 0) > 0 && rows.topOf(index) >= 0 && rows.bottomOf(index) <= windowEnd && items.none { it.offset < 0 && it.offset + it.size > 0 }) return kept(0)
    val min = if (first.index == 0) first.offset else Int.MIN_VALUE / 2
    val max = if (last.index == count - 1) maxOf(last.offset + last.size - windowEnd, min) else Int.MAX_VALUE / 2
    var before = contextBefore
    var after = contextAfter
    var rest: Int?
    var wanted: Int
    while (true) {
        val top = rows.topOf(index - before)
        val bottom = rows.bottomOf(index + after)
        // Never past the item's own top, as [keepVisible].
        wanted = if (top < 0) top else if (bottom > windowEnd) minOf(bottom - windowEnd, rows.topOf(index)).coerceAtLeast(0) else 0
        rest = restWithPeeks(wanted.coerceIn(min, max), windowEnd, peek, min, max, top, bottom, items.size, { items[it].offset }, { items[it].offset + items[it].size }, pastMax = if (foot != null && last.index == count - 1) windowEnd else 0)
        if (rest != null || maxOf(before, after) <= 1) break
        if (before >= after) before-- else after--
    }
    val delta = rest ?: wanted.coerceIn(min, max)
    foot?.intValue = (delta - max).coerceAtLeast(0)
    if (delta != 0) {
        // Above every row laid out (a finger left the focus far up) no row can anchor the rest: the row goes first, as in [keepVisible], and the check after this layout finds its rest.
        val anchor = items.lastOrNull { it.offset <= delta }
        if (anchor == null) requestScrollToItem(index - before) else requestScrollToItem(anchor.index, delta - anchor.offset)
    }
    return kept(delta)
}

/** Rows' places in a lazy list's last layout; a row not laid out is placed from the average stride. */
private class LaidOutRows(
    info: LazyListLayoutInfo,
) {
    private val items = info.visibleItemsInfo
    private val first = items.first()
    private val last = items.last()
    private val stride = if (last.index > first.index) (last.offset - first.offset) / (last.index - first.index) else first.size + info.mainAxisItemSpacing

    /** The first laid-out row whose top is [from] or more and at most [upTo] (its index and top), or null. */
    fun firstTopAtLeast(
        from: Int,
        upTo: Int,
        accepts: (Int) -> Boolean = { true },
    ): Pair<Int, Int>? = items.firstOrNull { it.offset >= from && it.offset <= upTo && accepts(it.index) }?.let { it.index to it.offset }

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
