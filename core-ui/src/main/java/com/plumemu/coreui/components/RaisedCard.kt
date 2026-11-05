package com.plumemu.coreui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.CardDimens
import com.plumemu.coreui.theme.CardLook
import com.plumemu.coreui.theme.PlumTheme
import com.plumemu.coreui.theme.RaisedCard

/**
 * A raised card behind the item (docs/design/rules.md > Cards), the board's PANEL: its two shadows (cached Gaussian images, no blur
 * filter or layer), the CSS gradient fill and the 1 board px border inside its box with a lighter top side; colours from `PlumComposeColors.card`, read while drawing. The card is the item's
 * bounds, [start] and [end] in from the sides, grown by [outset] at the sides and top and [outsetBottom] below; nothing is laid out,
 * so the content keeps its place. [corner] is the card's; a small tile inside a card takes [CardDimens.tileCorner].
 */
@Composable
fun Modifier.raisedCard(
    outset: Dp = 0.dp,
    start: Dp = 0.dp,
    end: Dp = 0.dp,
    outsetBottom: Dp = outset,
    corner: Dp = CardDimens.corner,
): Modifier {
    val colors = PlumTheme.colors
    return drawBehind {
        val card = colors.card
        val grow = outset.toPx()
        val left = start.toPx() - grow
        val top = -grow
        val width = size.width - start.toPx() - end.toPx() + 2 * grow
        val height = size.height + grow + outsetBottom.toPx()
        drawRaisedCard(card, left, top, width, height, corner.toPx())
    }
}

/** The card's layers at [left], [top], [width] x [height] px with [corner] px corners: the far and near shadows, the gradient fill, the border. */
internal fun DrawScope.drawRaisedCard(
    card: CardLook,
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    corner: Float = CardDimens.corner.toPx(),
) {
    val topLeft = Offset(left, top)
    val box = Size(width, height)
    drawSoft(topLeft, box, corner, CardDimens.shadowFarDrop.toPx(), CardDimens.shadowFarSigma.toPx(), card.shadow.copy(alpha = card.shadow.alpha * RaisedCard.SHADOW_FAR.toFloat()), true)
    drawSoft(topLeft, box, corner, CardDimens.shadowNearDrop.toPx(), CardDimens.shadowNearSigma.toPx(), card.shadow.copy(alpha = card.shadow.alpha * RaisedCard.SHADOW_NEAR.toFloat()), true)
    drawRoundRect(Brush.verticalGradient(listOf(card.top, card.bottom), startY = top, endY = top + height), topLeft, box, CornerRadius(corner))
    drawCardBorder(Outline.Rounded(RoundRect(left, top, left + width, top + height, CornerRadius(corner))), card.edge, card.edgeTop, CardDimens.edge.toPx())
}

/**
 * The CSS border of a card: [width] px just inside [outline], its sides in [side] and its top side in [top]; the colours meet half way
 * round each top corner, as a CSS border's do.
 */
internal fun DrawScope.drawCardBorder(
    outline: Outline.Rounded,
    side: Color,
    top: Color,
    width: Float,
) {
    val box = outline.roundRect
    val split = box.top + box.topLeftCornerRadius.x * (1f - CORNER_HALF_WAY)
    clipRect(left = box.left - width, top = split, right = box.right + width, bottom = box.bottom + width) { drawRingInside(outline, side, width) }
    clipRect(left = box.left - width, top = box.top - width, right = box.right + width, bottom = split) { drawRingInside(outline, top, width) }
}

/** cos 45 deg: where a rounded CSS border's top colour gives way to its side colour. */
private const val CORNER_HALF_WAY = 0.70710677f
