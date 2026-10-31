package com.plumemu.coreinput.strip

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.plumemu.coreinput.Column
import kotlin.math.abs

/**
 * The sizes the strip's stops follow (docs/design/rules.md › The strip). Taken as parameters so
 * core-input does not depend on the theme; the screen builds it from core-ui, e.g.
 * `StripGeometry(isNarrow = layout.widthClass == WidthClass.Narrow, leadingMargin =
 * PlumDimens.stripMarginSide, systemsWidth = PlumDimens.systemsColumnWidth, gamesListWidth =
 * layout.gamesListWidth, columnGap = PlumDimens.columnGap, peek = layout.peek,
 * detailsStopListPeek = PlumDimens.detailsStopListPeek)`.
 *
 * Strip coordinates, as on the board: the strip starts at the screen's left edge with the
 * [leadingMargin] inside the system column: at rest (offset 0) the column starts at the screen
 * edge (its selector runs from there) and its labels start at the margin. An offset is the strip's horizontal translation, negative as it slides left; the margin slides
 * away with the column, and peeks are measured from the screen edge (RP5-2-games moves the strip
 * by `peek − 224`, 224 = margin 24 + column 200, the column's right edge).
 */
@Immutable
data class StripGeometry(
    val isNarrow: Boolean,
    /** The left margin inside the strip before the system column (24). */
    val leadingMargin: Dp,
    val systemsWidth: Dp,
    val gamesListWidth: Dp,
    val columnGap: Dp,
    /** How much of the system column shows from the screen edge with games focused: 48 by default, 0–120 in Settings. */
    val peek: Dp,
    /** How much of the list shows from the screen edge in the narrow details stop (56). */
    val detailsStopListPeek: Dp,
) {
    /**
     * Where the strip rests for [column]:
     * - Systems: at its start.
     * - Games: the margin and the system column slid left until only the column's last [peek]
     *   shows, from x 0 to [peek] (48 on the RP5; the list then starts at 72).
     * - Details (narrow): the list slid out until only its last [detailsStopListPeek] shows,
     *   from x 0 (56 on the Nova; details then start at 80).
     */
    fun offsetOf(column: Column): Dp =
        when (column) {
            Column.Systems -> 0.dp
            Column.Games -> peek.coerceIn(0.dp, systemsWidth) - (leadingMargin + systemsWidth)
            Column.Details -> detailsStopListPeek - (leadingMargin + systemsWidth + columnGap + gamesListWidth)
        }

    /** The columns the strip may rest at for this focus and content, left to right. */
    fun stops(
        focus: StripFocus,
        content: StripContent,
    ): List<Column> {
        val entry = content.systems.getOrNull(focus.systemIndex)
        if (entry == null || entry.itemCount == 0) return listOf(Column.Systems)
        return if (isNarrow && content.hasBoxArt && entry.hasDetails) Column.entries else listOf(Column.Systems, Column.Games)
    }
}

/**
 * The stop a released drag settles on. A fling faster than [flingVelocity] (px/s, either sign)
 * goes to the next stop in its direction; a slower release goes to the nearest. [stopOffsets]
 * pairs each stop with its offset in px.
 */
internal fun settleStop(
    offset: Float,
    velocity: Float,
    stopOffsets: List<Pair<Column, Float>>,
    flingVelocity: Float,
): Column {
    // Systems first (offset 0), then more and more negative.
    val ordered = stopOffsets.sortedByDescending { it.second }
    val slack = 0.5f
    return when {
        velocity <= -flingVelocity -> (ordered.firstOrNull { it.second < offset - slack } ?: ordered.last()).first
        velocity >= flingVelocity -> (ordered.lastOrNull { it.second > offset + slack } ?: ordered.first()).first
        else -> ordered.minBy { abs(it.second - offset) }.first
    }
}
