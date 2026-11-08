package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.plumemu.coreui.theme.FocusLift

/**
 * What lies under a lifted item ([FocusLift]): the small depth shadow and the tight glow of the rim's shape (the frame grown by the rim), as
 * one cached image ([SoftImages.drops]) drawn with [strength] (0..1) as its alpha. The image is the look's, not the cell's (nine slices, built for the
 * shortest and narrowest box that shows a whole edge and corner), so a grid's size step builds none; it is the same for every frame of a grow; the caller draws it inside the lift's own scale. Draw it before the item, which covers its middle: nothing here is a blur or a layer.
 */
fun DrawScope.drawFocusLiftUnder(
    frame: Rect,
    corner: Float,
    strength: Float,
    dark: Boolean,
    ink: Color,
    /** False for an item with text just under it (an Apps tile): the glow only, no depth shadow to darken or cut across the name. */
    shadow: Boolean = true,
) {
    if (strength <= 0f) return
    val rim = FocusLift.rimWidth.toPx()
    val w = Math.round(frame.width + 2 * rim)
    val h = Math.round(frame.height + 2 * rim)
    val depth = FocusLift.shadowColor(dark).let { if (shadow) it else it.copy(alpha = 0f) }
    val placed =
        SoftImages.drops(
            w,
            h,
            corner + rim,
            SoftImages.Inset(depth, FocusLift.shadowDrop.toPx(), FocusLift.shadowSigma.toPx()),
            SoftImages.Inset(depth.copy(alpha = 0f), 0f, FocusLift.shadowSigma.toPx()),
            SoftImages.Inset(FocusLift.glowColor(dark, ink), 0f, FocusLift.glowSigma.toPx()),
            sliceHeight = true,
        ) ?: return
    val at = IntOffset(Math.round(frame.left - rim) + placed.dx, Math.round(frame.top - rim) + placed.dy)
    val width = w - 2 * placed.dx
    // The image is padded evenly above and below: its drawn height is the box's plus the padding twice.
    val height = h - 2 * placed.dy
    if (placed.keep >= 0 || placed.keepY >= 0) {
        drawSliced(placed.image, placed.keep, placed.keepY, 1, at, IntSize(width, height), strength)
    } else {
        drawImage(placed.image, IntOffset.Zero, IntSize(placed.image.width, placed.image.height), at, IntSize(width, height), alpha = strength, filterQuality = FilterQuality.Low)
    }
}

/** The rim of a lifted item: a [FocusLift.rimWidth] stroke whose inner edge is the rounded rectangle [frame] (corner [corner] px), faded by [strength] (0..1). Drawn over, outside the frame only. */
fun DrawScope.drawFocusRim(
    frame: Rect,
    corner: Float,
    strength: Float,
    dark: Boolean,
    ink: Color,
) {
    if (strength <= 0f) return
    val color = FocusLift.rimColor(dark, ink)
    drawRimStroke(frame.topLeft, frame.size, corner, color.copy(alpha = color.alpha * strength))
}

/**
 * The one rim stroke of the focus (covers, tiles, buttons, switches): a [FocusLift.rimWidth] line in [color] (from [FocusLift.rimColor]) whose
 * inner edge is the rounded rectangle at [topLeft] of [size] with corner [corner] px, concentric with it. One stroke, no layer.
 */
internal fun DrawScope.drawRimStroke(
    topLeft: Offset,
    size: Size,
    corner: Float,
    color: Color,
) {
    val width = FocusLift.rimWidth.toPx()
    val inflate = width / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(topLeft.x - inflate, topLeft.y - inflate),
        size = Size(size.width + inflate * 2, size.height + inflate * 2),
        cornerRadius = CornerRadius(corner + inflate),
        style = strokeOf(width),
    )
}
