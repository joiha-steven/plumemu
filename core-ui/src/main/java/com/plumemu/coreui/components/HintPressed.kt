package com.plumemu.coreui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext

/**
 * The key caps whose button is held right now ("A", "L1", "START", "↑"...), provided once at the top of
 * the app from core-input's `InputRouter.held`. A [Hint] reads it only inside a coroutine and its draw
 * and layer blocks, so a press never recomposes a screen. The default holds nothing (previews, tests).
 */
val LocalHeldKeys = compositionLocalOf<State<Set<String>>> { NothingHeld }

private val NothingHeld: State<Set<String>> = mutableStateOf(emptySet())

private const val ARROWS = "↑↓←→"

/**
 * The key names a cap stands for: "L1 R1" is both, "↑↓" is up and down, "›" is the right arrow.
 * A hint whose cap text is none of the names a button has simply never lights.
 */
fun keyCapNames(keyCap: String): Set<String> =
    keyCap.split(' ').filter { it.isNotEmpty() }.flatMapTo(mutableSetOf()) { token ->
        when {
            token == "›" -> listOf("→")
            token.all { it in ARROWS } -> token.map { it.toString() }
            else -> listOf(token)
        }
    }

/**
 * How pressed a [Hint] looks: [progress] goes 0 (at rest) to 1 (fully pressed) and only draw and
 * layer blocks read it. [touching] is true while a finger is down on the hint.
 */
@Stable
internal class HintPress(
    initial: Float,
) {
    val progress = Animatable(initial)
    var touching by mutableStateOf(false)
}

/**
 * Drives [HintPress.progress] from the held keys named by [keyCap] and from touch: in 60 ms ease-out, out
 * 100 ms ease-out, but not before [PlumMotion.PRESS_MIN_VISIBLE_MS] have passed since the press began. With
 * animations off the change is instant (the scale is dropped by the caller).
 */
@Composable
internal fun rememberHintPress(keyCap: String): HintPress {
    val held = LocalHeldKeys.current
    val names = remember(keyCap) { keyCapNames(keyCap) }
    // A hint that appears while its button is still down (A opened this screen) starts pressed. The read is
    // not observed: it must not subscribe the composition.
    val press = remember { HintPress(if (Snapshot.withoutReadObservation { held.value.any { it in names } }) 1f else 0f) }
    val motionOn by rememberUpdatedState(PlumTheme.motion.isOn)
    LaunchedEffect(held, names, press) {
        // The states are read here, in a coroutine, not in composition: a key going down recomposes nothing.
        var pressedAt = Long.MIN_VALUE / 2
        snapshotFlow { press.touching || held.value.any { it in names } }
            .distinctUntilChanged()
            .collectLatest { down ->
                if (down) {
                    // Times come from the frame clock (not the system clock), so tests on a virtual clock agree.
                    coroutineScope {
                        launch { pressedAt = withFrameMillis { it } }
                        // A quick release must not cut the dip short: the way in always finishes.
                        withContext(NonCancellable) {
                            press.progress.animateTo(1f, if (motionOn) tween(PlumMotion.PRESS_IN_MS, easing = PlumMotion.EaseOut) else tween(0))
                        }
                    }
                } else if (!motionOn) {
                    // Animations off: the fill changes at once, no minimum visible time.
                    press.progress.snapTo(0f)
                } else {
                    val visibleMs = withFrameMillis { it } - pressedAt
                    if (visibleMs < PlumMotion.PRESS_MIN_VISIBLE_MS) delay(PlumMotion.PRESS_MIN_VISIBLE_MS - visibleMs)
                    press.progress.animateTo(0f, tween(PlumMotion.PRESS_OUT_MS, easing = PlumMotion.EaseOut))
                }
            }
    }
    return press
}

/** Marks the hint pressed while a finger is down on it, without taking the touch from the tap handler. */
internal fun Modifier.trackTouch(press: HintPress): Modifier =
    pointerInput(press) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            press.touching = true
            try {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            } finally {
                press.touching = false
            }
        }
    }
