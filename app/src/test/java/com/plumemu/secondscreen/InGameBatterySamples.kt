package com.plumemu.secondscreen

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.TimeLeft
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The in-game battery part's states as the mockups draw them,
 * as plain values: the clock text, the battery line, the part, the drift point. Times in UTC.
 */
internal data class InGameBatterySample(
    val name: String,
    val minute: Int,
    val reading: BatteryReading?,
    val wifi: Boolean,
    val part: InGameBattery?,
    val shift: DpOffset = DpOffset.Zero,
)

internal object InGameBatterySamples {
    /** 22 October 2026, 21:00 UTC. */
    val NINE = 29_878_380L * 60_000L

    fun at(minute: Int) = NINE + minute * 60_000L

    fun format(
        pattern: String,
        millis: Long,
    ): String = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    /** Android's 24-hour format, and its 12-hour one (en-US). */
    val time24: (Long) -> String = { format("HH:mm", it) }
    val time12: (Long) -> String = { format("h:mm a", it) }

    private fun points(vararg p: Pair<Int, Int>) = p.map { (m, l) -> ChartPoint(m.toFloat(), l.toFloat()) }

    /**
     * The part's figures from points in minutes after 21:00 (UTC), as the model makes them: the chart
     * from the play's first point to [aheadTo] (at least an hour), the usual line cut at its end.
     */
    private fun figures(
        left: TimeLeft?,
        nowMinute: Int,
        played: List<ChartPoint>,
        aheadTo: ChartPoint?,
        pace: Int?,
        usual: UsualPace = UsualPace.None,
        usualLine: Pair<ChartPoint, ChartPoint>? = null,
        charging: Boolean = false,
        low: Boolean = false,
        since: Long? = null,
        driftNote: Boolean = false,
        exact: Boolean = false,
    ): BatteryFigures {
        val start = played.first().minute
        val width = maxOf(aheadTo?.minute ?: 0f, nowMinute.toFloat(), start + MIN_CHART_MINUTES) - start
        fun shifted(point: ChartPoint) = ChartPoint(point.minute - start, point.percent)
        val usualTo =
            usualLine?.let { (from, to) ->
                val t = ((start + width - from.minute) / (to.minute - from.minute)).coerceAtMost(1f)
                ChartPoint(from.minute + (to.minute - from.minute) * t, from.percent + (to.percent - from.percent) * t)
            }
        val firstHour = (start.toInt() / 60 + if (start.toInt() % 60 == 0) 0 else 1) * 60
        return BatteryFigures(
            isCharging = charging,
            isLow = low,
            level = played.last().percent.toInt(),
            left = left,
            isLeftExact = exact,
            endsAtMillis = left?.let { at(nowMinute + it.minutes) },
            pacePercent = pace,
            usual = usual,
            chargingSinceMillis = since,
            playedMinutes = nowMinute - start.toInt(),
            usedPercent = if (charging) null else (played.first().percent - played.last().percent).toInt(),
            chart =
                BatteryChart(
                    startMillis = at(start.toInt()),
                    minutes = width,
                    played = played.map(::shifted),
                    aheadTo = aheadTo?.let(::shifted),
                    usualFrom = usualLine?.first?.let(::shifted),
                    usualTo = usualTo?.let(::shifted),
                    hourMinutes = (firstHour..(start + width).toInt() step 60).map { it - start },
                    aheadEnds = true,
                ),
            driftNote = driftNote,
        )
    }

    /** 1. A heavy game 40 minutes in, faster than usual: the usual pace dotted. */
    val heavy =
        InGameBatterySample(
            "heavy", 47, BatteryReading(38, false), wifi = true,
            InGameBattery(
                figures(
                    TimeLeft(45, false), 47, points(7 to 70, 20 to 60, 33 to 49, 47 to 38), ChartPoint(92f, 0f), pace = 48,
                    usual = UsualPace.Differs(40), usualLine = ChartPoint(7f, 70f) to ChartPoint(112f, 0f),
                ),
                null,
            ),
            shift = DpOffset((-4).dp, (-8).dp),
        )

    /** 2. A light game at its usual pace, no Wi-Fi: "as usual", no dotted line. */
    val light =
        InGameBatterySample(
            "light", 47, BatteryReading(61, false), wifi = false,
            InGameBattery(
                figures(TimeLeft(270, false), 47, points(7 to 70, 20 to 67, 33 to 64, 47 to 61), ChartPoint(317f, 0f), pace = 14, usual = UsualPace.AsUsual),
                null,
            ),
            shift = DpOffset(2.dp, 4.dp),
        )

    /** 3. Charging: the line climbs to full; Android's own time to full. */
    val charging =
        InGameBatterySample(
            "charging", 47, BatteryReading(58, true), wifi = true,
            InGameBattery(
                figures(
                    TimeLeft(63, false), 47, points(20 to 40, 33 to 49, 47 to 58), ChartPoint(110f, 100f), pace = 40, charging = true,
                    since = at(20), exact = true,
                ),
                null,
            ),
            shift = DpOffset(4.dp, 12.dp),
        )

    /** 4. 15% and below, not charging: the time left and the line in the low red. A play from 22:47. */
    val low =
        InGameBatterySample(
            "low", 167, BatteryReading(12, false), wifi = true,
            InGameBattery(
                figures(
                    TimeLeft(15, false), 167, points(107 to 70, 130 to 52, 150 to 30, 167 to 12), ChartPoint(182f, 0f), pace = 48,
                    usual = UsualPace.Differs(40), usualLine = ChartPoint(107f, 70f) to ChartPoint(212f, 0f), low = true,
                ),
                null,
            ),
            shift = DpOffset((-2).dp, 8.dp),
        )

    /** 5. A game played for the first time, its first minute: the clock card alone, "Measuring battery use…". */
    val measuring = InGameBatterySample("measuring", 8, BatteryReading(70, false), wifi = true, InGameBattery(null, InGameNote.Measuring))

    /** 6. The very first time the card shows, for a minute: why it moves. */
    val drift =
        InGameBatterySample("drift", 8, BatteryReading(70, false), wifi = true, InGameBattery(null, InGameNote.Drift), shift = DpOffset(4.dp, (-4).dp))

    /** The first time ever with a game measured before: the note takes the key's place for its minute. */
    val driftWithFigures =
        InGameBatterySample(
            "drift_with_figures", 8, BatteryReading(70, false), wifi = true,
            InGameBattery(figures(TimeLeft(105, true), 8, points(7 to 70, 8 to 70), ChartPoint(113f, 0f), pace = 40, usual = UsualPace.AsUsual, driftNote = true), null),
        )

    /** Rounding hid minutes: "About 4 h 15 min left". */
    val about =
        InGameBatterySample(
            "about", 47, BatteryReading(61, false), wifi = false,
            InGameBattery(
                figures(TimeLeft(255, true), 47, points(7 to 70, 47 to 61), ChartPoint(302f, 0f), pace = 14, usual = UsualPace.Differs(9),
                    usualLine = ChartPoint(7f, 70f) to ChartPoint(473f, 0f)),
                null,
            ),
        )

    val mockups = listOf(heavy, light, charging, low, measuring, drift)
    val extras = listOf(driftWithFigures, about)

    /**
     * The widest words: "About 4 h 45 min left" with "empty at about 12:58 PM" (12-hour), "Played 9:59",
     * "Used 100%", "100% an hour" and "usually 100%"; and charging "+100% an hour", "since 12:58 PM".
     */
    val widest =
        listOf(
            InGameBatterySample(
                "widest", 47, BatteryReading(100, false), wifi = true,
                InGameBattery(
                    figures(TimeLeft(285, true), 47, points(7 to 100, 47 to 88), ChartPoint(332f, 0f), pace = 100, usual = UsualPace.Differs(100),
                        usualLine = ChartPoint(7f, 100f) to ChartPoint(67f, 0f)).copy(endsAtMillis = 29_877_898L * 60_000L, playedMinutes = 599, usedPercent = 100),
                    null,
                ),
            ),
            InGameBatterySample(
                "widest_charging", 47, BatteryReading(58, true), wifi = true,
                InGameBattery(
                    figures(TimeLeft(83, false), 47, points(20 to 40, 47 to 58), ChartPoint(130f, 100f), pace = 100, charging = true,
                        since = 29_877_898L * 60_000L, exact = true).copy(endsAtMillis = 29_877_898L * 60_000L),
                    null,
                ),
            ),
            driftWithFigures,
            measuring,
            drift,
        )
}
