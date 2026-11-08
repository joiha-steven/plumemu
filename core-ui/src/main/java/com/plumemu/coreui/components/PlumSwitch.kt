package com.plumemu.coreui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.plumemu.coreui.theme.FlatPartDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.SwitchSliderDimens as D

/**
 * The switch, flat (docs/design/rules.md > Flat parts): a solid track (the accent when [on], the sunken ladder tone with a hairline that
 * shows on every set when off), a solid light thumb with a crisp 1 dp shadow that slides with [PlumTheme.motion]; no collar. [focused] draws
 * the glued rim round the track; `!enabled` (locked) is the off thumb, never an opacity fade. The caller passes the tap or the confirm key
 * through [modifier] (core-ui does not read input). Same shape as the app's `SetupSwitch`: swap by passing `isOn`/`isFocused`
 * as [on]/[focused] and its `stateText` through `modifier.semantics { stateDescription = ... }`. The rim's glow and shadow reach
 * `FlatPartDimens.focusRimRoom` past the track: leave that unclipped.
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
    val focus = animateFloatAsState(if (focused && enabled) 1f else 0f, if (motion.isOn) tween(PlumMotion.FOCUS_GROW_MS, easing = PlumMotion.Decelerate) else snap(), label = "switch focus")
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
                val lit = if (on && enabled) travel.value else 0f
                // The flat look (docs/design/rules.md > Flat parts): a solid track, a solid thumb, the rim on focus; no collar.
                drawFlatSwitch(colors.collar.flat, colors.accent, Offset(lip, lip), Size(trackW, trackH), D.switchKnob.toPx(), lit, travel.value, focus.value, enabled)
            },
    )
}
