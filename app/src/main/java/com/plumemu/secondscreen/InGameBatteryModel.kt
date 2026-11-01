package com.plumemu.secondscreen

import androidx.compose.runtime.Immutable
import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.BatteryTuning
import com.plumemu.launcher.play.battery.TimeLeft
import com.plumemu.launcher.play.battery.exactMinutesLeft
import com.plumemu.launcher.play.battery.roundedMinutesLeft
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil
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
 * The battery part. [level] the level the clock card shows (the ring). [left] is the time until
 * empty (or full while charging), null when not known or full; [isLeftExact] when it is Android's
 * own time to full, exact to the minute ("1 h 3 min to full"), not an estimate that rounding happened
 * to leave whole ("1 h 30 min to full"); [endsAtMillis] the wall-clock time it comes to.
 * [pacePercent] the pace in % of the full charge an hour (into the battery while charging).
 * [chargingSinceMillis] when charging was first seen in this play. [playedMinutes] since the play's
 * first point; [usedPercent] the level it has taken (null once a charger came in: the drop no longer
 * says what the play used). [driftNote]: "Moves a little…" in the facts' place for that minute.
 */
@Immutable
internal data class BatteryFigures(
    val isCharging: Boolean,
    val isLow: Boolean,
    val level: Int,
    val left: TimeLeft?,
    val isLeftExact: Boolean,
    val endsAtMillis: Long?,
    val pacePercent: Int?,
    val usual: UsualPace,
    val chargingSinceMillis: Long?,
    val playedMinutes: Int,
    val usedPercent: Int?,
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
 * The chart, a timeline of this play: from its first point ([startMillis], wall clock, "Started
 * 21:07") to empty or full ([minutes] wide, at least [MIN_CHART_MINUTES]); x in minutes from the
 * start, y in percent. [played] is this play's level, its last point now; [aheadTo] where the line
 * ahead ends (empty or full); [usualFrom] to [usualTo] the usual pace's dotted line, cut at the
 * chart's right end. [hourMinutes]: the whole hours of the wall clock, for the ticks under it.
 */
@Immutable
internal data class BatteryChart(
    val startMillis: Long,
    val minutes: Float,
    val played: List<ChartPoint>,
    val aheadTo: ChartPoint?,
    val usualFrom: ChartPoint?,
    val usualTo: ChartPoint?,
    val hourMinutes: List<Float>,
    val aheadEnds: Boolean,
)

/**
 * The chart's vertical scale in percent: from the lowest point it draws to the highest, out to the
 * tens around them, and at least [MIN_SCALE_SPAN] tall (within 0 to 100). A line at 65% over three
 * hours at 8% an hour no longer hugs the top of a 0 to 100 box, and a fast drain still looks steep.
 */
internal fun BatteryChart.scale(): ClosedFloatingPointRange<Float> {
    val values = played.map { it.percent } + listOfNotNull(aheadTo, usualFrom, usualTo).map { it.percent }
    if (values.isEmpty()) return 0f..PERCENT.toFloat()
    val step = SCALE_STEP.toFloat()
    var top = (ceil((values.max() + SCALE_HEADROOM) / step) * step).coerceAtMost(PERCENT.toFloat())
    var bottom = (floor(values.min() / step) * step).coerceAtLeast(0f)
    if (top - bottom < MIN_SCALE_SPAN) {
        bottom = (top - MIN_SCALE_SPAN).coerceAtLeast(0f)
        top = (bottom + MIN_SCALE_SPAN).coerceAtMost(PERCENT.toFloat())
    }
    return bottom..top
}

/** The scale's ends are on tens, with at least 2 points over the highest value (its stroke stays clear of the top). */
private const val SCALE_STEP = 10
private const val SCALE_HEADROOM = 2f

/** The scale is never under 40 points tall: a steady 2% an hour does not look like a cliff. */
internal const val MIN_SCALE_SPAN = 40f

@Immutable
internal data class ChartPoint(val minute: Float, val percent: Float)

/** The chart is at least an hour wide: the first minutes of a play without a time left are not a cliff. */
internal const val MIN_CHART_MINUTES = 60f

/** The chart shows at most this far ahead of now. */
internal const val MAX_AHEAD_MINUTES = 12 * 60f

/** Past this many hours on the chart, its ticks mark every second hour (or third...): about this many at most. */
internal const val MAX_HOUR_TICKS = 12

/** The pace differs noticeably from the usual one from this share of it on. */
internal const val USUAL_DIFFERS_BY = 0.15

/**
 * The part from what the meter has ([snapshot], null before its first read), the battery
 * broadcast's [reading] (the level the clock card shows) and the wall clock [nowMillis]. Null:
 * nothing to show, not even a note: before the first read, or on a device that gives no figure
 * ([givesNoFigure]), where the card is the clock alone as with Show battery off. [driftNote]: the
 * once-ever minute of "Moves a little…". [starting]: the first minutes of a play are not shown
 * ([SteadyBatteryPace]), "Measuring battery use…" instead. [zoneOffset]: the wall clock's offset
 * from UTC at a time, for the chart's whole hours.
 */
internal fun inGameBatteryOf(
    snapshot: BatterySnapshot?,
    reading: BatteryReading?,
    nowMillis: Long,
    driftNote: Boolean,
    starting: Boolean = false,
    zoneOffset: (Long) -> Int = { TimeZone.getDefault().getOffset(it) },
): InGameBattery? {
    val noteAlone = if (driftNote) InGameNote.Drift else InGameNote.Measuring
    if (snapshot == null) return if (driftNote) InGameBattery(null, InGameNote.Drift) else null
    if (starting) return InGameBattery(null, noteAlone)
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
            zoneOffset = zoneOffset,
        )
    val first = history.firstOrNull()
    val playStart = minOf(first?.atMillis ?: nowMinute, nowMinute)
    return InGameBattery(
        figures =
            BatteryFigures(
                isCharging = charging,
                isLow = !charging && levelNow <= LOW_BATTERY_PERCENT,
                level = levelNow,
                left = left,
                isLeftExact = left != null && leftIsExact,
                endsAtMillis =
                    when {
                        left == null || !chart.aheadEnds -> null
                        leftIsExact -> nowMinute + left.minutes * MILLIS_PER_MINUTE
                        else -> estimate.minutesLeft?.let { endsAt(nowMillis, it) }
                    },
                pacePercent = pace?.let(::percentOf),
                usual = usual,
                chargingSinceMillis = if (charging) chargingSince(history) else null,
                playedMinutes = ((nowMinute - playStart) / MILLIS_PER_MINUTE).toInt(),
                usedPercent = first?.takeIf { !charging && history.none { it.isCharging } }?.let { (it.level - levelNow).coerceAtLeast(0) },
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

/**
 * When an estimate comes to, to the nearest [ENDS_AT_STEP_MINUTES] of the wall clock: a steady pace
 * gives a steady "empty at 11:35", where now plus a rounded time left moved on every minute.
 */
internal fun endsAt(
    nowMillis: Long,
    minutesLeft: Double,
): Long {
    val step = ENDS_AT_STEP_MINUTES * MILLIS_PER_MINUTE
    val at = nowMillis + (minutesLeft * MILLIS_PER_MINUTE).toLong()
    return Math.floorDiv(at + step / 2, step) * step
}

/** "empty at" moves in steps of 5 minutes. */
internal const val ENDS_AT_STEP_MINUTES = 5L

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
 * The play from its first point (on the minute) to where the line ahead ends: empty, full, or now
 * when there is no time left. The usual pace's line starts where the latest stretch without
 * charging did and is cut at the chart's right end.
 */
private fun chartOf(
    history: List<WallLevel>,
    nowMillis: Long,
    levelNow: Int,
    minutesAhead: Double?,
    charging: Boolean,
    usualPercentPerHour: Double?,
    zoneOffset: (Long) -> Int,
): BatteryChart {
    val first = history.firstOrNull()?.atMillis?.coerceAtMost(nowMillis) ?: nowMillis
    val start = first.floorDiv(MILLIS_PER_MINUTE) * MILLIS_PER_MINUTE
    fun minuteOf(millis: Long) = (millis - start).toDouble() / MILLIS_PER_MINUTE
    val nowAt = minuteOf(nowMillis)
    val ahead = minutesAhead?.takeIf { it > 0.0 }
    // Days ahead (a very low draw) would squeeze the play to a few pixels: the forecast stops at
    // MAX_AHEAD_MINUTES, on its way down, with no end time.
    val shownAhead = ahead?.coerceAtMost(MAX_AHEAD_MINUTES.toDouble())
    val aheadEnds = ahead != null && ahead <= MAX_AHEAD_MINUTES
    val width = maxOf(nowAt + (shownAhead ?: 0.0), MIN_CHART_MINUTES.toDouble())

    val played =
        (history.filter { it.atMillis < nowMillis }.map { minuteOf(it.atMillis) to it.level.toDouble() } + (nowAt to levelNow.toDouble()))
            .map { (m, p) -> point(m, p) }
            .distinct()
    val target = if (charging) PERCENT else 0.0
    val aheadTo =
        if (ahead == null || shownAhead == null) {
            null
        } else {
            point(nowAt + shownAhead, levelNow + (target - levelNow) * shownAhead / ahead)
        }
    val usual =
        usualPercentPerHour?.let { perHour ->
            // From the first point of the latest stretch without charging, at the usual pace.
            val from = history.asReversed().takeWhile { !it.isCharging }.lastOrNull()
            val fromAt = from?.let { minuteOf(it.atMillis) } ?: nowAt
            val fromLevel = from?.level?.toDouble() ?: levelNow.toDouble()
            point(fromAt, fromLevel) to cutAtEnd(fromAt, fromLevel, fromAt + fromLevel / perHour * MINUTES_PER_HOUR, 0.0, width)
        }
    return BatteryChart(
        startMillis = start,
        minutes = quarter(width),
        played = played,
        aheadTo = aheadTo,
        usualFrom = usual?.first,
        usualTo = usual?.second,
        hourMinutes = wholeHours(start, width, zoneOffset),
        aheadEnds = aheadEnds,
    )
}

/** The whole hours of the wall clock within [minutes] of [start], in minutes from it; every second hour (or more) on a long chart. */
private fun wholeHours(
    start: Long,
    minutes: Double,
    zoneOffset: (Long) -> Int,
): List<Float> {
    val local = start + zoneOffset(start)
    val firstHour = Math.floorDiv(local + HOUR_MILLIS - 1, HOUR_MILLIS) * HOUR_MILLIS - zoneOffset(start)
    val every = maxOf(1, ceil(minutes / MINUTES_PER_HOUR / MAX_HOUR_TICKS).toInt())
    return generateSequence(firstHour) { it + every * HOUR_MILLIS }
        .map { (it - start).toDouble() / MILLIS_PER_MINUTE }
        .takeWhile { it <= minutes }
        .map { quarter(it) }
        .toList()
}

/** The straight line's end, cut at the chart's right end [width]. */
private fun cutAtEnd(
    m0: Double,
    p0: Double,
    m1: Double,
    p1: Double,
    width: Double,
): ChartPoint {
    if (m1 <= width || m1 <= m0) return point(m1, p1)
    val t = (width - m0) / (m1 - m0)
    return point(width, p0 + (p1 - p0) * t)
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

