package com.plumemu.home

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.coreui.theme.LaunchOrigin
import com.plumemu.coreui.theme.LocalLaunchOrigin
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.launcher.LaunchAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.plumemu.coreui.components.plainLayer

/**
 * The launch moment: when a game or app starts from the home, the home moves into the focused cover or row (it grows
 * by [PlumMotion.LAUNCH_DEPTH] about that item's centre) and dims to [PlumMotion.LAUNCH_DIM] over [PlumMotion.SCREEN_MS].
 * A game's window is asked for [PlumMotion.LAUNCH_LEAD_MS] into it, so Android's own opening animation takes over from
 * ours; where Android honours it the window also grows out of the same rectangle (`makeScaleUpAnimation`). An app opens
 * on the main thread, which must not wait: it is asked for at once. A start that throws gives the home back at once and
 * throws on; one whose window has not come after [LAUNCH_GIVE_UP_MS] gives it back too. With motion off: no animation,
 * no lead.
 */
@Composable
internal fun ProvideLaunchOrigin(content: @Composable () -> Unit) {
    val origin = remember { LaunchOrigin() }
    val view = LocalView.current
    val motionOn = rememberUpdatedState(PlumTheme.motion.isOn)
    val scope = rememberCoroutineScope()
    val launch = remember { LaunchMotion(scope) }
    DisposableEffect(origin, view) {
        val host: (Intent) -> Unit = { intent -> launch.start(view, origin, motionOn.value, intent) }
        LaunchAnimation.host = host
        onDispose { if (LaunchAnimation.host === host) LaunchAnimation.host = null }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Back at the front: the home is whole again (its entrance settles it in, rememberHomeEntrances).
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { launch.leave.snapTo(0f) } }
    // Still in front a while after the start: the window never came, so the home comes back as it left.
    LaunchedEffect(launch.leave.targetValue) {
        if (launch.leave.targetValue == 1f) {
            delay(LAUNCH_GIVE_UP_MS)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) launch.leave.animateTo(0f, tween(PlumMotion.SCREEN_MS, easing = PlumMotion.Decelerate))
        }
    }
    val colors = PlumTheme.colors
    Box(
        Modifier
            .onPlaced { launch.placed = it }
            .plainLayer {
                val gone = launch.leave.value
                if (gone != 0f) {
                    val scale = 1f + PlumMotion.LAUNCH_DEPTH * gone
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = launch.pivot
                }
            }
            // The dim is a veil of the page colour over the opaque home, never the layer's alpha: a full-screen alpha
            // draws offscreen every frame.
            .drawWithContent {
                drawContent()
                val veil = (1f - PlumMotion.LAUNCH_DIM) * launch.leave.value
                if (veil > 0f) drawRect(colors.bg, alpha = veil)
            },
    ) {
        CompositionLocalProvider(LocalLaunchOrigin provides origin, content = content)
    }
}

/** Long enough for a slow emulator's window to come; then a start still in front gives the home back. */
private const val LAUNCH_GIVE_UP_MS = 2_500L

/** The home's part of a start: its motion into the item, and the start itself on the caller's thread. */
private class LaunchMotion(
    private val scope: CoroutineScope,
) {
    val leave = Animatable(0f)

    /** The home's box, for the item's centre as a share of it. Main thread only. */
    var placed: LayoutCoordinates? = null

    var pivot = TransformOrigin.Center
        private set

    fun start(
        view: View,
        origin: LaunchOrigin,
        isMotionOn: Boolean,
        intent: Intent,
    ) {
        if (!isMotionOn) {
            view.context.startActivity(intent, ActivityOptions.makeCustomAnimation(view.context, 0, 0).toBundle())
            return
        }
        val onMain = Looper.myLooper() == Looper.getMainLooper()
        var options: Bundle? = null
        // Views and layout are read on the main thread only: the starting thread waits for them, within the lead.
        val ready = CountDownLatch(1)
        val begin = {
            options = scaleUpFrom(view, origin)
            pivot = pivotOf(origin, placed)
            scope.launch { leave.animateTo(1f, tween(PlumMotion.SCREEN_MS, easing = PlumMotion.Decelerate)) }
            ready.countDown()
        }
        if (onMain) {
            begin()
        } else {
            view.post(begin)
            Thread.sleep(PlumMotion.LAUNCH_LEAD_MS)
            ready.await(PlumMotion.LAUNCH_LEAD_MS, TimeUnit.MILLISECONDS)
        }
        try {
            view.context.startActivity(intent, options)
        } catch (e: RuntimeException) {
            // Refused (no such activity, not allowed): the home comes back at once and the failure is told as before.
            view.post { scope.launch { leave.snapTo(0f) } }
            throw e
        }
    }
}

/** The focused item's centre as a share of [box], from [origin]'s window rectangle; the box's centre without one. */
private fun pivotOf(
    origin: LaunchOrigin,
    box: LayoutCoordinates?,
): TransformOrigin {
    val from = origin.window
    if (from == null || box == null || !box.isAttached || box.size.width == 0 || box.size.height == 0) return TransformOrigin.Center
    val centre = box.windowToLocal(Offset(from.center.x, from.center.y))
    return TransformOrigin((centre.x / box.size.width).coerceIn(0f, 1f), (centre.y / box.size.height).coerceIn(0f, 1f))
}

private fun scaleUpFrom(
    view: View,
    origin: LaunchOrigin,
): Bundle? {
    val from = origin.window ?: return null
    return ActivityOptions
        .makeScaleUpAnimation(view, from.left.roundToInt(), from.top.roundToInt(), from.width.roundToInt(), from.height.roundToInt())
        .toBundle()
}
