package com.plumemu.game

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.ceil

/**
 * At the end of a list that scrolls, how far (px) its content must move up so the first row, cut at the top, does not leave a sliver of its
 * text: the text (centred in the row, [lead] clear above and below it) shows under the top fade's solid [lead] either not at all or by half
 * or more. 0 when nothing is cut or the list is not at its end.
 */
internal fun LazyListState.sliverAtTopPx(
    lead: Float,
    bands: Map<Any, RowTextBands>,
): Int {
    if (!canScrollBackward || canScrollForward) return 0
    val info = layoutInfo
    // Offsets start at the list's own top; the rows composed in the room above it (for the glide) end above it.
    val first = info.visibleItemsInfo.firstOrNull { it.offset + it.size > 0 } ?: return 0
    val top = first.offset.toFloat()
    if (top >= 0f) return 0
    val lines = bands[first.key]?.bands().orEmpty()
    if (lines.isNotEmpty()) {
        // Each text shows under the solid lead either not at all or by half or more: the most any of them needs to move up.
        return lines.maxOf { (from, to) ->
            val shown = top + to - lead
            if (to > from && shown > 0f && shown < (to - from) / 2f) ceil(shown).toInt() else 0
        }
    }
    val textHeight = first.size - 2 * lead
    val shown = top + first.size - lead - lead
    return if (textHeight > 0f && shown > 0f && shown < textHeight / 2f) ceil(shown).toInt() else 0
}

/**
 * How far (px) above the window's end [height] the rows must stop so the one cut there leaves no sliver of its text: as at the top, a text shows either not at all or by
 * half or more. 0 when the list ends here, nothing is cut that way or the cut row is the focused one ([focused]), which is never hidden. [lead] as in [sliverAtTopPx].
 */
internal fun LazyListState.hiddenAtEndPx(
    height: Float,
    lead: Float,
    focused: Any?,
    bands: Map<Any, RowTextBands>,
): Float {
    if (!canScrollForward) return 0f
    val row = layoutInfo.visibleItemsInfo.lastOrNull { it.offset < height } ?: return 0f
    if (row.key == focused || row.offset + row.size <= height) return 0f
    val lines = bands[row.key]?.bands().orEmpty().ifEmpty { listOf(lead to row.size - lead) }
    return bottomSliver(height, row.offset.toFloat(), lines)
}

/**
 * See [hiddenAtEndPx]: for a row whose top is at [top] with its texts at [lines], from its top. Texts under the cut go with it; a row left with no text goes whole,
 * so what is hidden is never a blank band of it.
 */
internal fun bottomSliver(
    height: Float,
    top: Float,
    lines: List<Pair<Float, Float>>,
): Float {
    val shows = { from: Float, to: Float -> height - (top + from) >= (to - from) / 2f }
    val kept = lines.filter { (from, to) -> to > from && shows(from, to) }
    if (kept.size == lines.size) return 0f
    return height - if (kept.isEmpty()) top else top + lines.filter { it !in kept }.minOf { it.first }
}

/** A press in the part [hidden] says is hidden, measured from the box's end, reaches nothing: a row drawn nowhere takes no tap. */
internal fun Modifier.ignoringHiddenEnd(hidden: (height: Float) -> Float): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (down.position.y >= size.height - hidden(size.height.toFloat())) down.consume()
        }
    }
