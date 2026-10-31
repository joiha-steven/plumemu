package com.plumemu.coreui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumMotion
import com.plumemu.coreui.theme.PlumTheme

/**
 * Pins [ScanPulse] at one point of its breathing (0 = dimmest, 1 = brightest) with no animation.
 * Only for screenshot tests, so a shot is the same every time; null (the default) lets it breathe.
 */
val LocalScanPulsePhase = staticCompositionLocalOf<Float?> { null }

/**
 * The sign that a scan is running (design/rules.md › Shapes and motion): a small accent dot whose
 * alpha eases slowly between [PlumMotion.SCAN_PULSE_MIN_ALPHA] and 1, [PlumMotion.SCAN_PULSE_CYCLE_MS]
 * per cycle, like a light bulb. Calm, not a spinner. With motion off it is a solid dot.
 *
 * The animated value is read only inside `graphicsLayer`, so frames change this dot's layer alpha and recompose nothing.
 * Show it only while the scan runs; when the scan ends, leave it out.
 */
@Composable
fun ScanPulse(modifier: Modifier = Modifier) {
    val color = PlumTheme.colors.accent
    val pinned = LocalScanPulsePhase.current
    val steady: Float? =
        when {
            pinned != null -> alphaAt(pinned)
            !PlumTheme.motion.isOn -> 1f
            else -> null
        }
    val breathing: State<Float>? =
        if (steady == null) {
            rememberInfiniteTransition(label = "scanPulse").animateFloat(
                initialValue = PlumMotion.SCAN_PULSE_MIN_ALPHA,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(PlumMotion.SCAN_PULSE_CYCLE_MS / 2, easing = PlumMotion.EaseInOut), RepeatMode.Reverse),
                label = "scanPulseAlpha",
            )
        } else {
            null
        }
    // Alpha through graphicsLayer: the dot is its own layer, so a frame redraws nothing around it.
    Box(
        modifier
            .size(PlumDimens.scanPulseSize)
            .graphicsLayer { alpha = steady ?: breathing?.value ?: 1f }
            .drawBehind { drawCircle(color) },
    )
}

/**
 * A scan's status line: the breathing dot, then [text]. The dot sits at the line's first baseline
 * (its bottom edge on it), so with a wrapped text it stays by the first line, not the middle of the block.
 */
@Composable
fun ScanPulseLine(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    dotModifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
) {
    Row(modifier) {
        ScanPulse(dotModifier.alignBy { it.measuredHeight })
        Spacer(Modifier.width(PlumDimens.scanPulseGap))
        BasicText(text = text, style = style, modifier = textModifier.weight(1f, fill = false).alignBy(FirstBaseline))
    }
}

private fun alphaAt(phase: Float): Float = PlumMotion.SCAN_PULSE_MIN_ALPHA + (1f - PlumMotion.SCAN_PULSE_MIN_ALPHA) * phase.coerceIn(0f, 1f)
