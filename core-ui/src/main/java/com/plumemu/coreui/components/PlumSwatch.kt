package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.ChoiceDimens
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.readableInk

/**
 * A colour to pick (V2 board "Swatches"): the colour is the cap, in a [CollarSize.Key] collar. [selected] draws a
 * check in black or white, whichever reads on the colour; [focused] lights the collar's groove and halo. Off ([enabled]
 * false) is a flat sunken cap pulled toward the background, never faded by opacity. Taps come in through [modifier].
 * [color] is read while drawing, so a moving colour redraws and recomposes nothing.
 */
@Composable
fun PlumSwatch(
    color: () -> Color,
    selected: Boolean,
    focused: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    corner: Dp = ChoiceDimens.swatchCorner,
    /** The cap's size inside the collar; a grid sizes it from its cell instead. */
    size: Modifier = Modifier.size(ChoiceDimens.swatchSize),
) {
    val colors = PlumTheme.colors
    Box(
        modifier
            .collar(CollarSize.Key, focused = focused && enabled, radius = corner)
            .then(size)
            .drawBehind {
                val fill = if (enabled) color() else lerp(color(), colors.bg, OFF_TOWARD_BG)
                drawCssCap(colors.collar.seat.swatch(fill), corner = corner.toPx(), press = if (enabled) 0f else 1f)
                if (selected) drawCheckMark(if (enabled) readableInk(fill) else colors.muted)
            },
    )
}

private const val OFF_TOWARD_BG = 0.55f
