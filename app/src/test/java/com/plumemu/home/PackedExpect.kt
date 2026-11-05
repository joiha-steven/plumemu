package com.plumemu.home

import androidx.compose.ui.unit.Dp
import com.plumemu.coreui.components.CollarSize
import com.plumemu.coreui.theme.CoverShape
import com.plumemu.coreui.theme.PlumLayout

/**
 * The packing rule worked out again for tests (not the production code): one shape fills the span with the Size step's even
 * columns; mixed shapes are justified rows, each scaled to span the width at its own height, the breaks chosen to keep each height
 * near the step's target, the list's last row left aligned at the target (docs/design/rules.md › Grid).
 * [at] is each game's centre in the grid, [rows] the games of each row.
 */
internal class ExpectedPacking(
    val rows: List<List<Int>>,
    val at: List<Float>,
    /** Each row's outer height, and each game's left edge and width (dp). */
    val heights: List<Float> = emptyList(),
    val lefts: List<Float> = emptyList(),
    val widths: List<Float> = emptyList(),
) {
    fun rowOf(index: Int): Int = rows.indexOfFirst { index in it }

    /** The game of the next row whose centre is nearest [from]'s; of two as near, the left one; [from] itself on the last row. */
    fun down(from: Int): Int {
        val next = rows.getOrNull(rowOf(from) + 1) ?: return from
        return next.minWith(compareBy({ Math.abs(at[it] - at[from]) }, { it }))
    }

    /** The game of the row above, as [down]. */
    fun up(from: Int): Int {
        val above = rows.getOrNull(rowOf(from) - 1) ?: return from
        return above.minWith(compareBy({ Math.abs(at[it] - at[from]) }, { it }))
    }
}

internal fun expectedPacking(
    aspects: List<Float>,
    layout: PlumLayout,
    shape: CoverShape,
): ExpectedPacking {
    val span = layout.gridCoversWidth.value
    val gap = layout.gridGap(shape).value
    val target = layout.gridRowHeight(shape).value
    val chromeY = (CollarSize.Cover.topPadding + CollarSize.Cover.bottomPadding).value
    val collar = (CollarSize.Cover.sidePadding * 2).value
    val rows = mutableListOf<MutableList<Int>>()
    val at = FloatArray(aspects.size)
    val lefts = FloatArray(aspects.size)
    val widths = FloatArray(aspects.size)
    val heights = mutableListOf<Float>()
    if (aspects.all { Math.abs(it - aspects[0]) <= 0.005f * aspects[0] }) {
        // One shape: the Size step's columns share the span exactly.
        val columns = layout.gridColumns(shape)
        val box = (span - (columns - 1) * gap) / columns
        val height = (box - collar) / aspects[0] + chromeY
        aspects.indices.forEach { i ->
            if (i % columns == 0) {
                rows += mutableListOf<Int>()
                heights += height
            }
            rows.last() += i
            lefts[i] = (i % columns) * (box + gap)
            widths[i] = box
            at[i] = lefts[i] + box / 2f
        }
        return ExpectedPacking(rows, at.toList(), heights, lefts.toList(), widths.toList())
    }
    // Mixed shapes: a row of games from..to-1 is justified at the height that spans the width; the breaks are the cheapest by the
    // squared log of height over target, found forwards (the production code searches backwards), the list's last row at the target
    // for free when it fits there.
    fun height(from: Int, to: Int): Double? {
        val room = span - (to - from - 1) * gap - (to - from) * collar
        return if (room <= 0f) null else chromeY + room / (from until to).sumOf { aspects[it].toDouble() }
    }
    val n = aspects.size
    val cost = DoubleArray(n + 1) { Double.MAX_VALUE }
    val from = IntArray(n + 1)
    cost[0] = 0.0
    for (start in 0 until n) {
        for (to in start + 1..n) {
            val h = height(start, to) ?: break
            val row = if (to == n && h >= target) 0.0 else Math.log(h / target).let { it * it }
            if (cost[start] + row < cost[to]) {
                cost[to] = cost[start] + row
                from[to] = start
            }
            if (h <= target * 0.5) break
        }
        if (cost[start + 1] == Double.MAX_VALUE) { cost[start + 1] = cost[start]; from[start + 1] = start }
    }
    val breaks = ArrayList<Pair<Int, Int>>()
    var end = n
    while (end > 0) { breaks += from[end] to end; end = from[end] }
    for ((start, to) in breaks.reversed()) {
        val h = height(start, to)
        val isLast = to == n && h != null && h >= target
        val rowHeight = if (isLast || h == null) target else h.toFloat()
        heights += rowHeight
        var x = 0f
        for (i in start until to) {
            if (i == start) rows += mutableListOf<Int>()
            rows.last() += i
            val w = (rowHeight - chromeY) * aspects[i] + collar
            lefts[i] = x
            widths[i] = w
            at[i] = x + w / 2f
            x += w + gap
        }
    }
    return ExpectedPacking(rows, at.toList(), heights, lefts.toList(), widths.toList())
}

/** Dp as a plain float for the expectations above. */
internal val Dp.f: Float get() = value

/** The board's Super Nintendo grid as [expectedPacking] sees it, from the picture shapes the art fakes give. */
internal fun boardSnesPacking(layout: PlumLayout): ExpectedPacking {
    val board = FakeHomeContent.withArtwork()
    val entry = board.entries[FakeHomeContent.BOARD_SNES_INDEX]
    val art = artFor(board)
    return expectedPacking(entry.rows.map { checkNotNull(art.getValue(artKey(it.id)).grid).aspect }, layout, entry.coverShape)
}
