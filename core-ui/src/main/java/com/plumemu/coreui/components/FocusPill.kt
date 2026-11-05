package com.plumemu.coreui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.SlabDimens
import com.plumemu.coreui.theme.PlumTheme
import kotlin.math.min

/**
 * How far the slab is drawn past a row of [rowHeightPx] above and below it: half of what [SlabDimens.height] adds (0 for a row that is as tall or taller).
 * The row's layout does not change; the lists leave room for this at their ends.
 */
fun slabOverhang(
    rowHeightPx: Float,
    density: Density,
): Float = ((with(density) { SlabDimens.height.toPx() } - rowHeightPx) / 2f).coerceAtLeast(0f)

/**
 * Clips at the sides (where a strip slides off screen) but lets drawing reach [room] above and below: the focused slab of a first or last row
 * overhangs it ([slabOverhang]), and a grid's ring and glow reach the bar gaps.
 */
fun Modifier.clipSidesLeavingRoom(
    room: Dp = PlumDimens.barGap,
    /** Room above, when the glow of a first row reaches further than the gap below a last row allows (a cut row under the hint keys would show there). */
    above: Dp = room,
): Modifier = graphicsLayer {
    clip = true
    shape = SidesOnlyClip(above.toPx(), room.toPx())
}

/**
 * A real clip layer (not a clip in the draw pass), so what is outside it is also outside the semantics' visible bounds: a
 * strip slid off screen reports only the part that is on it.
 */
private class SidesOnlyClip(
    private val above: Float,
    private val below: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rectangle(Rect(0f, -above, size.width, size.height + below))
}

/**
 * The shape of a focused row: corners of half the row's height up to [PlumDimens.cornerFocusPill], so a
 * one-line row is a full pill and a tall one (a wrapped label) keeps rounded corners instead of a stadium. Drawn [slabOverhang] taller than the row at each end.
 */
object FocusPillShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val over = slabOverhang(size.height, density)
        val radius = min((size.height + 2f * over) / 2f, with(density) { PlumDimens.cornerFocusPill.toPx() })
        return Outline.Rounded(RoundRect(0f, -over, size.width, size.height + over, CornerRadius(radius, radius)))
    }
}

/**
 * [FocusPillShape] starting [inset] from the box's left edge (the system column: the whole row is the touch target and
 * dips, only the pill is drawn inset from the screen edge).
 */
class InsetFocusPillShape(
    private val inset: Dp,
    /** Where the accent bar stands from the slab's left edge. */
    val barInset: Dp = SlabDimens.barInset,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val left = with(density) { inset.toPx() }.coerceAtMost(size.width)
        val over = slabOverhang(size.height, density)
        val radius = min((size.height + 2f * over) / 2f, with(density) { PlumDimens.cornerFocusPill.toPx() })
        return Outline.Rounded(RoundRect(left, -over, size.width, size.height + over, CornerRadius(radius, radius)))
    }
}

/**
 * A row that has been picked up to be moved (Settings > Library > Systems): the focused row's shape turned solid, filled with the
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
 * The one look of a focused row (docs/design/rules.md > Focus and current): the slab. A [FocusPillShape] raised surface
 * lighter than the background (`PlumComposeColors.slab`), a hairline edge, a short accent bar inside its left edge, a soft shadow
 * under it and, on dark themes, the focus glow ([drawSlabOutside], [drawSlabFace]); no ring. It is
 * [pressFeel] too, so the row dips under a press ([rest] is the fill of a row that is not focused, for a card). Put it where the row's `background` was, before the padding, so the
 * slab spans the row's whole box. Colours are read while drawing: a colour that moves redraws the row and
 * recomposes nothing.
 */
fun Modifier.focusPill(
    focused: Boolean,
    answersButton: Boolean = true,
    /** The fill of the row when it is not focused (a quiet card), read while drawing; none by default. */
    rest: (() -> Color?)? = null,
    /** Cards that keep their own corners (setup's) pass them, so a card has one shape focused or not. */
    shape: Shape = FocusPillShape,
    /** A key inside the row (a switch's knob) that a finger press on the row presses too. */
    mirror: KeyState? = null,
    /** A card with a title and a line under it: its accent bar stands beside the title's first line ([StripeAnchor]), not in the card's middle. */
    stripe: StripeAnchor? = null,
): Modifier =
    pressFeelWith(
        focused,
        { colors -> if (focused) colors.slab.fill else rest?.invoke() },
        shape,
        answersButton,
        ring = null,
        slab = focused,
        listDrawsPill = true,
        mirror = mirror,
        stripe = stripe,
    ).then(if (stripe != null) stripe.card() else Modifier)

/**
 * The focused row's look drawn as a picture only, for a sample row that shows what focus looks like (Settings' colour pages):
 * no press, no touch. Colours are read while drawing.
 */
fun Modifier.focusPillSample(): Modifier =
    composed {
        val colors = PlumTheme.colors
        drawBehind {
            val outline = FocusPillShape.createOutline(size, layoutDirection, this)
            drawSlabOutside(outline, colors.slab, colors.slabGlow, under = colors.bg)
            drawSlabFill(outline, colors.slab, onCard = false)
            drawSlabFace(outline, colors)
        }
    }
