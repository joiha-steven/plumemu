package com.plumemu.coreui.components.keyboard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.geometry.CornerRadius

/*
 * The keyboard's three icons (shift, delete, hide), drawn on the canvas like WifiGlyph: no image
 * file, no icon library. Each is drawn in a 24-unit square (the board's SVG viewBox) scaled to the
 * icon's size, with the board's 1.8-unit round stroke.
 */

/** Which icon a key shows. */
internal enum class KeyIcon {
    Shift,
    Delete,
    Hide,
}

/**
 * Draws [icon] in [ink], filling this scope's square. [shift] matters for the shift icon only:
 * off and once draw the arrow's outline, locked fills it and draws a bar under it (the
 * computer keyboard's caps-lock mark), so locked differs from once by shape, not only by colour.
 */
internal fun DrawScope.drawKeyIcon(
    icon: KeyIcon,
    ink: Color,
    shift: ShiftMode,
) {
    val unit = size.minDimension / VIEW_BOX
    scale(unit, unit, pivot = Offset.Zero) {
        val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (icon) {
            KeyIcon.Shift -> {
                val arrow = shiftArrow()
                if (shift == ShiftMode.Locked) {
                    drawPath(arrow, ink, style = Fill)
                    drawPath(arrow, ink, style = stroke)
                    drawLine(ink, Offset(LOCK_BAR_START, LOCK_BAR_Y), Offset(LOCK_BAR_END, LOCK_BAR_Y), STROKE, StrokeCap.Round)
                } else {
                    drawPath(arrow, ink, style = stroke)
                }
            }
            KeyIcon.Delete -> {
                drawPath(deleteOutline(), ink, style = stroke)
                drawLine(ink, Offset(11.5f, 9f), Offset(17f, 15f), STROKE, StrokeCap.Round)
                drawLine(ink, Offset(17f, 9f), Offset(11.5f, 15f), STROKE, StrokeCap.Round)
            }
            KeyIcon.Hide -> drawHide(ink, stroke)
        }
    }
}

/** The board's hide icon: a keyboard outline (three keys and a space bar) over a down chevron. */
private fun DrawScope.drawHide(
    ink: Color,
    stroke: Stroke,
) {
    drawRoundRect(ink, topLeft = Offset(3f, 3f), size = Size(18f, 11f), cornerRadius = CornerRadius(2f), style = stroke)
    for (x in listOf(7f, 11f, 15f)) drawCircle(ink, radius = STROKE / 2f, center = Offset(x, 7f))
    drawLine(ink, Offset(8f, 10.5f), Offset(16f, 10.5f), STROKE, StrokeCap.Round)
    val chevron =
        Path().apply {
            moveTo(9f, 18f)
            lineTo(12f, 21f)
            lineTo(15f, 18f)
        }
    drawPath(chevron, ink, style = stroke)
}

/** An upward arrow: a triangle head on a short stem. */
private fun shiftArrow(): Path =
    Path().apply {
        moveTo(12f, 3.5f)
        lineTo(4f, 11.5f)
        lineTo(8.5f, 11.5f)
        lineTo(8.5f, 17f)
        lineTo(15.5f, 17f)
        lineTo(15.5f, 11.5f)
        lineTo(20f, 11.5f)
        close()
    }

/** A key cap pointing left (the delete sign's outline), rounded on the right. */
private fun deleteOutline(): Path =
    Path().apply {
        moveTo(8.5f, 5f)
        lineTo(19f, 5f)
        quadraticTo(21f, 5f, 21f, 7f)
        lineTo(21f, 17f)
        quadraticTo(21f, 19f, 19f, 19f)
        lineTo(8.5f, 19f)
        lineTo(2.5f, 12f)
        close()
    }

private const val VIEW_BOX = 24f
private const val STROKE = 1.8f
private const val LOCK_BAR_START = 8.5f
private const val LOCK_BAR_END = 15.5f
private const val LOCK_BAR_Y = 20.5f
