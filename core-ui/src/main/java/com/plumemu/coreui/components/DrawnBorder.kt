package com.plumemu.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import kotlin.math.ceil
import kotlin.math.min

/**
 * The ring `Modifier.border(width, color, RoundedCornerShape(corner))` draws, with the colour read while
 * drawing: `border` takes a value, so a theme colour read for it recomposes the item whenever the colour
 * moves (a colour slider's preview). [corner] null is a circle (a pill when the box is not square).
 */
fun Modifier.borderDrawn(
    width: Dp,
    color: () -> Color,
    corner: Dp? = null,
): Modifier =
    drawWithContent {
        drawContent()
        val stroke = min(ceil(width.toPx()), ceil(size.minDimension / 2))
        val radius = corner?.toPx() ?: (size.minDimension / 2)
        drawRoundRect(
            color(),
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = CornerRadius((radius - stroke / 2).coerceAtLeast(0f)),
            style = Stroke(stroke),
        )
    }
