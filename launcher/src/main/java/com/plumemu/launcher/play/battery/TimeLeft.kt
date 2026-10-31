package com.plumemu.launcher.play.battery

import kotlin.math.roundToInt

/**
 * Minutes as people are shown them: to the nearest 5
 * under an hour, to the nearest 15 from an hour on, never under 5. [isApproximate] when the
 * rounding hid minutes ("About 45 min" for 47, "45 min" for 45).
 */
data class TimeLeft(val minutes: Int, val isApproximate: Boolean)

fun roundedMinutesLeft(minutes: Double): TimeLeft {
    val exact = minutes.roundToInt().coerceAtLeast(0)
    val step = if (exact < HOUR) UNDER_AN_HOUR_STEP else HOUR_STEP
    // Rounded to the nearest step; at least one step, so a nearly empty battery never reads 0.
    val rounded = ((exact + step / 2) / step * step).coerceAtLeast(UNDER_AN_HOUR_STEP)
    return TimeLeft(rounded, isApproximate = rounded != exact)
}

/** Android's own time to full is exact to the minute: shown as it is ("1 h 03 to full"). */
fun exactMinutesLeft(minutes: Double): TimeLeft = TimeLeft(minutes.roundToInt().coerceAtLeast(1), isApproximate = false)

private const val HOUR = 60
private const val UNDER_AN_HOUR_STEP = 5
private const val HOUR_STEP = 15
