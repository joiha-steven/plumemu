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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.FocusPillShape
import com.plumemu.coreui.components.rememberGlidingPill
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.rememberListGlide
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.plumemu.coreui.components.ListEdgeFade
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.GLIDE_CONTEXT_ROWS
import com.plumemu.home.KeepFocusedVisible
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
    /** Not null: the column fades the row cut at an edge it can scroll past, tagged `<fadeTag>_bottom` / `<fadeTag>_top` (design/rules.md › Lists that scroll). */
    fadeTag: String? = null,
    pillShape: Shape = FocusPillShape,
    pillIndex: (() -> Int)? = null,
    content: @Composable ColumnScope.(place: (Int) -> Modifier) -> Unit,
) {
    val scroll = rememberScrollState()
    val layout = PlumTheme.layout
    val glide = rememberListGlide(stride = layout.rowHeight + gap)
    // Each row's top and bottom inside the column (px).
    val spans = remember { HashMap<Int, IntRange>() }
    val scope = rememberCoroutineScope()
    val follower = remember(scroll, spans) { ColumnFollower(scroll, spans, scope) }
    // Spans are kept by index while the rows change (another section's options): rows past the
    // current count are stale and must not count as context.
    follower.rowCount = rowCount
    // The glide makes the scroll's viewport [extra] taller at each end: the visible window is what is left.
    val extraPx = with(LocalDensity.current) { glide.extra.roundToPx() }
    follower.extraPx = extraPx
    follower.glide = glide
    FollowFocusedRow(follower, focusedIndex, glide.extra > 0.dp)
    val pill =
        rememberGlidingPill(scroll, glide, stride = layout.rowHeight + gap, extraGap = layout.rowHeight * TALL_STEP_ROWS, shape = pillShape, focusedIndex = pillIndex ?: focusedIndex)
    // One modifier per row, kept: a move creates none.
    val places = remember(spans) { HashMap<Int, Modifier>() }
    val place =
        remember(spans) {
            { index: Int ->
                places.getOrPut(index) {
                    Modifier.onPlaced {
                        val top = it.positionInParent().y.toInt()
                        val before = spans[index]
                        val span = top until top + it.size.height
                        spans[index] = span
                        if (before != span && index == focusedIndex()) {
                            val target = keepVisible(scroll.value, follower.visible(), span)
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
    val rows: @Composable ColumnScope.() -> Unit = { content(place) }
    val column =
        @Composable {
            pill.Provide {
                Column(
                    modifier.then(pill.modifier).then(glide.modifier).verticalScroll(scroll),
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
    Box {
        column()
        if (scroll.canScrollForward) ListEdgeFade(atBottom = true, Modifier.align(Alignment.BottomCenter).testTag(fadeTag + FADE_BOTTOM_SUFFIX))
        if (scroll.canScrollBackward) ListEdgeFade(atBottom = false, Modifier.align(Alignment.TopCenter).testTag(fadeTag + FADE_TOP_SUFFIX))
    }
}

/** A step of a list's pill may be this many plain rows long (a heading or a tall row between two rows): its lag may span it. */
private const val TALL_STEP_ROWS = 6

/** The 200 ms decelerating glide ([PlumMotion.GLIDE_MS]) as an animation spec, for a scroll that is animated rather than jumped (the licence texts); a jump with motion off. */
internal fun <T> PlumMotionState.glideSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.GLIDE_MS, easing = PlumMotion.Decelerate) else snap()

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

    /** The column's rows now; set while composing, before the focus is followed. */
    var rowCount = 0

    /** The room the glide adds above and below the visible window, px; 0 with motion off. */
    var extraPx = 0

    /** The column's glide; set while composing. */
    var glide: ListGlide? = null

    /** The height of what is shown: the scroll's viewport without the glide's room. */
    fun visible(): Int = (scroll.viewportSize - 2 * extraPx).coerceAtLeast(0)

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
        val target = keepVisible(now, viewport, spanAround(index, if (isMotionOn) GLIDE_CONTEXT_ROWS else 1, viewport))
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
 * entered: the list stays where it was and wears no pill.
 */
@Composable
internal fun PanelLazyList(
    focusedIndex: Int,
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = focusedIndex.coerceAtLeast(0))
    val latest = rememberUpdatedState(focusedIndex)
    val lastEntered = remember { IntArray(1) }
    val layout = PlumTheme.layout
    val glide = rememberListGlide(stride = layout.rowHeight + PlumDimens.listRowGap)
    // Panel rows differ in height (a card with a detail line): the focused row's own box is the pill's.
    val pill =
        rememberGlidingPill(state, glide, rowHeight = layout.rowHeight, stride = layout.rowHeight + PlumDimens.listRowGap, extraGap = layout.rowHeight * TALL_STEP_ROWS, rowsVary = true) { latest.value }
    pill.Provide {
        LazyColumn(
            state = state,
            modifier = modifier.testTag(SettingsTags.PANEL_LIST).then(pill.modifier).then(glide.modifier).then(pill.contentModifier),
            contentPadding = PaddingValues(vertical = glide.extra),
            verticalArrangement = Arrangement.spacedBy(PlumDimens.listRowGap),
            content = content,
        )
    }
    KeepFocusedVisible(state, glide) {
        val index = latest.value
        if (index >= 0) lastEntered[0] = index
        lastEntered[0]
    }
}
