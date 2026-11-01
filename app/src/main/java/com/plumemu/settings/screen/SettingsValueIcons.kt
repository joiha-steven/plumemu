package com.plumemu.settings.screen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle

/*
 * The small drawn marks at the right of an option row (decision 0011 point 4): the chevrons of a value
 * cycler and of a row that opens a page, and the check before a signed-in status. They are drawn, not
 * text glyphs, because "‹ ›" sits unevenly on the text line. Their size follows the value's font size,
 * so they keep their look at every text size; the shape is the approved mockup's (a 9 x 20 stroke with
 * round caps).
 */

/** Proportions of the marks, as a share of the value text's font size (the mockup's shapes at its 13 sp). */
private const val CHEVRON_HEIGHT = 0.62f
private const val CHEVRON_WIDTH = 0.30f
private const val STROKE_WIDTH = 0.12f
private const val CHECK_SIZE = 0.95f
private const val CHECK_BOX = 24f
private const val CHECK_STROKE = 3.2f

/** Which way a chevron points. */
internal enum class ChevronSide { Left, Right }

/** A chevron as tall as [CHEVRON_HEIGHT] of [style]'s font size; [color] is read while drawing. */
@Composable
internal fun ValueChevron(
    side: ChevronSide,
    style: TextStyle,
    color: () -> Color,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val density = LocalDensity.current
    val font = with(density) { style.fontSize.toDp() }
    val box = Modifier.size(font * (CHEVRON_WIDTH + STROKE_WIDTH), font * (CHEVRON_HEIGHT + STROKE_WIDTH))
    Canvas(modifier.then(box).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        val stroke = size.width * STROKE_WIDTH / (CHEVRON_WIDTH + STROKE_WIDTH)
        val half = stroke / 2
        val left = half
        val right = size.width - half
        val (tip, back) = if (side == ChevronSide.Right) right to left else left to right
        val path =
            Path().apply {
                moveTo(back, half)
                lineTo(tip, size.height / 2)
                lineTo(back, size.height - half)
            }
        drawPath(path, color(), style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A check mark in [color] (the accent), the height of the value's font size. */
@Composable
internal fun ValueCheck(
    style: TextStyle,
    color: () -> Color,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val font = with(LocalDensity.current) { style.fontSize.toDp() }
    Canvas(modifier.size(font * CHECK_SIZE).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        // The mockup's check ("M4.5 12.5l5 5L19.5 7" in a 24 box), stroked 3.2 of 24.
        val unit = size.width / CHECK_BOX
        val path =
            Path().apply {
                moveTo(4.5f * unit, 12.5f * unit)
                lineTo(9.5f * unit, 17.5f * unit)
                lineTo(19.5f * unit, 7f * unit)
            }
        drawPath(path, color(), style = Stroke(width = CHECK_STROKE * unit, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
