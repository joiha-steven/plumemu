package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The 32 dp fade at the right edge over content that runs past it (docs/design/rules.md › The
 * strip): from clear to the background. Draws only; touches pass through to the rows below.
 */
@Composable
fun EdgeFade(modifier: Modifier = Modifier) {
    val colors = PlumTheme.colors
    Box(
        modifier =
            modifier
                .width(PlumDimens.edgeFade)
                .fillMaxHeight()
                // Read while drawing: the background may be moving under a colour slider.
                .drawBehind { drawRect(Brush.horizontalGradient(listOf(colors.bg.copy(alpha = 0f), colors.bg))) },
    )
}

/**
 * The fade at the bottom ([atBottom]) or top edge of a list that scrolls, drawn only while rows
 * continue past that edge: a row cut there fades out instead of ending in a hard line, so it is
 * plain the list scrolls. Place it over the list (Box, aligned to that edge); touches pass through.
 */
@Composable
fun ListEdgeFade(
    atBottom: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PlumTheme.colors
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(PlumDimens.listEdgeFade)
                .drawBehind {
                    val clear = colors.bg.copy(alpha = 0f)
                    drawRect(Brush.verticalGradient(if (atBottom) listOf(clear, colors.bg) else listOf(colors.bg, clear)))
                },
    )
}
