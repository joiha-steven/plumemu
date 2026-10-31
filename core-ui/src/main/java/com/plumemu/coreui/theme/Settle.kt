package com.plumemu.coreui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

/**
 * A value that settles to 0 frame by frame: the shared engine of [ListGlide] and [ContentFade].
 * Read [value] where it is drawn (`graphicsLayer`), so a frame of motion never recomposes or lays
 * out anything.
 *
 * [start] is called while composing the frame that must already show the start value (the frame
 * whose layout jumped, or that first shows new content); the value is set at once, and the frames
 * after it move it to 0 on the composition's frame clock. The coroutine ends at 0: nothing runs at
 * rest. Not `Animatable`: its `snapTo` suspends, so the start value would reach the screen one frame
 * after the jump it hides.
 */
@Stable
class Settle internal constructor(
    private val scope: CoroutineScope,
) {
    private val current = mutableFloatStateOf(0f)

    /** The value to draw now; 0 at rest. */
    val value: Float
        get() = current.floatValue

    /** Whether frames are still moving [value] towards 0. Not state: read it to decide, not to draw. */
    val isSettling: Boolean
        get() = job != null

    private var from = 0f
    private var durationNanos = 0L
    private var easing: Easing = PlumMotion.EaseOut
    private var startedAt = 0L
    private var isStartPending = false

    /** The shortest gap seen between two frames: the display's frame period. */
    private var framePeriodNanos = SettleMath.DEFAULT_FRAME_NANOS
    private var job: Job? = null

    /** Shows [from] from this frame on and settles it to 0 over [durationNanos] with [easing]. */
    fun start(
        from: Float,
        durationNanos: Long,
        easing: Easing,
    ) {
        if (from == 0f || durationNanos <= 0L) {
            stop()
            return
        }
        this.from = from
        this.durationNanos = durationNanos
        this.easing = easing
        current.floatValue = from
        isStartPending = true
        if (job == null) job = scope.launch { run() }
    }

    /** Back to rest at once (motion turned off, the content went away). */
    fun stop() {
        job?.cancel()
        job = null
        isStartPending = false
        current.floatValue = 0f
    }

    private suspend fun run() {
        val self = currentCoroutineContext()[Job]
        var lastFrame = NO_FRAME
        try {
            while (true) {
                val settled =
                    withFrameNanos { frame ->
                        if (lastFrame != NO_FRAME) framePeriodNanos = SettleMath.framePeriod(framePeriodNanos, frame - lastFrame)
                        lastFrame = frame
                        // The start value was drawn by the frame before this one.
                        if (isStartPending) {
                            startedAt = frame - framePeriodNanos
                            isStartPending = false
                        }
                        val value = SettleMath.valueAt(from, frame - startedAt, durationNanos, easing)
                        current.floatValue = value
                        value == 0f
                    }
                // A start() between this frame's callback and here (the same frame's composition,
                // e.g. a held key's next jump) must keep the loop going, or its glide never runs.
                if (settled && !isStartPending) break
            }
        } finally {
            // A stop() followed by a start() may already have launched this coroutine's successor.
            if (job === self) job = null
        }
    }

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
    }
}

internal object SettleMath {
    /** Until two frames have been seen: 60 Hz, the slowest display Plumemu runs on. */
    const val DEFAULT_FRAME_NANOS = 16_666_667L

    /** Gaps shorter than this are timing noise, not a frame period (240 Hz is about 4.2 ms). */
    private const val SHORTEST_FRAME_NANOS = 4_000_000L

    /** [from] eased towards 0: exactly 0 once [elapsedNanos] reaches [durationNanos]. */
    fun valueAt(
        from: Float,
        elapsedNanos: Long,
        durationNanos: Long,
        easing: Easing,
    ): Float {
        if (elapsedNanos >= durationNanos) return 0f
        val fraction = (elapsedNanos.coerceAtLeast(0L).toFloat() / durationNanos).coerceIn(0f, 1f)
        return from * (1f - easing.transform(fraction))
    }

    /** The frame period, from the shortest gap seen so far; a dropped frame (a longer gap) does not count. */
    fun framePeriod(
        known: Long,
        gap: Long,
    ): Long = if (gap in SHORTEST_FRAME_NANOS until known) gap else known
}
