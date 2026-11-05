package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.plumemu.coreui.theme.ChoiceDimens

/** A check in [ink] centred in the DrawScope: two round-capped strokes (plain lines, no path, no layer). */
internal fun DrawScope.drawCheckMark(
    ink: Color,
    centre: Offset = Offset(size.width / 2, size.height / 2),
) {
    val box = size.minDimension * ChoiceDimens.CHECK_SIZE
    val stroke = size.minDimension * ChoiceDimens.CHECK_STROKE
    val left = Offset(centre.x - box * 0.5f, centre.y + box * 0.02f)
    val bottom = Offset(centre.x - box * 0.12f, centre.y + box * 0.38f)
    val right = Offset(centre.x + box * 0.5f, centre.y - box * 0.34f)
    drawLine(ink, left, bottom, stroke, StrokeCap.Round)
    drawLine(ink, bottom, right, stroke, StrokeCap.Round)
}
