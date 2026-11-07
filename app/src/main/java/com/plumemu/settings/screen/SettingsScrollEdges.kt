package com.plumemu.settings.screen

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.ListEdgeFade
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.setup.BringStop
import com.plumemu.setup.BringStops
import com.plumemu.setup.LocalBringRoom
import com.plumemu.setup.LocalBringStops
import com.plumemu.setup.bringIntoViewWhen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/*
 * How Settings' scrolling lists and panels meet their edges: the one fade of design/rules.md › Lists that scroll, with what Settings adds. Where content continues past an edge a
 * part of it shows under the band and the focused row stays clear of the band with its shadow; where it does not, no fade, and the last row keeps [SlabDimens.listEndRoom].
 */

/** What shows of the next row past an edge a list continues over: the band, past the slab's overhang so it never touches the focused slab, and as much clear of it, so a cut text reads as text, not specks. */
internal val EdgePeek: Dp get() = PlumDimens.listFadeBand * 2 + SlabDimens.maxOverhang

/** What a [PanelScrollColumn] that nearly fits takes off its end room rather than scroll: a focused button's glow still ends inside the window, as buttons have no overhang. */
internal val PanelFootGive: Dp get() = SlabDimens.maxOverhang + PlumDimens.focusRingOutset

/** What a list inside [PanelListLayout] tells it: its tallest row seen while the panel has the focus, px, so the header can give it room. */
internal class PanelListRoom {
    var tallestRow by mutableIntStateOf(0)
}

internal val LocalPanelListRoom = compositionLocalOf<PanelListRoom?> { null }

/** Keeps [PanelListRoom.tallestRow] up to date, in its own scope. It only grows while [isEntered], so a header that left does not come back mid-list. */
@Composable
internal fun ReportTallestRow(
    state: LazyListState,
    isEntered: () -> Boolean,
) {
    val room = LocalPanelListRoom.current ?: return
    val entered by rememberUpdatedState(isEntered)
    LaunchedEffect(state, room) {
        snapshotFlow {
            if (!entered()) {
                0
            } else {
                var tallest = 0
                val items = state.layoutInfo.visibleItemsInfo
                for (i in items.indices) tallest = maxOf(tallest, items[i].size)
                tallest
            }
        }.collect { room.tallestRow = if (it == 0) 0 else maxOf(room.tallestRow, it) }
    }
}

/** The room a list needs for a row [row] px tall with the peeks of the rows around it, px. */
internal fun Density.roomForRow(row: Int): Int = if (row <= 0) 0 else row + 2 * (PlumDimens.listRowGap + EdgePeek).roundToPx()

/**
 * The text blocks and focus stops of a [PanelScrollColumn] (titles and bodies place themselves here, [panelBlock]; stops through [bringIntoViewWhen]). A key never leaves a block half
 * gone at the top: the scroll goes on the way it went, to the block's top or to where only its tail shows under the fade, keeping the focused stop on screen, so Up always moves.
 * On the first stop the text above it comes back as far as the stop stays on screen. A finger rests where it lifts. The column grows at its foot to reach a rest, and only for it.
 */
internal class PanelBlocks : BringStops {
    private val nodes = ArrayList<LayoutCoordinates>()
    private val stops = ArrayList<BringStop>()
    private var window: LayoutCoordinates? = null
    private val held = ArrayList<BringStop>()
    private var isByFinger = false
    private var lastFrom = NO_MOVE
    private var lastTo = NO_MOVE

    /** Where the last scroll came to rest: the way the next one went is measured from here. */
    private var restedAt = 0

    /** The stop that took the focus last; its change alone (a move that scrolls nothing) asks for a rest. */
    var focused by mutableStateOf<BringStop?>(null)
        private set

    /** The presses of a panel with no stops: with animations off a press's whole scroll passes inside one snapshot, unseen by [ScrollState.isScrollInProgress]. */
    var keyMoves by mutableIntStateOf(0)
        private set

    /** Room added under the content to reach a rest. */
    var extra by mutableIntStateOf(0)
        private set

    /** The window's height in this measure pass, px. */
    var windowPx = 0

    fun placeWindow(coordinates: LayoutCoordinates) {
        window = coordinates
    }

    fun place(block: LayoutCoordinates) {
        if (block !in nodes) nodes += block
    }

    override fun place(stop: BringStop) {
        if (stop !in stops) stops += stop
    }

    override fun focusedOn(stop: BringStop) {
        held += stop
        focused = stop
        keyTookOver()
    }

    override fun focusLeft(stop: BringStop) {
        held -= stop
        if (focused === stop) focused = held.lastOrNull()
    }

    /** A finger took the column: nothing settles until a key moves it again (a fling starts after the drag ends, so the end of a scroll does not say the finger is done). */
    fun fingerMoved() {
        isByFinger = true
    }

    fun keyMoved(moves: Int) {
        keyTookOver()
        keyMoves = moves
    }

    private fun keyTookOver() {
        isByFinger = false
        lastFrom = NO_MOVE
    }

    suspend fun settle(
        scroll: ScrollState,
        gapPx: Int,
        tailPx: Int,
        roomPx: Int,
        motion: PlumMotionState,
    ) {
        val box = window?.takeIf { it.isAttached } ?: return
        nodes.removeAll { !it.isAttached }
        stops.removeAll { it.coordinates?.isAttached != true }
        if (!isByFinger) {
            val now = scroll.value
            val target = restFor(box, now, gapPx, tailPx, roomPx)
            if (target > scroll.maxValue) {
                extra += target - scroll.maxValue
                withFrameNanos {}
            }
            val rest = target.coerceAtMost(scroll.maxValue)
            // The same move twice with no key between: something else holds the column there (a bring into view still under way), and two rests would chase each other for ever.
            if (rest != now && (now != lastFrom || rest != lastTo)) {
                lastFrom = now
                lastTo = rest
                if (!moveTo(rest, scroll, motion)) return
            }
        }
        restedAt = scroll.value
        val needed = (scroll.value - (scroll.maxValue - extra)).coerceAtLeast(0)
        if (needed < extra) extra = needed
    }

    /** On the first stop, as much of the text above it as the stop leaves room for: from [shown], a block cut there goes only if text stays between it and the stop. */
    private fun restOnFirstStop(
        box: LayoutCoordinates,
        shown: Int,
        now: Int,
        stopTop: Int,
        gapPx: Int,
        tailPx: Int,
    ): Int {
        for (i in nodes.indices) {
            val top = box.localPositionOf(nodes[i], Offset.Zero).y.toInt() + now - shown
            val end = top + nodes[i].size.height
            if (top >= 0 || end + gapPx <= tailPx) continue
            val textStays = nodes.any { other -> box.localPositionOf(other, Offset.Zero).y.toInt() + now - shown >= end && box.localPositionOf(other, Offset.Zero).y.toInt() + other.size.height <= stopTop }
            return if (textStays) shown + end + gapPx - tailPx else shown
        }
        return shown
    }

    /** False when another scroll took over: that one's end settles again, this collector must live on. */
    private suspend fun moveTo(
        rest: Int,
        scroll: ScrollState,
        motion: PlumMotionState,
    ): Boolean {
        try {
            if (motion.isOn) scroll.animateScrollTo(rest, motion.glideSpec()) else scroll.scrollTo(rest)
        } catch (e: CancellationException) {
            currentCoroutineContext().ensureActive()
            return false
        }
        return true
    }

    private fun restFor(
        box: LayoutCoordinates,
        now: Int,
        gapPx: Int,
        tailPx: Int,
        roomPx: Int,
    ): Int {
        val height = box.size.height
        // Two stops can hold the focus at once (a tile and a row of one panel): the lower one rules, a rest above it would lose it.
        val lowest = held.filter { it.coordinates?.isAttached == true }.maxByOrNull { stop -> stop.coordinates?.let { box.localPositionOf(it, Offset.Zero).y } ?: 0f }
        val stop = lowest?.coordinates
        val stopTop = stop?.let { box.localPositionOf(it, Offset.Zero).y.toInt() } ?: 0
        val stopEnd = stopTop + (stop?.size?.height ?: 0) + (lowest?.belowPx ?: 0)
        if (stop != null && stops.none { other -> other.coordinates?.let { box.localPositionOf(it, Offset.Zero).y.toInt() < stopTop } == true }) {
            // The only stop, whole with the column at its top: nothing past it needs room, so no text above it is cut for its sake.
            if (stops.size == 1 && now + stopEnd <= height) return 0
            return restOnFirstStop(box, minOf(now, (now + stopEnd + roomPx - height).coerceAtLeast(0)), now, stopTop, gapPx, tailPx)
        }
        val keepsStop = { rest: Int -> stop == null || stopEnd - (rest - now) + roomPx <= height && stopTop - (rest - now) >= 0 }
        for (i in nodes.indices) {
            val node = nodes[i]
            val top = box.localPositionOf(node, Offset.Zero).y.toInt()
            val end = top + node.size.height
            val ahead = now + end + gapPx - tailPx
            if (top >= 0 || end <= 0 || ahead <= now || end - top > height) continue
            val back = (now + top - tailPx).coerceAtLeast(0)
            val canGoAhead = keepsStop(ahead)
            val canGoBack = keepsStop(back)
            return when {
                now > restedAt && canGoAhead -> ahead
                now < restedAt && canGoBack -> back
                canGoAhead && canGoBack -> if (ahead - now <= now - back) ahead else back
                canGoAhead -> ahead
                canGoBack -> back
                else -> now
            }
        }
        return now
    }

    private companion object {
        const val NO_MOVE = -1
    }
}

internal val LocalPanelBlocks = compositionLocalOf<PanelBlocks?> { null }

/** On a text block of a panel: inside a [PanelScrollColumn] it is never left half gone. */
@Composable
internal fun Modifier.panelBlock(): Modifier {
    val blocks = LocalPanelBlocks.current ?: return this
    return onPlaced { blocks.place(it) }
}

/**
 * A panel's column that scrolls when its text is large: everything a plain panel needs at its edges in one place. It ends [SlabDimens.listEndRoom] above its box so the last
 * item's shadow is whole, keeps a focused item clear of the fades ([LocalBringRoom]), fades where it continues and never rests with a text block half gone ([PanelBlocks]).
 * [window] goes around the scroll, [inside] within it; [tag] names the fades as `<tag>_fade_bottom`, `<tag>_fade_top`. A panel that wraps its height (one measured beside the look
 * preview) passes [fillsHeight] false; a form above the keyboard tray keeps every px and passes no [bringRoom] or [endRoom], as a panel with nothing focusable no [endRoom].
 * [tail] is how much of a block a rest may leave under the top fade, [fades] false for a panel that draws its own. A panel that scrolls by key with no focus stop counts its presses
 * in [keyMoves]. Content over the window by no more than [PanelFootGive] does not scroll: the end room gives that much up, so no block is cut for a few px.
 */
@Composable
internal fun PanelScrollColumn(
    gap: Dp,
    modifier: Modifier = Modifier,
    window: Modifier = Modifier,
    inside: Modifier = Modifier,
    scroll: ScrollState = rememberScrollState(),
    tag: String? = null,
    fillsHeight: Boolean = true,
    bringRoom: Dp = EdgePeek,
    endRoom: Dp = SlabDimens.listEndRoom,
    tail: Dp = EdgePeek,
    fades: Boolean = true,
    keyMoves: Int = 0,
    content: @Composable ColumnScope.() -> Unit,
) {
    val blocks = remember { PanelBlocks() }
    val motion = PlumTheme.motion
    val gapPx = with(LocalDensity.current) { gap.roundToPx() }
    val tailPx = with(LocalDensity.current) { tail.roundToPx() }
    val roomPx = with(LocalDensity.current) { bringRoom.roundToPx() }
    LaunchedEffect(scroll, blocks, gapPx, tailPx, roomPx) {
        snapshotFlow { Triple(scroll.isScrollInProgress, blocks.focused, blocks.keyMoves) }.collect { (isScrolling) ->
            if (!isScrolling) {
                // A new focus or press asks for its scroll in this frame: that scroll's end comes here again.
                withFrameNanos {}
                if (!scroll.isScrollInProgress) blocks.settle(scroll, gapPx, tailPx, roomPx, motion)
            }
        }
    }
    LaunchedEffect(scroll, blocks) { scroll.interactionSource.interactions.collect { if (it is DragInteraction.Start) blocks.fingerMoved() } }
    LaunchedEffect(blocks, keyMoves) { blocks.keyMoved(keyMoves) }
    val height = if (fillsHeight) Modifier.fillMaxHeight() else Modifier
    val footGivePx = with(LocalDensity.current) { minOf(endRoom, PanelFootGive).roundToPx() }
    Box(modifier.then(height).onPlaced { blocks.placeWindow(it) }) {
        Column(
            height
                .then(window)
                .layout { measurable, constraints ->
                    blocks.windowPx = constraints.maxHeight
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.verticalScroll(scroll)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val over = placeable.height - blocks.windowPx
                    val give = if (blocks.extra == 0 && over in 1..footGivePx) over else 0
                    layout(placeable.width, placeable.height + blocks.extra - give) { placeable.place(0, 0) }
                }.padding(bottom = endRoom)
                .then(inside)
                .then(if (tag == null) Modifier else Modifier.testTag(tag)),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            CompositionLocalProvider(LocalPanelBlocks provides blocks, LocalBringStops provides blocks, LocalBringRoom provides bringRoom) { content() }
        }
        if (fades) ScrollEdgeFades(scroll, tag)
    }
}

/** The list-edge fades over a plain scroll filling this box, each only while the content continues past that edge. Its own scope: reaching an end redraws the fades alone. */
@Composable
internal fun BoxScope.ScrollEdgeFades(
    scroll: ScrollState,
    tag: String?,
) {
    if (scroll.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).then(if (tag == null) Modifier else Modifier.testTag(tag + FADE_BOTTOM_SUFFIX)))
    if (scroll.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).then(if (tag == null) Modifier else Modifier.testTag(tag + FADE_TOP_SUFFIX)))
}
