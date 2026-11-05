package com.plumemu.coreinput.strip

import androidx.compose.runtime.Immutable

/**
 * A cover grid's rows as the screen packed them (boxes as wide as their own shapes, so rows hold different counts):
 * [rowStarts] is the index of each row's first game (ascending, first is 0), [centres] each game's horizontal
 * centre in the grid, in one unit. The reducer does not know widths; the grid computes this and hands it over.
 */
@Immutable
data class PackedRows(
    val rowStarts: List<Int>,
    val centres: List<Float>,
    /** The games' ids in the order packed (empty: unknown); a reorder with the same count is told apart by them. */
    val ids: List<String> = emptyList(),
) {
    val rowCount: Int get() = rowStarts.size

    /** The row holding game [index]. */
    fun rowOf(index: Int): Int {
        var low = 0
        var high = rowStarts.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (rowStarts[mid] <= index) low = mid else high = mid - 1
        }
        return low
    }

    fun isRowStart(index: Int): Boolean = rowStarts.binarySearch(index) >= 0

    /** Whether game [index] is the last of its row. */
    fun isRowEnd(index: Int): Boolean = index >= centres.size - 1 || isRowStart(index + 1)

    /** The game of [row] whose centre is nearest [x]; of two equally near, the left one. */
    fun nearestIn(
        row: Int,
        x: Float,
    ): Int {
        val from = rowStarts[row]
        val to = if (row + 1 < rowStarts.size) rowStarts[row + 1] - 1 else centres.size - 1
        var best = from
        for (i in from + 1..to) if (kotlin.math.abs(centres[i] - x) < kotlin.math.abs(centres[best] - x)) best = i
        return best
    }

    /** Whether this describes [count] games, and the same games in the same order when both sides know [entryIds] (a stale table is never used). */
    fun covers(
        count: Int,
        entryIds: List<String> = emptyList(),
    ): Boolean = count > 0 && centres.size == count && rowStarts.firstOrNull() == 0 && (ids.isEmpty() || entryIds.isEmpty() || ids == entryIds)
}
