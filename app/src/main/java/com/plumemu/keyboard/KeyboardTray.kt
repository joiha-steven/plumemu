package com.plumemu.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import com.plumemu.R
import com.plumemu.coreui.components.keyboard.KeyboardEvent
import com.plumemu.coreui.components.keyboard.KeyboardLabels
import com.plumemu.coreui.components.keyboard.KeyboardSpec
import com.plumemu.coreui.components.keyboard.KeyboardState
import com.plumemu.coreui.components.keyboard.PlumKeyboard
import com.plumemu.coreui.theme.PlumTheme

/**
 * Plumemu's keyboard as a screen shows it: the tray along the screen's
 * bottom, full width and [com.plumemu.coreui.theme.PlumLayout.keyboardTrayHeight] high, its keys
 * from the bottom of the space this is given and the [belowContent] under it (the hint bar's gap,
 * the hint bar and the bottom margin) on the tray, where the hint bar is drawn over it. What lies
 * under the tray runs on underneath it.
 *
 * It slides up when [isUp] turns on and down when it turns off, by translation only, 180 ms
 * ease-out, or at once with motion off; once down it leaves the composition, and while
 * up nothing runs. The tray's key argument says what it belongs to (Search, a panel's field): a new one starts
 * a new slide. Taps on its keys go to [onEvent]; a tap anywhere else on the tray stops there, never
 * reaching what is underneath. Search and the game panel's fields both use it.
 */
@Composable
internal fun KeyboardTray(
    owner: Any,
    isUp: Boolean,
    keys: KeyboardState,
    spec: KeyboardSpec,
    onEvent: (KeyboardEvent) -> Unit,
    belowContent: Dp,
    tag: String,
) {
    val motion = PlumTheme.motion
    // 0: up, 1: down by its own height.
    val down = remember(owner) { Animatable(if (isUp && !motion.isOn) 0f else 1f) }
    LaunchedEffect(owner, isUp, motion.isOn) { down.animateTo(if (isUp) 0f else 1f, motion.slideSpec()) }
    val upNow by rememberUpdatedState(isUp)
    val isShown by remember(owner) { derivedStateOf { upNow || down.value < 1f } }
    if (!isShown) return
    val trayHeight = PlumTheme.layout.keyboardTrayHeight
    val labels =
        KeyboardLabels(
            space = stringResource(R.string.keyboard_space),
            shift = stringResource(R.string.keyboard_shift),
            delete = stringResource(R.string.keyboard_delete),
            hide = stringResource(R.string.keyboard_hide),
        )
    Layout(
        content = {
            PlumKeyboard(
                state = keys,
                spec = spec,
                labels = labels,
                onEvent = onEvent,
                modifier =
                    Modifier
                        // Taps between the keys land on the tray, not on what is under it.
                        .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
                        .testTag(tag),
            )
        },
    ) { measurables, constraints ->
        val trayPx = trayHeight.roundToPx()
        val tray = measurables.single().measure(Constraints.fixed(constraints.maxWidth, trayPx))
        layout(constraints.maxWidth, constraints.maxHeight) {
            tray.placeWithLayer(0, constraints.maxHeight + belowContent.roundToPx() - trayPx) { translationY = down.value * trayPx }
        }
    }
}
