package com.plumemu.settings.screen

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.PlumSwatch
import com.plumemu.coreui.components.pressFeel
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.Rgb
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A colour in its collar ([PlumSwatch]): focus lights the groove and halo, a selected one carries the check in the ink that
 * reads on it; its edge is the cap's own. [size] is the cap's size inside the collar. A fixed-size swatch [bleed]s: the collar
 * falls in the gap around it, so the board's columns stay; a grid cell takes it from its own width instead.
 */
@Composable
internal fun Swatch(
    color: Color,
    corner: Dp,
    isFocused: Boolean,
    modifier: Modifier,
    size: Modifier,
    bleed: Boolean,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(corner)
    val colors = PlumTheme.colors
    val line = PlumDimens.focusRingKeyline
    // Focus lights the groove in the accent, which a swatch of that colour swallows: there a line in the accent's own ink tells it, inside the cap's edge.
    val edge =
        if (isFocused) {
            Modifier.drawWithContent {
                drawContent()
                if (nearAccent(color, colors.accent)) {
                    val width = line.toPx()
                    val inset = width / 2
                    drawRoundRect(
                        colors.onAccent,
                        topLeft = Offset(inset, inset),
                        size = Size(this.size.width - width, this.size.height - width),
                        cornerRadius = CornerRadius(corner.toPx() - inset),
                        style = Stroke(width),
                    )
                }
            }
        } else {
            Modifier
        }
    PlumSwatch(
        color = { color },
        selected = selected,
        focused = isFocused,
        enabled = true,
        // Outside the collar, so the whole swatch dips with a press.
        modifier = modifier.pressFeel(isFocused, null, shape).then(if (bleed) Modifier.collarBleed(CollarSize.Key) else Modifier),
        corner = corner,
        size = size.then(edge),
    )
}

private const val RGB_ONLY = 0xFFFFFF

/** CIE76 difference under which the accent's focus light does not stand out on a swatch: about twice "clearly different at a glance" (12). */
private const val NEAR_ACCENT_DISTANCE = 24.0

/** Close enough that the accent's focus light does not stand out on [swatch]. */
internal fun nearAccent(
    swatch: Color,
    accent: Color,
): Boolean = cie76(Rgb(swatch.toArgb() and RGB_ONLY), Rgb(accent.toArgb() and RGB_ONLY)) < NEAR_ACCENT_DISTANCE

private fun cie76(
    a: Rgb,
    b: Rgb,
): Double {
    val (l1, a1, b1) = lab(a)
    val (l2, a2, b2) = lab(b)
    return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
}

private fun lab(c: Rgb): Triple<Double, Double, Double> {
    fun linear(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    val r = linear(c.red)
    val g = linear(c.green)
    val bl = linear(c.blue)
    val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * bl) / 0.95047
    val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * bl
    val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * bl) / 1.08883
    fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
    return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
}
