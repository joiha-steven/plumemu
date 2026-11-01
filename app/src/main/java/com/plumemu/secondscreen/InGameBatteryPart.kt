package com.plumemu.secondscreen

import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.MultiContentMeasurePolicy
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
    private val steady = SteadyBatteryPace()

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
        val levelNow = reading?.percent ?: snapshot?.levels?.lastOrNull()?.level
        // A device that gives no figure keeps its own answer (the clock card alone), not "Measuring…".
        val shown = if (snapshot?.givesNoFigure == true) SteadyBatteryPace.Steady.Figures(snapshot) else steady.steady(snapshot, levelNow)
        val next =
            when (shown) {
                SteadyBatteryPace.Steady.Starting -> inGameBatteryOf(snapshot, reading, wallClock(), driftNote, starting = true)
                is SteadyBatteryPace.Steady.Figures -> inGameBatteryOf(shown.snapshot, reading, wallClock(), driftNote)
            }
        if (next != state.value) state.value = next
    }

    private companion object {
        const val NEVER = Long.MIN_VALUE
        const val MILLIS_PER_SECOND = 1_000L
    }
}

/**
 * The battery part under the clock card: the head (the level ring, and beside it the time left with
 * "empty at about 23:15" under it), the chart of this play from its start to empty or full with
 * "Started 21:07" and "Empty 23:15" under its ends, the facts row ("Played 0:44 · Used 6% · 8% an
 * hour · as usual"), or the drift note in their place for its minute. The chart takes what height is
 * left up to [InGameDimens.chartMaxHeight]. When less than [InGameDimens.chartMinHeight] would be
 * left, parts give way in this order until it fits: "empty at" (the time left and the chart's
 * "Empty 23:15" say the same), the drift note (its words are also in Settings ›
 * Second screen › In game), the facts, the chart's labels (their ticks stay). The ring gives way when the head is too wide for it;
 * [onRingShown] says whether it shows, so the clock card can carry the level when it does not.
 * [formatTime] formats a time of day as Android is set (12 or 24 h).
 */
@Composable
internal fun InGameBatteryPart(
    figures: BatteryFigures,
    dimens: InGameDimens,
    formatTime: (Long) -> String,
    modifier: Modifier = Modifier,
    onRingShown: (Boolean) -> Unit = {},
) {
    val density = LocalDensity.current
    val type = PlumTheme.type
    val line = dimens.partStyle(InGameDimens.lineRole, type.font, type.textSize, density).copy(color = InGameColors.partText)
    val leftStyle = dimens.partStyle(dimens.leftRole, type.font, type.textSize, density)
    val unitStyle = dimens.partStyle(dimens.unitRole, type.font, type.textSize, density)
    val lineColor = if (figures.isLow) InGameColors.partLow else InGameColors.partText
    // Full, or no counter to divide by: no time left, no "empty at".
    val left = figures.left
    val endsAt = figures.endsAtMillis?.takeIf { left != null }
    val endText =
        endsAt?.let {
            val words =
                when {
                    figures.isCharging && figures.isLeftExact -> R.string.in_game_battery_full_at
                    figures.isCharging -> R.string.in_game_battery_full_at_about
                    else -> R.string.in_game_battery_empty_at_about
                }
            stringResource(words, formatTime(it))
        }
    Layout(
        modifier = modifier,
        contents =
            listOf(
                {
                    val ring = if (figures.isLow) InGameColors.batteryLow else InGameColors.ahead
                    LevelRing(figures.level, dimens, line, ring, if (figures.isLow) InGameColors.batteryLow else InGameColors.battery)
                },
                {
                    if (left != null) {
                        val text = timeLeftText(left, figures.isCharging, leftStyle, unitStyle)
                        BasicText(text, Modifier.testTag(InGameBatteryTags.TIME_LEFT), style = leftStyle.copy(color = lineColor), maxLines = 1, softWrap = false)
                    }
                },
                { if (endText != null) BasicText(endText, Modifier.testTag(InGameBatteryTags.ENDS_AT), style = line, maxLines = 1, softWrap = false) },
                { InGameBatteryChart(figures.chart, dimens, lineColor) },
                // The chart's ends repeat the time left and the facts: a step dimmer, the line ahead's colour.
                { ChartLabels(figures.chart.startMillis, endsAt, figures.isCharging, line.copy(color = InGameColors.ahead), formatTime) },
                // For its one minute the drift note takes the facts' place.
                { if (!figures.driftNote) Facts(figures, line, formatTime) },
                { if (figures.driftNote) Note(stringResource(R.string.in_game_drift_note), line) },
            ),
        measurePolicy = batteryPartMeasure(dimens, onRingShown),
    )
}

/** The part's contents in [InGameBatteryPart]'s order. */
private enum class PartSlot { Ring, TimeLeft, EndsAt, Chart, Labels, Facts, Note }

/** What stays when the chart needs room, in the order things give way. */
private data class Keeps(
    val end: Boolean,
    val note: Boolean,
    val labels: Boolean,
    val facts: Boolean,
)

private val keepsInOrder =
    listOf(
        Keeps(end = true, note = true, labels = true, facts = true),
        Keeps(end = false, note = true, labels = true, facts = true),
        Keeps(end = false, note = false, labels = true, facts = true),
        Keeps(end = false, note = false, labels = true, facts = false),
        Keeps(end = false, note = false, labels = false, facts = false),
    )

private fun batteryPartMeasure(
    dimens: InGameDimens,
    onRingShown: (Boolean) -> Unit,
) =
    MultiContentMeasurePolicy { parts, constraints ->
        fun slot(slot: PartSlot) = parts[slot.ordinal].firstOrNull()
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val ring = slot(PartSlot.Ring)?.measure(loose)
        val hero = slot(PartSlot.TimeLeft)?.measure(loose)
        val end = slot(PartSlot.EndsAt)?.measure(loose)
        val labels = slot(PartSlot.Labels)?.measure(loose)
        val facts = slot(PartSlot.Facts)?.measure(loose)?.takeIf { it.width > 0 }
        val note = slot(PartSlot.Note)?.measure(loose)
        // The ring only where the time left and "empty at" still fit beside it.
        val ringGap = dimens.ringGap.roundToPx()
        val textWidth = maxOf(hero?.width ?: 0, end?.width ?: 0)
        val shownRing = ring?.takeIf { it.width + (if (textWidth > 0) ringGap + textWidth else 0) <= width }
        val textX = shownRing?.let { it.width + ringGap } ?: 0
        onRingShown(shownRing != null)

        fun textHeight(withEnd: Boolean) = (hero?.height ?: 0) + (if (withEnd && end != null) end.height else 0)
        fun headHeight(withEnd: Boolean) = maxOf(shownRing?.height ?: 0, textHeight(withEnd))
        val headGap = if (headHeight(true) > 0) dimens.headToChartGap.roundToPx() else 0
        val rowGap = dimens.labelsToKeyGap.roundToPx()
        val labelsRoom = labels?.let { it.height + InGameDimens.chartToLabelsGap.roundToPx() } ?: 0
        fun room(keeps: Keeps): Int =
            constraints.maxHeight - headHeight(keeps.end) - headGap -
                (if (keeps.labels) labelsRoom else 0) -
                (if (keeps.facts && facts != null) rowGap + facts.height else 0) -
                (if (keeps.note && note != null) rowGap + note.height else 0)
        val minChart = dimens.chartMinHeight.roundToPx()
        val first = keepsInOrder.firstOrNull { room(it) >= minChart } ?: keepsInOrder.last()
        // "empty at" stays wherever it costs no height (beside a ring taller than both lines).
        val keeps = if (headHeight(true) == headHeight(false)) first.copy(end = true) else first
        val chartHeight = room(keeps).coerceIn(0, dimens.chartMaxHeight.roundToPx())
        // Still no room once everything has given way (a very short window): no chart rather than one
        // squeezed to nothing, and no labels for it.
        val hasChart = chartHeight >= minChart
        val chart = slot(PartSlot.Chart)!!.measure(Constraints.fixed(width, chartHeight))
        layout(width, constraints.maxHeight) {
            val head = headHeight(keeps.end)
            shownRing?.place(0, (head - shownRing.height) / 2)
            val textTop = (head - textHeight(keeps.end)) / 2
            hero?.place(textX, textTop)
            if (keeps.end) end?.place(textX, textTop + (hero?.height ?: 0))
            val chartTop = head + headGap
            if (hasChart) chart.place(0, chartTop)
            var below = chartTop + chartHeight
            if (hasChart && keeps.labels && labels != null) {
                labels.place(0, below + InGameDimens.chartToLabelsGap.roundToPx())
                below += labelsRoom
            }
            if (keeps.facts && facts != null) facts.place((width - facts.width) / 2, below + rowGap)
            if (keeps.note && note != null) note.place(0, constraints.maxHeight - note.height)
        }
    }

/**
 * The numbers large and the words small: "About 4 h 30 min left", "1 h 3 min to full". The words come
 * from whole sentences with placeholders (a duration inside "%1$s left"), so a translation orders them
 * its own way; each number goes in as its own mark first and is found again by that mark, so a
 * translation that puts `%2$s` before `%1$s` still gets each number in its place.
 */
@Composable
private fun timeLeftText(
    left: TimeLeft,
    charging: Boolean,
    leftStyle: TextStyle,
    unitStyle: TextStyle,
): AnnotatedString {
    val locale = currentLocale()
    val hours = left.minutes / MINUTES_PER_HOUR
    val minutes = left.minutes % MINUTES_PER_HOUR
    val numbers = mutableListOf<String>()
    fun mark(value: Int): String {
        numbers += String.format(locale, "%d", value)
        return numberMark(numbers.size - 1).toString()
    }
    val duration = when {
        hours == 0 -> stringResource(R.string.in_game_battery_duration_minutes, mark(minutes))
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

/**
 * The level the clock card shows, as a thin ring round its number: the track in [InGameColors.tick],
 * the level in [color] (the line ahead's grey, or the low red), the number in [numberColor], the clock card's battery
 * colour (it repeats that line, so it is no brighter). A number wider than the ring's inside makes
 * the ring larger.
 */
@Composable
private fun LevelRing(
    level: Int,
    dimens: InGameDimens,
    style: TextStyle,
    color: Color,
    numberColor: Color,
) {
    val text = formatBattery(level, currentLocale())
    Layout(
        modifier =
            Modifier.testTag(InGameBatteryTags.RING).drawBehind {
                val stroke = dimens.ringStroke.toPx()
                val radius = size.minDimension / 2 - stroke / 2
                val arc = Size(radius * 2, radius * 2)
                val corner = Offset(size.width / 2 - radius, size.height / 2 - radius)
                val ring = Stroke(stroke, cap = StrokeCap.Round)
                drawArc(InGameColors.tick, 0f, FULL_TURN, false, corner, arc, style = ring)
                drawArc(color, RING_START, FULL_TURN * level.coerceIn(0, PERCENT) / PERCENT, false, corner, arc, style = ring)
            },
        content = { BasicText(text, style = style.copy(color = numberColor), maxLines = 1, softWrap = false) },
    ) { measurables, _ ->
        val number = measurables.first().measure(Constraints())
        val inside = number.width + 2 * (dimens.ringStroke.roundToPx() + InGameDimens.ringPad.roundToPx())
        val side = maxOf(2 * dimens.ringRadius.roundToPx() + dimens.ringStroke.roundToPx(), inside)
        layout(side, side) { number.place((side - number.width) / 2, (side - number.height) / 2) }
    }
}

/**
 * The facts under the chart, one centred row: "Played 0:44 · Used 6% · 8% an hour · as usual" (the
 * usual pace "usually 9%" with the chart's dotted line), charging "Played 0:44 · +40% an hour · since
 * 21:20". Where the row is too wide, "Played" gives way first, then "Used", then the usual pace.
 */
@Composable
private fun Facts(
    figures: BatteryFigures,
    style: TextStyle,
    formatTime: (Long) -> String,
) {
    val locale = currentLocale()
    val played =
        stringResource(
            R.string.in_game_battery_played,
            String.format(locale, "%d:%02d", figures.playedMinutes / MINUTES_PER_HOUR, figures.playedMinutes % MINUTES_PER_HOUR),
        )
    val used = figures.usedPercent?.let { stringResource(R.string.in_game_battery_used, formatBattery(it, locale)) }
    val pace =
        figures.pacePercent?.let {
            stringResource(if (figures.isCharging) R.string.in_game_battery_charging_pace else R.string.in_game_battery_pace, formatBattery(it, locale))
        }
    val last: String? =
        when {
            pace == null -> null
            figures.isCharging -> figures.chargingSinceMillis?.let { stringResource(R.string.in_game_battery_charging_since, formatTime(it)) }
            else ->
                when (val usual = figures.usual) {
                    UsualPace.None -> null
                    UsualPace.AsUsual -> stringResource(R.string.in_game_battery_as_usual)
                    is UsualPace.Differs -> stringResource(R.string.in_game_battery_usual, formatBattery(usual.percent, locale))
                }
        }
    // In the order they give way.
    val pieces = listOfNotNull(played, used, last, pace)
    val shown = listOfNotNull(played, used, pace, last)
    val separator = stringResource(R.string.second_screen_separator).trim()
    Layout(
        modifier = Modifier.testTag(InGameBatteryTags.FACTS),
        content = {
            for (piece in shown) BasicText(piece, style = style, maxLines = 1, softWrap = false)
            repeat(shown.size - 1) { BasicText(separator, style = style.copy(color = InGameColors.usual), maxLines = 1, softWrap = false) }
        },
    ) { measurables, constraints ->
        val texts = measurables.take(shown.size).map { it.measure(Constraints()) }
        val dots = measurables.drop(shown.size).map { it.measure(Constraints()) }
        val gap = InGameDimens.factGap.roundToPx()
        val dot = dots.firstOrNull()?.width ?: 0
        fun widthOf(kept: List<Int>) = kept.sumOf { texts[it].width } + (kept.size - 1).coerceAtLeast(0) * (2 * gap + dot)
        var kept = shown.indices.toList()
        for (dropped in pieces.dropLast(1)) {
            if (widthOf(kept) <= constraints.maxWidth) break
            kept = kept - shown.indexOf(dropped)
        }
        val height = texts.maxOfOrNull { it.height } ?: 0
        layout(widthOf(kept).coerceAtMost(constraints.maxWidth), height) {
            var x = 0
            kept.forEachIndexed { i, index ->
                if (i > 0) {
                    dots[i - 1].place(x + gap, 0)
                    x += 2 * gap + dot
                }
                texts[index].place(x, 0)
                x += texts[index].width
            }
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
    const val RING = "in_game_battery_ring"
    const val FACTS = "in_game_battery_facts"
    const val NOTE = "in_game_battery_note"
}

private const val MINUTES_PER_HOUR = 60
private const val PERCENT = 100

/** The ring: a whole turn, its level drawn from the top clockwise. */
private const val FULL_TURN = 360f
private const val RING_START = -90f

/** A note wraps to two lines at the largest text sizes rather than being cut. */
private const val NOTE_MAX_LINES = 2
