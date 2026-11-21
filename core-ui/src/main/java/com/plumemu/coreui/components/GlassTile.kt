package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * A second-screen tile (play time, achievements, facts, statistics, the in-game battery): the same plate as the top screen's focused row
 * ([com.plumemu.coreui.theme.SlabLook], one family on both screens), without its accent bar or glow: the slab's lift as one cached image,
 * its one flat face (the page lit toward white, keeping its hue; [com.plumemu.coreui.theme.FlatSurface]); no outline, no line. The tile is the item's bounds; nothing is laid out. Colours are read while drawing. A tile is no control: it is never focused
 * or touched.
 */
@Composable
fun Modifier.glassTile(corner: Dp = CardDimens.tileCorner): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val look = colors.slab
        val outline = Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(corner.toPx())))
        drawSlabOutside(outline, look, Color.Transparent, under = colors.bg)
        drawSlabFill(outline, look, onCard = false)
    }
}
