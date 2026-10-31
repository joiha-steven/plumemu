package com.plumemu.secondscreen

import androidx.compose.runtime.Immutable
import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.BatteryTuning
import com.plumemu.launcher.play.battery.TimeLeft
import com.plumemu.launcher.play.battery.exactMinutesLeft
import com.plumemu.launcher.play.battery.roundedMinutesLeft
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * What the in-game card shows under the clock. [figures] null: the clock card alone, centred, as without the battery part, with
 * [note] at the bottom when there is one. Plain values: equal values draw the same, so the part
 * redraws only when something it shows changed.
 */
@Immutable
internal data class InGameBattery(
    val figures: BatteryFigures?,
    val note: InGameNote?,
)

/** A line at the bottom: the first minute of a game never measured, or why the card moves (once ever, one minute). */
internal enum class InGameNote { Measuring, Drift }

/**
 * The battery part. [left] is the time until empty (or full while charging), null when not known
 * or full; [isLeftExact] when it is Android's own time to full, exact to the minute ("1 h 03 to
 * full"), not an estimate that rounding happened to leave whole ("1 h 30 min to full");
 * [endsAtMillis] the wall-clock time it comes to. [pacePercent] the pace in % of the full
 * charge an hour (into the battery while charging). [chargingSinceMillis] when charging was first
 * seen in this play. [driftNote]: the key gives way to "Moves a little…" for that minute.
 */
@Immutable
internal data class BatteryFigures(
    val isCharging: Boolean,
    val isLow: Boolean,
    val left: TimeLeft?,
    val isLeftExact: Boolean,
    val endsAtMillis: Long?,
    val pacePercent: Int?,
    val usual: UsualPace,
    val chargingSinceMillis: Long?,
    val chart: BatteryChart,
    val driftNote: Boolean = false,
)

/** The game's usual pace against this play's (discharging only). */
internal sealed interface UsualPace {
    /** Never measured, or charging: nothing in the key, no dotted line. */
    data object None : UsualPace

    /** Within [USUAL_DIFFERS_BY] of it: "as usual", no dotted line. */
    data object AsUsual : UsualPace

    /** Noticeably different: "usually 40%" and the dotted line. */
    data class Differs(val percent: Int) : UsualPace
}

/**
 * The chart, a fixed [CHART_MINUTES] wide from [startMillis] (wall clock): x in minutes from the
 * start, y in percent. [played] is this play's level, its last point now; [aheadTo] where the line
 * ahead ends (empty, full, or the chart's right edge); [usualFrom] to [usualTo] the usual pace's
 * dotted line. Everything is already cut to the chart's three hours.
 */
@Immutable
internal data class BatteryChart(
    val startMillis: Long,
    val played: List<ChartPoint>,
    val aheadTo: ChartPoint?,
    val usualFrom: ChartPoint?,
    val usualTo: ChartPoint?,
)

@Immutable
internal data class ChartPoint(val minute: Float, val percent: Float)

/** The chart's width in minutes: three hours, whatever the pace, so a steep line means a fast drain. */
internal const val CHART_MINUTES = 180

/** The pace differs noticeably from the usual one from this share of it on. */
internal const val USUAL_DIFFERS_BY = 0.15

/**
 * The part from what the meter has ([snapshot], null before its first read), the battery
 * broadcast's [reading] (the level the clock card shows) and the wall clock [nowMillis]. Null:
 * nothing to show, not even a note: before the first read, or on a device that gives no figure
 * ([givesNoFigure]), where the card is the clock alone as with Show battery off. [driftNote]: the
 * once-ever minute of "Moves a little…".
 */
internal fun inGameBatteryOf(
    snapshot: BatterySnapshot?,
    reading: BatteryReading?,
    nowMillis: Long,
    driftNote: Boolean,
): InGameBattery? {
    val noteAlone = if (driftNote) InGameNote.Drift else InGameNote.Measuring
    if (snapshot == null) return if (driftNote) InGameBattery(null, InGameNote.Drift) else null
    val estimate = snapshot.estimate
    val levelNow = reading?.percent ?: snapshot.levels.lastOrNull()?.level
    if (estimate == null || levelNow == null) return if (!driftNote && snapshot.givesNoFigure) null else InGameBattery(null, noteAlone)
    val charging = estimate.isCharging
    val full = estimate.fullChargeMicroampHours
    val pace = full?.let { abs(estimate.drawMicroamps) / it * PERCENT }?.takeIf { it > 0.0 }
    val leftIsExact = charging && estimate.isMinutesLeftExact
    val left = estimate.minutesLeft?.let { minutes -> if (leftIsExact) exactMinutesLeft(minutes) else roundedMinutesLeft(minutes) }
    val nowMinute = floor(nowMillis / MILLIS_PER_MINUTE.toDouble()).toLong() * MILLIS_PER_MINUTE
    val usualPercent = if (charging) null else snapshot.usualDrawMicroamps?.takeIf { it > 0 }?.let { usual -> full?.let { usual / it * PERCENT } }
    val usual =
        when {
            usualPercent == null || pace == null -> UsualPace.None
            abs(pace - usualPercent) >= USUAL_DIFFERS_BY * usualPercent && percentOf(pace) != percentOf(usualPercent) ->
                UsualPace.Differs(percentOf(usualPercent))
            else -> UsualPace.AsUsual
        }
    // The level points on the wall clock: elapsed time does not jump when the clock is set.
    val toWall = nowMillis - snapshot.nowMs
    val history = snapshot.levels.map { WallLevel(it.atMs + toWall, it.level, it.isCharging) }
    val chart =
        chartOf(
            history = history,
            nowMillis = nowMinute,
            levelNow = levelNow,
            minutesAhead = estimate.minutesLeft,
            charging = charging,
            usualPercentPerHour = (usual as? UsualPace.Differs)?.let { usualPercent },
        )
    return InGameBattery(
        figures =
            BatteryFigures(
                isCharging = charging,
                isLow = !charging && levelNow <= LOW_BATTERY_PERCENT,
                left = left,
                isLeftExact = left != null && leftIsExact,
                endsAtMillis = left?.let { nowMinute + it.minutes * MILLIS_PER_MINUTE },
                pacePercent = pace?.let(::percentOf),
                usual = usual,
                chargingSinceMillis = if (charging) chargingSince(history) else null,
                chart = chart,
                driftNote = driftNote,
            ),
        note = null,
    )
}

/**
 * Read for over a minute (and a read's slack) and still no estimate: neither the current nor the
 * charge counter gives a pace on this device, so "Measuring battery use…" would stay all play.
 * Counted in read time, not since the play's first point: a screen off in the first minute does not
 * give up early.
 */
internal val BatterySnapshot.givesNoFigure: Boolean
    get() = estimate == null && readMs >= NO_FIGURE_AFTER_MS

/** A game with no usual pace has its first figure after a minute measured; one read later, none is coming. */
internal const val NO_FIGURE_AFTER_MS = BatteryTuning.FIRST_FIGURE_MS + BatteryTuning.READ_EVERY_MS

private class WallLevel(val atMillis: Long, val level: Int, val isCharging: Boolean)

/** At least 1%: a pace that rounds to 0 is still a pace. */
private fun percentOf(value: Double): Int = value.roundToInt().coerceAtLeast(1)

/** When the latest run of charging points began; null without one. */
private fun chargingSince(history: List<WallLevel>): Long? {
    var since: Long? = null
    for (point in history.asReversed()) {
        if (!point.isCharging) break
        since = point.atMillis
    }
    return since
}

/**
 * The window starts at the play's first point, down to the [START_STEP_MINUTES] (a round label),
 * and moves on an hour at a time once now is past [SLIDE_AFTER_MINUTES] of it, so the line ahead
 * keeps room.
 */
private fun chartOf(
    history: List<WallLevel>,
    nowMillis: Long,
    levelNow: Int,
    minutesAhead: Double?,
    charging: Boolean,
    usualPercentPerHour: Double?,
): BatteryChart {
    val first = history.firstOrNull()?.atMillis?.coerceAtMost(nowMillis) ?: nowMillis
    val step = START_STEP_MINUTES * MILLIS_PER_MINUTE
    var start = first.floorDiv(step) * step
    while (nowMillis - start > SLIDE_AFTER_MINUTES * MILLIS_PER_MINUTE) start += HOUR_MILLIS
    fun minuteOf(millis: Long) = (millis - start).toDouble() / MILLIS_PER_MINUTE
    val nowAt = minuteOf(nowMillis)

    val raw = history.filter { it.atMillis < nowMillis }.map { minuteOf(it.atMillis) to it.level.toDouble() } + (nowAt to levelNow.toDouble())
    val played = cutAtStart(raw).map { (m, p) -> point(m, p) }.distinct()

    val ahead =
        minutesAhead?.takeIf { it > 0.0 }?.let { minutes ->
            val end = if (charging) PERCENT else 0.0
            cutAtEnd(nowAt, levelNow.toDouble(), nowAt + minutes, end)
        }
    val usual =
        usualPercentPerHour?.let { perHour ->
            // From the first point of the latest stretch without charging, at the usual pace.
            val from = history.asReversed().takeWhile { !it.isCharging }.lastOrNull()
            val fromAt = from?.let { minuteOf(it.atMillis) } ?: nowAt
            val fromLevel = from?.level?.toDouble() ?: levelNow.toDouble()
            val emptyAt = fromAt + fromLevel / perHour * MINUTES_PER_HOUR
            val shown = cutAtStart(listOf(fromAt to fromLevel, emptyAt to 0.0))
            if (shown.size < 2) null else shown[0] to cutAtEnd(shown[0].first, shown[0].second, shown[1].first, shown[1].second)
        }
    return BatteryChart(
        startMillis = start,
        played = played,
        aheadTo = ahead,
        usualFrom = usual?.let { point(it.first.first, it.first.second) },
        usualTo = usual?.second,
    )
}

/** The line from its last point before minute 0 on, that point moved onto minute 0. */
private fun cutAtStart(points: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    val firstInside = points.indexOfFirst { it.first >= 0.0 }
    if (firstInside <= 0) return if (firstInside < 0) emptyList() else points
    val (m0, p0) = points[firstInside - 1]
    val (m1, p1) = points[firstInside]
    val atZero = p0 + (p1 - p0) * (0.0 - m0) / (m1 - m0)
    return listOf(0.0 to atZero) + points.subList(firstInside, points.size)
}

/** The straight line's end, cut at the chart's right edge. */
private fun cutAtEnd(
    m0: Double,
    p0: Double,
    m1: Double,
    p1: Double,
): ChartPoint {
    if (m1 <= CHART_MINUTES || m1 <= m0) return point(m1, p1)
    val t = (CHART_MINUTES - m0) / (m1 - m0)
    return point(CHART_MINUTES.toDouble(), p0 + (p1 - p0) * t)
}

/**
 * Rounded to a quarter of a minute and of a percent (under a dp on the Thor's 458 dp chart): an
 * estimate moving by less leaves the chart equal, so it does not redraw.
 */
private fun point(
    minute: Double,
    percent: Double,
): ChartPoint = ChartPoint(quarter(minute), quarter(percent.coerceIn(0.0, PERCENT)))

private fun quarter(value: Double): Float = (value * QUARTERS).roundToInt() / QUARTERS.toFloat()

private const val PERCENT = 100.0
private const val QUARTERS = 4
private const val MILLIS_PER_MINUTE = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val MINUTES_PER_HOUR = 60.0

/** The window starts on a round 10 minutes ("22:40"). */
private const val START_STEP_MINUTES = 10L

/** Past two hours in, the window moves on by an hour, so the line ahead has an hour of room. */
private const val SLIDE_AFTER_MINUTES = 120L

