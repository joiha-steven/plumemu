package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.launch

/**
 * How pressed ([press], 0..1) and how raised ([raise], 0..1: focused or under a mouse) a key, button or knob is. Only
 * draw and layer blocks read them, so a press redraws that one item and recomposes nothing. Made by
 * [rememberKeyState], driven by [keyDrive].
 */
@Stable
class KeyState internal constructor() {
    internal val dip = PressDip(PlumMotion.KEY_PRESS_OUT_MS)
    internal val lift = Animatable(0f)

    /** True while a mouse or stylus is over the key. */
    internal var hovered = false

    internal suspend fun liftTo(up: Boolean, motionOn: Boolean) {
        val target = if (up) 1f else 0f
        if (motionOn) lift.animateTo(target, tween(PlumMotion.RAISE_MS, easing = PlumMotion.EaseOut)) else lift.snapTo(target)
    }

    val press: Float get() = dip.progress.value
    val raise: Float get() = lift.value
}

@Composable
fun rememberKeyState(): KeyState = remember { KeyState() }

/**
 * Drives [state]: a finger down presses it (after the tap delay, so a scroll never flashes it), the confirm button
 * presses it while it is [focused] and [answersButton], and focus or a mouse hover raises it (hover never moves the
 * focus). A press that is cancelled (drag off, focus lost, the item leaves the screen) returns to rest. With
 * animations off every change is instant. Put it before the draw modifier that reads [state].
 */
fun Modifier.keyDrive(
    state: KeyState,
    focused: Boolean,
    answersButton: Boolean = true,
): Modifier =
    composed {
        val scope = rememberCoroutineScope()
        val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
        val isFocused by rememberUpdatedState(focused)
        if (focused && answersButton) {
            val held = LocalHeldKeys.current
            val cap = LocalConfirmCap.current
            LaunchedEffect(held, cap, state) { driveConfirmPress(state.dip, held, cap, { motionOn }) { Unit } }
        }
        // Nothing runs for an unfocused key nobody hovers: the lift moves on a focus change, a hover enter or exit.
        LaunchedEffect(focused) {
            if (focused || state.lift.value != 0f) state.liftTo(focused || state.hovered, motionOn)
        }
        this.then(HoverLiftElement(state) { up -> scope.launch { state.liftTo(up || isFocused, motionOn) } })
            .touchPress(state.dip, scope, motionOn = { motionOn })
    }

private class HoverLiftElement(val state: KeyState, val onHover: (Boolean) -> Unit) : ModifierNodeElement<HoverLiftNode>() {
    override fun create() = HoverLiftNode(state, onHover)

    override fun update(node: HoverLiftNode) {
        node.state = state
        node.onHover = onHover
    }

    override fun equals(other: Any?) = other is HoverLiftElement && other.state === state

    override fun hashCode() = System.identityHashCode(state)
}

/** Watches pointer enter and exit without a coroutine (a mouse or stylus; a finger never hovers). */
private class HoverLiftNode(var state: KeyState, var onHover: (Boolean) -> Unit) : Modifier.Node(), PointerInputModifierNode {
    override fun onPointerEvent(pointerEvent: PointerEvent, pass: PointerEventPass, bounds: IntSize) {
        if (pass != PointerEventPass.Main) return
        val up =
            when (pointerEvent.type) {
                PointerEventType.Enter -> true
                PointerEventType.Exit -> false
                else -> return
            }
        if (state.hovered == up) return
        state.hovered = up
        onHover(up)
    }

    override fun onCancelPointerInput() {
        if (!state.hovered) return
        state.hovered = false
        onHover(false)
    }
}

/**
 * A button with a flat face: [keyDrive] plus the drawing. The face, and the content on it, move as [state] says; the
 * colour flash of a press is the text colour at [PlumMotion.PRESS_FILL_MIX]. Layout never changes. Put it after the
 * item's size and before its padding, and a focus ring outside it.
 */
fun Modifier.flatKey(
    state: KeyState,
    focused: Boolean,
    face: (PlumComposeColors) -> FlatFace,
    corner: Dp,
    colors: PlumComposeColors,
    answersButton: Boolean = true,
): Modifier =
    keyDrive(state, focused, answersButton).drawWithContent {
        val press = state.press
        val raise = state.raise
        drawFlatFace(
            face(colors),
            overlay = colors.text.copy(alpha = PlumMotion.PRESS_FILL_MIX * press),
            corner = corner.toPx(),
            press = press,
            raise = raise,
        )
        translate(top = flatFaceShift(press, raise)) { this@drawWithContent.drawContent() }
    }
