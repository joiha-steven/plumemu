package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.GlassDimens
import com.plumemu.coreui.theme.GlassTile
import com.plumemu.coreui.theme.PlumTheme

/**
 * A glass tile (`PlumComposeColors.glass`, [GlassTile]): the card's soft shadow under a vertical gradient that lets the page through, the
 * board's inset shadows (a shade rising from the foot, a lit line under the top) and a hairline edge whose top is brighter. The tile is the
 * item's bounds; nothing is laid out. Colours are read while drawing. A tile is no control: it is never focused or touched.
 */
@Composable
fun Modifier.glassTile(corner: Dp = CardDimens.tileCorner): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val glass = colors.glass
        val radius = corner.toPx()
        val box = size
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, box.width, box.height, CornerRadius(radius))) }
        // The glass lets the page through, so the shadow is cut out under the tile (an opaque card hides it; this one would not).
        clipPath(shape, ClipOp.Difference) {
            val shadow = colors.card.shadow
            drawSoft(Offset.Zero, box, radius, GlassDimens.shadowFarDrop.toPx(), GlassDimens.shadowFarSigma.toPx(), shadow.copy(alpha = shadow.alpha * GlassTile.SHADOW_FAR), false)
            drawSoft(Offset.Zero, box, radius, GlassDimens.shadowNearDrop.toPx(), GlassDimens.shadowNearSigma.toPx(), shadow.copy(alpha = shadow.alpha * GlassTile.SHADOW_NEAR), false)
        }
        // As CSS lays a background out: over the padding box and repeated under the border, so the top border shows the foot's colour.
        val edgeLine = GlassDimens.edge.toPx()
        clipPath(shape) {
            drawRect(
                Brush.verticalGradient(
                    0f to glass.top,
                    GlassTile.MID_AT to glass.mid,
                    1f to glass.foot,
                    startY = edgeLine,
                    endY = box.height - edgeLine,
                    tileMode = TileMode.Repeated,
                ),
            )
        }
        val line = GlassDimens.edge.toPx()
        // The board's inset shadows lie inside the border, over the gradient and under the edge.
        val padAt = Offset(line, line)
        val pad = Size(box.width - 2 * line, box.height - 2 * line)
        val padRadius = (radius - line).coerceAtLeast(0f)
        val (rise, sigma) = if (glass.dark) GlassDimens.shadeRiseDark to GlassDimens.shadeSigmaDark else GlassDimens.shadeRiseLight to GlassDimens.shadeSigmaLight
        drawInsetShadow(padAt, pad, padRadius, -rise.toPx(), sigma.toPx(), glass.shade)
        drawInsetShadow(padAt, pad, padRadius, GlassDimens.highlightDrop.toPx(), GlassDimens.highlightSigma.toPx(), glass.highlight)
        drawRoundRect(glass.ring, Offset(-line / 2, -line / 2), Size(box.width + line, box.height + line), CornerRadius(radius + line / 2), Stroke(line))
        // The border is the box less its padding box. Like CSS, its top side takes the top colour and meets the other sides on the
        // corners' diagonals, so the two colours neither overlap nor leave a gap.
        val border =
            Path().apply {
                fillType = PathFillType.EvenOdd
                addPath(shape)
                addRoundRect(RoundRect(line, line, box.width - line, box.height - line, CornerRadius(padRadius)))
            }
        val reach = maxOf(radius, line)
        val topSide =
            Path().apply {
                moveTo(0f, 0f)
                lineTo(box.width, 0f)
                lineTo(box.width - reach, reach)
                lineTo(reach, reach)
                close()
            }
        clipPath(topSide) { drawPath(border, glass.edgeTop) }
        clipPath(topSide, ClipOp.Difference) { drawPath(border, glass.edge) }
    }
}
