package com.plumemu.coreui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.CssFace
import com.plumemu.coreui.theme.CssShadow
import com.plumemu.coreui.theme.SeatLook
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SwitchSliderDimens as D

/**
 * The V2 switch (docs/design/rules.md › Switch): a sunken pill track in a lip, the knob a cap face with thickness that slides
 * with [PlumTheme.motion]. [on] turns the track to the accent; [focused] lights a ring on the lip's edge with the halo;
 * `!enabled` (locked) is the off knob and a plain track, never an opacity fade. The caller passes the tap or the confirm key
 * through [modifier] (core-ui does not read input). Same shape as the app's `SetupSwitch`: swap by passing `isOn`/`isFocused`
 * as [on]/[focused] and its `stateText` through `modifier.semantics { stateDescription = ... }`. Reaches the focus halo
 * outside its bounds: leave that unclipped.
 */
@Composable
fun PlumSwitch(
    on: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = PlumTheme.colors
    val motion = PlumTheme.motion
    val travel = animateFloatAsState(if (on) 1f else 0f, motion.slideSpec(), label = "switch knob")
    val focus = animateFloatAsState(if (focused && enabled) 1f else 0f, if (motion.isOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.EaseOut) else snap(), label = "switch focus")
    Box(
        modifier
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(on)
                if (!enabled) disabled()
            }.size(
                D.switchTrackWidth + D.switchLip * 2,
                D.switchTrackHeight + D.switchLip + D.switchLipBottom,
            ).drawBehind {
                val lip = D.switchLip.toPx()
                val trackW = D.switchTrackWidth.toPx()
                val trackH = D.switchTrackHeight.toPx()
                val seat = colors.collar.seat
                val f = focus.value
                drawCollar(colors.collar, CollarSize.Switch, cornerRadius = trackH / 2, focus = f, accent = colors.accent, glow = colors.partGlow, focusRing = D.switchFocusRing)
                val at = Offset(lip, lip)
                val track = Size(trackW, trackH)
                val lit = if (on && enabled) travel.value else 0f
                // Off: the WELL. On: `linear-gradient(accent, accentLight)`, `inset 0 2px 4px .40` and a `0 0 12px` glow; cross-faded as the knob slides.
                if (lit < 1f) drawCssFace(CssFace(listOf(seat.well), listOf(seat.wellShade)), at, track, trackH / 2, 1f - lit)
                if (lit > 0f) {
                    val accent = colors.accent
                    drawCssShadows(listOf(CssShadow(colors.partGlow, 0.dp, CollarDimens.grooveGlowBlur, glow = true)), at, track, trackH / 2, lit)
                    drawCssFace(CssFace(listOf(accent, SeatLook.accentLight(accent)), listOf(seat.switchOnShade)), at, track, trackH / 2, lit)
                }
                val knob = D.switchKnob.toPx()
                val gap = (trackH - knob) / 2
                val x = lip + gap + (trackW - knob - 2 * gap) * travel.value
                // The enabled knob has a solid edge under its body; lifting by half of it centres body plus edge in the groove (the locked knob is flat).
                val knobAt = Offset(x, lip + gap - if (enabled) D.switchKnobLift.toPx() else 0f)
                if (enabled) drawCssCap(seat.buttonCap, knobAt, Size(knob, knob)) else drawCssFace(CssFace(listOf(seat.capOff), listOf(seat.lockedKnobShade)), knobAt, Size(knob, knob), knob / 2)
            },
    )
}

