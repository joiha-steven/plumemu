package com.plumemu.home

import androidx.compose.runtime.Immutable
import com.plumemu.coreinput.strip.PackedRows
import kotlin.math.abs
import kotlin.math.ln

/** One box of a packed row: its left edge in the grid and its width, in dp (the collar's outer edge). */
@Immutable
internal data class PackedBox(
    val x: Float,
    val width: Float,
)

/** One row of a packed grid: the index of its first game, its boxes left to right and its height in dp (the collars' outer height). */
@Immutable
internal data class PackedRow(
    val start: Int,
    val boxes: List<PackedBox>,
    val height: Float,
)

/**
 * A cover grid's rows (docs/design/rules.md › Grid): the one place the packing is computed. The drawing reads
 * [rows]; the focus rules read [toRows]. Equal grids compare equal, so a recompute that changes nothing changes nothing.
 */
@Immutable
internal data class PackedGrid(
    val rows: List<PackedRow>,
    val count: Int,
) {
    /** The row holding game [index] (0 for an empty grid). */
    fun rowOf(index: Int): Int {
        var low = 0
        var high = rows.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (rows[mid].start <= index) low = mid else high = mid - 1
        }
        return low.coerceAtLeast(0)
    }

    /** The box of game [index]. */
    fun boxOf(index: Int): PackedBox? {
        val row = rows.getOrNull(rowOf(index)) ?: return null
        return row.boxes.getOrNull(index - row.start)
    }

    /** What the focus rules need: where each row starts and each box's centre. */
    fun toRows(ids: List<String> = emptyList()): PackedRows = PackedRows(rows.map { it.start }, rows.flatMap { row -> row.boxes.map { it.x + it.width / 2f } }, ids)

    companion object {
        val Empty = PackedGrid(emptyList(), 0)
    }
}

/**
 * Packs boxes of [aspects] (width over height) into rows, [gap] apart, that span [maxWidth] edge to edge. A box is a collar (the
 * outer edge that is drawn) round a picture of its aspect: its width is the picture's height times the aspect plus [chromeX], its
 * height the picture's plus [chromeY]. The collar stays the board's thin one and the picture stays whole; only the picture's size changes.
 *
 * - Every box one shape: [columns] boxes to a row (the Size step's count), ONE box width `(maxWidth - (columns - 1) * gap) / columns`
 *   exactly, one height from the shape. The last row keeps the same columns and may end short.
 * - Mixed shapes: each row scaled to span [maxWidth] exactly at its own height; see [justifiedRows]. [rowHeight] is their target.
 *
 * A box wider than a whole row takes the row's width. Units are the caller's (dp). A damaged aspect counts as square.
 */
internal fun packRows(
    aspects: List<Float>,
    columns: Int,
    rowHeight: Float,
    gap: Float,
    maxWidth: Float,
    chromeX: Float,
    chromeY: Float,
): PackedGrid {
    if (aspects.isEmpty()) return PackedGrid.Empty
    val shapes = aspects.map { raw -> raw.takeIf { it.isFinite() && it > 0f } ?: 1f }
    val uniform = shapes.all { abs(it - shapes[0]) <= ASPECT_MATCH * shapes[0] }
    val rows = if (uniform) evenColumns(shapes.size, shapes[0], columns, gap, maxWidth, chromeX, chromeY) else justifiedRows(shapes, rowHeight, gap, maxWidth, chromeX, chromeY)
    return PackedGrid(rows, aspects.size)
}

/** [count] boxes of [aspect] in [columns] columns that fill [maxWidth] exactly with one box width and one height. */
private fun evenColumns(
    count: Int,
    aspect: Float,
    columns: Int,
    gap: Float,
    maxWidth: Float,
    chromeX: Float,
    chromeY: Float,
): List<PackedRow> {
    val across = columns.coerceAtLeast(1)
    val boxWidth = (maxWidth - (across - 1) * gap) / across
    val height = (boxWidth - chromeX) / aspect + chromeY
    return List((count + across - 1) / across) { row ->
        val start = row * across
        PackedRow(start, List(minOf(across, count - start)) { PackedBox(it * (boxWidth + gap), boxWidth) }, height)
    }
}

/**
 * Mixed shapes: rows justified to [maxWidth]. A row of boxes `start until end` spans it exactly at the picture height
 * `chromeY + (maxWidth - (n - 1) * gap - n * chromeX) / sum of aspects`, every box scaled by the same factor (its collar's thin
 * padding unchanged). The row breaks are the ones that keep every row's height near [rowHeight], the Size step's target: a dynamic
 * program over the sum of squared log ratios of height to target, so one awkward box does not drag a whole row away from it. There is
 * no cap on a row's scale. Only the very last row of the list, when its boxes fit at the target height (justifying them would make
 * them larger), stays at the target, left aligned, and may end short.
 */
private fun justifiedRows(
    aspects: List<Float>,
    rowHeight: Float,
    gap: Float,
    maxWidth: Float,
    chromeX: Float,
    chromeY: Float,
): List<PackedRow> {
    val count = aspects.size
    val prefix = DoubleArray(count + 1)
    for (i in 0 until count) prefix[i + 1] = prefix[i] + aspects[i]
    // The height of a row of boxes from..to, or NaN when even their collars do not fit across.
    fun heightOf(from: Int, to: Int): Double {
        val room = maxWidth - (to - from - 1) * gap - (to - from) * chromeX
        return if (room <= 0f) Double.NaN else chromeY + room / (prefix[to] - prefix[from])
    }
    fun cost(height: Double): Double = ln(height / rowHeight).let { it * it }
    val best = DoubleArray(count + 1)
    val next = IntArray(count + 1)
    for (from in count - 1 downTo 0) {
        var to = from + 1
        var bestCost = Double.MAX_VALUE
        var bestEnd = from + 1
        while (to <= count) {
            val height = heightOf(from, to)
            if (height.isNaN()) break
            // The list's last row at the target height costs nothing when its boxes fit across there.
            val rowCost = if (to == count && height >= rowHeight) 0.0 else cost(height)
            val total = rowCost + best[to]
            if (total < bestCost) {
                bestCost = total
                bestEnd = to
            }
            if (height <= rowHeight * SHORTEST_ROW) break
            to++
        }
        // A lone box whose collars do not even fit across (a zero-width grid) takes a row at the target.
        best[from] = if (bestCost == Double.MAX_VALUE) best[from + 1] else bestCost
        next[from] = bestEnd
    }
    val rows = ArrayList<PackedRow>()
    var start = 0
    while (start < count) {
        val end = next[start]
        val fitted = heightOf(start, end)
        val isLast = end == count && fitted >= rowHeight
        val height = if (isLast || fitted.isNaN()) rowHeight else fitted.toFloat()
        var x = 0f
        val boxes =
            List(end - start) { k ->
                val width = if (!isLast && !fitted.isNaN() && start + k == end - 1) maxWidth - x else (height - chromeY) * aspects[start + k] + chromeX
                PackedBox(x, width).also { x += width + gap }
            }
        rows += PackedRow(start, boxes, height)
        start = end
    }
    return rows
}

/** The shortest row (a share of the target height) the row-break search still looks at: past it every longer row is shorter still. */
private const val SHORTEST_ROW = 0.5

/** Two aspects within this share of each other count as the same shape. */
private const val ASPECT_MATCH = 0.005f

/** The glide's room for a justified row taller than its target (a share of it); rows are not capped, but the break search keeps them near it. */
internal const val ROW_SCALE_ROOM = 1.3f
