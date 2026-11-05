package com.plumemu.coreui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CssFace
import com.plumemu.coreui.theme.CssShadow
import com.plumemu.coreui.theme.SeatLook
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SwitchSliderDimens as D

/** Where a touch at [x] (px) puts a slider [width] px wide whose knob (with its ring) is [knob] px across: the knob's centre runs from half a knob in to half a knob short of the end. */
internal fun sliderFraction(
    x: Float,
    width: Float,
    knob: Float,
): Float = if (width <= knob) 0f else ((x - knob / 2) / (width - knob)).coerceIn(0f, 1f)

/**
 * The V2 slider (docs/design/rules.md › Slider): a sunken track in a lip, the accent filling it up to a round knob (a cap face with
 * thickness inside a groove ring). [value] is 0..1. [focused] lights the ring in the accent with the halo and shows [valueText]
 * in a small bubble above the knob; [hue] draws the colour spectrum in the track instead of the fill; `!enabled` (locked) is the empty
 * sunken track, no knob and no fill, ignoring touch. Touch: a tap or a horizontal drag sets the value ([onValueChange], 0..1; [onValueChangeFinished]
 * when the finger lifts). The gamepad's left and right are the caller's: it moves [value] and passes it back.
 * Width comes from [modifier] ([D.sliderDefaultWidth] if none); the height is the knob's with its ring. The bubble is drawn
 * above that height (about 32 dp): the parent must not clip it and leaves the room.
 */
@Composable
fun PlumSlider(
    value: Float,
    focused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueText: String = "",
    hue: Boolean = false,
    onValueChange: (Float) -> Unit = {},
    onValueChangeFinished: () -> Unit = {},
) {
    val colors = PlumTheme.colors
    val motion = PlumTheme.motion
    val measurer = rememberTextMeasurer()
    val metaStyle = PlumTheme.type.meta
    val focus = animateFloatAsState(if (focused && enabled) 1f else 0f, if (motion.isOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.EaseOut) else snap(), label = "slider focus")
    val v = value.coerceIn(0f, 1f)
    val change by rememberUpdatedState(onValueChange)
    val finished by rememberUpdatedState(onValueChangeFinished)
    val ringed = D.sliderKnob + D.sliderKnobRing * 2
    Box(
        (if (modifier == Modifier) Modifier.width(D.sliderDefaultWidth) else modifier)
            .height(ringed)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(v, 0f..1f)
                if (valueText.isNotEmpty()) stateDescription = valueText
                if (!enabled) disabled()
            }.then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures {
                                change(sliderFraction(it.x, size.width.toFloat(), ringed.toPx()))
                                finished()
                            }
                        }.pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { change(sliderFraction(it.x, size.width.toFloat(), ringed.toPx())) },
                                onDragEnd = { finished() },
                                onDragCancel = { finished() },
                                onHorizontalDrag = { c, _ ->
                                    change(sliderFraction(c.position.x, size.width.toFloat(), ringed.toPx()))
                                    c.consume()
                                },
                            )
                        }
                },
            ).drawBehind {
                val seat = colors.collar.seat
                val accent = colors.accent
                val ring = D.sliderKnobRing.toPx()
                val knob = D.sliderKnob.toPx()
                val lip = D.sliderLip.toPx()
                val trackH = D.sliderTrack.toPx()
                val trackTop = size.height / 2 - trackH / 2
                val lipTop = trackTop - D.sliderLipTop.toPx()
                val lipSize = Size(size.width, D.sliderLipTop.toPx() + trackH + D.sliderLipBottom.toPx())
                val corner = D.sliderLipCorner.toPx()
                val f = focus.value
                // The collar is the LIP alone (`padding: 7px 8px 8px`); focused it glows `0 0 22px`.
                if (f > 0f) drawCssShadows(listOf(CssShadow(colors.partGlow, 0.dp, CollarDimens.glowBlur)), Offset(0f, lipTop), lipSize, corner, f)
                drawLip(seat, Offset(0f, lipTop), lipSize, corner)
                val trackAt = Offset(lip, trackTop)
                val trackW = size.width - 2 * lip
                val trackR = trackH / 2
                val cx = (knob + 2 * ring) / 2 + (size.width - knob - 2 * ring) * v
                val cy = size.height / 2
                if (hue) {
                    // The spectrum is the track itself, `inset 0 1px 3px .45`; locked at half opacity.
                    val a = if (enabled) 1f else 0.5f
                    drawCssFace(CssFace(D.hueStops, listOf(seat.hueShade)), trackAt, Size(trackW, trackH), trackR, a, Brush.horizontalGradient(D.hueStops, startX = trackAt.x, endX = trackAt.x + trackW))
                } else {
                    drawCssFace(CssFace(listOf(seat.well), listOf(seat.wellShade)), trackAt, Size(trackW, trackH), trackR)
                    if (enabled) {
                        // The fill runs to the knob's centre: `linear-gradient(accent, accentLight)`, `inset 0 1px 2px .35`, `0 0 10px` glow.
                        val fillW = (cx - trackAt.x).coerceIn(trackH, trackW)
                        val fillSize = Size(fillW, trackH)
                        drawCssShadows(listOf(CssShadow(colors.partGlow, 0.dp, D.fillGlowBlur)), trackAt, fillSize, trackR)
                        drawCssFace(CssFace(listOf(accent, SeatLook.accentLight(accent)), listOf(seat.focusShade)), trackAt, fillSize, trackR)
                    }
                }
                val r = knob / 2 + ring
                val ringAt = Offset(cx - r, cy - r)
                // Focused, the knob's 2 px ring turns accent with a `0 0 14px` glow.
                if (f > 0f) {
                    drawCssShadows(listOf(CssShadow(colors.partGlow, 0.dp, D.knobGlowBlur)), ringAt, Size(2 * r, 2 * r), r, f)
                    drawCircle(accent.copy(alpha = accent.alpha * f), r, Offset(cx, cy))
                }
                val knobAt = Offset(cx - knob / 2, cy - knob / 2)
                if (enabled) drawCssCap(seat.buttonCap, knobAt, Size(knob, knob)) else drawCircle(seat.capOff, knob / 2, Offset(cx, cy))
                if (f > 0f && valueText.isNotEmpty()) drawBubble(measurer, valueText, metaStyle.copy(color = colors.strong), cx, cy - r - D.bubbleGap.toPx(), f, colors.sel2, colors.panelEdge, colors.panelShadow)
            },
    )
}


/** The value bubble in the panel's look, centred on [cx], its bottom edge [bottom] px from the top of the slider's bounds (above them when negative). */
private fun DrawScope.drawBubble(
    measurer: TextMeasurer,
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    cx: Float,
    bottom: Float,
    alpha: Float,
    fill: Color,
    edge: Color,
    shadow: Color,
) {
    val w = D.bubbleWidth.toPx()
    val h = D.bubbleHeight.toPx()
    val top = bottom - h
    drawPanelChrome(fill.copy(alpha = alpha), edge.copy(alpha = edge.alpha * alpha), shadow.copy(alpha = shadow.alpha * alpha), D.bubbleCorner.toPx(), cx - w / 2, top, w, h, bottomReach = 4f * density)
    val laid = measurer.measure(text, style, maxLines = 1)
    drawText(laid, topLeft = Offset(cx - laid.size.width / 2f, top + (h - laid.size.height) / 2f))
}
