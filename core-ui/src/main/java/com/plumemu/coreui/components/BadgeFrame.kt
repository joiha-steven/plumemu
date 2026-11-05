package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.BadgeFrame
import com.plumemu.coreui.theme.BadgeFrameDimens
import com.plumemu.coreui.theme.PlumTheme

/**
 * The frame of an achievement badge (the boards' `inset 0 1px 0` white, `0 0 0 1px` ring and `0 2px 4px` drop): the soft drop and the
 * hairline ring outside the item, the lit top line inside it over the picture. Shadows take the slab's tint and strength, so they are
 * brown and lighter on light themes. Put it before the item's clip: the drop and the ring lie outside its bounds.
 */
@Composable
fun Modifier.badgeFrame(corner: Dp): Modifier {
    val shadow = PlumTheme.colors.slab.shadow
    return drawWithContent {
        val radius = corner.toPx()
        val ring = BadgeFrameDimens.ring.toPx()
        drawSoft(Offset.Zero, size, radius, BadgeFrameDimens.drop.toPx(), BadgeFrameDimens.sigma.toPx(), shadow.copy(alpha = shadow.alpha * BadgeFrame.DROP), false)
        drawRoundRect(
            shadow.copy(alpha = shadow.alpha * BadgeFrame.RING),
            Offset(-ring / 2, -ring / 2),
            Size(size.width + ring, size.height + ring),
            CornerRadius(radius + ring / 2),
            Stroke(ring),
        )
        drawContent()
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
        val line = BadgeFrameDimens.topLine.toPx()
        clipPath(shape) {
            // The inset shadow moved 1 px down: the shape less itself one line lower, a lit crescent along the top.
            clipPath(Path().apply { addRoundRect(RoundRect(0f, line, size.width, size.height + line, CornerRadius(radius))) }, ClipOp.Difference) {
                drawRect(BadgeFrame.topLine)
            }
        }
    }
}
