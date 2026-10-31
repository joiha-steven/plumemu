package com.plumemu.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import com.plumemu.coreinput.Column
import com.plumemu.coreinput.strip.StripState
import com.plumemu.coreui.components.FocusTravel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The grid ring's lag never exceeds this many cell heights: what the glide's room past the grid's edge holds. */
internal const val RING_LAG_ROWS = 2f

/** [LiftSlot.index] of a slot that holds no cover. */
internal const val NO_COVER = -1

/**
 * One cover that is drawn larger over the grid's clip (design/rules.md › Grid): the focused one,
 * or one that was and is still shrinking back. [layer] holds the cover's own drawing, recorded by its
 * cell while it has a slot; the grid's ring pass draws it [grow] (0..1) of the way to
 * its scale ([PlumDimens.FOCUSED_COVER_SCALE] for covers, [PlumDimens.FOCUSED_ICON_SCALE] for Apps icons). [index] is snapshot state: a cell's draw block reads it, so a cell
 * redraws (and records) when it gains or loses its slot, and nothing recomposes.
 */
internal class LiftSlot(
    val layer: GraphicsLayer,
    opening: Int = NO_COVER,
) {
    var index by mutableIntStateOf(opening)
    var isFocused by mutableStateOf(opening != NO_COVER)
    val grow = Animatable(if (opening != NO_COVER) 1f else 0f)

    /**
     * Which cover last recorded itself into [layer]: the ring's pass draws the layer only for the cover it belongs to.
     * Snapshot state, read by the ring's pass: the cell may record after the pass has drawn in the same frame
     * (the slot is assigned a frame before), and the pass must then draw again.
     */
    var recordedIndex by mutableIntStateOf(NO_COVER)
    var recordedEntryId: String? = null
    var job: Job? = null
}

/**
 * The lifted covers of a grid: [SLOTS] of them, so that a cover leaving focus can finish shrinking
 * while the next one grows, and a third while a key is held. Motion is draw-phase
 * scale only (never layout): [grow] animates, the ring pass reads it while drawing.
 */
internal class CoverLift(
    val slots: List<LiftSlot>,
    /** The cover focused when the grid opened ([NO_COVER]: none): its slot starts lifted, at full scale. */
    val opening: Int,
    private val scope: CoroutineScope,
    private val isMotionOn: () -> Boolean,
) {
    /** The slot holding the cover at [index], if it is lifted or shrinking back. Reads snapshot state: call it while drawing. */
    fun slotOf(index: Int): LiftSlot? = slots.firstOrNull { it.index == index }

    /** Focus moved to cover [index] ([NO_COVER]: none, the grid lost focus or a panel opened): the old one shrinks back, the new one grows. */
    fun moveTo(index: Int) {
        for (slot in slots) {
            if (slot.isFocused && slot.index != index) release(slot)
        }
        if (index == NO_COVER) return
        val slot = slotOf(index) ?: slots.firstOrNull { it.index == NO_COVER } ?: slots.minBy { it.grow.value }
        slot.job?.cancel()
        slot.index = index
        slot.isFocused = true
        slot.job = scope.launch { grow(slot, 1f) }
    }

    private fun release(slot: LiftSlot) {
        slot.isFocused = false
        slot.job?.cancel()
        slot.job =
            scope.launch {
                grow(slot, 0f)
                slot.index = NO_COVER
            }
    }

    private suspend fun grow(
        slot: LiftSlot,
        to: Float,
    ) {
        if (isMotionOn()) slot.grow.animateTo(to, tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.EaseOut)) else slot.grow.snapTo(to)
    }

    companion object {
        const val SLOTS = 3
    }
}

@Composable
internal fun rememberCoverLift(
    opening: Int = NO_COVER,
    isMotionOn: () -> Boolean,
): CoverLift {
    val scope = rememberCoroutineScope()
    val layers = List(CoverLift.SLOTS) { rememberGraphicsLayer() }
    return remember(scope) { CoverLift(layers.mapIndexed { i, layer -> LiftSlot(layer, if (i == 0) opening else NO_COVER) }, opening, scope, isMotionOn) }
}

/**
 * Makes the lifts follow the focus of a grid ([entryId]'s games): the cover that has it grows ([lift]) and, once focus has
 * rested on it, [sweep] plays; [travel] sends the ring from the cell the focus left. The grid's opening focus is already lifted ([CoverLift.opening]): nothing to grow or sweep.
 * Shared by the cover grid and the Apps grid.
 */
@Composable
internal fun FollowFocusLift(
    state: StripState,
    entryId: String,
    panelOpen: State<Boolean>,
    lift: CoverLift,
    sweep: FocusSweep,
    motionOn: State<Boolean>,
    travel: FocusTravel,
) {
    LaunchedEffect(state, entryId) {
        var first = true
        snapshotFlow {
            val focus = state.focus
            if (focus.column == Column.Games && !panelOpen.value) focus.rowOf(entryId) else NO_COVER
        }.collectLatest { index ->
            val isOpening = first && index == lift.opening
            first = false
            if (isOpening) return@collectLatest
            lift.moveTo(index)
            travel.moved(index)
            if (index == NO_COVER) sweep.stop() else sweep.play(motionOn.value)
        }
    }
}

/**
 * The light band that crosses the focused cover once (design/rules.md › Focus and current). It
 * starts only after focus has rested on the cover [PlumMotion.FOCUS_SWEEP_REST_MS], so a held
 * direction does not flash every cover it passes; the caller cancels [play] when focus leaves.
 * [progress] runs 0..1 while the band is on the cover; [Animatable.isRunning] says it is.
 */
internal class FocusSweep {
    val progress = Animatable(0f)

    /** Waits for the rest, then plays once. Nothing plays when [isMotionOn] is false (system animations off, or the app's setting). */
    suspend fun play(isMotionOn: Boolean) {
        progress.snapTo(0f)
        if (!isMotionOn) return
        delay(PlumMotion.FOCUS_SWEEP_REST_MS.toLong())
        progress.animateTo(1f, tween(PlumMotion.FOCUS_SWEEP_MS, easing = PlumMotion.EaseInOut))
    }

    /** Focus left every cover: the band is gone. */
    suspend fun stop() = progress.snapTo(0f)
}

/**
 * The band at [progress] (0..1) over [cover] (px), clipped to the cover's rounded shape: white,
 * soft on both edges, travelling along a direction a little below horizontal.
 */
internal fun DrawScope.drawSweep(
    cover: Rect,
    corner: Float,
    progress: Float,
) {
    val angle = Math.toRadians(PlumDimens.FOCUS_SWEEP_ANGLE_DEGREES.toDouble())
    val direction = Offset(cos(angle).toFloat(), sin(angle).toFloat())
    val span = abs(direction.x) * cover.width + abs(direction.y) * cover.height
    val band = span * PlumDimens.FOCUS_SWEEP_WIDTH
    // From wholly before the cover to wholly past it, measured along the direction from the cover's centre.
    val along = -span / 2 - band / 2 + progress * (span + band)
    val middle = cover.center + direction * along
    val peak = PlumDimens.FOCUS_SWEEP_ALPHA
    val clear = PlumDimens.focusSweepColor.copy(alpha = 0f)
    val brush =
        Brush.linearGradient(
            0f to clear,
            0.25f to PlumDimens.focusSweepColor.copy(alpha = peak * SHOULDER),
            0.5f to PlumDimens.focusSweepColor.copy(alpha = peak),
            0.75f to PlumDimens.focusSweepColor.copy(alpha = peak * SHOULDER),
            1f to clear,
            start = middle - direction * (band / 2),
            end = middle + direction * (band / 2),
        )
    val shape = Path().apply { addRoundRect(RoundRect(cover, CornerRadius(corner))) }
    clipPath(shape) { drawRect(brush, topLeft = cover.topLeft, size = cover.size) }
}

/**
 * [layer] (the focused cover at rest in [resting], px) drawn [scale] times larger about its
 * centre and raised [lift] px, over a plate of [background] in its own larger shape ([cover], [corner]). The plate
 * hides the copy the cell still draws in place (it lies wholly inside the larger shape), so a cover's
 * transparent parts show the background, as when it is not focused, not its own copy and hairline.
 */
internal fun DrawScope.drawFocusedCover(
    layer: GraphicsLayer,
    resting: Rect,
    cover: Rect,
    corner: Dp,
    background: Color,
    scale: Float,
    lift: Float = 0f,
    pivot: Offset = resting.size.center,
) {
    drawRoundRect(background, topLeft = cover.topLeft, size = cover.size, cornerRadius = CornerRadius(corner.toPx()))
    withTransform({
        translate(resting.left, resting.top - lift)
        scale(scale, scale, pivot = pivot)
    }) { drawLayer(layer) }
}

/** The band's brightness a quarter of its width in from an edge, so its sides fall off like a bell, not a tent. */
private const val SHOULDER = 0.35f
