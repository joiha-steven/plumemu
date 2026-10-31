package com.plumemu.coreui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * A scrolling list that glides (design/rules.md › Shapes and motion). The list still jumps in
 * layout, in the frame its focus moves; the glide only draws the rows where they were and moves
 * them to their new place with `graphicsLayer { translationY }` over [PlumMotion.GLIDE_MS]
 * on [PlumMotion.Decelerate]. A jump that comes while the last one is still gliding (a held key)
 * starts from where the rows are drawn now plus its own row, so the drawn offset never jumps back,
 * and glides at constant speed over the time since the last jump, landing as the next repeat
 * comes: the list keeps moving evenly, never trailing by more than a row. Nothing is laid
 * out per frame. With motion off it is a plain jump, and no extra rows are composed.
 *
 * Use it on a vertical lazy list or grid:
 *
 *     val glide = rememberListGlide(stride = row height + gap)
 *     LazyColumn(
 *         modifier = modifier.testTag(...).then(glide.modifier),
 *         contentPadding = PaddingValues(vertical = glide.extra),
 *     )
 *     ... and call glide.jumped(px) in the frame the list's layout jumps by px.
 *
 * [modifier] clips to the list's own bounds and makes the lazy layout [extra] taller at each end,
 * with that much content padding, so one more row is composed past each edge: while the rows are
 * drawn off their place, that row fills the gap instead of the background. Item offsets, the
 * scroll position and `scrollToItem` are unchanged: the padding starts where the visible list
 * starts. The visible window ends at `viewportEndOffset - afterContentPadding`.
 */
@Stable
class ListGlide internal constructor(
    private val settle: Settle,
    private val nowNanos: () -> Long,
) {
    private var extraPx by mutableIntStateOf(0)
    private var lastJumpAt = Long.MIN_VALUE

    /** Room past each edge of the list: one row's stride with motion on, 0 with it off. */
    var extra: Dp by mutableStateOf(0.dp)
        private set

    /** How far the rows are drawn from their laid-out place, in px (positive: lower). */
    val offset: Float
        get() = settle.value

    /** [extra] in px: what the list's layout is taller than its visible window at each end. */
    internal val extraPixels: Int
        get() = extraPx

    /** Whether a glide is running (frames are being drawn for it). */
    internal val isGliding: Boolean
        get() = settle.isSettling

    /** Clip, extra rows and the glide's translation; goes after the list's own size and tags. */
    val modifier: Modifier =
        Modifier
            .clipToBounds()
            .layout { measurable, constraints ->
                val extra = extraPx
                if (extra == 0 || !constraints.hasBoundedHeight) {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                } else {
                    val taller =
                        constraints.copy(minHeight = constraints.minHeight + 2 * extra, maxHeight = constraints.maxHeight + 2 * extra)
                    val placeable = measurable.measure(taller)
                    layout(placeable.width, (placeable.height - 2 * extra).coerceAtLeast(0)) { placeable.place(0, -extra) }
                }
            }.graphicsLayer { translationY = settle.value }

    /**
     * The list's layout moved its rows by [movedPx] this frame (positive: down; 0: the focus moved
     * without a scroll). Call it while composing that frame; the rows are drawn where they were and
     * glide to their new place. [roomAbovePx] and [roomBelowPx] are how far they may be drawn above
     * or below their place without hiding a row that must stay whole (the caller's: near a list's
     * end there is no second row of context to glide into). A glide still running from an earlier
     * jump is brought within them too; with no room the rows are drawn at their place at once.
     */
    fun jumped(
        movedPx: Float,
        roomAbovePx: Float = Float.MAX_VALUE,
        roomBelowPx: Float = Float.MAX_VALUE,
    ) {
        if (extraPx == 0) return
        val above = roomAbovePx.coerceAtLeast(0f)
        val below = roomBelowPx.coerceAtLeast(0f)
        if (movedPx == 0f) {
            val now = settle.value
            val kept = now.coerceIn(-above, below)
            if (kept != now) settle.start(kept, PlumMotion.GLIDE_MS * NANOS_PER_MS, PlumMotion.Decelerate)
            return
        }
        val now = nowNanos()
        val since = if (lastJumpAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastJumpAt
        lastJumpAt = now
        val plan = GlideMath.plan(settle.value, movedPx, since, extraPx.toFloat())
        settle.start(plan.from.coerceIn(-above, below), plan.durationNanos, plan.easing)
    }

    internal fun update(
        stride: Dp,
        isOn: Boolean,
        density: Density,
    ) {
        val room = if (isOn) stride * ROOM_STRIDES else 0.dp
        val roomPx = with(density) { room.roundToPx() }
        if (room != extra) extra = room
        if (roomPx != extraPx) extraPx = roomPx
        if (!isOn && settle.isSettling) settle.stop()
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
        const val ROOM_STRIDES = 2f
    }
}

/**
 * The clock (nanoseconds) a glide tells "a held key's next jump" by: real time in the app. A frame
 * test provides its compose clock (`mainClock.currentTime * 1_000_000L`), so the 200 ms held-key window and
 * the glide's duration follow frame time, not how fast the machine runs.
 */
val LocalGlideClock = compositionLocalOf<() -> Long> { System::nanoTime }

/**
 * A [ListGlide] for a list whose rows are [stride] apart (row height plus gap): that much room is
 * kept past each edge, and a glide never draws the rows further than that from their place.
 */
@Composable
fun rememberListGlide(stride: Dp): ListGlide {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val glide = remember(scope) { ListGlide(Settle(scope)) { clock.value() } }
    glide.update(stride, PlumTheme.motion.isOn, LocalDensity.current)
    return glide
}

/** The glide's arithmetic, apart from frames and Compose so it can be tested on its own. */
internal object GlideMath {
    data class Plan(
        val from: Float,
        val durationNanos: Long,
        val easing: Easing,
    )

    /**
     * Where a glide starts after the rows jumped by [movedPx] while drawn [current] px off their
     * place: the jump is added to what is drawn now (a held key retargets, it never restarts from
     * zero), kept within [limitPx] so the extra rows always cover the gap. A jump from rest runs on
     * [PlumMotion.Decelerate] for [PlumMotion.GLIDE_MS]. A jump within that time of the last one
     * (a held key) runs linearly at the speed of the repeats: one row per gap since the last jump,
     * the rows owed from earlier steps on top of it, up to 1.25 gaps at that same speed (the lag
     * stays), more than that caught up gently. The rows never stop between
     * repeats; a repeat rate that quickens makes the lag grow, and the room caps it. The last
     * glide of a held key is
     * linear too: nobody knows a repeat is the last until none follows, so it cannot ease out.
     */
    fun plan(
        current: Float,
        movedPx: Float,
        sinceLastJumpNanos: Long,
        limitPx: Float,
    ): Plan {
        val from = (current - movedPx).coerceIn(-limitPx, limitPx)
        val glideNanos = PlumMotion.GLIDE_MS * NANOS_PER_MS
        if (sinceLastJumpNanos !in 0 until glideNanos) return Plan(from, glideNanos, PlumMotion.Decelerate)
        val gap = sinceLastJumpNanos.coerceIn(PlumMotion.GLIDE_HELD_MIN_MS * NANOS_PER_MS, glideNanos)
        val owed = if (movedPx == 0f) 1f else abs(from / movedPx)
        val duration = (gap * (if (owed <= LAG_KEPT_ROWS) owed else LAG_KEPT_ROWS + (owed - LAG_KEPT_ROWS) * LAG_CAUGHT_UP)).toLong().coerceIn(PlumMotion.GLIDE_HELD_MIN_MS * NANOS_PER_MS, glideNanos)
        return Plan(from, duration, LinearEasing)
    }

    private const val NANOS_PER_MS = 1_000_000L

    /**
     * A held step covers the row it brings and the rows owed from earlier steps in this many gaps: at the repeats' own speed up
     * to [LAG_KEPT_ROWS] (the lag stays as it is), and past that only half of what is owed beyond it counts, so the rows speed
     * up a little to catch up. A hold that quickens then does not pile up more lag than the room holds, and no step jumps to
     * a speed far above the repeats'.
     */
    private const val LAG_KEPT_ROWS = 1.25f
    private const val LAG_CAUGHT_UP = 0.5f
}
