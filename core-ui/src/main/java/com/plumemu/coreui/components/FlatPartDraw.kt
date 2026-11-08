package com.plumemu.coreui.components

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.FlatPartLook
import kotlin.math.max

/**
 * The flat parts (docs/design/rules.md > Flat parts), drawn with plain shapes: nothing cached at rest, no layer, no clip. A focus adds one
 * cached image (the rim's glow and depth shadow, [drawGluedRim]) and one stroke.
 */

/** A hairline: one pixel at least, half a dp otherwise. */
private fun DrawScope.hairline(): Float = max(1f, FlatPartDimens.flatHairline.toPx())

/** A solid pill or box with its hairline inside its edge. */
private fun DrawScope.solid(
    at: Offset,
    size: Size,
    corner: Float,
    fill: Color,
    edge: Color?,
    edgeWidth: Float = hairline(),
) {
    drawRoundRect(fill, at, size, CornerRadius(corner))
    if (edge == null) return
    val h = edgeWidth
    drawRoundRect(edge, Offset(at.x + h / 2, at.y + h / 2), Size(size.width - h, size.height - h), CornerRadius(max(0f, corner - h / 2)), style = Stroke(h))
}

/**
 * A button's cap over the whole draw area ([size], a pill): the page's secondary fill or the accent, a hairline, on a light page a 1 dp drop
 * shadow, [press] (0..1) taking it to the pressed fill, [focus] (0..1) drawing the rim. Nothing moves, nothing scales. A disabled cap is the
 * flat off tone with no edge, no shadow.
 */
internal fun DrawScope.drawFlatButton(
    look: FlatPartLook,
    accent: Color,
    primary: Boolean,
    enabled: Boolean,
    press: Float,
    focus: Float,
) {
    val corner = size.height / 2
    if (!enabled) {
        solid(Offset.Zero, size, corner, look.off, null)
        return
    }
    val a = if (primary) look.primary(accent) else null
    val fill = if (a != null) lerp(a.fill, a.pressed, press) else lerp(look.secondary, look.secondaryPressed, press)
    val edge = a?.edge ?: look.secondaryEdge
    if (!look.dark) drawRoundRect(look.shade.copy(alpha = FlatPartLook.LIGHT_BUTTON_SHADOW_ALPHA), Offset(0f, FlatPartDimens.flatDrop.toPx()), size, CornerRadius(corner))
    solid(Offset.Zero, size, corner, fill, edge)
    drawGluedRim(Offset.Zero, size, corner, look.rim, look.dark, look.shade, focus)
}

/**
 * A switch: the track at [at] of [track] (solid accent with a hairline when [lit]; off, the sunken tone with an outline of
 * [FlatPartDimens.switchOffOutline] inside its bounds; cross-faded by [lit]), the thumb with a crisp 1 dp shadow and no glow, the rim round
 * the track when [focus]. The on thumb is the light solid of diameter [knob]; the off one is the outline's colour, [FlatPartDimens.switchOffKnobShrink]
 * smaller. [travel] (0..1) moves it. A locked switch has the disabled tone ([enabled] false).
 */
internal fun DrawScope.drawFlatSwitch(
    look: FlatPartLook,
    accent: Color,
    at: Offset,
    track: Size,
    knob: Float,
    lit: Float,
    travel: Float,
    focus: Float,
    enabled: Boolean,
) {
    val corner = track.height / 2
    val a = look.primary(accent)
    val outline = FlatPartDimens.switchOffOutline.toPx()
    solid(at, track, corner, lerp(look.track, a.fill, lit), lerp(look.trackEdge, a.edge, lit), outline + (hairline() - outline) * lit)
    val offKnob = knob - FlatPartDimens.switchOffKnobShrink.toPx()
    val d = offKnob + (knob - offKnob) * lit
    val offGap = (track.height - offKnob) / 2
    val onGap = (track.height - knob) / 2
    val cx = at.x + (offGap + offKnob / 2) + ((track.width - onGap - knob / 2) - (offGap + offKnob / 2)) * travel
    val cy = at.y + track.height / 2
    val r = d / 2
    if (enabled) {
        val alpha = if (look.dark) FlatPartLook.THUMB_SHADOW_ALPHA_DARK else FlatPartLook.THUMB_SHADOW_ALPHA_LIGHT
        drawCircle(look.shade.copy(alpha = alpha), r, Offset(cx, cy + FlatPartDimens.flatDrop.toPx()))
    }
    drawCircle(if (enabled) lerp(look.thumbOff, look.thumb, lit) else look.off, r, Offset(cx, cy))
    drawGluedRim(at, track, corner, look.rim, look.dark, look.shade, focus)
}
