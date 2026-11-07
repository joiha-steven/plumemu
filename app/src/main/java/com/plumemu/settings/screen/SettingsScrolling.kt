package com.plumemu.settings.screen

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.plumemu.coreui.components.slabOverhang
import com.plumemu.home.BarRunClips
import com.plumemu.coreui.components.FocusPillShape
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.rememberListGlide
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plumemu.coreui.components.ListEdgeFade
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.home.GLIDE_CONTEXT_ROWS
import com.plumemu.home.KeepFocusedVisible
import com.plumemu.home.restWithPeeks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A column of rows that scrolls so row [focusedIndex] stays on screen with rows past it towards each edge (design/rules.md ›
 * Focus and current, Shapes and motion: the focus never rides the edge). Each row passes `place(index)` as a modifier so the
 * column knows where it is. A finger can scroll it too.
 *
 * It moves like home's lists: the scroll is decided while composing the focus change and applied before
 * that frame is laid out ([FollowFocusedRow]), then a [ListGlide] draws the rows where they were and glides them to their
 * place over 200 ms on a decelerating curve; a held key retargets and glides at the repeats' speed. The focused row and the
 * row past it are whole on every frame (`SettingsScrollFramesTest`). The column keeps [GLIDE_CONTEXT_ROWS] rows past the
 * focus with motion on, one with it off (a jump). The focus pill is drawn by the column ([rememberGlidingPill]) so it
 * glides from row to row; it takes the focused row's own box, so it keeps that row's height; [pillShape] is the shape the rows
 * draw their pill in (the sections' inset one) and [pillIndex] the row that wears it (default the focused one; -1 none).
 * The column opens as it always did: the focused row whole, no context.
 */
@Composable
internal fun KeepFocusedColumn(
    focusedIndex: () -> Int,
    rowCount: Int,
    gap: Dp,
    modifier: Modifier = Modifier,
    /** Not null: the column fades the row cut at an edge it can scroll past, tagged `<fadeTag>_fade_bottom` / `<fadeTag>_fade_top` (design/rules.md › Lists that scroll). */
    fadeTag: String? = null,
    pillShape: Shape = FocusPillShape,
    pillIndex: (() -> Int)? = null,
    /**
     * The column ends this far above its box: the clip of the list's window reaches that far past the column (the focused slab's overhang), so a
     * row the column cuts would show inside it. Settings' columns end above the hint keys' gap and pass [SlabDimens.listEndRoom] (the overhang and the
     * glow around it, as the home lists keep), so nothing draws past the box and a focused end row's glow is never cut flat.
     */
    endRoom: Dp = 0.dp,
    /** Focused on this row the column rests at its very top when the row and the next are whole there: what stands above it (System order) is never left half under the fade. */
    restsAtTopOn: Int = -1,
    content: @Composable ColumnScope.(place: (Int) -> Modifier) -> Unit,
) {
    val scroll = rememberScrollState()
    val layout = PlumTheme.layout
    val glide = rememberListGlide(stride = layout.rowHeight + gap, glowRoom = endRoom >= SlabDimens.listEndRoom)
    // Each row's top and bottom inside the column (px).
    val spans = remember { HashMap<Int, IntRange>() }
    val scope = rememberCoroutineScope()
    val follower = remember(scroll, spans) { ColumnFollower(scroll, spans, scope) }
    // Spans are kept by index while the rows change (another section's options): rows past the
    // current count are stale and must not count as context.
    follower.rowCount = rowCount
    follower.restsAtTopOn = restsAtTopOn
    // The glide makes the scroll's viewport [extra] taller at each end: the visible window is what is left.
    val extraPx = with(LocalDensity.current) { glide.extra.roundToPx() }
    follower.extraPx = extraPx
    follower.density = LocalDensity.current
    follower.peekPx = with(LocalDensity.current) { EdgePeek.roundToPx() }
    follower.glide = glide
    val clipShapes = remember { EndsClipCache() }
    val endReach = with(LocalDensity.current) { EndReach(SlabDimens.listEndRoom.toPx(), ROW_SHADOW_ABOVE.toPx(), SlabDimens.maxOverhang.toPx()) }
    FollowFocusedRow(follower, focusedIndex, glide.motionOn)
    // A window that changes size with no row or focus change (the top bar grew with the text size) would leave the focused row past its end.
    val latestFocus = rememberUpdatedState(focusedIndex)
    LaunchedEffect(scroll, follower) { snapshotFlow { scroll.viewportSize }.collect { follower.refit(latestFocus.value()) } }
    val pill =
        rememberGlidingPill(scroll, glide, stride = layout.rowHeight + gap, extraGap = layout.rowHeight * TALL_STEP_ROWS, shape = pillShape, focusedIndex = pillIndex ?: focusedIndex)
    val place = rememberRowPlaces(spans, follower, scroll, focusedIndex)
    val rows: @Composable ColumnScope.() -> Unit = { content(place) }
    val column =
        @Composable {
            pill.Provide {
                Column(
                    // The pill is drawn outside the rows' clip: its shadow fades out by itself ([ListGlide.keepsGlowRoom]), where the clip would cut it flat.
                    modifier.padding(bottom = endRoom).then(pill.modifier).clipEnds(scroll, clipShapes, { follower.bleedAbove(endReach, focusedIndex()) }) { follower.bleedBelow(endReach, focusedIndex()) }.then(glide.modifier).verticalScroll(scroll),
                ) {
                    // The glide's room above and below the rows; the rows themselves are spaced by [gap].
                    Column(Modifier.padding(vertical = glide.extra).then(pill.contentModifier), verticalArrangement = Arrangement.spacedBy(gap), content = rows)
                }
            }
        }
    if (fadeTag == null) {
        column()
        return
    }
    // A fade stops short of the focused row and of the last whole row: it covers only the room between them and the edge.
    val fadePx = with(LocalDensity.current) { PlumDimens.listFadeBand.toPx() }
    val focused by rememberUpdatedState(focusedIndex)
    val reachBelow = remember(follower, fadePx) { { minOf(follower.roomPast(focused(), below = true), follower.roomPastWholeRows(below = true)) / fadePx } }
    val reachAbove = remember(follower, fadePx) { { minOf(follower.roomPast(focused(), below = false), follower.roomPastWholeRows(below = false)) / fadePx } }
    Box {
        column()
        // Over the column's box (a bare `fillMaxWidth` here was 0 wide), less [endRoom]: the rows are clipped there, so the bottom fade must lie above that cut.
        Box(Modifier.matchParentSize().padding(bottom = endRoom)) {
            if (scroll.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(fadeTag + FADE_BOTTOM_SUFFIX), reachBelow)
            if (scroll.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).testTag(fadeTag + FADE_TOP_SUFFIX), reachAbove)
        }
    }
}

/** Each row's modifier, recording where the row lands and keeping the focused one in view when it moves. One per row, kept: a move creates none. */
@Composable
private fun rememberRowPlaces(
    spans: HashMap<Int, IntRange>,
    follower: ColumnFollower,
    scroll: ScrollState,
    focusedIndex: () -> Int,
): (Int) -> Modifier {
    val scope = rememberCoroutineScope()
    val places = remember(spans) { HashMap<Int, Modifier>() }
    return remember(spans) {
        { index: Int ->
            places.getOrPut(index) {
                Modifier.onPlaced {
                    val top = it.positionInParent().y.toInt()
                    val before = spans[index]
                    val span = top until top + it.size.height
                    spans[index] = span
                    if (before != span) follower.placedCount++
                    if (before != span && index == focusedIndex()) {
                        val target = follower.restAround(index, 0)
                        if (target != scroll.value) {
                            // Placed for the first time (the column opens): show it whole, gliding with
                            // motion on; the context is kept from the first move on. Moved or grown with
                            // no focus change (the text size changed): keep it in view at once.
                            val glides = before == null
                            scope.launch {
                                val from = scroll.value
                                scroll.scrollTo(target)
                                if (glides) follower.glide?.jumped((from - scroll.value).toFloat())
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The rows' clip, worked out at draw time. Once the list has scrolled it lets [bleedAbove] px past the window's top edge through (see [ColumnFollower.bleedAbove]), at the
 * bottom [bleedBelow] ([ColumnFollower.bleedBelow]), so a row the window cuts ends at the window under the fade and a row wholly past it shows no fragment.
 */
private fun Modifier.clipEnds(
    scroll: ScrollState,
    shapes: EndsClipCache,
    bleedAbove: () -> Float,
    bleedBelow: () -> Float,
): Modifier =
    graphicsLayer {
        clip = true
        shape = shapes.of(if (scroll.value > 0) bleedAbove() else Float.MAX_VALUE / 4, bleedBelow())
    }

/** One clip shape, replaced only when its two reaches change: a scroll frame that changes neither allocates nothing. */
private class EndsClipCache {
    private var shape = EndsClip(0f, 0f)

    fun of(
        top: Float,
        bottom: Float,
    ): Shape {
        if (shape.top != top || shape.bottom != bottom) shape = EndsClip(top, bottom)
        return shape
    }
}

private class EndsClip(
    val top: Float,
    val bottom: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rectangle(Rect(-HUGE, -top.coerceAtMost(HUGE), size.width + HUGE, size.height + bottom.coerceAtMost(HUGE)))

    private companion object {
        const val HUGE = 100_000f
    }
}

/** What a row draws past its own box above it, at most: a control's glow ([CollarDimens.grooveGlowBlur]) is the widest. */
private val ROW_SHADOW_ABOVE = CollarDimens.grooveGlowBlur

/** The reaches [ColumnFollower.bleedBelow] works with, in px. */
internal class EndReach(
    val room: Float,
    val shadowAbove: Float,
    val overhang: Float,
)

/** A step of a list's pill may be this many plain rows long (a heading or a tall row between two rows): its lag may span it. */
private const val TALL_STEP_ROWS = 6

/** The 200 ms decelerating glide ([PlumMotion.GLIDE_MS]) as an animation spec, for a scroll that is animated rather than jumped (the licence texts); a jump with motion off. */
internal fun <T> PlumMotionState.glideSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.GLIDE_MS, easing = PlumMotion.Decelerate) else snap()

/** The least of a row a scrolled column shows at an edge. */
private const val MIN_ROW_SHOWN = 1f / 3f

internal const val FADE_BOTTOM_SUFFIX = "_fade_bottom"
internal const val FADE_TOP_SUFFIX = "_fade_top"

/**
 * Follows the focus in its own small scope: only this recomposes when the focused row changes (a
 * move must not recompose the column), and its effect runs after that composition, before the
 * frame's layout.
 */
@Composable
private fun FollowFocusedRow(
    follower: ColumnFollower,
    focusedIndex: () -> Int,
    isMotionOn: Boolean,
) {
    val latest = rememberUpdatedState(focusedIndex)
    val index by remember(follower) { derivedStateOf { latest.value() } }
    val target = index
    SideEffect { follower.follow(target, isMotionOn) }
}

/** Scrolls a [KeepFocusedColumn] to its focused row; see there. Main thread only. */
private class ColumnFollower(
    private val scroll: ScrollState,
    private val spans: Map<Int, IntRange>,
    private val scope: CoroutineScope,
) {
    private var followed = NONE

    /** Bumped when a row is placed anew: what the clip works out from the rows' places is redone at the next draw (the spans are not state). */
    var placedCount by mutableIntStateOf(0)

    /** The column's rows now; set while composing, before the focus is followed. */
    var rowCount = 0

    /** See [KeepFocusedColumn]; set while composing. */
    var restsAtTopOn = -1

    /** Px of the next row kept past an edge the column continues over ([restWithPeeks]); set while composing. */
    var peekPx = 0

    /** The room the glide adds above and below the visible window, px; 0 with motion off. */
    var extraPx = 0

    /** The column's glide; set while composing. */
    var glide: ListGlide? = null

    /** For the slab's overhang, set while composing. */
    var density: Density = Density(1f)

    /** The height of what is shown: the scroll's viewport without the glide's room. */
    fun visible(): Int = (scroll.viewportSize - 2 * extraPx).coerceAtLeast(0)

    /** Px past the last whole row towards the bottom ([below]) or top edge of what is shown, 0 when no row is cut there: a fade covers no whole row. */
    fun roomPastWholeRows(below: Boolean): Float {
        val start = scroll.value
        val end = start + visible()
        var cut = false
        var room = visible()
        for (i in 0 until rowCount) {
            val span = spans[i] ?: continue
            val first = span.first
            val past = span.last + 1
            if (past <= start || first >= end) continue
            if (first >= start && past <= end) {
                room = minOf(room, if (below) end - past else first - start)
            } else if (if (below) past > end else first < start) {
                cut = true
            }
        }
        return if (cut) room.toFloat() else 0f
    }

    /** Px between row [index]'s slab and the bottom ([below]) or top edge of what is shown; unbounded when the row is not placed or a finger took it to or past the edge. */
    fun roomPast(
        index: Int,
        below: Boolean,
    ): Float {
        val own = spans[index] ?: return Float.MAX_VALUE
        val now = scroll.value
        // The focused row's slab is drawn past the row: a fade stops short of it, not of the row.
        val slab = slabOverhang((own.last + 1 - own.first).toFloat(), density)
        val room = if (below) now + visible() - (own.last + 1) else own.first - now
        return if (room < 0) Float.MAX_VALUE else (room - slab).coerceAtLeast(0f)
    }

    /**
     * How far (px) the clip lets drawing past the window's top edge through once the list has scrolled, the twin of [bleedBelow]; negative where it starts below the edge: a row the
     * edge leaves under a third of is hidden, so no sliver of it shows under the fade, the focused row right below it keeping its slab's overhang.
     */
    fun bleedAbove(
        reach: EndReach,
        focused: Int,
    ): Float {
        if (placedCount < 0) return 0f
        val start = scroll.value
        var previous = Int.MIN_VALUE
        for (i in 0 until rowCount) {
            val span = spans[i] ?: continue
            if (span.first < start && span.last + 1 > start) {
                val shown = (span.last + 1 - start).toFloat()
                // A row runs under the fade; only a sliver of it is left out.
                if (shown >= (span.last + 1 - span.first) * MIN_ROW_SHOWN) return 0f
                val next = spans[i + 1]
                val focusedBelow = focused == i + 1 && next != null && next.first - (span.last + 1) <= reach.overhang
                return -(if (focusedBelow) (shown - reach.overhang).coerceAtLeast(0f) else shown)
            }
            if (span.last + 1 <= start && span.last + 1 > previous) previous = span.last + 1
        }
        if (previous == Int.MIN_VALUE) return reach.room
        val own = spans[focused]
        val slab = if (own != null && own.first >= start && own.first - start < start - previous) reach.overhang else 0f
        return maxOf(slab, start - previous - reach.shadowAbove).coerceIn(0f, reach.room)
    }

    /**
     * How far (px) the clip lets drawing past the window's bottom edge through; negative where it stops above the edge, over a row left under a third of itself. A row cut by the edge stays cut there, under the fade. With a row wholly
     * below the edge nothing of it may show (its glow reaches [EndReach.shadowAbove] above its top), so only the room before that is open; the focused
     * row ending at the edge keeps its slab's overhang. With no row below, the whole [EndReach.room] is open for the last row's slab and glow.
     */
    fun bleedBelow(
        reach: EndReach,
        focused: Int,
    ): Float {
        if (placedCount < 0) return 0f
        val end = scroll.value + visible()
        var next = Int.MAX_VALUE
        for (i in 0 until rowCount) {
            val span = spans[i] ?: continue
            if (span.first < end && span.last + 1 > end) {
                val shown = (end - span.first).toFloat()
                // As at the top: only a sliver is left out, and never the focused row.
                return if (i == focused || shown >= (span.last + 1 - span.first) * MIN_ROW_SHOWN) 0f else -shown
            }
            if (span.first >= end && span.first < next) next = span.first
        }
        if (next == Int.MAX_VALUE) return reach.room
        val own = spans[focused]
        val slab = if (own != null && own.last + 1 <= end && end - (own.last + 1) < next - end) reach.overhang else 0f
        return maxOf(slab, next - end - reach.shadowAbove).coerceIn(0f, reach.room)
    }

    /** [wanted], or the rest nearest it that keeps [keep] whole and shows the next rows past both edges ([restWithPeeks]); null when the window has no room for that. */
    private fun restShowingPeeks(
        wanted: Int,
        viewport: Int,
        keep: IntRange,
    ): Int? {
        var last = 0
        for (i in 0 until rowCount) last = maxOf(last, (spans[i] ?: return wanted).last + 1)
        return restWithPeeks(wanted, viewport, peekPx, 0, (last - viewport).coerceAtLeast(0), keep.first, keep.last + 1, rowCount, { spans.getValue(it).first }, { spans.getValue(it).last + 1 })
    }

    /** Where the column rests with row [index] and up to [context] rows past it whole. Context gives way to the next rows' peeks, down to one row: with no room for peeks that row stays whole (the focus never rides the edge). */
    fun restAround(
        index: Int,
        context: Int,
        now: Int = scroll.value,
    ): Int {
        if (index == restsAtTopOn) {
            val next = spans[index + 1] ?: spans[index]
            if (next != null && next.last + 1 <= visible()) return 0
        }
        val viewport = visible()
        for (rows in context downTo minOf(context, 1)) {
            val keep = spanAround(index, rows, viewport)
            restShowingPeeks(keepVisible(now, viewport, keep), viewport, keep)?.let { return it }
        }
        return keepVisible(now, viewport, spanAround(index, context, viewport))
    }

    /** Keeps row [index] whole after the window changed size, as near the top as that allows: a window that grew (a header gave way) shows the rows above it again. */
    suspend fun refit(index: Int) {
        if (spans[index] == null) return
        val target = restAround(index, 0, now = 0)
        if (target != scroll.value) scroll.scrollTo(target)
    }

    fun follow(
        index: Int,
        isMotionOn: Boolean,
    ) {
        if (index == followed) return
        // Not placed yet (the column's first frame): its placement shows it (KeepFocusedColumn).
        if (spans[index] == null) return
        followed = index
        val now = scroll.value
        val viewport = visible()
        // Past the row, as many rows as a glide keeps in a games list (contextRowsFor): the column scrolls early enough that a held key never catches up with the edge.
        // Jumped to in this frame's layout: the row and the rows past it are whole, then the glide draws the rows where they were.
        val target = restAround(index, if (isMotionOn) GLIDE_CONTEXT_ROWS else 1)
        if (target != now) scroll.dispatchRawDelta((target - now).toFloat())
        if (!isMotionOn) return
        val after = scroll.value
        // How far the glide may draw the rows from their place: not past an edge with the row above or below the focus (the focused row itself at the ends).
        val own = spans.getValue(index)
        val above = spans[index - 1] ?: own
        val below = spans[index + 1]?.takeIf { index + 1 < rowCount } ?: own
        val roomAbove = (above.first - after).coerceAtLeast(0)
        val roomBelow = (after + viewport - (below.last + 1)).coerceAtLeast(0)
        glide?.jumped((now - after).toFloat(), roomAbove.toFloat(), roomBelow.toFloat())
    }

    /** Row [index] with up to [context] rows on each side of it, as many as there are and as fit. */
    private fun spanAround(
        index: Int,
        context: Int,
        viewport: Int,
    ): IntRange {
        val own = spans.getValue(index)
        for (rows in context downTo 1) {
            // The farthest row of context that exists now and has been placed, on each side.
            val above = (rows downTo 1).map { index - it }.filter { it >= 0 }.firstNotNullOfOrNull { spans[it] }
            val below = (rows downTo 1).map { index + it }.filter { it < rowCount }.firstNotNullOfOrNull { spans[it] }
            val top = minOf(above?.first ?: own.first, own.first)
            val bottom = maxOf(below?.last ?: own.last, own.last)
            if (bottom - top + 1 <= viewport) return top..bottom
        }
        return own
    }

    private companion object {
        const val NONE = Int.MIN_VALUE
    }
}

/**
 * A lazy column for a panel list that can be long (every game of a library, every folder or
 * emulator): composes only the rows on screen, also while the panel only previews, and keeps row
 * [focusedIndex] on screen with home's [KeepFocusedVisible], gliding like home's lists (200 ms, a
 * decelerating curve, the pill drawn by the list). -1 means the panel is not
 * entered: the list stays where it was and wears no pill. [restsAtTopOn] is the first row a cursor stands on, when a row above it takes none ([KeepFocusedVisible]).
 */
@Composable
internal fun PanelLazyList(
    focusedIndex: Int,
    modifier: Modifier = Modifier,
    restsAtTopOn: Int = -1,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = if (focusedIndex == restsAtTopOn) 0 else focusedIndex.coerceAtLeast(0))
    val latest = rememberUpdatedState(focusedIndex)
    val lastEntered = remember { IntArray(1) }
    val layout = PlumTheme.layout
    val glide = rememberListGlide(stride = layout.rowHeight + PlumDimens.listRowGap, shadowPastEnds = true)
    // Panel rows differ in height (a card with a detail line): the focused row's own box is the pill's.
    val pill =
        rememberGlidingPill(state, glide, rowHeight = layout.rowHeight, stride = layout.rowHeight + PlumDimens.listRowGap, extraGap = layout.rowHeight * TALL_STEP_ROWS, rowsVary = true) { latest.value }
    // Rows fade into the panel's edges with the screen's recipe, short of the focused row's slab; the list rests with a part of the next row there. The ramp is always on: a hard cut across a row is a fault, and on a card no screen fade lies over it.
    val density = LocalDensity.current
    val fades = remember(state, glide, density) { BarRunClips(state, glide, topRoom = 0f, ramps = true, density = density) { latest.value } }
    // Room under the last row, only for a rest that takes a tall row wholly past the top: the keeper writes it and asks again once it is there.
    val foot = remember(state) { mutableIntStateOf(0) }
    pill.Provide {
        LazyColumn(
            state = state,
            modifier = modifier.testTag(SettingsTags.PANEL_LIST).then(pill.modifier).then(glide.withFadeMask(fades, isOn = true)).then(pill.contentModifier),
            contentPadding = PaddingValues(top = glide.extra, bottom = glide.extra + with(density) { foot.intValue.toDp() }),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
            content = content,
        )
    }
    ReportTallestRow(state) { latest.value >= 0 }
    // A window that changes size (the header gave way to a tall row) brings the focused row into view again.
    val window by remember(state) { derivedStateOf { state.layoutInfo.viewportSize.height } }
    KeepFocusedVisible(state, glide, contentKey = window to foot.intValue, peekPx = with(density) { EdgePeek.roundToPx() }, restsAtTopOn = restsAtTopOn, foot = foot) {
        val index = latest.value
        if (index >= 0) lastEntered[0] = index
        lastEntered[0]
    }
}
