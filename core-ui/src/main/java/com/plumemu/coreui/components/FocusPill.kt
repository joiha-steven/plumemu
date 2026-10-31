package com.plumemu.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.min

/**
 * The shape of a focused row: corners of half the row's height up to [PlumDimens.cornerFocusPill], so a
 * one-line row is a full pill and a tall one (a wrapped label) keeps rounded corners instead of a stadium.
 */
object FocusPillShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = min(size.height / 2f, with(density) { PlumDimens.cornerFocusPill.toPx() })
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius, radius)))
    }
}

/**
 * [FocusPillShape] starting [inset] from the box's left edge (the system column: the whole row is the touch target and
 * dips, only the pill is drawn inset from the screen edge).
 */
class InsetFocusPillShape(
    private val inset: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = with(density) { inset.toPx() }.coerceAtMost(size.width)
        val radius = min(size.height / 2f, with(density) { PlumDimens.cornerFocusPill.toPx() })
        return Outline.Rounded(RoundRect(left, 0f, size.width, size.height, CornerRadius(radius, radius)))
    }
}

/**
 * A row that has been picked up to be moved (Settings > Library > Systems): the focus pill turned solid, filled with the
 * accent and ringed in `onAccent`, so pressing A visibly changes the row. Text on it is `onAccent`, the background or the
 * text colour, whichever stands out more on the accent: not a 4.5:1 guarantee (the default red on Midnight, Plum and Forest
 * reaches 4.2-4.45, `PlumColorsTest`; a custom accent only has to clear 3:1 on the background), the same trade the badge
 * and action keys make (the accent is not adjusted). The row is carried only for a moment. It is [pressFeel] too; both colours are read while drawing.
 */
fun Modifier.carriedPill(): Modifier =
    pressFeelWith(
        true,
        { colors -> colors.accent },
        FocusPillShape,
        true,
        ring = { colors -> colors.onAccent },
    )

/**
 * The one look of a focused row (docs/design/rules.md > Focus and current): a [FocusPillShape] pill
 * tinted with the accent (`PlumColors.pill`) and a [PlumDimens.focusPillRing] accent ring inside its edge. It is
 * [pressFeel] too, so the row dips under a press ([rest] is the fill of a row that is not focused, for a card). Put it where the row's `background` was, before the padding, so the
 * pill spans the row's whole box. Both colours are read while drawing: a colour that moves redraws the row and
 * recomposes nothing.
 */
fun Modifier.focusPill(
    focused: Boolean,
    answersButton: Boolean = true,
    /** The fill of the row when it is not focused (a quiet card), read while drawing; none by default. */
    rest: (() -> Color?)? = null,
    /** Cards that keep their own corners (setup's) pass them, so a card has one shape focused or not. */
    shape: Shape = FocusPillShape,
): Modifier =
    pressFeelWith(
        focused,
        { colors -> if (focused) colors.pill else rest?.invoke() },
        shape,
        answersButton,
        ring = { colors -> if (focused) colors.accent else null },
        listDrawsPill = true,
    )

/**
 * The focused row's look drawn as a picture only, for a sample row that shows what focus looks like (Settings' colour pages):
 * no press, no touch. Colours are read while drawing.
 */
fun Modifier.focusPillSample(): Modifier =
    composed {
        val colors = PlumTheme.colors
        drawBehind {
            val outline = FocusPillShape.createOutline(size, layoutDirection, this)
            drawOutline(outline, colors.pill)
            drawRingInside(outline, colors.accent)
        }
    }
