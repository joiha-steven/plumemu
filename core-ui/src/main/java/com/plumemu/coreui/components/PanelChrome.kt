package com.plumemu.coreui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.Depth
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val SHADOW_LAYERS = 4
private const val LINE_FALLBACK = 1.2f

/** Test tag of a panel title's dot. */
const val PANEL_TITLE_DOT_TAG = "panel_title_dot"

/**
 * A panel's chrome (decision 0011, point 5): a soft shadow of [SHADOW_LAYERS] stacked rounded rects (no blur, no layer),
 * the [fill] and a hairline [edge] inside its border. The rect is [left], [top], [width], [height] in px; the shadow reaches
 * [bottomReach] below it (a panel with little room under it asks for less).
 */
fun DrawScope.drawPanelChrome(
    fill: Color,
    edge: Color,
    shadow: Color,
    corner: Float,
    left: Float = 0f,
    top: Float = 0f,
    width: Float = size.width,
    height: Float = size.height,
    bottomReach: Float = (PlumDimens.panelShadowReach + PlumDimens.panelShadowDrop).toPx() * Depth.SHADOW_REACH,
) {
    val reach = PlumDimens.panelShadowReach.toPx() * Depth.SHADOW_REACH
    val drop = PlumDimens.panelShadowDrop.toPx() * Depth.SHADOW_REACH
    val layerAlpha = shadow.alpha * Depth.SHADOW / SHADOW_LAYERS
    for (i in 1..SHADOW_LAYERS) {
        val share = (SHADOW_LAYERS + 1 - i).toFloat() / SHADOW_LAYERS
        val grow = reach * share
        // The top reaches [grow] less the drop; the bottom reaches [bottomReach] in the same shares.
        val above = grow - drop
        val below = bottomReach * share
        drawRoundRect(
            shadow.copy(alpha = layerAlpha),
            topLeft = Offset(left - grow, top - above),
            size = Size(width + 2 * grow, height + above + below),
            cornerRadius = CornerRadius(corner + grow),
        )
    }
    drawRoundRect(fill, Offset(left, top), Size(width, height), CornerRadius(corner))
    val line = PlumDimens.panelHairline.toPx()
    drawRoundRect(
        edge,
        topLeft = Offset(left + line / 2, top + line / 2),
        size = Size(width - line, height - line),
        cornerRadius = CornerRadius(corner - line / 2),
        style = Stroke(line),
    )
}

/** The small accent dot at the end of a panel's title, [PlumDimens.panelTitleDot] across. */
@Composable
fun PanelTitleDot(modifier: Modifier = Modifier) {
    val colors = PlumTheme.colors
    Box(modifier.size(PlumDimens.panelTitleDot).drawBehind { drawCircle(colors.accent) }.testTag(PANEL_TITLE_DOT_TAG))
}

/**
 * A title with the accent dot right after its last visible character ([PlumDimens.panelTitleDotGap] away), centred on that
 * line. The title keeps its left edge; room for the dot is held back, so a long title wraps (or is cut at [maxLines]
 * with an ellipsis) a word earlier and the dot never leaves the panel.
 */
@Composable
fun PanelTitleWithDot(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    titleModifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    val layoutOf = remember { arrayOfNulls<TextLayoutResult>(1) }
    Layout(
        content = {
            BasicText(
                text = text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
                onTextLayout = { layoutOf[0] = it }, modifier = titleModifier,
            )
            PanelTitleDot()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val dotSize = PlumDimens.panelTitleDot.roundToPx()
        val gap = PlumDimens.panelTitleDotGap.roundToPx()
        val room = if (constraints.hasBoundedWidth) (constraints.maxWidth - dotSize - gap).coerceAtLeast(0) else Constraints.Infinity
        // onTextLayout runs while the title is measured, so the line geometry is known right after.
        val title = measurables[0].measure(Constraints(maxWidth = room, maxHeight = constraints.maxHeight))
        val dot = measurables[1].measure(Constraints.fixed(dotSize, dotSize))
        val result = layoutOf[0]
        val last = result?.let { it.lineCount - 1 }
        val lineRight = if (result != null && last != null && last >= 0) ceil(result.getLineRight(last)).toInt() else title.width
        // Centre on the line's own box (its set height, as a one-line title always had), moved down to the last line.
        val lineTop = if (result != null && last != null && last >= 0) result.getLineTop(last).roundToInt() else 0
        val lineHeight = if (style.lineHeight.isSpecified) style.lineHeight.toDp() else (style.fontSize.value * LINE_FALLBACK).sp.toDp()
        val dotTop = lineTop + ((lineHeight - PlumDimens.panelTitleDot) / 2).roundToPx()
        val width = maxOf(title.width, lineRight + gap + dotSize).coerceIn(constraints.minWidth, maxOf(constraints.minWidth, constraints.maxWidth))
        val height = title.height.coerceAtLeast(constraints.minHeight)
        layout(width, height) {
            title.placeRelative(0, 0)
            dot.placeRelative(lineRight + gap, dotTop)
        }
    }
}
