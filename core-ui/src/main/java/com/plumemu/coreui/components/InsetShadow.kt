package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.plumemu.coreui.theme.MaskOpaque

/** The layer's plain paint, never changed: one for every draw instead of one per call. */
private val layerPaint = Paint()

/**
 * A CSS inset shadow over the rounded box at [topLeft] of [size]: [color] wherever the box, moved [dy] px down and blurred by [sigma] px
 * (a CSS blur of B is a sigma of B / 2), does not reach. The box is filled in a layer and the moved box cut out of it; a [sigma] of 0
 * cuts it sharp (the boards' `inset 0 -1px 0` lines).
 */
fun DrawScope.drawInsetShadow(
    topLeft: Offset,
    size: Size,
    radius: Float,
    dy: Float,
    sigma: Float,
    color: Color,
) {
    if (color.alpha <= 0f || size.width <= 0f || size.height <= 0f) return
    drawContext.canvas.saveLayer(Rect(topLeft, size), layerPaint)
    drawRoundRect(color, topLeft, size, CornerRadius(radius))
    if (sigma > 0f) {
        drawSoft(topLeft, size, radius, dy, sigma, MaskOpaque, false, BlendMode.DstOut)
    } else {
        drawRoundRect(MaskOpaque, topLeft + Offset(0f, dy), size, CornerRadius(radius), blendMode = BlendMode.DstOut)
    }
    drawContext.canvas.restore()
}
