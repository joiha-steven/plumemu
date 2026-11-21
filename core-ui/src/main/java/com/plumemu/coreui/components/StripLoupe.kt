package com.plumemu.coreui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.plumemu.coreui.theme.FlatFace
import com.plumemu.coreui.theme.PlumDimens

private const val GLOSS_WIDTH = 1.6f
private const val GLOSS_HEIGHT = 0.85f

/**
 * The colour page's loupe: a round lens over the strip. A pale glass rim (the seated knob's face, the same on every
 * colour set, with its lit hairline, edge and drop shadow) round a disc of [colour], a dark hairline between them so a
 * pale colour reads against the rim, one flat soft gloss on the lens's upper part and a pointer under it. Flat shapes
 * only (no gradient, blur or layer); fills the drawing area, which is square.
 */
fun DrawScope.drawLoupe(colour: Color) {
    val face = FlatFace.knob()
    drawFlatFace(face, press = 0f)
    val radius = size.minDimension / 2 - PlumDimens.settingsLoupeRim.toPx()
    val centre = Offset(size.width / 2, size.height / 2 + PlumDimens.flatRim.toPx() / 2)
    drawCircle(colour, radius, centre)
    val line = PlumDimens.settingsSwatchEdge.toPx()
    drawCircle(FlatFace.LOUPE_LINE, radius - line / 2, centre, style = Stroke(line))
    val lens = Path().apply { addOval(Rect(centre, radius)) }
    clipPath(lens) {
        val gloss = Size(radius * GLOSS_WIDTH, radius * GLOSS_HEIGHT)
        drawOval(FlatFace.LOUPE_GLOSS, topLeft = Offset(centre.x - gloss.width / 2, centre.y - radius), size = gloss)
    }
    val notch = PlumDimens.settingsLoupeNotch.toPx()
    val foot = size.height - line
    val pointer =
        Path().apply {
            moveTo(centre.x - notch, foot)
            lineTo(centre.x + notch, foot)
            lineTo(centre.x, foot + notch)
            close()
        }
    drawPath(pointer, FlatFace.KNOB_FACE)
}
