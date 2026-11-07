package com.plumemu.coreui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.SlabDimens
import kotlin.math.roundToInt

/**
 * Px to draw a game row's right-hand text from where it is laid out, read while drawing: a list whose width changes with the strip's stop carries the text between its two places
 * as the strip slides, without laying a row out. Null for a list that keeps its width.
 */
val LocalRowEndShift = staticCompositionLocalOf<(() -> Float)?> { null }

internal fun Modifier.shiftedBy(shift: (() -> Float)?): Modifier =
    if (shift == null) this else drawWithContent { translate(left = shift()) { this@drawWithContent.drawContent() } }

/**
 * Draws [draw] with its right end [extraPx] further right: the slab's two ends as they are, the run between them stretched. It reuses what [draw] has built, so a slab whose end moves
 * with [LocalRowEndShift] builds no image in a frame of the slide; at rest (0) it is the plain draw. The run is plain from side to side, which is why stretching it does not show.
 */
fun DrawScope.drawWithEndMoved(
    extraPx: Float,
    draw: DrawScope.() -> Unit,
) {
    val cap = SlabDimens.endCap.toPx().roundToInt().toFloat()
    val run = size.width - 2f * cap
    val shift = extraPx.roundToInt().toFloat()
    if (shift == 0f || run <= 0f) return draw()
    clipRect(left = -OUTSIDE, top = -OUTSIDE, right = cap, bottom = OUTSIDE) { draw() }
    withTransform({
        translate(left = cap)
        scale(scaleX = (run + shift) / run, scaleY = 1f, pivot = Offset.Zero)
        translate(left = -cap)
    }) {
        // A pixel over each seam: the two clips do not meet on a whole pixel once stretched.
        clipRect(left = cap - 1f, top = -OUTSIDE, right = size.width - cap + 1f, bottom = OUTSIDE) { draw() }
    }
    translate(left = shift) { clipRect(left = size.width - cap, top = -OUTSIDE, right = OUTSIDE, bottom = OUTSIDE) { draw() } }
}

private const val OUTSIDE = 100_000f
