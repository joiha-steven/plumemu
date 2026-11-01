package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.sunkenRim

private const val SHADOW_LAYERS = 3

/**
 * How far the face of a key moves down (px) at [press] (0 rest .. 1 down) and [raise] (0 .. 1, focused or hovered):
 * down by the edge's height when pressed, up by [PlumDimens.flatRaise] when raised.
 */
fun DrawScope.flatFaceShift(
    press: Float,
    raise: Float,
): Float = PlumDimens.flatEdge.toPx() * press - PlumDimens.flatRaise.toPx() * raise * (1f - press)

/**
 * A key with a flat face (docs/design/rules.md › Controls): a soft shadow under it, the edge, a hairline of
 * light on the top edge, then one solid face. At rest the edge is 2 dp and hangs below the item's bounds with the
 * shadow (nothing moves). A pill, or a circle when square; [corner] (px) rounds it less. [press] (0..1) sinks the face
 * into its edge, which disappears, and the shadow shrinks away; [raise] (0..1, focused or hovered) lifts the face by
 * [PlumDimens.flatRaise], grows the edge and shadow and lightens the face a touch. [overlay] is drawn on the face (a pressed
 * key's colour flash). Pass [moveFace] false when the caller moves the item itself (the face then stays at the top).
 * Plain filled shapes only: no blur, no layer, one draw block per key.
 */
fun DrawScope.drawFlatFace(
    look: FlatFace,
    overlay: Color = Color.Transparent,
    corner: Float = size.minDimension / 2,
    press: Float = 0f,
    raise: Float = 0f,
    moveFace: Boolean = true,
) {
    val radius = CornerRadius(minOf(corner, size.minDimension / 2))
    val edge = PlumDimens.flatEdge.toPx()
    val rim = PlumDimens.flatRim.toPx()
    val reach = PlumDimens.flatShadow.toPx()
    val lift = PlumDimens.flatRaise.toPx() * raise
    val up = 1f - press
    val faceTop = if (moveFace) flatFaceShift(press, raise) else 0f
    val edgeHeight = (edge + lift) * up
    // The shadow: three rounded rects growing downward, each a third of the peak; it fades as the key goes down.
    val peak = look.shadow.alpha * up * (1f + SHADOW_RAISE * raise)
    val layerAlpha = peak / SHADOW_LAYERS
    if (layerAlpha > 0.002f) {
        for (i in 1..SHADOW_LAYERS) {
            val grow = reach * i / SHADOW_LAYERS
            drawRoundRect(
                look.shadow.copy(alpha = layerAlpha),
                topLeft = Offset(-grow / 2, (edgeHeight + lift) * i / SHADOW_LAYERS),
                size = Size(size.width + grow, size.height + grow / 2),
                cornerRadius = CornerRadius(radius.x + grow / 2),
            )
        }
    }
    if (edgeHeight > 0.01f) drawRoundRect(look.edge, topLeft = Offset(0f, faceTop + edgeHeight), cornerRadius = radius)
    val face = if (raise > 0f) lerp(look.face, look.faceUp, raise) else look.face
    val rimColor = if (raise > 0f) lerp(look.rim, look.rimUp, raise) else look.rim
    drawRoundRect(rimColor, topLeft = Offset(0f, faceTop), cornerRadius = radius)
    // The face is the rim shape moved down by the hairline, same bottom: the rim shows as a crescent on top.
    drawRoundRect(face, topLeft = Offset(0f, faceTop + rim), size = Size(size.width, size.height - rim), cornerRadius = radius)
    if (overlay.alpha > 0f) drawRoundRect(overlay, topLeft = Offset(0f, faceTop + rim), size = Size(size.width, size.height - rim), cornerRadius = radius)
}

private const val SHADOW_RAISE = 0.4f

/**
 * A sunken track or field: [fill] with a 1 dp inset shadow along its top edge ([inset] is black at its alpha).
 * [radius] defaults to a pill.
 */
fun DrawScope.drawSunken(
    fill: Color,
    inset: Color,
    radius: Float = size.height / 2,
) {
    val shadow = PlumDimens.insetShadow.toPx()
    drawRoundRect(sunkenRim(fill, inset), cornerRadius = CornerRadius(radius))
    drawRoundRect(fill, topLeft = Offset(0f, shadow), size = Size(size.width, size.height - shadow), cornerRadius = CornerRadius(radius))
}

/**
 * A text field's sunken pill: `sel2` with the inset shadow, drawn [insetX] / [insetY] past the field's own bounds
 * (negative: inside them), so the text keeps its place and the row does not grow. Every field wears it, whichever
 * keyboard types into it.
 */
@Composable
fun Modifier.sunkenField(
    insetX: Dp = PlumDimens.fieldInsetX,
    insetY: Dp = PlumDimens.fieldInsetY,
): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val x = insetX.toPx()
        val y = insetY.toPx()
        inset(-x, -y, -x, -y) { drawSunken(colors.sel2, colors.inset) }
    }
}

/** [sunkenField] for a field that fills its row's height (Plumemu's own keyboard's `CaretLine`): the pill sits that far inside it, as tall as the Android field's. */
@Composable
fun Modifier.sunkenFieldInRow(): Modifier = sunkenField(insetY = -PlumDimens.fieldInsetY)
