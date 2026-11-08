package com.plumemu.settings.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumMotionState
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.components.plainLayer

/** The fade's spec: the one curve (Decelerate) over [PlumMotion.FADE_IN_MS], or an instant jump with motion off. */
internal fun <T> PlumMotionState.fadeSpec(): AnimationSpec<T> = if (isOn) tween(PlumMotion.FADE_IN_MS, easing = PlumMotion.Decelerate) else snap()

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
    /**
     * A whole screen arriving: it also settles from this much smaller (negative, going in) or larger (positive, coming back)
     * to its size, over [PlumMotion.SCREEN_MS] on [PlumMotion.Decelerate], under a veil of the page colour (it must
     * stand on the page colour, as the main screen and Settings do). 0: the plain 110 ms fade.
     */
    depth: Float = 0f,
    /**
     * While true, [content] is composed and laid out but not drawn (the page colour shows in its place) and the fade waits:
     * a screen composed over several frames ([SettingsOpening]) starts its motion once it is whole, never part-drawn.
     */
    isHeld: Boolean = false,
    content: @Composable () -> Unit,
) {
    val motion = PlumTheme.motion
    val alpha = remember(key) { Animatable(if (motion.isOn && isFading) 0f else 1f) }
    // Nothing to animate when not fading: no frame callbacks for 110 ms on first launch.
    LaunchedEffect(alpha, motion.isOn, isHeld) {
        if (!isHeld && alpha.value != 1f) alpha.animateTo(1f, if (depth != 0f && motion.isOn) tween(PlumMotion.SCREEN_MS, easing = PlumMotion.Decelerate) else motion.fadeSpec())
    }
    val colors = PlumTheme.colors
    val held = if (isHeld) Modifier.drawWithContent { drawRect(colors.bg) } else Modifier
    Box(
        if (depth == 0f) {
            modifier.then(held).plainLayer { this.alpha = alpha.value }
        } else {
            // A whole screen: drawn opaque under a veil of the page colour that thins out, the same pixels as fading it
            // in over the page, without the offscreen layer a full-screen alpha costs each frame.
            modifier
                .then(held)
                .plainLayer {
                    val scale = 1f + depth * (1f - alpha.value)
                    scaleX = scale
                    scaleY = scale
                }.drawWithContent {
                    drawContent()
                    val veil = 1f - alpha.value
                    if (veil > 0f) drawRect(colors.bg, alpha = veil)
                }
        },
    ) { content() }
}

/**
 * Settings opening over several frames instead of one (on the Nova one 62 ms frame composed and measured all of it while
 * the main screen was disposed): the press's frame only covers the main screen, the next ones compose Settings' bars, then
 * its sections column, then its options column; then the main screen leaves and the screen settles in ([FadeIn]'s isHeld).
 */
internal object SettingsOpening {
    /** From this stage Settings is composed, its sections and options columns held back. */
    const val SCREEN = 1

    /** From this stage the sections column is composed. */
    const val SECTIONS = 2

    /** From this stage the options column is composed. */
    const val OPTIONS = 3

    /** Settings is whole: its motion starts and the main screen under it leaves. */
    const val WHOLE = 4
}

/** How far Settings' opening has come ([SettingsOpening]); a held column is a Spacer of its width meanwhile, never drawn. */
internal val LocalSettingsOpeningStage = compositionLocalOf { SettingsOpening.WHOLE }

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
