package com.plumemu.coreinput

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay

/**
 * Frames from a timer, [periodNanos] apart (60 Hz by default), for an [InputRouter] whose scope
 * has no display frame clock: unit tests on virtual time, where it makes repeats land as they do on
 * a display of that rate. It keeps its own timeline from 0: [withFrameNanos] waits for the next
 * multiple of the period, [rest] for a given time; the waits are whole milliseconds without drift.
 */
internal class TimerFrameClock(
    private val periodNanos: Long = 16_666_666L,
) : MonotonicFrameClock {
    private var nowNanos = 0L
    private var waitedMs = 0L

    /** How many frames were asked for: a held key should not ask for every one. */
    var framesServed = 0
        private set

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        framesServed++
        advanceTo((nowNanos / periodNanos + 1) * periodNanos)
        return onFrame(nowNanos)
    }

    /** Waits [nanos] on this clock's timeline (the router resting between repeats). */
    suspend fun rest(nanos: Long) = advanceTo(nowNanos + nanos)

    private suspend fun advanceTo(time: Long) {
        nowNanos = time
        val dueMs = (time + NANOS_PER_MS - 1) / NANOS_PER_MS
        delay(dueMs - waitedMs)
        waitedMs = dueMs
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000L
    }
}
