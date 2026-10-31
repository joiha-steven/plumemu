package com.plumemu.battery

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.plumemu.R
import com.plumemu.home.currentLocale
import com.plumemu.home.formatBattery
import com.plumemu.home.BatteryReading
import com.plumemu.home.LocalBatteryReading
import com.plumemu.home.rememberCountFormat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filterNotNull

/** Test tags of the picking line. */
internal object PickingBatteryTags {
    const val LINE = "picking_battery_line"
    const val TEXT = "picking_battery_text"
    const val GLYPH = "picking_battery_glyph"
}

/**
 * The picking line: a battery glyph at the
 * text's size showing the level now, then "2 h on this charge · 30% an hour" ("30% an hour" while
 * charging), in [style] (the place's own fact role). The level and charging are the screen's one
 * reading ([LocalBatteryReading]: no receiver of its own, so the line comes in the frame its pace
 * does); until the screen has one, or with Show battery off (the screen gives none), nothing.
 *
 * [pace]'s figures are those of when it was read; once the reading's level or charging differs from
 * the one the line first showed with, they are worked out again ([rememberPaceNow]), so a game rested
 * on through a charge does not keep the time of the charge before.
 *
 * The text wraps to [maxLines] rather than being cut. Where the whole sentence does not fit the room
 * it is given (those lines, and the height left: beside a cover at the largest sizes only one line),
 * the words "on this charge" go first ("About 2 h 45 min · 30% an hour"), never a number; where not
 * even that fits, the line is not shown at all rather than cut or shown in part. [topGap] above it
 * (only when it shows).
 */
@Composable
internal fun PickingBatteryLine(
    pace: BatteryPace,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = MAX_LINES,
    topGap: Dp = 0.dp,
) {
    val readings = LocalBatteryReading.current
    val reading = readings.value ?: return
    val now = rememberPaceNow(pace, readings) ?: return
    val parts = pickingBatteryParts(now, reading.isCharging) ?: return
    val whole = pickingBatteryText(parts, withWords = true)
    val short = pickingBatteryText(parts, withWords = false)
    val measurer = rememberTextMeasurer(cacheSize = 0)
    val density = LocalDensity.current
    // What the Row gives the glyph and the gap after it, to the pixel, so the text is measured at its own width.
    val glyphRoom = with(density) { GLYPH_WIDTH.dpOf(style.fontSize, density).roundToPx() + GLYPH_GAP.dpOf(style.fontSize, density).roundToPx() }
    BoxWithConstraints(modifier) {
        val gap = with(density) { topGap.roundToPx() }
        val height = if (constraints.hasBoundedHeight) (constraints.maxHeight - gap).coerceAtLeast(0) else Constraints.Infinity
        val room = Constraints(maxWidth = (constraints.maxWidth - glyphRoom).coerceAtLeast(0), maxHeight = height)
        fun fits(text: String) = !measurer.measure(text, style, overflow = TextOverflow.Ellipsis, maxLines = maxLines, constraints = room).hasVisualOverflow
        val text =
            when {
                fits(whole) -> whole
                short != whole && fits(short) -> short
                else -> null
            }
        if (text != null) {
            Row(Modifier.padding(top = topGap).testTag(PickingBatteryTags.LINE), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.Start) {
                BatteryLevelGlyph(reading.percent, style.color, style.fontSize, style.lineHeight)
                Spacer(Modifier.width(GLYPH_GAP.dpOf(style.fontSize, density)))
                BasicText(
                    text = text,
                    style = style,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).testTag(PickingBatteryTags.TEXT),
                )
            }
        }
    }
}

/**
 * [pace], worked out again ([LocalBatteryPaceNow], off the main thread) each time the [readings]' level
 * or charging changes from the one the line was first shown with: keyed on those two, not on every
 * broadcast (the voltage and temperature change them too), and never on the first frame (the figures
 * were just read). A change while a read runs cancels it for the newer one. Null when the battery no
 * longer tells anything. Without a reader or a draw ([BatteryPace.usualDrawMicroamps]) [pace] as given.
 * A read that throws is logged and the figures shown are kept.
 */
@Composable
private fun rememberPaceNow(
    pace: BatteryPace,
    readings: State<BatteryReading?>,
): BatteryPace? {
    val shown = remember(pace) { mutableStateOf<BatteryPace?>(pace) }
    val reader = LocalBatteryPaceNow.current
    val draw = pace.usualDrawMicroamps
    if (reader != null && draw != null) {
        val shownWith = remember(pace) { readings.value?.let(::paceKey) }
        LaunchedEffect(pace, reader) {
            snapshotFlow { readings.value?.let(::paceKey) }
                .filterNotNull()
                .dropWhile { it == shownWith }
                .distinctUntilChanged()
                .collectLatest { key ->
                    shown.value =
                        try {
                            reader.of(draw)
                        } catch (e: Exception) {
                            // Only this effect's own cancel (a newer reading, or the line gone) is rethrown.
                            currentCoroutineContext().ensureActive()
                            Log.w(TAG, "Battery pace could not be worked out again at $key (${e.javaClass.simpleName}); the line keeps its figures", e)
                            shown.value
                        }
                }
        }
    }
    return shown.value
}

/** What the line's figures depend on in a reading: the level and whether it charges. */
private fun paceKey(reading: BatteryReading): Pair<Int, Boolean> = reading.percent to reading.isCharging

/**
 * "2 h on this charge · 30% an hour", "About 45 min on this charge · under 1% an hour", "30% an hour"
 * for [parts] without a time, "2 h on this charge" without a pace; without [withWords] the time drops
 * "on this charge" when a pace follows it ("About 2 h 45 min · 30% an hour").
 */
@Composable
internal fun pickingBatteryText(
    parts: PickingBatteryParts,
    withWords: Boolean = true,
): String {
    val time = parts.onCharge
    val pace =
        parts.percentPerHour?.let { percent ->
            val locale = currentLocale()
            when {
                percent != UNDER_ONE_PERCENT -> stringResource(R.string.battery_line_pace, formatBattery(percent, locale))
                time == null -> stringResource(R.string.battery_line_pace_under_one_alone, formatBattery(1, locale))
                else -> stringResource(R.string.battery_line_pace_under_one, formatBattery(1, locale))
            }
        }
    if (time == null) return pace.orEmpty()
    val duration = durationText(time.minutes)
    val onCharge =
        when {
            withWords || pace == null -> stringResource(if (time.isApproximate) R.string.battery_line_on_charge_about else R.string.battery_line_on_charge, duration)
            time.isApproximate -> stringResource(R.string.battery_line_about, duration)
            else -> duration
        }
    return if (pace == null) onCharge else stringResource(R.string.home_details_pair, onCharge, pace)
}

/** "45 min", "2 h", "2 h 15 min" in the details column's words. */
@Composable
private fun durationText(minutes: Int): String {
    val numbers = rememberCountFormat()
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0 -> stringResource(R.string.home_details_minutes, numbers.format(rest))
        rest == 0 -> stringResource(R.string.battery_line_hours, numbers.format(hours))
        else -> stringResource(R.string.home_details_hours_minutes, numbers.format(hours), numbers.format(rest))
    }
}

/**
 * An outline battery with its nub on the right and [GLYPH_STEPS] bars inside, [glyphSteps] of them
 * filled from the left, drawn in [color] at the size of text of [fontSize] (everything a share of
 * it, so it keeps its look at every text size).
 */
@Composable
private fun BatteryLevelGlyph(
    percent: Int,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
) {
    val density = LocalDensity.current
    val bodyWidth = BODY_WIDTH.dpOf(fontSize, density)
    val bodyHeight = BODY_HEIGHT.dpOf(fontSize, density)
    val nubWidth = NUB_WIDTH.dpOf(fontSize, density)
    val steps = glyphSteps(percent)
    val description = stringResource(R.string.battery_line_level, formatBattery(percent, currentLocale()))
    val emSize = fontSize.sizeIn()
    // On the first line when the text wraps: centred in the line's height, not in the whole text's.
    val lineTop = with(LocalDensity.current) { if (lineHeight.isSpecified) ((lineHeight.toPx() - bodyHeight.toPx()) / 2).coerceAtLeast(0f).toDp() else 0.dp }
    Canvas(Modifier.padding(top = lineTop).size(GLYPH_WIDTH.dpOf(fontSize, density), bodyHeight).testTag(PickingBatteryTags.GLYPH).semantics { contentDescription = description }) {
        val stroke = (STROKE * emSize).coerceAtLeast(1f)
        val corner = CORNER * emSize
        val inset = INSET * emSize
        val body = Size(bodyWidth.toPx(), bodyHeight.toPx())
        // The stroke is centred on its path: half inside, so the outline stays within the body.
        drawRoundRect(color, Offset(stroke / 2, stroke / 2), Size(body.width - stroke, body.height - stroke), CornerRadius(corner), Stroke(stroke))
        val nubHeight = body.height * NUB_SHARE
        drawRoundRect(color, Offset(body.width, (body.height - nubHeight) / 2), Size(nubWidth.toPx(), nubHeight), CornerRadius(stroke))
        val gap = BAR_GAP * emSize
        val room = body.width - 2 * (stroke + inset)
        val barWidth = (room - gap * (GLYPH_STEPS - 1)) / GLYPH_STEPS
        repeat(steps) { bar ->
            drawRoundRect(
                color,
                Offset(stroke + inset + bar * (barWidth + gap), stroke + inset),
                Size(barWidth, body.height - 2 * (stroke + inset)),
                CornerRadius(stroke / 2),
            )
        }
    }
}

private fun Float.dpOf(
    fontSize: TextUnit,
    density: Density,
) = with(density) { (this@dpOf * fontSize.toPx()).toDp() }

@Composable
private fun TextUnit.sizeIn(): Float = with(LocalDensity.current) { toPx() }

private const val MINUTES_PER_HOUR = 60

private const val TAG = "Battery"

/** The line wraps to a second line rather than lose its pace (the largest text sizes, a long time). */
private const val MAX_LINES = 2

/** The glyph's parts as shares of the text's size (an em). */
private const val BODY_WIDTH = 1.4f
private const val BODY_HEIGHT = 0.75f
private const val NUB_WIDTH = 0.12f

/** The glyph's whole width: body and nub. */
private const val GLYPH_WIDTH = BODY_WIDTH + NUB_WIDTH
private const val NUB_SHARE = 0.4f
private const val STROKE = 0.07f
private const val CORNER = 0.14f
private const val INSET = 0.07f
private const val BAR_GAP = 0.07f
private const val GLYPH_GAP = 0.5f
