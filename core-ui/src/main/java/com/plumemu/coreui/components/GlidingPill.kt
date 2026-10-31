package com.plumemu.coreui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.ListGlide
import com.plumemu.coreui.theme.LocalGlideClock
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Settle
import kotlin.math.abs

/**
 * What a list that draws the focus pill itself ([rememberGlidingPill]) tells its rows: they stop drawing their own pill
 * (see [focusPill]) and the focused one hands over its press dip, so the pill dips with it. Null (the default) outside
 * such a list and with animations off: each row draws its pill, as it always did.
 */
internal val LocalGlidingPill = compositionLocalOf<GlidingPill?> { null }

/**
 * The focus pill of a home list, drawn once by the list behind its rows so it can glide from the row it leaves to the row it
 * lands on (the PS Vita's way; docs/design/rules.md › Focus and current): over [PlumMotion.GLIDE_MS] on
 * [PlumMotion.Decelerate], the list glide's own tokens. Nothing recomposes per frame: the pill is a draw block that reads the
 * list's layout, the glide's offset and a [Settle], all while drawing.
 *
 * The pill is drawn at the focused row's drawn place (its laid-out place plus the list glide's translation, so it never
 * runs ahead of or behind its row while the list glides) plus a lag that settles to 0. A move starts the lag where the
 * pill was drawn last frame, so a move during a move never snaps back; the lag is kept within one [stridePx] (plus the gap above the first system). A key held
 * (the next move comes within [PlumMotion.GLIDE_MS] of the last) settles linearly at the speed of the repeats, like the
 * list glide, so the pill stays with the moving focus and never strobes.
 */
@Stable
class GlidingPill internal constructor(
    private val settle: Settle,
    private val nowNanos: () -> Long,
) {
    /** Set by the focused row, while it composes: its press dip, so the pill is scaled with it. */
    internal var press: PressDip? = null

    /**
     * Set by the focused row, while it is placed, in a list whose rows differ in height (the panels'): its own top inside its
     * lazy item and its height, in px. Null: the list's fixed row height at the top of the item.
     */
    internal var rowBox: RowBox? = null

    /** True when [rememberGlidingPill] was told the rows differ in height: the focused row then reports its [RowBox]. */
    internal var measuresRows = false

    /**
     * Set while the content of a scrolling column is placed ([GlidingPillHost.contentModifier]): the focused row's [RowBox] is then
     * measured from it, so it holds wherever the row sits in the column (and does not change when the column scrolls).
     */
    internal var content: LayoutCoordinates? = null

    private var lastIndex = NONE
    private var lastTop = Float.NaN
    private var lastMoveAt = Long.MIN_VALUE

    internal fun draw(
        scope: DrawScope,
        input: PillInput,
    ) = with(scope) {
        val index = input.focusedIndex()
        val scroll = input.scroll
        val height: Float
        val rowTop: Float
        if (scroll != null) {
            // A column with a ScrollState: the focused row's box is in the column's content coordinates; the visible window starts at the scroll.
            val box = rowBox
            if (index < 0 || box == null || !input.isOn) {
                stopTracking()
                return@with
            }
            height = box.height
            rowTop = box.topIn(content) - scroll.value + input.glide.offset
        } else {
            val info = input.listState!!.layoutInfo
            val item = if (index < 0) null else info.visibleItemsInfo.firstOrNull { it.index == index }
            if (item == null || !input.isOn) {
                stopTracking()
                return@with
            }
            val box = if (measuresRows) rowBox else null
            height = box?.height ?: input.heightPx
            // Rows are laid out inside the list's own bounds shifted up by the extra room, then drawn translated by the glide.
            rowTop =
                if (box != null && content != null) {
                    // The box is measured from the lazy list itself ([GlidingPillHost.contentModifier]): a row at the root of its item, or in a wrapper, is the same.
                    box.topIn(content) - input.glide.extraPixels + input.glide.offset
                } else {
                    val fromBottom = if (box != null) box.top else if (input.fromBottom) item.size - height else 0f
                    item.offset + info.beforeContentPadding - input.glide.extraPixels + fromBottom + input.glide.offset
                }
        }
        if (index != lastIndex) {
            if (lastIndex != NONE && !lastTop.isNaN()) startLag(lastTop - rowTop, input.stridePx, input.stridePx + input.extraGapPx)
            lastIndex = index
        }
        val top = rowTop + settle.value
        lastTop = top
        val colors = input.colors
        val p = press?.progress?.value ?: 0f
        val scale = if (p == 0f) 1f else 1f - (1f - PlumMotion.PRESS_SCALE) * p
        // Settings' lists (a scroll state, or a lazy list measured from itself) clip only above and below: a clip at the side edge that the pill's round cap
        // touches shades one pixel of the cap, which would differ from the row's own pill (the Settings shots).
        val side = if (scroll != null || content != null) SIDE_UNCLIPPED_PX else 0f
        clipRect(left = -side, right = size.width + side) {
            withTransform({
                translate(0f, top)
                if (scale != 1f) scale(scale, scale, Offset(size.width / 2f, height / 2f))
            }) {
                val outline = input.shape.createOutline(androidx.compose.ui.geometry.Size(size.width, height), layoutDirection, this)
                drawOutline(outline, if (p == 0f) colors.pill else lerp(colors.pill, colors.text, PlumMotion.PRESS_FILL_MIX * p))
                drawRingInside(outline, colors.accent)
            }
        }
    }

    private fun stopTracking() {
        lastIndex = NONE
        lastTop = Float.NaN
        if (settle.isSettling) settle.stop()
    }

    private fun startLag(
        lagPx: Float,
        stridePx: Float,
        capPx: Float,
    ) {
        val lag = lagPx.coerceIn(-capPx, capPx)
        val now = nowNanos()
        val since = if (lastMoveAt == Long.MIN_VALUE) Long.MAX_VALUE else now - lastMoveAt
        lastMoveAt = now
        val glideNanos = PlumMotion.GLIDE_MS * NANOS_PER_MS
        if (since !in 0 until glideNanos) {
            settle.start(lag, glideNanos, PlumMotion.Decelerate)
            return
        }
        val rows = if (stridePx > 0f) abs(lag) / stridePx else 1f
        val duration = (since * rows).toLong().coerceIn(PlumMotion.GLIDE_HELD_MIN_MS * NANOS_PER_MS, glideNanos)
        settle.start(lag, duration, LinearEasing)
    }

    private companion object {
        const val NONE = -1
        const val NANOS_PER_MS = 1_000_000L
        const val SIDE_UNCLIPPED_PX = 64f
    }
}

/** A focused row's place in its lazy item: [top] from the item's top and [height], in px. */
internal class RowBox(
    val top: Float,
    val height: Float,
    /** The row's node, kept so a list measured from itself ([GlidingPillHost.contentModifier]) reads the row's place live: a scroll moves a row nested in a wrapper without placing it again. */
    val coordinates: LayoutCoordinates? = null,
) {
    /** The row's top from [content] now; the placed [top] when the row or the list is not attached. */
    fun topIn(content: LayoutCoordinates?): Float =
        if (content != null && content.isAttached && coordinates != null && coordinates.isAttached) content.localPositionOf(coordinates, Offset.Zero).y else top
}

internal class PillInput(
    /** The lazy list the pill is drawn for; null for a column with a [scroll] state. */
    val listState: LazyListState?,
    val scroll: ScrollState?,
    val glide: ListGlide,
    val colors: com.plumemu.coreui.theme.PlumComposeColors,
    val shape: Shape,
    val heightPx: Float,
    val stridePx: Float,
    val extraGapPx: Float,
    val fromBottom: Boolean,
    val isOn: Boolean,
    val focusedIndex: () -> Int,
)

/** A [GlidingPill] and the modifier that draws it. */
@Stable
class GlidingPillHost internal constructor(
    internal val pill: GlidingPill?,
    /** Goes on the list before its [ListGlide.modifier]: draws the pill behind the rows, not translated by the glide's layer. */
    val modifier: Modifier,
    /**
     * Optional on a lazy list, needed on a scrolling column ([rememberGlidingPill] with a ScrollState): goes on the lazy list after its
     * `glide.modifier` (or on the column inside the scroll), so the focused row is measured from the list itself and sits right
     * wherever it is in its item. Without it a lazy list's rows must be the root of their item, as the game panels' are.
     */
    val contentModifier: Modifier = Modifier,
) {
    /** Provide it around the list so its rows stop drawing their own pill. */
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        androidx.compose.runtime.CompositionLocalProvider(LocalGlidingPill provides pill, content = content)
    }
}

/**
 * The home lists' gliding focus pill (see [GlidingPill]): put [GlidingPillHost.modifier] on the lazy column before
 * `glide.modifier`, and wrap the column in [GlidingPillHost.Provide]. [focusedIndex] is the lazy item that wears the pill, or
 * -1 for none; it is read while drawing. The pill is [rowHeight] tall; with [fromBottom] it sits at the bottom of its item
 * (the item has a gap above its row, as the first system's) instead of the top. [stridePx] is the distance between two rows.
 * With animations off nothing is drawn here and the rows draw their pill, as before.
 */
@Composable
fun rememberGlidingPill(
    listState: LazyListState,
    glide: ListGlide,
    rowHeight: Dp,
    stride: Dp,
    /** Extra distance between two particular rows (the gap above the first system): the lag may reach it, so that step does not jump. */
    extraGap: Dp = 0.dp,
    shape: Shape = FocusPillShape,
    fromBottom: Boolean = false,
    /** The rows differ in height (a note or a badge under a label, a label on three lines): the focused row's own box is the pill's. */
    rowsVary: Boolean = false,
    focusedIndex: () -> Int,
): GlidingPillHost {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val isOn = PlumTheme.motion.isOn
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val latestIndex = rememberUpdatedState(focusedIndex)
    val pill = remember(scope) { GlidingPill(Settle(scope)) { clock.value() } }
    val input =
        remember(listState, glide, colors, shape, rowHeight, stride, extraGap, fromBottom, isOn, density) {
            PillInput(listState, null, glide, colors, shape, with(density) { rowHeight.roundToPx() }.toFloat(), with(density) { stride.toPx() }, with(density) { extraGap.toPx() }, fromBottom, isOn) { latestIndex.value() }
        }
    pill.measuresRows = rowsVary
    return remember(pill, input) {
        GlidingPillHost(if (isOn) pill else null, Modifier.drawBehind { pill.draw(this, input) }, Modifier.onPlaced { pill.content = it })
    }
}

/**
 * The same pill for a column that scrolls with a [ScrollState] (Settings' columns, whose rows are all composed): put
 * [GlidingPillHost.modifier] on the column before `glide.modifier` (outside the scroll), [GlidingPillHost.contentModifier] on the
 * column inside the scroll, and wrap it in [GlidingPillHost.Provide]. Rows differ in height, so the focused row reports its own box.
 * The pill takes [shape] (a row that is drawn inset passes the same inset shape). [focusedIndex] only tells when the focus moves
 * (-1: none); the pill sits where the focused row reports itself.
 */
@Composable
fun rememberGlidingPill(
    scroll: ScrollState,
    glide: ListGlide,
    stride: Dp,
    /** The most one step may be longer than a [stride] (a heading between two rows, a tall row): the pill's lag may span it, so that step does not jump. */
    extraGap: Dp = 0.dp,
    shape: Shape = FocusPillShape,
    focusedIndex: () -> Int,
): GlidingPillHost {
    val scope = rememberCoroutineScope()
    val clock = rememberUpdatedState(LocalGlideClock.current)
    val isOn = PlumTheme.motion.isOn
    val colors = PlumTheme.colors
    val density = LocalDensity.current
    val latestIndex = rememberUpdatedState(focusedIndex)
    val pill = remember(scope) { GlidingPill(Settle(scope)) { clock.value() } }
    val input =
        remember(scroll, glide, colors, shape, stride, extraGap, isOn, density) {
            PillInput(null, scroll, glide, colors, shape, 0f, with(density) { stride.toPx() }, with(density) { extraGap.toPx() }, false, isOn) { latestIndex.value() }
        }
    pill.measuresRows = true
    return remember(pill, input) {
        GlidingPillHost(
            if (isOn) pill else null,
            Modifier.drawBehind { pill.draw(this, input) },
            Modifier.onPlaced { pill.content = it },
        )
    }
}
