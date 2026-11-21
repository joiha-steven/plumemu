package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.plumemu.coreui.theme.PlumTheme

/**
 * The flat scrim behind a raised overlay card ([com.plumemu.coreui.theme.PlumComposeColors.scrim], one for every such card): drawn from
 * this box out over the whole screen, so it ends at no line across the page, at [shown] (0..1, the card's own slide) of its strength.
 * [shown] is read while drawing: the slide redraws one rectangle and recomposes nothing. What is composed after it (the hint bar) stays
 * on top, undimmed.
 */
@Composable
fun Modifier.overlayScrim(shown: () -> Float): Modifier {
    val colors = PlumTheme.colors
    val screen = PlumTheme.layout
    return drawBehind {
        val share = shown().coerceIn(0f, 1f)
        if (share <= 0f) return@drawBehind
        val w = screen.width.toPx()
        val h = screen.height.toPx()
        val scrim = colors.scrim
        // From well outside the box on every side: the box is the strip's area, the screen reaches past it to the bars.
        drawRect(scrim.copy(alpha = scrim.alpha * share), Offset(-w, -h), Size(size.width + 2 * w, size.height + 2 * h))
    }
}
