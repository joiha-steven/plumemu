package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.FocusHalo
import com.plumemu.coreui.theme.PlumDimens

/**
 * The focus ring of a cover, swatch or button (docs/design/rules.md › Focus and current): a
 * 2.5 dp [ring] stroke 3 dp outside the item at [topLeft] of [itemSize] (px), following its
 * [corner], inside the soft focus [halo] ([drawFocusHalo]; transparent on a light theme).
 *
 * Drawn only, never laid out, so a focus move costs no measure or layout, and drawn
 * only outside the item, so a parent can draw it over the item's neighbours after they are drawn
 * (the home grid does: a ring drawn by the item would be cut by the grid's clip). It reaches
 * [PlumDimens.focusRingOutset] past the item; leave that unclipped.
 *
 */
fun DrawScope.drawFocusRing(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    ring: Color,
    halo: Color,
) {
    drawFocusHalo(topLeft, itemSize, corner.toPx(), PlumDimens.focusRingOutset.toPx(), halo)
    drawRing(topLeft, itemSize, corner, ring)
}

/**
 * The focus ring of a grid item: hugging the item's own shape, a [PlumDimens.focusRingGap] wide band of the
 * screen's [gap] colour just outside it, then the [ring] ([PlumDimens.focusRingWidth], the accent), then the focus [halo] outside the ring (never over the item). The
 * glow under the item is its own colour, drawn by the caller. Colours are passed as values, so a caller reads them
 * (States) inside its draw block and nothing recomposes when one changes. Reaches [PlumDimens.focusRingOutset] past the item.
 */
fun DrawScope.drawGridFocusRing(
    topLeft: Offset,
    itemSize: Size,
    corner: Dp,
    ring: Color,
    gap: Color,
    halo: Color = Color.Transparent,
) {
    drawFocusHalo(topLeft, itemSize, corner.toPx(), PlumDimens.focusRingOutset.toPx(), halo)
    val gapWidth = PlumDimens.focusRingGap.toPx()
    drawOutline(topLeft, itemSize, gapWidth / 2, corner.toPx(), gap, gapWidth)
    drawRing(topLeft, itemSize, corner, ring)
}

/**
 * The focus mark of a grid cover whose collar's outer edge is the rounded rectangle at [topLeft] of [size] with corner [corner]
 * (px), faded in by [strength] (0..1): a [PlumDimens.focusRingGap] band of the screen's [gap] colour, the [ring]
 * ([PlumDimens.focusRingWidth]) and a [PlumDimens.focusRingKeyline] band of [gap] again so the ring's outside is always the screen colour
 * (its contrast never depends on what lies beyond). Only strokes outside the shape: never laid out, no image to build, so a focus move
 * costs a handful of strokes. Colours are values, read by the caller while drawing. The ring reaches [PlumDimens.focusRingOutset] past
 * the collar. No halo: it would show only where no neighbour stands, so the mark would be lopsided.
 */
fun DrawScope.drawCoverFocusMark(
    topLeft: Offset,
    size: Size,
    corner: Float,
    ring: Color,
    gap: Color,
    strength: Float,
) {
    if (strength <= 0f) return
    val outset = PlumDimens.focusRingOutset.toPx()
    val keyline = PlumDimens.focusRingKeyline.toPx()
    drawOutline(topLeft, size, outset + keyline / 2, corner, gap.copy(alpha = gap.alpha * strength), keyline)
    val gapWidth = PlumDimens.focusRingGap.toPx()
    drawOutline(topLeft, size, gapWidth / 2, corner, gap.copy(alpha = gap.alpha * strength), gapWidth)
    val width = PlumDimens.focusRingWidth.toPx()
    drawOutline(topLeft, size, gapWidth + width / 2, corner, ring.copy(alpha = ring.alpha * strength), width)
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

/**
 * The focus halo (docs/design/rules.md > Focus and current): [PlumDimens.focusHaloReach] of soft accent fading to nothing, drawn as
 * [PlumDimens.FOCUS_HALO_STEPS] bands (strokes) that start [start] px outside the rounded rectangle at [topLeft] of [itemSize] and
 * follow its [corner] (px). [halo] carries the colour and its peak alpha (`PlumComposeColors.focusGlow`); a transparent one (a
 * light theme) draws nothing. Only strokes outside the shape, so it never covers the item; no blur and no layout. A mask-filter
 * blur is ignored by hardware canvases before Android 9, and the bands draw the same on every version and in screenshot tests.
 */
fun DrawScope.drawFocusHalo(
    topLeft: Offset,
    itemSize: Size,
    corner: Float,
    start: Float,
    halo: Color,
    /** How far the bands reach (px); the halo's own by default. */
    reach: Float = PlumDimens.focusHaloReach.toPx(),
) {
    if (halo.alpha <= 0f) return
    val steps = PlumDimens.FOCUS_HALO_STEPS
    val band = reach / steps
    for (i in 0 until steps) {
        val inflate = start + (i + 0.5f) * band
        drawOutline(topLeft, itemSize, inflate, corner, halo.copy(alpha = FocusHalo.bandAlpha(halo.alpha, i, steps)), band)
    }
}

/** [drawFocusHalo] hugging a focus pill's [outline] (a rounded rectangle; another outline gets none), starting at its edge. */
internal fun DrawScope.drawHaloOutside(
    outline: Outline,
    halo: Color,
) {
    if (outline !is Outline.Rounded) return
    val box = outline.roundRect
    drawFocusHalo(Offset(box.left, box.top), Size(box.width, box.height), box.topLeftCornerRadius.x, 0f, halo)
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
        style = strokeOf(width),
    )
}

@Volatile private var strokeA = Stroke(width = 0f)

@Volatile private var strokeB = Stroke(width = 0f)

/**
 * A [Stroke] of [width] px from the last two used (the halo's band width and a ring's), so a frame allocates none. Strokes are immutable and each cell is
 * read once into a local, so the main thread and the image-build thread may both call this: the worst a race does is allocate one.
 */
internal fun strokeOf(width: Float): Stroke {
    val a = strokeA
    if (a.width == width) return a
    val b = strokeB
    if (b.width == width) return b
    val made = Stroke(width = width)
    strokeB = a
    strokeA = made
    return made
}
