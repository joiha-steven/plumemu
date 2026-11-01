package com.plumemu.secondscreen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import com.plumemu.R
import com.plumemu.coreui.theme.InGameColors
import com.plumemu.coreui.theme.InGameDimens

/**
 * The chart of this play, on the vertical scale of [BatteryChart.scale]: from its start at the left
 * to empty or full at the right. This play's line over a soft area fading to the floor; the line
 * ahead thinner and fading along its length over a fainter area; the usual pace dotted; now a dot in
 * a soft glow with a faint dotted guide down to the floor; a small ring at the right end; the whole
 * hours as short ticks under the floor (no grid, no baseline). Only the area ahead is drawn in a
 * layer of its own, so it can fade both down and along ([BlendMode.DstIn]); the rest is not, so the
 * glow at now is never cut at the chart's bounds.
 */
@Composable
internal fun InGameBatteryChart(
    chart: BatteryChart,
    dimens: InGameDimens,
    lineColor: Color,
) {
    Canvas(Modifier.fillMaxWidth().testTag(InGameBatteryTags.CHART)) {
        val pad = InGameDimens.chartPad.toPx()
        val side = InGameDimens.endMarkRadius.toPx() + InGameDimens.endMarkStroke.toPx()
        val floor = size.height - InGameDimens.tickGap.toPx() - InGameDimens.tickLength.toPx()
        if (floor <= 2 * pad) return@Canvas
        val scale = chart.scale()
        val span = scale.endInclusive - scale.start
        fun x(minute: Float) = side + (size.width - 2 * side) * minute / chart.minutes
        fun y(percent: Float) = pad + (floor - 2 * pad) * (1f - (percent - scale.start) / span)
        fun at(point: ChartPoint) = Offset(x(point.minute), y(point.percent))
        val tickTop = floor + InGameDimens.tickGap.toPx()
        for (minute in chart.hourMinutes) {
            drawLine(InGameColors.tick, Offset(x(minute), tickTop), Offset(x(minute), tickTop + InGameDimens.tickLength.toPx()), InGameDimens.tickStroke.toPx())
        }
        val now = chart.played.lastOrNull()?.let(::at) ?: return@Canvas
        val end = chart.aheadTo?.let(::at)
        if (end != null) drawAheadArea(now, end, floor, lineColor)
        drawPlayedArea(chart.played.map(::at), floor, lineColor)
        drawNowGuide(now, pad, floor)
        val from = chart.usualFrom
        val to = chart.usualTo
        if (from != null && to != null) {
            val dots = PathEffect.dashPathEffect(floatArrayOf(DOT_LENGTH, InGameDimens.usualDotGap.toPx()))
            drawLine(InGameColors.usual, at(from), at(to), dimens.usualStroke.toPx(), StrokeCap.Round, dots)
        }
        if (end != null) {
            val fading = Brush.linearGradient(listOf(InGameColors.ahead, InGameColors.ahead.copy(alpha = InGameColors.AHEAD_FADE_TO)), now, end)
            drawLine(fading, now, end, dimens.aheadStroke.toPx(), StrokeCap.Round)
            if (chart.aheadEnds) drawCircle(InGameColors.endMark, InGameDimens.endMarkRadius.toPx(), end, style = Stroke(InGameDimens.endMarkStroke.toPx()))
        }
        drawPlayed(chart.played.map(::at), lineColor, dimens.playedStroke.toPx())
        val radius = dimens.nowDotRadius.toPx()
        val glow = radius * NOW_GLOW_SCALE
        drawCircle(Brush.radialGradient(listOf(lineColor.copy(alpha = InGameColors.NOW_GLOW_ALPHA), Color.Transparent), now, glow), glow, now)
        drawCircle(lineColor, radius, now)
    }
}

/** The area under the line ahead: faint where it starts, none at its end or at the floor. */
private fun DrawScope.drawAheadArea(
    now: Offset,
    end: Offset,
    floor: Float,
    color: Color,
) {
    if (end.x <= now.x) return
    val area =
        Path().apply {
            moveTo(now.x, floor)
            lineTo(now.x, now.y)
            lineTo(end.x, end.y)
            lineTo(end.x, floor)
            close()
        }
    val along = Brush.horizontalGradient(listOf(color.copy(alpha = InGameColors.AHEAD_AREA_ALPHA), Color.Transparent), now.x, end.x)
    val top = minOf(now.y, end.y)
    drawContext.canvas.saveLayer(Rect(now.x, top, end.x, floor), Paint())
    drawPath(area, along)
    // DstIn keeps the area by the mask's alpha only: any opaque colour will do.
    val down = Brush.verticalGradient(listOf(color.copy(alpha = 1f), Color.Transparent), top, floor)
    drawRect(down, Offset(now.x, top), Size(end.x - now.x, floor - top), blendMode = BlendMode.DstIn)
    drawContext.canvas.restore()
}

/** The area under this play's line, its colour at [InGameColors.AREA_ALPHA] at the line's top, none at the floor. */
private fun DrawScope.drawPlayedArea(
    points: List<Offset>,
    floor: Float,
    color: Color,
) {
    if (points.size < 2) return
    val area =
        Path().apply {
            moveTo(points.first().x, floor)
            for (point in points) lineTo(point.x, point.y)
            lineTo(points.last().x, floor)
            close()
        }
    val top = points.minOf { it.y }
    drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = InGameColors.AREA_ALPHA), Color.Transparent), top, floor))
}

/** A faint dotted line from the chart's top down to its floor at now. */
private fun DrawScope.drawNowGuide(
    now: Offset,
    top: Float,
    floor: Float,
) {
    val dots = PathEffect.dashPathEffect(floatArrayOf(DOT_LENGTH, InGameDimens.nowGuideGap.toPx()))
    drawLine(InGameColors.tick, Offset(now.x, top), Offset(now.x, floor), InGameDimens.nowGuideStroke.toPx(), StrokeCap.Round, dots)
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
 * Under the chart's ends: "Started 21:07" at the left, "Empty 23:15" (or "Full 22:50") at the right
 * when there is an end. Where both do not fit, the start gives way.
 */
@Composable
internal fun ChartLabels(
    startMillis: Long,
    endMillis: Long?,
    charging: Boolean,
    style: TextStyle,
    formatTime: (Long) -> String,
) {
    val started = stringResource(R.string.in_game_battery_started, formatTime(startMillis))
    val ends = endMillis?.let { stringResource(if (charging) R.string.in_game_battery_full_label else R.string.in_game_battery_empty_label, formatTime(it)) }
    Layout(
        modifier = Modifier.testTag(InGameBatteryTags.LABELS),
        content = {
            BasicText(started, style = style, maxLines = 1, softWrap = false)
            if (ends != null) BasicText(ends, style = style, maxLines = 1, softWrap = false)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placed = measurables.map { it.measure(Constraints()) }
        val first = placed[0]
        val last = placed.getOrNull(1)
        val both = last == null || first.width + InGameDimens.labelGap.roundToPx() + last.width <= width
        layout(width, placed.maxOf { it.height }) {
            if (both) first.place(0, 0)
            last?.place(width - last.width, 0)
        }
    }
}

/** The glow round the dot at now, against the dot's radius. */
private const val NOW_GLOW_SCALE = 2.6f

/** A dot: a dash this short with round caps draws a circle as wide as the stroke. */
private const val DOT_LENGTH = 0.1f
