package com.plumemu.secondscreen

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.plumemu.coreui.theme.EntryCardDimens
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The line style the board lines are drawn with: the line centred, the font's own top and foot kept, never taller than asked. */
internal val BoardLineStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both, LineHeightStyle.Mode.Tight)

/** [this] on the board's line of [lineEm] (in em of its size, [EntryCardDimens.JOST_OWN_LINE] for `normal`); off Jost unchanged. */
internal fun TextStyle.onBoardLine(
    lineEm: Float,
    isJost: Boolean,
): TextStyle = if (isJost) copy(lineHeight = (fontSize.value * lineEm).sp, lineHeightStyle = BoardLineStyle) else this

/**
 * Lays a text out on the board's line box: the browser centres the font's ascent and descent in the `line-height`, even
 * when that line is shorter than the font's own, while Android never makes a line shorter than the font's own. So the box
 * is the line's height (plus the lines below the first) and the first baseline sits where the browser puts it; marks may
 * reach past the box, as they do on the board. [style] carries the board's line ([onBoardLine]); [sharing] is the size of
 * a span on the same line (the unit after a figure), whose own centred box can reach below the text's. The boxes are
 * worked out from Jost's metrics, so off Jost ([isJost] false) the text keeps its own box.
 */
internal fun Modifier.boardLine(
    style: TextStyle,
    isJost: Boolean,
    sharing: TextUnit? = null,
): Modifier =
    if (!isJost) {
        this
    } else {
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val size = style.fontSize.toPx()
            val lineEm = style.lineHeight.value / style.fontSize.value
            val own = BoardLine.of(size)
            // `normal` is the font's own rounded height; a set line-height is used as it is.
            val line = if (abs(lineEm - EntryCardDimens.JOST_OWN_LINE) < NORMAL_SLACK) own.height.toFloat() else lineEm * size
            val shared = sharing?.let { BoardLine.of(it.toPx()) }
            val whole = line.roundToInt()
            val top = maxOf(own.above(line), shared?.above(line) ?: 0)
            val bottom = maxOf(whole - own.above(line), shared?.let { whole - it.above(line) } ?: 0)
            val first = placeable[FirstBaseline]
            val last = placeable[LastBaseline]
            if (first == AlignmentLine.Unspecified || last == AlignmentLine.Unspecified) {
                // An intrinsic measure has no baselines: Android draws one line of this style ceil(line) tall, the rest are the lines below.
                val below = placeable.height - ceil(style.lineHeight.toPx()).toInt()
                layout(placeable.width, top + bottom + below.coerceAtLeast(0)) { placeable.place(0, 0) }
            } else {
                layout(placeable.width, top + (last - first) + bottom) { placeable.place(0, top - first) }
            }
        }
    }

private const val NORMAL_SLACK = 0.001f

/**
 * Lays one line of monospace text on the box the board's `ui-monospace` (Menlo) gives it at [boardSize]: its ascent and
 * descent rounded each on its own, the baseline at the ascent. The text keeps its own font and measure; only its box and
 * baseline move.
 */
internal fun Modifier.boardMonoLine(boardSize: TextUnit): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val size = boardSize.toPx()
        val ascent = (size * MENLO_ASCENT).roundToInt()
        val height = ascent + (size * MENLO_DESCENT).roundToInt()
        val first = placeable[FirstBaseline]
        if (first == AlignmentLine.Unspecified) {
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        } else {
            layout(placeable.width, height) { placeable.place(0, ascent - first) }
        }
    }

/** Menlo's ascent and descent in em (1901 and 483 of 2048 units). */
private const val MENLO_ASCENT = 1901f / 2048
private const val MENLO_DESCENT = 483f / 2048

/** Jost's ascent and descent at one size, each rounded to whole pixels as the browser does. */
private class BoardLine(
    val ascent: Int,
    val descent: Int,
) {
    val height: Int get() = ascent + descent

    /** The baseline's place in a [line] box: the browser gives the top the smaller half of the leading. */
    fun above(line: Float): Int = ascent + floor((line - height) / 2f).toInt()

    companion object {
        fun of(size: Float) =
            BoardLine(
                (EntryCardDimens.JOST_ASCENT * size).roundToInt(),
                ((EntryCardDimens.JOST_OWN_LINE - EntryCardDimens.JOST_ASCENT) * size).roundToInt(),
            )
    }
}

/** The OpenType feature for figures of one width: the boards set `font-variant-numeric: tabular-nums` on every card. */
internal const val TABULAR_FIGURES = "tnum"
