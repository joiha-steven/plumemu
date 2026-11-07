package com.plumemu.home

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.components.drawCoverFocusMark
import com.plumemu.coreui.theme.CollarDimens
import com.plumemu.coreui.theme.PlumComposeColors
import com.plumemu.coreui.theme.PlumDimens
import com.plumemu.coreui.theme.focusedCoverScale

/**
 * A grid cover that holds a lift slot draws itself in place and records itself into the slot's layer; the grid's
 * overlay pass ([drawLiftedCover]) draws that layer larger, over the neighbours and the grid's clip.
 */
internal fun ContentDrawScope.drawCoverForLift(
    slot: LiftSlot,
    index: Int,
    entryId: String,
) {
    drawContent()
    slot.layer.record { this@drawCoverForLift.drawContent() }
    slot.recordedEntryId = entryId
    slot.recordedIndex = index
}

/**
 * The focused cover of the grid (or the one just left, fading back) over the grid, at [slot]'s growth: its recorded copy drawn
 * [PlumDimens.FOCUSED_COVER_SCALE] times its size about the collar's centre ([collar], px, as laid out; [cell] is the cell it stands in)
 * on a plate of the page colour, then the accent ring around the collar ([drawCoverFocusMark]). [pressed] dips the whole
 * cover about its centre. Draw only: nothing is laid out, so the neighbours do not move, and a move redraws, nothing recomposes.
 */
internal fun DrawScope.drawLiftedCover(
    slot: LiftSlot,
    entryId: String,
    collar: Rect,
    cell: Rect,
    pressed: Float,
    colors: PlumComposeColors,
    /** The glint's place (0..1) while it crosses the cover ([FocusSweep]); 0 draws none. */
    sweep: Float = 0f,
) {
    val grow = slot.grow.value
    val scale = coverScale(grow, collar)
    val lifted = liftedCollar(collar, scale)
    val corner = liftedCorner(collar, scale)
    val recorded = slot.recordedIndex == slot.index && slot.recordedEntryId == entryId
    // The cell's own copy stays in place under the larger one; a dip smaller than the lift would show it, so the plate covers the cell.
    if (pressed < 1f && recorded) drawRect(colors.bg, cell.topLeft, cell.size)
    withTransform({ scale(pressed, pressed, pivot = collar.center) }) {
        if (recorded) {
            drawRoundRect(colors.bg, lifted.topLeft, lifted.size, CornerRadius(corner))
            withTransform({
                translate(cell.left, cell.top)
                scale(scale, scale, pivot = collar.center - cell.topLeft)
            }) { drawLayer(slot.layer) }
            if (sweep > 0f) drawSweep(lifted, corner, sweep)
        }
        drawCoverFocusMark(lifted.topLeft, lifted.size, corner, colors.accent, colors.bg, grow)
    }
}

private fun DrawScope.coverScale(
    grow: Float,
    collar: Rect,
): Float = 1f + (focusedCoverScale(collar.size.maxDimension.toDp().value) - 1f) * grow

private fun liftedCollar(
    collar: Rect,
    scale: Float,
): Rect = Rect(Offset.Zero, collar.size * scale).let { it.translate(collar.center - it.center) }

private fun DrawScope.liftedCorner(
    collar: Rect,
    scale: Float,
): Float = CollarSize.Cover.outerCorner(this, CollarDimens.coverBoxCorner.toPx(), collar.size) * scale
