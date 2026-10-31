package com.plumemu.coreui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle

/*
 * The Wi-Fi glyph of the top bar: three arcs over a dot, drawn on a Canvas like the other small
 * icons (no image file, no icon library). Its ink is as tall as the digits of the bar's text
 * (PlumFont.digitHeightEm) and centred on them, so it scales with the text size; its colour is the text's.
 */

/**
 * Wi-Fi glyph as tall as the digits of [style] (its ink fills its box) and [color]ed like the text it sits by;
 * [description] is what a screen reader says.
 */
@Composable
fun WifiGlyph(
    style: TextStyle,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
) = WifiGlyph(style, { color }, description, modifier)

/** [WifiGlyph] with a colour read while drawing, so a theme colour that moves redraws it without recomposing. */
@Composable
fun WifiGlyph(
    style: TextStyle,
    color: () -> Color,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val font = PlumTheme.type.font
    val height = with(density) { (style.fontSize.toPx() * font.digitHeightEm).toDp() }
    val width = height * WIDTH_PER_HEIGHT
    // The row centres the box on the text line; the digits' ink sits a little off that line centre.
    val shift = with(density) { (style.fontSize.toPx() * font.digitCentreBelowLineCentreEm).roundToInt() }
    Canvas(modifier.offset { IntOffset(0, shift) }.size(width, height).semantics { contentDescription = description }) {
        val color = color()
        val stroke = size.height * STROKE_PER_HEIGHT
        val dotRadius = stroke * DOT_PER_STROKE
        val centre = Offset(size.width / 2f, size.height - dotRadius)
        // The outer arc's top edge and the dot's bottom edge are the box's top and bottom: the ink fills it.
        val outer = size.height - dotRadius - stroke / 2f
        drawCircle(color, dotRadius, centre)
        for (arc in 1..ARCS) {
            val radius = outer * arc / ARCS
            drawArc(
                color = color,
                startAngle = ARC_START,
                sweepAngle = ARC_SWEEP,
                useCenter = false,
                topLeft = Offset(centre.x - radius, centre.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** The fan spans a 90° sweep: 2 (outer radius sin 45° + half a stroke) over the height is about 1.3. */
private const val WIDTH_PER_HEIGHT = 1.3f

private const val STROKE_PER_HEIGHT = 0.14f
private const val DOT_PER_STROKE = 0.8f
private const val ARCS = 3
private const val ARC_START = -135f
private const val ARC_SWEEP = 90f
