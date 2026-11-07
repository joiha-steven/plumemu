package com.plumemu.home

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.components.EdgeFades
import com.plumemu.coreui.components.slabOverhang
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * Home's columns run from the top bar to the hint bar and rows go under both (design/rules.md › Lists that scroll): the one fade
 * ([com.plumemu.coreui.components.BarFades]) lies across the whole screen on those two edges. [top] and [bottom] are the room between the strip and each bar;
 * [height] is the whole run, bar to bar.
 */
@Immutable
internal data class BarRoom(
    val top: Dp,
    val bottom: Dp,
    val height: Dp,
    /** How much taller the strip stands than [com.plumemu.coreui.theme.PlumLayout.stripHeight], which counts the hint bar: what its absence adds. */
    val taller: Dp = 0.dp,
)

internal val LocalBarRoom = staticCompositionLocalOf { BarRoom(PlumDimens.barGap, PlumDimens.footerGap, 0.dp) }

/** Lays the strip out from bar to bar: it is [BarRoom.top] and [BarRoom.bottom] taller than the room it is given, and placed that much higher. */
internal fun Modifier.runsToBars(room: BarRoom): Modifier =
    layout { measurable, constraints ->
        val top = room.top.roundToPx()
        val bottom = room.bottom.roundToPx()
        val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + top + bottom, maxHeight = constraints.maxHeight + top + bottom))
        layout(placeable.width, (placeable.height - top - bottom).coerceAtLeast(0)) { placeable.place(0, -top) }
    }

/** Keeps what does not scroll (the details, an empty message) in the strip's place inside a column that runs from bar to bar. */
@Composable
internal fun Modifier.inStrip(): Modifier {
    val room = LocalBarRoom.current
    return padding(top = room.top, bottom = room.bottom)
}

/**
 * Where a list's rows rest between the bars, in px of the run from bar to bar: [rows] whole rows clear of both fades, what is left shared evenly above and below them, so a
 * scrolled list shows the same part of a row under each bar. [inset] is how far below the strip's top the first whole row rests once the list has scrolled (the list's first
 * row stands at the strip's top); [below] the room under the last whole row, down to the hint bar.
 */
@Immutable
internal data class BarRows(
    val rows: Int,
    val inset: Int,
    val below: Int,
)

@Composable
internal fun barRows(
    stride: Dp,
    gap: Dp,
): BarRows {
    val room = LocalBarRoom.current
    return with(LocalDensity.current) {
        val run = room.height.roundToPx()
        val top = room.top.roundToPx()
        val step = stride.roundToPx().coerceAtLeast(1)
        val gapPx = gap.roundToPx()
        val rows = ((run - 2 * PlumDimens.listFadeBand.roundToPx() + gapPx) / step).coerceAtLeast(1)
        val height = rows * step - gapPx
        val inset = ((run - height) / 2 - top).coerceAtLeast(0)
        BarRows(rows, inset, (run - top - inset - height).coerceAtLeast(0))
    }
}

/**
 * Where a list's rows stop under the bars, read while drawing: past an edge the list continues beyond, nothing is drawn outside the run, and at rest a row showing less than
 * [minShown] of itself is left out, so no sliver stands at a bar (the focus keeps its row whole; one showing so little was moved by a finger); a glide is left alone, or rows would pop as they pass. [topRoom] is the room
 * above the list's window, [gapAbove] and [gapBelow] the empty space an item carries around its row.
 */
@Stable
internal class BarRunClips(
    private val list: LazyListState,
    private val glide: ListGlide,
    private val topRoom: Float,
    private val gapAbove: (index: Int) -> Float = { 0f },
    private val gapBelow: Float = 0f,
    /** A list on a card, which no screen fade lies over: it draws the same ramp itself, at its own edges, short of the focused row. */
    private val ramps: Boolean = false,
    /** Whether an item is a heading: one is left out with a row left out right under it, so it never ends the list alone. */
    private val isHeading: (Int) -> Boolean = { false },
    /** Whether this list's page is the one on screen, read while drawing. */
    private val isShown: () -> Boolean = { true },
    /** The least of a row that shows at a bar. */
    private val minShown: Float = MIN_SHOWN,
    /** Not null: the ramp also stops short of the focused row's slab, not only of its box. */
    private val density: Density? = null,
    /** Px the list hides at its end at rest for its own reason (a text left as a sliver), past what [minShown] hides. */
    private val hiddenAtEnd: (height: Float) -> Float = { 0f },
    private val focused: () -> Int,
) : EdgeFades, BarRunner {
    override val canScroll: Boolean
        get() = list.canScrollForward || list.canScrollBackward

    override fun top(fadePx: Float, height: Float): Float {
        if (!ramps || !continuesAbove()) return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focused() } ?: return fadePx
        return short(fadePx, rowTop(item), item)
    }

    override fun bottom(fadePx: Float, height: Float): Float {
        if (!ramps || !continuesBelow(height)) return 0f
        val item = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focused() } ?: return fadePx
        return short(fadePx, height - rowEnd(item), item)
    }

    override fun clearTop(height: Float): Float {
        if (!continuesAbove()) return 0f
        if (!atRest()) return ROOM_ONLY_PX
        val sliver = list.layoutInfo.visibleItemsInfo.firstOrNull { rowTop(it) < 0f && rowEnd(it) > 0f && rowEnd(it) < minShown * rowHeight(it) }
        return sliver?.let { rowEnd(it) } ?: ROOM_ONLY_PX
    }

    override fun clearBottom(height: Float): Float {
        if (!continuesBelow(height)) return 0f
        if (!atRest()) return ROOM_ONLY_PX
        val items = list.layoutInfo.visibleItemsInfo
        val at = items.indexOfFirst { rowTop(it) < height && rowEnd(it) > height && height - rowTop(it) < minShown * rowHeight(it) }
        val own = hiddenAtEnd(height)
        if (at < 0) return if (own > 0f) own else ROOM_ONLY_PX
        return maxOf(own, height - rowTop(items[if (at > 0 && isHeading(items[at - 1].index)) at - 1 else at]))
    }

    override val runsUnderTop: Boolean
        get() = isShown() && continuesAbove()

    override fun focusUnderTop(bandPx: Float): Boolean = isShown() && list.layoutInfo.visibleItemsInfo.any { it.index == focused() && rowTop(it) < bandPx }

    /** Whether rows run past the top ([atTop]) or the bottom of a window [height] px tall; read while drawing or in a derived state. */
    fun continuesPast(
        atTop: Boolean,
        height: Float,
    ): Boolean = if (atTop) continuesAbove() else continuesBelow(height)

    /** The ramp between the focused row and the edge [toEdge] px past it; a row a finger took past the edge leaves the whole of it. */
    private fun short(
        fadePx: Float,
        toEdge: Float,
        item: LazyListItemInfo,
    ): Float {
        if (toEdge < 0f) return if (density == null) 0f else fadePx
        val slab = density?.let { slabOverhang(rowHeight(item), it) } ?: 0f
        return minOf(fadePx, (toEdge - slab).coerceAtLeast(0f))
    }

    private fun atRest() = glide.offset == 0f && !list.isScrollInProgress

    private fun continuesAbove(): Boolean {
        val items = list.layoutInfo.visibleItemsInfo
        return items.isNotEmpty() && (items.first().index > 0 || items.any { rowTop(it) < -EDGE_PX })
    }

    private fun continuesBelow(height: Float): Boolean {
        val info = list.layoutInfo
        val items = info.visibleItemsInfo
        return items.isNotEmpty() && (items.last().index < info.totalItemsCount - 1 || items.any { rowEnd(it) > height + EDGE_PX })
    }

    private fun rowTop(item: LazyListItemInfo) = topRoom + item.offset + gapAbove(item.index) + glide.offset

    private fun rowEnd(item: LazyListItemInfo) = topRoom + item.offset + item.size - gapBelow + glide.offset

    private fun rowHeight(item: LazyListItemInfo) = item.size - gapBelow - gapAbove(item.index)

    private companion object {
        /** Clears the room past the run and none of the run (the mask adds the room to it). */
        const val ROOM_ONLY_PX = 0.01f

        /** A row this few px past an edge is not cut: rows stand on whole px, dp rounding can leave one over. */
        const val EDGE_PX = 1f

        const val MIN_SHOWN = 1f / 3f
    }
}

/** A row of one centred line, such as the systems column's: under half of it, all that shows of its line is the tops or the feet of the glyphs. */
internal const val ONE_LINE_MIN_SHOWN = 1f / 2f

/** A column that may run under the top bar. The top fade is drawn while one does, and not over a focused row or cover standing at the strip's top (its slab reaches past the row). */
internal interface BarRunner {
    val runsUnderTop: Boolean

    fun focusUnderTop(bandPx: Float): Boolean

    /** Px from the bottom of what the focus lifts over the column (a cover's ring) down to the hint bar; [Float.POSITIVE_INFINITY] for a column whose focus never stands in the band. */
    val roomUnderFocus: Float
        get() = Float.POSITIVE_INFINITY
}

/** The columns on screen now, read by the fade while drawing. */
@Stable
internal class BarRunners {
    private val shown = mutableStateListOf<BarRunner>()

    fun add(runner: BarRunner) {
        shown += runner
    }

    fun remove(runner: BarRunner) {
        shown -= runner
    }

    fun topFadeShows(bandPx: Float): Boolean = shown.any { it.runsUnderTop } && shown.none { it.focusUnderTop(bandPx) }

    /** Share of [bandPx] the bottom fade may take: it ends at a lifted ring, as a panel's ramp ends short of its focused row. */
    fun bottomFadeReach(bandPx: Float): Float = shown.minOfOrNull { it.roomUnderFocus / bandPx }?.coerceIn(0f, 1f) ?: 1f
}

internal val LocalBarRunners = staticCompositionLocalOf { BarRunners() }

/** Tells the fade about [runner] while it is composed. */
@Composable
internal fun FollowBarRunner(runner: BarRunner) {
    val runners = LocalBarRunners.current
    DisposableEffect(runners, runner) {
        runners.add(runner)
        onDispose { runners.remove(runner) }
    }
}
