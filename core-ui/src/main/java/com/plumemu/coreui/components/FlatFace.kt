package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.compositeOver
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CssFace
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.FieldDimens
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
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
    val peak = look.shadow.alpha * Depth.SHADOW * up * (1f + SHADOW_RAISE * raise)
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
    val down = if (look.innerShade > 0f) press else 0f
    val face = lerp(if (raise > 0f) lerp(look.face, look.faceUp, raise) else look.face, look.downBottom, down)
    val topTone = lerp(if (raise > 0f) lerp(look.faceTop, look.faceTopUp, raise) else look.faceTop, look.downTop, down)
    val rimColor = if (raise > 0f) lerp(look.rim, look.rimUp, raise) else look.rim
    // Pressed: the top highlight gives way to a dark inner band (the part's top shadow), about the crest's height.
    val shade = look.shaded(topTone, down)
    val rimShape = if (down > 0f) lerp(rimColor, shade, down) else rimColor
    val top = if (down > 0f) rim + (CollarDimens.crest.toPx() - rim) * down else rim
    drawRoundRect(rimShape, topLeft = Offset(0f, faceTop), cornerRadius = radius)
    // The face is the rim shape moved down by the hairline, same bottom: the rim shows as a crescent on top.
    val faceH = size.height - top
    if (topTone == face) {
        drawRoundRect(face, topLeft = Offset(0f, faceTop + top), size = Size(size.width, faceH), cornerRadius = radius)
    } else {
        // The face fades from the top tone to face: the top tone whole, then darker steps, each the last moved down (same bottom, so the curve stays).
        for (i in 0..FACE_STEPS) {
            val tone = if (i == FACE_STEPS) face else lerp(topTone, face, i / FACE_STEPS.toFloat())
            val moved = faceH * FACE_FADE * i / FACE_STEPS
            drawRoundRect(tone, topLeft = Offset(0f, faceTop + top + moved), size = Size(size.width, faceH - moved), cornerRadius = radius)
        }
    }
    if (overlay.alpha > 0f) drawRoundRect(overlay, topLeft = Offset(0f, faceTop + rim), size = Size(size.width, size.height - rim), cornerRadius = radius)
}

private const val SHADOW_RAISE = 0.4f

/** A fading face is [FACE_STEPS] + 1 shapes; the last (the bottom tone) starts [FACE_FADE] of the way down. */
private const val FACE_STEPS = 5
private const val FACE_FADE = 0.30f

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
 * keyboard types into it. The pill sits in a [CollarSize.Field] collar drawn outside it, also past the bounds (the row
 * does not grow; its clip must leave room, see [FieldRowFrame]): [focused] lights the groove in the accent with the halo,
 * [error] draws it in the danger colour and tints the well toward it, so an error never looks like focus (the message under the field is the caller's).
 */
@Composable
fun Modifier.sunkenField(
    insetX: Dp = PlumDimens.fieldInsetX,
    insetY: Dp = PlumDimens.fieldInsetY,
    focused: Boolean = false,
    error: Boolean = false,
    /** The well is at least this high (times the text size); 0 leaves it at the text plus [insetY] each side. */
    minWell: Dp = 0.dp,
): Modifier {
    val minWellPx = with(LocalDensity.current) { (minWell * PlumTheme.layout.textScale).toPx() }
    val colors = PlumTheme.colors
    val progress = remember { Animatable(if (focused) 1f else 0f) }
    val motionOn = PlumTheme.motion.isOn
    LaunchedEffect(focused, motionOn) {
        progress.animateTo(if (focused) 1f else 0f, if (motionOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.Decelerate) else snap())
    }
    return drawBehind {
        val x = insetX.toPx()
        // A field shorter than the least well grows its well to it, centred, so the text keeps its place.
        val y = if (minWellPx > 0f) maxOf(insetY.toPx(), (minWellPx - size.height) / 2f) else insetY.toPx()
        inset(-x, -y, -x, -y) {
            val pad = CollarSize.Field.padding
            val l = pad.calculateLeftPadding(layoutDirection).toPx()
            val t = pad.calculateTopPadding().toPx()
            val b = pad.calculateBottomPadding().toPx()
            val corner = minOf(FieldDimens.radius.toPx(), size.height / 2)
            inset(-l, -t, -l, -b) {
                drawCollar(
                    look = colors.collar,
                    collarSize = CollarSize.Field,
                    cornerRadius = corner,
                    focus = progress.value,
                    grooveOverride = if (error) colors.danger else null,
                    accent = colors.accent,
                    glow = colors.partGlow,
                )
            }
            // The well: `#0A0B0D` (Paper `#E0DBD0`) with `inset 0 3px 6px .55`; an error tints it toward the danger colour.
            val seat = colors.collar.seat
            val well = if (error) FieldDimens.tint(seat.fieldWell, colors.danger) else seat.fieldWell
            drawCssFace(CssFace(listOf(well), listOf(seat.fieldShade)), Offset.Zero, size, corner)
        }
    }
}

/**
 * A field's collar and sunken well drawn INSIDE the bounds (the board's Fields: lip, groove, well), so the field and
 * its collar are one box: nothing hangs past it. [focused] lights the groove in the accent with the halo, [error] draws
 * it in the danger colour and tints the well; the row that holds it shows no other focus. The bounds must be at least
 * the well plus the collar high (the content's padding is [fieldContentPadding]).
 */
@Composable
fun Modifier.fieldCollar(
    focused: Boolean = false,
    error: Boolean = false,
): Modifier {
    val colors = PlumTheme.colors
    val progress = remember { Animatable(if (focused) 1f else 0f) }
    val motionOn = PlumTheme.motion.isOn
    LaunchedEffect(focused, motionOn) {
        progress.animateTo(if (focused) 1f else 0f, if (motionOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.Decelerate) else snap())
    }
    return drawBehind {
        val pad = CollarSize.Field.padding
        val l = pad.calculateLeftPadding(layoutDirection).toPx()
        val t = pad.calculateTopPadding().toPx()
        val b = pad.calculateBottomPadding().toPx()
        val corner = minOf(FieldDimens.radius.toPx(), (size.height - t - b) / 2)
        drawCollar(
            look = colors.collar,
            collarSize = CollarSize.Field,
            cornerRadius = corner,
            focus = progress.value,
            grooveOverride = if (error) colors.danger else null,
            accent = colors.accent,
            glow = colors.partGlow,
        )
        val seat = colors.collar.seat
        val well = if (error) FieldDimens.tint(seat.fieldWell, colors.danger) else seat.fieldWell
        inset(l, t, l, b) { drawCssFace(CssFace(listOf(well), listOf(seat.fieldShade)), Offset.Zero, size, corner) }
    }
}

/** The room a field's content keeps inside [fieldCollar]: the collar, then the well's side padding. */
fun fieldContentPadding(): PaddingValues {
    val c = CollarSize.Field
    return PaddingValues(start = c.sidePadding + FieldDimens.wellPadX, end = c.sidePadding + FieldDimens.wellPadX, top = c.topPadding, bottom = c.bottomPadding)
}
