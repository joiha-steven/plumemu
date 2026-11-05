package com.plumemu.home

internal enum class DetailsCell { GENRE, RATING, PLAYED, LAST, EMULATOR, BATTERY }

/**
 * Where the cells of the details box stand: [rows] of cells in reading order, each cell's [shares] of the column's width (a row's shares add
 * up to 1), and whether the battery cell says its time without the word "About" ([batteryShort]) so that every cell fits.
 */
internal class DetailsArrangement(
    val rows: List<List<DetailsCell>>,
    val shares: Map<DetailsCell, Float>,
    val batteryShort: Boolean,
    /** What each cell needs to stand whole on one line (at most 1): a cell with a share under it is ellipsised. */
    val needs: Map<DetailsCell, Float> = emptyMap(),
)

/**
 * The arrangement of the cells in [needs] (the share of the column each needs to stand on one line, with its gutter; only the cells that have a
 * value) in at most [maxRows] rows, a pure function of the values in hand: a value that comes late gets the place it would have had on time.
 *
 * Preferred: the pairs GENRE + RATING, PLAYED + LAST PLAYED, EMULATOR + BATTERY. When the emulator's name needs a row of its own, BATTERY
 * goes before it into the row of PLAYED + LAST PLAYED (which stay together), else after RATING. Only if that is not enough the battery cell
 * says its time without "About" ([batteryShortNeed], its share). Every value is one line: a row whose cells need more than the column gives way
 * in the cells of [floors] (the least share each keeps), GENRE first, then EMULATOR, and their text ends in an ellipsis. A row of k cells gives
 * each 1/k when all fit that; else each its need and the slack shared. Between choices that keep the same text whole, the one whose rows of two
 * share one column line. When no choice holds the rows, the fewest rows.
 */
internal fun arrangeCells(
    needs: Map<DetailsCell, Float>,
    batteryShortNeed: Float?,
    maxRows: Int,
    floors: Map<DetailsCell, Float> = emptyMap(),
): DetailsArrangement {
    val tolerance = 1.0001f
    var best: DetailsArrangement? = null
    var bestScore: List<Int>? = null
    for (short in listOf(false, true)) {
        if (short && (batteryShortNeed == null || DetailsCell.BATTERY !in needs)) continue
        val effective = if (short) needs + (DetailsCell.BATTERY to batteryShortNeed!!) else needs
        for ((variant, base) in ORDERS.withIndex()) {
            val order = base.filter { it in effective }
            if (variant > 0 && (DetailsCell.BATTERY !in effective || DetailsCell.EMULATOR !in effective)) continue
            for (sizes in compositions(order.size)) {
                val rows = mutableListOf<List<DetailsCell>>()
                var at = 0
                for (size in sizes) {
                    rows += order.subList(at, at + size)
                    at += size
                }
                val placed = rows.map { rowShares(it, effective, floors, tolerance) }
                val fits = placed.all { it.fits }
                val split = rows.indexOfFirst { DetailsCell.PLAYED in it } != rows.indexOfFirst { DetailsCell.LAST in it }
                val capped = effective.mapValues { it.value.coerceAtMost(1f) }
                val lined = alignedShares(rows, placed.flatMap { it.shares }.toMap(), capped, floors)
                val offLine = rows.filter { it.size == 2 }.map { lined.getValue(it[0]) }.let { it.isNotEmpty() && it.max() - it.min() > EQUAL_SLACK }
                val score =
                    listOf(
                        if (fits) 0 else 1,
                        (rows.size - maxRows).coerceAtLeast(0),
                        if (split) 1 else 0,
                        placed.count { it.shrunk.contains(DetailsCell.EMULATOR) },
                        if (short) 1 else 0,
                        placed.count { it.shrunk.contains(DetailsCell.GENRE) },
                        if (offLine) 1 else 0,
                        variant,
                        sizes.sumOf { if (it == 2) 0 else if (it == 1) 1 else 2 },
                        rows.size,
                        -(sizes.firstOrNull() ?: 0),
                    )
                if (bestScore == null || score.compareLists(bestScore) < 0) {
                    bestScore = score
                    best = DetailsArrangement(rows, placed.flatMap { it.shares }.toMap(), short, capped)
                }
            }
        }
    }
    val chosen = best ?: return DetailsArrangement(emptyList(), emptyMap(), false)
    return DetailsArrangement(chosen.rows, alignedShares(chosen.rows, chosen.shares, chosen.needs, floors), chosen.batteryShort, chosen.needs)
}

/**
 * The rows of two cells share one column line (the second cell starts at the same x in each) when a single split holds all of them: each first
 * cell keeps its need (a cut one its floor) and each second cell its need. The line is the middle, or, where a first cell is cut, as far right as the second cells allow.
 */
private fun alignedShares(
    rows: List<List<DetailsCell>>,
    shares: Map<DetailsCell, Float>,
    needs: Map<DetailsCell, Float>,
    floors: Map<DetailsCell, Float>,
): Map<DetailsCell, Float> {
    val pairs = rows.filter { it.size == 2 }
    if (pairs.size < 2) return shares
    fun least(cell: DetailsCell) = if (shares.getValue(cell) < needs.getValue(cell) - EQUAL_SLACK) floors[cell] ?: shares.getValue(cell) else needs.getValue(cell)
    val lo = pairs.maxOf { least(it[0]) }
    val hi = pairs.minOf { 1f - least(it[1]) }
    if (lo > hi) return shares
    val cut = pairs.any { shares.getValue(it[0]) < needs.getValue(it[0]) - EQUAL_SLACK }
    val line = if (cut) hi else 0.5f.coerceIn(lo, hi)
    return shares + pairs.flatMap { listOf(it[0] to line, it[1] to 1f - line) }
}

private class RowShares(val shares: List<Pair<DetailsCell, Float>>, val fits: Boolean, val shrunk: Set<DetailsCell>)

private fun rowShares(
    row: List<DetailsCell>,
    needs: Map<DetailsCell, Float>,
    floors: Map<DetailsCell, Float>,
    tolerance: Float,
): RowShares {
    val capped = row.associateWith { needs.getValue(it).coerceAtMost(1f) }
    var excess = capped.values.sum() - 1f
    if (excess <= tolerance - 1f) return RowShares(sharesOf(row, needs), true, emptySet())
    // One line each: GENRE gives way first, then EMULATOR, down to their floors.
    val shrunk = mutableMapOf<DetailsCell, Float>()
    for (cell in SHRINK_ORDER) {
        if (cell !in capped || excess <= 0f) continue
        val floor = floors[cell] ?: continue
        val take = minOf(excess, (capped.getValue(cell) - floor).coerceAtLeast(0f))
        if (take > 0f) {
            shrunk[cell] = capped.getValue(cell) - take
            excess -= take
        }
    }
    return RowShares(row.map { it to (shrunk[it] ?: capped.getValue(it)) }, excess <= tolerance - 1f, shrunk.keys)
}

private fun sharesOf(
    row: List<DetailsCell>,
    needs: Map<DetailsCell, Float>,
): List<Pair<DetailsCell, Float>> {
    val k = row.size
    val capped = row.map { needs.getValue(it).coerceAtMost(1f) }
    if (capped.all { it <= 1f / k + EQUAL_SLACK }) return row.map { it to 1f / k }
    val slack = (1f - capped.sum()).coerceAtLeast(0f) / k
    return row.mapIndexed { i, cell -> cell to (capped[i] + slack).coerceAtMost(1f) }
}

private fun List<Int>.compareLists(other: List<Int>): Int {
    for (i in indices) {
        val c = this[i].compareTo(other[i])
        if (c != 0) return c
    }
    return 0
}

/** Every way to split [n] cells into consecutive rows of 1 to 3 cells. */
private fun compositions(n: Int): List<List<Int>> =
    if (n == 0) listOf(emptyList()) else (1..minOf(MAX_PER_ROW, n)).flatMap { first -> compositions(n - first).map { listOf(first) + it } }

private val ORDERS =
    listOf(
        listOf(DetailsCell.GENRE, DetailsCell.RATING, DetailsCell.PLAYED, DetailsCell.LAST, DetailsCell.EMULATOR, DetailsCell.BATTERY),
        listOf(DetailsCell.GENRE, DetailsCell.RATING, DetailsCell.PLAYED, DetailsCell.LAST, DetailsCell.BATTERY, DetailsCell.EMULATOR),
        listOf(DetailsCell.GENRE, DetailsCell.RATING, DetailsCell.BATTERY, DetailsCell.PLAYED, DetailsCell.LAST, DetailsCell.EMULATOR),
    )
private val SHRINK_ORDER = listOf(DetailsCell.GENRE, DetailsCell.EMULATOR)
private const val MAX_PER_ROW = 3
private const val EQUAL_SLACK = 0.0001f
