package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.PlumDimens
import kotlin.math.exp

/**
 * The focus ring of a cover, swatch or button (docs/design/rules.md › Focus and current): a
 * 2.5 dp [ring] stroke 3 dp outside the item at [topLeft] of [itemSize] (px), following its
 * [corner], over a very soft [glow] (the board's `box-shadow: 0 0 12px 1px accent·25 %`).
 *
 * Drawn only, never laid out, so a focus move costs no measure or layout, and drawn
 * only outside the item, so a parent can draw it over the item's neighbours after they are drawn
 * (the home grid does: a ring drawn by the item would be cut by the grid's clip). It reaches
 * [PlumDimens.focusRingOutset] past the item; leave that unclipped.
 *
 * The glow is a stack of strokes fading out like a Gaussian blur rather than a blur mask filter:
 * hardware-accelerated canvases ignore mask filters before Android 9, and the stack draws the
 * same on every Android version and in screenshot tests.
 */
fun DrawScope.drawFocusRing(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    ring: Color,
    glow: Color,
) {
    drawGlow(topLeft, itemSize, corner, glow)
    drawRing(topLeft, itemSize, corner, ring)
}

/**
 * The focus ring of a grid item: hugging the item's own shape, a [PlumDimens.focusRingGap] wide band of the
 * screen's [gap] colour just outside it, then the [ring] ([PlumDimens.focusRingWidth], the accent). No soft glow of the accent: the
 * grid's glow is the item's own colour, drawn under the item by the caller. Colours are passed as values, so a caller reads them
 * (States) inside its draw block and nothing recomposes when one changes. Reaches [PlumDimens.focusRingOutset] past the item.
 */
fun DrawScope.drawGridFocusRing(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    ring: Color,
    gap: Color,
) {
    val gapWidth = PlumDimens.focusRingGap.toPx()
    drawOutline(topLeft, itemSize, gapWidth / 2, corner.toPx(), gap, gapWidth)
    drawRing(topLeft, itemSize, corner, ring)
}

private fun DrawScope.drawRing(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    ring: Color,
) {
    val width = PlumDimens.focusRingWidth.toPx()
    drawOutline(topLeft, itemSize, PlumDimens.focusRingGap.toPx() + width / 2, corner.toPx(), ring, width)
}

private fun DrawScope.drawGlow(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    glow: Color,
) {
    val spread = PlumDimens.focusGlowSpread.toPx()
    // CSS blur radius 12 is a Gaussian with sigma 6; it fades out by about two sigmas.
    val sigma = PlumDimens.focusGlowBlur.toPx() / 2
    val step = 2 * sigma / GLOW_LAYERS
    for (i in 0 until GLOW_LAYERS) {
        val distance = (i + 0.5f) * step
        // Share of the shadow left at this distance past its edge: the normal tail at d / sigma, logistic form.
        val share = 1f / (1f + exp(LOGISTIC_SCALE * distance / sigma))
        drawOutline(topLeft, itemSize, spread + distance, corner.toPx(), glow.copy(alpha = glow.alpha * share), step)
    }
}

/** A rounded-rectangle stroke centred [inflate] px outside the item at [topLeft] of [itemSize]. */
private fun DrawScope.drawOutline(
    topLeft: Offset,
    itemSize: Size,
    inflate: Float,
    corner: Float,
    color: Color,
    width: Float,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(topLeft.x - inflate, topLeft.y - inflate),
        size = Size(itemSize.width + inflate * 2, itemSize.height + inflate * 2),
        cornerRadius = CornerRadius(corner + inflate),
        style = Stroke(width = width),
    )
}

private const val GLOW_LAYERS = 6

/** The logistic curve that tracks the normal distribution's tail (1.702, Bowling et al.). */
private const val LOGISTIC_SCALE = 1.702f
