package com.plumemu.secondscreen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import com.plumemu.coreui.theme.BackdropGlowDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.exp

/**
 * The accent glow in the page's top right corner (the boards' blurred ellipse behind the cards): a Gaussian fall-off of
 * `PlumComposeColors.backdropGlow`, drawn as one radial gradient stretched to an ellipse (no blur, no layer). Sizes are the boards' px / 1.5,
 * fitted to the rendered boards: the middle sits 60 dp in from the right edge and 13 dp down, the spread is 114 dp across and 103 dp down.
 */
@Composable
internal fun Modifier.backdropGlow(): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val peak = colors.backdropGlow
        val across = BackdropGlowDimens.spreadAcross.toPx()
        val squash = BackdropGlowDimens.spreadDown.toPx() / across
        val centre = Offset(size.width - BackdropGlowDimens.fromRight.toPx(), BackdropGlowDimens.fromTop.toPx())
        val stops =
            Array(STEPS + 1) { step ->
                val reach = step.toFloat() / STEPS
                reach to peak.copy(alpha = peak.alpha * exp(-(reach * REACH_SPREADS) * (reach * REACH_SPREADS) / 2f))
            }
        val last = stops.last()
        stops[stops.lastIndex] = last.first to Color.Transparent
        withTransform({ scale(1f, squash, pivot = centre) }) {
            drawCircle(Brush.radialGradient(*stops, center = centre, radius = across * REACH_SPREADS), radius = across * REACH_SPREADS, center = centre)
        }
    }
}

/** The gradient runs out to this many spreads, in [STEPS] stops. */
private const val REACH_SPREADS = 3.2f
private const val STEPS = 16
