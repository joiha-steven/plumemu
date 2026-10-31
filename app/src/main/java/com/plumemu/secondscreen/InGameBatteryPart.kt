package com.plumemu.secondscreen

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.coreui.theme.InGameDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.home.BatteryReading
import com.plumemu.home.currentLocale
import com.plumemu.home.formatBattery
import com.plumemu.launcher.play.battery.BatteryReads
import com.plumemu.launcher.play.battery.BatteryTuning
import com.plumemu.launcher.play.battery.TimeLeft

/**
 * What the battery part shows, kept up to date without a timer of its own:
 * after the meter's reads ([afterRead], at most every [BatteryTuning.READ_EVERY_MS] once there are
 * figures; every read before, so the first figure shows as soon as there is one) and on the clock
 * card's minute tick or a new battery broadcast ([update]). [shown] changes only when what it shows
 * changed, so the part recomposes at most that often and the clock above it not at all. With the
 * screen off ([onScreen]) nothing is taken: the minute tick and the battery broadcast still come
 * while the CPU is awake, and each figure asks the system (Android's time to full while charging).
 */
internal class InGameBatteryFeed(
    private val reads: BatteryReads,
    private val wallClock: () -> Long,
    private val elapsedClock: () -> Long = SystemClock::elapsedRealtime,
    private val info: (String) -> Unit = { Log.i(SecondScreenHost.TAG, it) },
) {
    private val state = mutableStateOf<InGameBattery?>(null)
    private var reading: BatteryReading? = null
    private var driftNote = false
    private var refreshedAt = NEVER
    private var screenOn = true
    private var toldNoFigure = false

    /** Null: nothing yet (the clock card alone, no note). */
    val shown: State<InGameBattery?> get() = state

    fun update(
        reading: BatteryReading?,
        driftNote: Boolean,
    ) {
        this.reading = reading
        this.driftNote = driftNote
        if (screenOn) refresh()
    }

    /** The screen as the reads follow it (MeasureBatteryWhileShown): off, nothing is taken; on again, the figures at once. */
    fun onScreen(on: Boolean) {
        val cameOn = on && !screenOn
        screenOn = on
        if (cameOn) refresh()
    }

    fun afterRead() {
        val hasFigures = state.value?.figures != null
        if (hasFigures && refreshedAt != NEVER && elapsedClock() - refreshedAt < BatteryTuning.READ_EVERY_MS) return
        refresh()
    }

    private fun refresh() {
        refreshedAt = elapsedClock()
        val snapshot = reads.snapshot()
        if (!toldNoFigure && snapshot != null && snapshot.givesNoFigure) {
            toldNoFigure = true
            info("In-game battery: no figure after ${snapshot.readMs / MILLIS_PER_SECOND} s of reads (current and charge counter give no pace); the clock card shows alone")
        }
        val next = inGameBatteryOf(snapshot, reading, wallClock(), driftNote)
        if (next != state.value) state.value = next
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * The battery part under the clock card: the time
 * left with "empty at" on its baseline at the right (under it when both do not fit), the chart with
 * its hour labels, and the key at the bottom (or the drift note in its place). The chart takes what
 * height is left up to [InGameDimens.chartMaxHeight]. When less than [InGameDimens.chartMinHeight]
 * would be left, parts give way in this order until it fits: "empty at" when it would need a line of
 * its own (the time left says the same), the key (or the note: its words are also in Settings ›
 * Home screen › In game), the hour labels (their ticks stay). [formatTime] formats a time of day as
 * Android is set (12 or 24 h).
 */
@Composable
internal fun InGameBatteryPart(
    figures: BatteryFigures,
    dimens: InGameDimens,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val type = PlumTheme.type
    val line = dimens.partStyle(InGameDimens.lineRole, type.font, type.textSize, density).copy(color = InGameColors.partText)
    val leftStyle = dimens.partStyle(dimens.leftRole, type.font, type.textSize, density)
    val unitStyle = dimens.partStyle(dimens.unitRole, type.font, type.textSize, density)
    val lineColor = if (figures.isLow) InGameColors.partLow else InGameColors.partText
    // Full, or no counter to divide by: no time left, no "empty at", no gap.
    val left = figures.left
    val endText =
        figures.endsAtMillis?.takeIf { left != null }?.let {
            stringResource(if (figures.isCharging) R.string.in_game_battery_full_at else R.string.in_game_battery_empty_at, formatTime(it))
        }
    Layout(
        modifier = modifier,
        contents =
            listOf(
                {
                    if (left != null) {
                        val text = timeLeftText(left, figures.isCharging, figures.isLeftExact, leftStyle, unitStyle)
                        BasicText(text, Modifier.testTag(InGameBatteryTags.TIME_LEFT), style = leftStyle.copy(color = lineColor), maxLines = 1, softWrap = false)
                    }
                },
                { if (endText != null) BasicText(endText, Modifier.testTag(InGameBatteryTags.ENDS_AT), style = line, maxLines = 1, softWrap = false) },
                { Chart(figures.chart, dimens, lineColor) },
                { HourLabels(figures.chart.startMillis, line, formatTime) },
                {
                    if (figures.driftNote) {
                        Note(stringResource(R.string.in_game_drift_note), line)
                    } else {
                        Key(figures, dimens, line, formatTime)
                    }
                },
            ),
    ) { (heroes, ends, charts, labelsList, bottoms), constraints ->
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val hero = heroes.firstOrNull()?.measure(loose)
        val end = ends.firstOrNull()?.measure(loose)
        val labels = labelsList.first().measure(loose)
        val bottom = bottoms.first().measure(loose)
        // The head: the time left and "empty at" on one baseline (both here), or "empty at" on a line of its own.
        val beside = if (hero != null && end != null && hero.width + InGameDimens.headGap.roundToPx() + end.width <= width) hero to end else null
        val base = beside?.let { (heroPlaced, endPlaced) -> maxOf(heroPlaced[FirstBaseline], endPlaced[FirstBaseline]) } ?: 0
        val besideHeight = beside?.let { (h, e) -> maxOf(base - h[FirstBaseline] + h.height, base - e[FirstBaseline] + e.height) } ?: 0
        val headGap = if (hero != null) dimens.headToChartGap.roundToPx() else 0
        val labelsGap = InGameDimens.chartToLabelsGap.roundToPx()
        val bottomRoom = bottom.height + dimens.labelsToKeyGap.roundToPx()
        val minChart = dimens.chartMinHeight.roundToPx()

        fun headHeight(withEnd: Boolean): Int =
            when {
                hero == null -> 0
                beside != null -> besideHeight
                withEnd && end != null -> hero.height + end.height
                else -> hero.height
            }

        fun room(
            withEnd: Boolean,
            withBottom: Boolean,
            withLabels: Boolean,
        ): Int =
            constraints.maxHeight - headHeight(withEnd) - headGap -
                (if (withLabels) labelsGap + labels.height else 0) - (if (withBottom) bottomRoom else 0)

        // What stays, in the order things give way; the last keeps the chart alone under the head.
        val keeps =
            listOf(
                Keeps(end = true, bottom = true, labels = true),
                Keeps(end = false, bottom = true, labels = true),
                Keeps(end = false, bottom = false, labels = true),
                Keeps(end = false, bottom = false, labels = false),
            ).firstOrNull { room(it.end, it.bottom, it.labels) >= minChart } ?: Keeps(end = false, bottom = false, labels = false)
        val showsEnd = end != null && (beside != null || keeps.end)
        val chartHeight = room(keeps.end, keeps.bottom, keeps.labels).coerceIn(0, dimens.chartMaxHeight.roundToPx())
        val chart = charts.first().measure(Constraints.fixed(width, chartHeight))
        layout(width, constraints.maxHeight) {
            if (hero != null) {
                if (beside != null) {
                    val besideEnd = beside.second
                    hero.place(0, base - hero[FirstBaseline])
                    besideEnd.place(width - besideEnd.width, base - besideEnd[FirstBaseline])
                } else {
                    hero.place(0, 0)
                    if (showsEnd) end?.place(0, hero.height)
                }
            }
            val chartTop = headHeight(showsEnd) + headGap
            chart.place(0, chartTop)
            if (keeps.labels) labels.place(0, chartTop + chartHeight + labelsGap)
            if (keeps.bottom) bottom.place(0, constraints.maxHeight - bottom.height)
        }
    }
}

private class Keeps(
    val end: Boolean,
    val bottom: Boolean,
    val labels: Boolean,
)

/**
 * The numbers large and the words small: "About 4 h 30 min left", "1 h 03 to full". The words come
 * from whole sentences with placeholders (a duration inside "%1$s left"), so a translation orders them
 * its own way; each number goes in as its own mark first and is found again by that mark, so a
 * translation that puts `%2$s` before `%1$s` still gets each number in its place.
 */
@Composable
private fun timeLeftText(
    left: TimeLeft,
    charging: Boolean,
    exact: Boolean,
    leftStyle: TextStyle,
    unitStyle: TextStyle,
): AnnotatedString {
    val locale = currentLocale()
    val hours = left.minutes / MINUTES_PER_HOUR
    val minutes = left.minutes % MINUTES_PER_HOUR
    // Android's own time to full is exact: its minutes after the hours as "03", with no "min". The
    // counter's estimate keeps "min" even when rounding left it whole ("1 h 30 min to full").
    val clockStyle = charging && exact && hours > 0
    val numbers = mutableListOf<String>()
    fun mark(value: Int, pattern: String = "%d"): String {
        numbers += String.format(locale, pattern, value)
        return numberMark(numbers.size - 1).toString()
    }
    val duration = when {
        hours == 0 -> stringResource(R.string.in_game_battery_duration_minutes, mark(minutes))
        clockStyle -> stringResource(R.string.in_game_battery_duration_clock, mark(hours), mark(minutes, "%02d"))
        minutes == 0 -> stringResource(R.string.in_game_battery_duration_hours, mark(hours))
        else -> stringResource(R.string.in_game_battery_duration_hours_minutes, mark(hours), mark(minutes))
    }
    val sentence = when {
        charging && left.isApproximate -> R.string.in_game_battery_to_full_about
        charging -> R.string.in_game_battery_to_full
        left.isApproximate -> R.string.in_game_battery_left_about
        else -> R.string.in_game_battery_left
    }
    return styledNumbers(stringResource(sentence, duration), numbers, leftStyle.toSpanStyle(), unitStyle.toSpanStyle())
}

/**
 * The mark standing for the [index]th number while the battery sentence is formatted: one
 * private-use character per number (never in a translation), so the marks are found by index,
 * whatever order the translated sentence puts them in.
 */
internal fun numberMark(index: Int): Char = NUMBER_MARK_FIRST + index

private const val NUMBER_MARK_FIRST = '\uE000'

/** [text] with each [numberMark] replaced by its number of [numbers] in [numberSpan]; the rest in [wordSpan]. */
internal fun styledNumbers(text: String, numbers: List<String>, numberSpan: SpanStyle, wordSpan: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        val words = StringBuilder()
        fun flushWords() {
            if (words.isNotEmpty()) withStyle(wordSpan) { append(words.toString()) }
            words.clear()
        }
        for (ch in text) {
            val index = ch - NUMBER_MARK_FIRST
            if (index in numbers.indices) {
                flushWords()
                withStyle(numberSpan) { append(numbers[index]) }
            } else {
                words.append(ch)
            }
        }
        flushWords()
    }

/** The chart: hour ticks at the bottom (no grid), the usual pace dotted, the line ahead dashed, this play's line and its dot now. */
@Composable
private fun Chart(
    chart: BatteryChart,
    dimens: InGameDimens,
    lineColor: Color,
) {
    Canvas(Modifier.fillMaxWidth().testTag(InGameBatteryTags.CHART)) {
        val pad = InGameDimens.chartPad.toPx()
        fun x(minute: Float) = size.width * minute / CHART_MINUTES
        fun y(percent: Float) = pad + (size.height - 2 * pad) * (1f - percent / PERCENT)
        fun at(point: ChartPoint) = Offset(x(point.minute), y(point.percent))
        val tick = InGameDimens.tickLength.toPx()
        for (hour in 0..CHART_MINUTES / MINUTES_PER_HOUR) {
            val tx = x((hour * MINUTES_PER_HOUR).toFloat())
            drawLine(InGameColors.tick, Offset(tx, size.height), Offset(tx, size.height - tick), InGameDimens.tickStroke.toPx())
        }
        val from = chart.usualFrom
        val to = chart.usualTo
        if (from != null && to != null) {
            val dots = PathEffect.dashPathEffect(floatArrayOf(DOT_LENGTH, InGameDimens.usualDotGap.toPx()))
            drawLine(InGameColors.usual, at(from), at(to), dimens.usualStroke.toPx(), StrokeCap.Round, dots)
        }
        val now = chart.played.lastOrNull() ?: return@Canvas
        chart.aheadTo?.let { end ->
            val dashes = PathEffect.dashPathEffect(floatArrayOf(InGameDimens.aheadDash.toPx(), InGameDimens.aheadGap.toPx()))
            drawLine(InGameColors.ahead, at(now), at(end), dimens.aheadStroke.toPx(), StrokeCap.Round, dashes)
        }
        drawPlayed(chart.played.map(::at), lineColor, dimens.playedStroke.toPx())
        drawCircle(lineColor, dimens.nowDotRadius.toPx(), at(now))
    }
}

private fun DrawScope.drawPlayed(
    points: List<Offset>,
    color: Color,
    stroke: Float,
) {
    if (points.size < 2) return
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
    drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * The four hours' labels under their ticks: the first from the left edge, the last to the right one,
 * the others centred. When they would come nearer than [InGameDimens.labelGap] (12-hour times at the
 * largest text sizes) the two in the middle give way; their ticks stay.
 */
@Composable
private fun HourLabels(
    startMillis: Long,
    style: TextStyle,
    formatTime: (Long) -> String,
) {
    val hours = CHART_MINUTES / MINUTES_PER_HOUR
    Layout(
        modifier = Modifier.testTag(InGameBatteryTags.LABELS),
        content = {
            for (hour in 0..hours) {
                BasicText(formatTime(startMillis + hour * HOUR_MILLIS), style = style, maxLines = 1, softWrap = false)
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val xs =
            placeables.mapIndexed { i, label ->
                when (i) {
                    0 -> 0
                    hours -> width - label.width
                    else -> (width * i / hours - label.width / 2).coerceIn(0, width - label.width)
                }
            }
        val gap = InGameDimens.labelGap.roundToPx()
        val roomy = (0 until hours).all { i -> xs[i] + placeables[i].width + gap <= xs[i + 1] }
        layout(width, placeables.maxOf { it.height }) {
            placeables.forEachIndexed { i, label ->
                if (roomy || i == 0 || i == hours) label.place(xs[i], 0)
            }
        }
    }
}

/** "— 48% an hour   ··· usually 40%"; "as usual" without a swatch; charging "— +40% an hour   since 21:20". */
@Composable
private fun Key(
    figures: BatteryFigures,
    dimens: InGameDimens,
    style: TextStyle,
    formatTime: (Long) -> String,
) {
    val locale = currentLocale()
    val pace =
        figures.pacePercent?.let {
            val percent = formatBattery(it, locale)
            stringResource(if (figures.isCharging) R.string.in_game_battery_charging_pace else R.string.in_game_battery_pace, percent)
        }
    val second: Pair<Swatch?, String>? =
        when {
            figures.isCharging -> figures.chargingSinceMillis?.let { null to stringResource(R.string.in_game_battery_charging_since, formatTime(it)) }
            else ->
                when (val usual = figures.usual) {
                    UsualPace.None -> null
                    UsualPace.AsUsual -> null to stringResource(R.string.in_game_battery_as_usual)
                    is UsualPace.Differs -> Swatch.Usual to stringResource(R.string.in_game_battery_usual, formatBattery(usual.percent, locale))
                }
        }
    val lineColor = if (figures.isLow) InGameColors.partLow else InGameColors.partText
    Row(Modifier.testTag(InGameBatteryTags.KEY), verticalAlignment = Alignment.CenterVertically) {
        if (pace != null) KeyItem(Swatch.Played, pace, style, dimens, lineColor)
        if (pace != null && second != null) Spacer(Modifier.width(dimens.keyItemGap))
        if (second != null) KeyItem(second.first, second.second, style, dimens, lineColor)
    }
}

private enum class Swatch { Played, Usual }

@Composable
private fun KeyItem(
    swatch: Swatch?,
    text: String,
    style: TextStyle,
    dimens: InGameDimens,
    lineColor: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (swatch != null) {
            SwatchLine(swatch, dimens, lineColor)
            Spacer(Modifier.width(InGameDimens.keySwatchGap))
        }
        BasicText(text, style = style, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun SwatchLine(
    swatch: Swatch,
    dimens: InGameDimens,
    lineColor: Color,
) {
    val stroke: Dp = if (swatch == Swatch.Usual) dimens.usualStroke else dimens.aheadStroke
    Canvas(Modifier.size(InGameDimens.keySwatchWidth, stroke * 2)) {
        val w = stroke.toPx()
        val mid = size.height / 2
        // Inset by half the stroke: the round caps stay inside the swatch.
        val start = Offset(w / 2, mid)
        val end = Offset(size.width - w / 2, mid)
        if (swatch == Swatch.Usual) {
            val dots = PathEffect.dashPathEffect(floatArrayOf(DOT_LENGTH, InGameDimens.usualDotGap.toPx() * SWATCH_DOT_GAP))
            drawLine(InGameColors.usual, start, end, w, StrokeCap.Round, dots)
        } else {
            drawLine(lineColor, start, end, w, StrokeCap.Round)
        }
    }
}

/** A note at the bottom ("Measuring battery use…", "Moves a little every minute…"), centred. */
@Composable
internal fun Note(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text,
        modifier.fillMaxWidth().testTag(InGameBatteryTags.NOTE),
        style = style.copy(textAlign = TextAlign.Center),
        maxLines = NOTE_MAX_LINES,
    )
}

internal object InGameBatteryTags {
    /** The frame the clock card and the battery part fill (it shifts with them). */
    const val FRAME = "in_game_battery_frame"
    const val TIME_LEFT = "in_game_battery_time_left"
    const val ENDS_AT = "in_game_battery_ends_at"
    const val CHART = "in_game_battery_chart"
    const val LABELS = "in_game_battery_labels"
    const val KEY = "in_game_battery_key"
    const val NOTE = "in_game_battery_note"
}

private const val MINUTES_PER_HOUR = 60
private const val HOUR_MILLIS = 3_600_000L
private const val PERCENT = 100f

/** A dot: a dash this short with round caps draws a circle as wide as the stroke. */
private const val DOT_LENGTH = 0.1f

/** The swatch's dots a little closer than the chart's, so three show in 22 dp. */
private const val SWATCH_DOT_GAP = 0.85f

/** A note wraps to two lines at the largest text sizes rather than being cut. */
private const val NOTE_MAX_LINES = 2

