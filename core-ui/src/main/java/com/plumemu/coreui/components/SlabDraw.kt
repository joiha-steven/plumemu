package com.plumemu.coreui.components

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidBitmap
import com.plumemu.coreui.theme.FocusSlab
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.SlabLook
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.lerp
import com.plumemu.coreui.theme.SlabDimens
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * What the focused row's slab draws outside its fill (docs/design/rules.md > The focus slab), the CSS's outer box-shadows bottom to top: the glow
 * `0 0 glowBlur` ([glow], `PlumComposeColors.slabGlow`), the far and the near drop ([SlabLook.far] / [SlabLook.near]) and the dark `0 0 0 1px`
 * [SlabLook.ring]. [blob] is the light games list's floor glow under it all. Each soft layer is the exact Gaussian of the rounded shape ([drawSoft]),
 * never a blur or a stroke stack. CSS paints no outer shadow under the box; here the fill drawn after it is opaque (the gradient already composited
 * over the page or card, [drawSlabFill]), so it covers that part. A non-rounded outline draws none.
 */
internal fun DrawScope.drawSlabOutside(
    outline: Outline,
    look: SlabLook,
    glow: Color,
    blob: Color = Color.Transparent,
    /** The opaque colour mostly under the slab's soft layers (the page, or the card), so they composite over it exactly; transparent when unknown. */
    under: Color = Color.Transparent,
) {
    if (outline !is Outline.Rounded) return
    val box = outline.roundRect
    val topLeft = Offset(box.left, box.top)
    val size = Size(box.width, box.height)
    val corner = box.topLeftCornerRadius.x
    val noGlow = PerfSwitches.noGlow
    if (blob.alpha > 0f && !noGlow) drawBlob(box, blob)
    // The blob was fitted to the light list board's blue around the slab, its own glow included: drawn with it, the glow would count twice.
    drawDrops(topLeft, size, corner, look, if (blob.alpha > 0f || noGlow) Color.Transparent else glow, under)
    if (look.ring.alpha > 0f) drawBand(topLeft, size, corner, FocusSlab.RING_WIDTH_DP.dp.toPx() / 2f, FocusSlab.RING_WIDTH_DP.dp.toPx(), look.ring)
}

/**
 * The slab's own face under its text: the CSS `linear-gradient` ([SlabLook.stops], on a card [SlabLook.stopsOnCard]; each stop moved [press] of the way to
 * [pressInk] while pressed) and its inset shadows ([SlabLook.insetTop] over [SlabLook.insetBottom], one cached bitmap inside the border).
 */
internal fun DrawScope.drawSlabFill(
    outline: Outline,
    look: SlabLook,
    onCard: Boolean,
    press: Float = 0f,
    pressInk: Color = Color.Transparent,
) {
    val stops = if (onCard) look.stopsOnCard else look.stops
    val colors = if (press == 0f) stops else stops.map { lerp(it, pressInk, press) }
    val bounds = outline.bounds
    val brush = Brush.verticalGradient(0f to colors[0], FocusSlab.MID_AT to colors[1], 1f to colors[2], startY = bounds.top, endY = bounds.bottom)
    drawOutline(outline, brush)
    if (outline !is Outline.Rounded) return
    val w = Math.round(bounds.width)
    val h = Math.round(bounds.height)
    fun px(l: com.plumemu.coreui.theme.SlabShadow?) = l?.let { SoftImages.Inset(it.color, it.drop.toPx(), it.sigma.toPx()) }
    val placed = SoftImages.inset(w, h, outline.roundRect.topLeftCornerRadius.x, SlabDimens.edge.toPx(), px(look.insetTop), px(look.insetBottom)) ?: return
    // Placed at the outline's own (fractional) corner, so it sits on the gradient and border exactly wherever the slab is drawn.
    translate(bounds.left, bounds.top) { drawPlaced(placed, IntOffset.Zero, w, h) }
}

/** The glow, [SlabLook.far] and [SlabLook.near] as one cached image ([SoftImages.drops]), placed at the shape's corner rounded to whole pixels. */
private fun DrawScope.drawDrops(
    topLeft: Offset,
    size: Size,
    corner: Float,
    look: SlabLook,
    glow: Color,
    under: Color,
) {
    val near = look.near
    val far = look.far
    if (near == null || far == null) {
        if (glow.alpha > 0f) drawSoft(topLeft, size, corner, 0f, SlabDimens.glowSigma.toPx(), glow, false)
        for (s in listOfNotNull(far, near)) drawSoft(topLeft, size, corner, s.drop.toPx(), s.sigma.toPx(), s.color, true)
        return
    }
    val placed = SoftImages.drops(
        Math.round(size.width),
        Math.round(size.height),
        corner,
        SoftImages.Inset(near.color, near.drop.toPx(), near.sigma.toPx()),
        SoftImages.Inset(far.color, far.drop.toPx(), far.sigma.toPx()),
        if (glow.alpha > 0f) SoftImages.Inset(glow, 0f, SlabDimens.glowSigma.toPx()) else null,
        under,
    ) ?: return
    // Whole pixels, as [drawSoft]: a fractional offset would resample the image and add a level of rounding to its smooth ramp.
    val at = IntOffset(Math.round(topLeft.x) + placed.dx, Math.round(topLeft.y) + placed.dy)
    drawPlaced(placed, at, Math.round(size.width) - 2 * placed.dx, placed.image.height)
}

/** [placed] at [at], [width] x [height] px: as it is, or sliced to that width when it was built for a narrower box of its look. */
internal fun DrawScope.drawPlaced(
    placed: SoftImages.Placed,
    at: IntOffset,
    width: Int,
    height: Int,
) {
    val image = placed.image
    if (placed.keep >= 0) {
        drawSliced(image, placed.keep, -1, 1, at, IntSize(width, height))
    } else {
        drawImage(image, IntOffset.Zero, IntSize(image.width, image.height), at, IntSize(width, height), filterQuality = FilterQuality.Low)
    }
}

/**
 * The light themes' glow: a 2D Gaussian blob of [color] (its alpha is the peak) under the slab. Computed once per size into a small
 * bitmap ([SoftImages]) and drawn scaled with a bilinear filter: smooth, one draw per frame, no blur and no mask filter.
 */
internal fun DrawScope.drawBlob(
    box: androidx.compose.ui.geometry.RoundRect,
    color: Color,
) {
    val sx = SlabDimens.BLOB_SIGMA_X_SHARE * box.width
    val sy = SlabDimens.blobSigmaY.toPx()
    val cx = box.left + box.width / 2f
    val cy = box.top + box.height / 2f + SlabDimens.blobDrop.toPx()
    val image = SoftImages.blob(sx, sy, color)
    val scale = SoftImages.blobScale(sy)
    val w = image.width * scale
    val h = image.height * scale
    val x = Math.round(cx - w / 2f)
    val y = Math.round(cy - h / 2f)
    // Drawn without dithering: dither noise of one level, alternating by row, adds to the drops' own rounding into visible steps (SoftShadowStepTest).
    drawIntoCanvas { it.nativeCanvas.drawBitmap(image.asAndroidBitmap(), null, android.graphics.Rect(x, y, x + w, y + h), blobPaint) }
}

private val blobPaint = android.graphics.Paint().apply {
    isFilterBitmap = true
    isDither = false
}

/**
 * A shape at [topLeft] of [size], moved [drop] px down and blurred by [sigma] px, in [color] (its alpha is the strength deep inside): a Gaussian-blurred
 * rounded rectangle of radius [corner] (the shade follows the corners) computed once per size into a bitmap ([SoftImages]) and drawn
 * scaled, bilinear, so it is smooth at any density and one draw per frame. [inside] is kept for callers: the bitmap always holds the whole shadow,
 * and the fill drawn after it covers the part under the item. [blendMode] DstOut cuts the shape out of what is drawn before (an inset shadow).
 */
@Suppress("UNUSED_PARAMETER")
internal fun DrawScope.drawSoft(
    topLeft: Offset,
    size: Size,
    corner: Float,
    drop: Float,
    sigma: Float,
    color: Color,
    inside: Boolean,
    blendMode: BlendMode = DrawScope.DefaultBlendMode,
) {
    val w = Math.round(size.width)
    val h = Math.round(size.height)
    if (w <= 0 || h <= 0 || color.alpha <= 0f) return
    val shade = SoftImages.shadow(w, h, sigma, color, corner)
    val step = SoftImages.step(sigma)
    // The size the exact image has: a stand-in built for another box is drawn at it.
    val dw = SoftImages.cells(w, sigma) * step
    val dh = SoftImages.cells(h, sigma) * step
    val x = topLeft.x + (w - dw) / 2f
    val y = topLeft.y + drop + (h - dh) / 2f
    with(shade) { draw(IntOffset(Math.round(x), Math.round(y)), IntSize(dw, dh), step, blendMode) }
}

/** A rounded-rectangle stroke centred [d] px outside (negative: inside) the shape, [width] px wide; skipped when the shape is smaller than that. */
private fun DrawScope.drawBand(
    topLeft: Offset,
    size: Size,
    corner: Float,
    d: Float,
    width: Float,
    color: Color,
) {
    if (size.width + d * 2 <= 0f || size.height + d * 2 <= 0f) return
    drawRoundRect(
        color = color,
        topLeft = Offset(topLeft.x - d, topLeft.y - d),
        size = Size(size.width + d * 2, size.height + d * 2),
        cornerRadius = CornerRadius(max(corner + d, 0f)),
        style = strokeOf(width),
    )
}


/**
 * What the slab draws over its fill: the CSS border, 1 board px inside [outline], in [SlabLook.edge] with its top side in [SlabLook.top] (the colours meet
 * half way round each top corner, as a CSS border's do), and the short accent bar at its left with its `0 0 12px` glow ([glow]). No ring, no dot.
 */
internal fun DrawScope.drawSlabFace(
    outline: Outline,
    colors: PlumComposeColors,
    glow: Color = colors.slabGlow,
    /** The accent bar's offset from the slab's middle, px ([StripeAnchor]); 0 centres it. */
    barShift: Float = 0f,
    /** The bar's distance from the slab's left edge. */
    barInset: Dp = SlabDimens.barInset,
) {
    val look = colors.slab
    val bounds = outline.bounds
    val edge = SlabDimens.edge.toPx()
    val corner = (outline as? Outline.Rounded)?.roundRect?.topLeftCornerRadius?.x ?: 0f
    // Glass: a thin rim, lit at two opposite corners and nearly gone between them.
    val rim = look.rim(bounds.topLeft, bounds.bottomRight)
    drawRoundRect(
        rim,
        Offset(bounds.left + edge / 2f, bounds.top + edge / 2f),
        Size(bounds.width - edge, bounds.height - edge),
        CornerRadius(corner - edge / 2f),
        style = Stroke(edge),
    )
    val barHeight = min(SlabDimens.barHeight.toPx(), bounds.height * SlabDimens.BAR_HEIGHT_SHARE)
    val barWidth = SlabDimens.barWidth.toPx()
    val left = bounds.left + barInset.toPx()
    val middle = bounds.top + (bounds.height - barHeight) / 2f
    val top = if (barShift == 0f) middle else (middle + barShift).coerceIn(bounds.top + edge, maxOf(bounds.top + edge, bounds.bottom - edge - barHeight))
    if (glow.alpha > 0f) drawSoft(Offset(left, top), Size(barWidth, barHeight), barWidth / 2f, 0f, SlabDimens.barGlowSigma.toPx(), glow, false)
    drawRoundRect(colors.accent, Offset(left, top), Size(barWidth, barHeight), CornerRadius(barWidth / 2f))
}

