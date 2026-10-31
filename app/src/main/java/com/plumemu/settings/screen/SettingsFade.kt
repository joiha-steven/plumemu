package com.plumemu.settings.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.PlumTheme

/** The fade's spec: the strip's ease-out curve over [PlumMotion.FADE_IN_MS], or an instant jump with motion off. */
internal fun <T> PlumMotionState.fadeSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.FADE_IN_MS, easing = PlumMotion.EaseOut) else snap()

/**
 * Fades [content] in from clear whenever [key] changes (and when it first appears). Only the
 * layer's alpha changes, never layout; with motion off, or [isFading] false, it is
 * drawn at once. A caller that sometimes does not fade passes [isFading] instead of leaving this
 * out: the content must sit at the same place in the composition either way, or its
 * `rememberSaveable` state is not found again (the key follows the position).
 */
@Composable
internal fun FadeIn(
    key: Any?,
    modifier: Modifier = Modifier,
    isFading: Boolean = true,
    content: @Composable () -> Unit,
) {
    val motion = PlumTheme.motion
    val alpha = remember(key) { Animatable(if (motion.isOn && isFading) 0f else 1f) }
    // Nothing to animate when not fading: no frame callbacks for 110 ms on first launch.
    LaunchedEffect(alpha, motion.isOn) { if (alpha.value != 1f) alpha.animateTo(1f, motion.fadeSpec()) }
    Box(modifier.graphicsLayer { this.alpha = alpha.value }) { content() }
}

/** Counts what recomposes, so a test can hold Settings to "a focus move redraws only the rows that change". */
internal object SettingsRecomposeProbe {
    var strips = 0
    var sectionRows = 0
    var optionRows = 0

    /** Colors' panel tiles (`SettingsColorSetsPanel.kt`). */
    var colorTiles = 0

    fun reset() {
        strips = 0
        sectionRows = 0
        optionRows = 0
        colorTiles = 0
    }
}
