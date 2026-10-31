package com.plumemu.coreinput

import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Timing of a held direction. Android's own key repeat is ignored (`repeatCount > 0`) so the rate
 * is the same on every device and every source: keys, hat and stick.
 *
 * The times below are the feel in milliseconds; repeats land on display frames ([RepeatPacer]):
 * each wait is rounded to whole frames, at least [ACCELERATED_FLOOR_MS] worth (2 frames at 60 Hz,
 * 4 at 120 Hz), so every move is drawn and the cadence is even at any refresh rate.
 */
internal object HoldRepeat {
    /**
     * Before the first repeat. Long enough that a normal tap (about 100–150 ms) never repeats,
     * short enough that holding feels immediate; Android's own key repeat waits a similar time.
     */
    const val FIRST_REPEAT_DELAY_MS = 300L

    /**
     * With "Hold to scroll faster" off: 12.5 moves a second, slow enough to read titles going by.
     */
    const val CONSTANT_INTERVAL_MS = 80L

    /** With it on, repeats start a little slower than the constant rate ... */
    const val ACCELERATED_START_MS = 120L

    /** ... and each one is 20 % quicker, so the floor is reached after about one second of holding. */
    const val ACCELERATION_FACTOR = 0.8

    /**
     * ... down to 32 ms: about two frames at 60 Hz, so every move is still drawn on the slowest
     * screens, and 1,000 games pass in about half a minute.
     */
    const val ACCELERATED_FLOOR_MS = 32L

    /** The waits between repeats after the first one, forever. */
    fun intervals(accelerate: Boolean): Sequence<Long> =
        if (!accelerate) {
            generateSequence { CONSTANT_INTERVAL_MS }
        } else {
            generateSequence(ACCELERATED_START_MS.toDouble()) { previous ->
                maxOf(previous * ACCELERATION_FACTOR, ACCELERATED_FLOOR_MS.toDouble())
            }.map { it.roundToLong().coerceAtLeast(ACCELERATED_FLOOR_MS) }
        }

    /**
     * Whole frames for a wait of [intervalMs] on a display whose frames are [framePeriodNanos]
     * apart: the nearest count, never under the floor's (32 ms: 2 frames at 60 Hz, 4 at 120 Hz).
     */
    fun framesFor(
        intervalMs: Long,
        framePeriodNanos: Long,
    ): Int {
        val period = framePeriodNanos.toDouble()
        val floor = ceil(ACCELERATED_FLOOR_MS * NANOS_PER_MS / period - ROUNDING_SLACK).toInt()
        val nearest = (intervalMs * NANOS_PER_MS / period).roundToInt()
        return maxOf(nearest, floor, 1)
    }

    private const val NANOS_PER_MS = 1_000_000.0

    /** So a period a hair over 16 ms still counts 32 ms as two frames. */
    private const val ROUNDING_SLACK = 0.05
}

/**
 * Decides on which display frame a held direction repeats. Fed every frame's time
 * ([onFrame], `withFrameNanos`), it learns the frame period from the shortest gap between frames
 * and answers true on the frame a repeat is due: the first one after [HoldRepeat.FIRST_REPEAT_DELAY_MS],
 * then each wait of [intervals] in whole frames ([HoldRepeat.framesFor]). A late frame (dropped by
 * a busy moment) moves the next repeat with it instead of firing two at once.
 */
internal class RepeatPacer(
    intervals: Sequence<Long>,
) {
    private val waits = intervals.iterator()
    private var framePeriod = DEFAULT_FRAME_NANOS
    private var hasPeriod = false
    private var firstFrame = NO_FRAME
    private var lastFrame = NO_FRAME
    private var nextDue = NO_FRAME

    /** True when this frame carries a repeat. */
    fun onFrame(frameNanos: Long): Boolean {
        if (lastFrame == NO_FRAME) firstFrame = frameNanos else learnPeriod(frameNanos - lastFrame)
        lastFrame = frameNanos
        // The press came about a frame before the first frame seen.
        val due = if (nextDue == NO_FRAME) firstFrame - framePeriod + HoldRepeat.FIRST_REPEAT_DELAY_MS * NANOS_PER_MS else nextDue
        // Half a frame of slack: a frame time a little early (vsync jitter) still counts.
        if (frameNanos < due - framePeriod / 2) return false
        nextDue = frameNanos + HoldRepeat.framesFor(waits.next(), framePeriod) * framePeriod
        return true
    }

    /**
     * How long the router may sleep after the last frame without missing the next repeat: until
     * two frames before it is due, so the frames around it are seen and the period stays learnt.
     */
    fun restNanos(sinceFrameNanos: Long = 0L): Long {
        if (lastFrame == NO_FRAME) return 0L
        // The time already gone since that frame (a long frame, the resume) counts towards the rest.
        val spent = sinceFrameNanos.takeIf { it in 0L..LONGEST_FRAME_NANOS * 2 } ?: 0L
        val due = if (nextDue == NO_FRAME) firstFrame - framePeriod + HoldRepeat.FIRST_REPEAT_DELAY_MS * NANOS_PER_MS else nextDue
        return (due - lastFrame - 2 * framePeriod - spent).coerceAtLeast(0L)
    }

    /** The time of the last frame seen, in the frame clock's time base. */
    val lastFrameNanos: Long
        get() = lastFrame

    private fun learnPeriod(gap: Long) {
        // A gap across a rest is not a frame period: only short gaps count (20 Hz and up).
        if (gap in SHORTEST_FRAME_NANOS..LONGEST_FRAME_NANOS && (!hasPeriod || gap < framePeriod)) {
            framePeriod = gap
            hasPeriod = true
        }
    }

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE
        const val NANOS_PER_MS = 1_000_000L

        /** Until two frames have been seen: 60 Hz. */
        const val DEFAULT_FRAME_NANOS = 16_666_667L

        /** Gaps shorter than this are timing noise, not a frame period. */
        const val SHORTEST_FRAME_NANOS = 4_000_000L

        /** Gaps longer than this span a rest or a stall, not one frame. */
        const val LONGEST_FRAME_NANOS = 50_000_000L
    }
}
