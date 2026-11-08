package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CssShadow
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.FocusHalo
import com.plumemu.coreui.theme.FocusLift
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
 * The focus rim of a button or a switch: the shared rim stroke ([drawRimStroke]: [FocusLift.rimWidth] of [rim], glued to the edge of the rounded
 * rectangle at [topLeft] of [size], corner [corner] px, no gap, nothing scaled) over a tight glow and a small depth shadow (tinted [shade]),
 * faded by [strength] (0..1). The glow and the shadow are two layers of ONE cached image (`drawCssShadows`); no layer, no clip, nothing laid
 * out. It reaches [FlatPartDimens.focusRimRoom] past the part: leave that unclipped. [rim] is [FocusLift.rimColor] of the page.
 */
internal fun DrawScope.drawGluedRim(
    topLeft: Offset,
    size: Size,
    corner: Float,
    rim: Color,
    dark: Boolean,
    shade: Color,
    strength: Float,
) {
    if (strength <= 0f) return
    val glow = if (dark) FlatPartDimens.GLOW_ALPHA_DARK else FlatPartDimens.GLOW_ALPHA_LIGHT
    val drop = if (dark) FlatPartDimens.SHADOW_ALPHA_DARK else FlatPartDimens.SHADOW_ALPHA_LIGHT
    drawCssShadows(
        listOf(
            CssShadow(shade.copy(alpha = drop), FlatPartDimens.shadowDrop, FlatPartDimens.shadowBlur, spread = FocusLift.rimWidth, glow = true),
            CssShadow(rim.copy(alpha = glow), 0.dp, FocusLift.glowSigma * 2, spread = FocusLift.rimWidth, glow = true),
        ),
        topLeft,
        size,
        corner,
        strength,
    )
    drawRimStroke(topLeft, size, corner, rim.copy(alpha = rim.alpha * strength))
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
