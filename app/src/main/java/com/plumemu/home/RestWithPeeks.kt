package com.plumemu.home

import kotlin.math.abs

/** How a rest treats a row tall enough to be left as a lone line: see [peeksShow]. */
internal enum class TallRows { AsAnyRow, ShowMore, MayGo }

/** How many peeks tall a row is when its tail under the top fade would read as a lone line of text. */
internal const val TALL_ROW_PEEKS = 2.5f

/** How many peeks of a tall row must show past the top edge, or it shows none. */
internal const val TALL_ROW_SHOWN_PEEKS = 2

/**
 * Whether a list resting at [at] shows at least [peek] px of a row past each edge it can scroll past: a row cut there, so it is plain the list goes on. With [tall] other than
 * [TallRows.AsAnyRow] a tall row cut at the top must show [TALL_ROW_SHOWN_PEEKS] peeks, its last line alone under the fade reading as a stray line; [TallRows.MayGo] also lets it be gone wholly.
 */
internal inline fun peeksShow(
    at: Int,
    window: Int,
    peek: Int,
    min: Int,
    max: Int,
    count: Int,
    top: (Int) -> Int,
    end: (Int) -> Int,
    tall: TallRows = TallRows.AsAnyRow,
): Boolean {
    var aboveShows = at <= min
    var belowShows = at >= max
    val bottom = at + window
    for (i in 0 until count) {
        val t = top(i)
        val e = end(i)
        val isTall = tall != TallRows.AsAnyRow && e - t >= peek * TALL_ROW_PEEKS
        if (t < at && e - at >= (if (isTall) peek * TALL_ROW_SHOWN_PEEKS else peek)) aboveShows = true
        if (isTall && tall == TallRows.MayGo && e <= at && (i == count - 1 || top(i + 1) >= at)) aboveShows = true
        if (e > bottom && bottom - t >= peek) belowShows = true
    }
    return aboveShows && belowShows
}

/**
 * The scroll position nearest [wanted] where [keepTop] to [keepEnd] stay whole in a window [window] px tall and each edge the list continues past
 * shows [peek] px of the next row (design/rules.md › Lists that scroll), or null when there is none (the window is too short for all of it). Positions
 * run from [min] to [max]; [top] and [end] are row edges in the same px, for rows `0 until count` in order. A move of the focus only; allocates nothing.
 * A rest that leaves a tall row's tail alone at the top ([peeksShow]) gives way to one showing more of the row, else to one with the row gone, up to [pastMax] px past [max] where
 * the list can add room at its foot.
 */
internal inline fun restWithPeeks(
    wanted: Int,
    window: Int,
    peek: Int,
    min: Int,
    max: Int,
    keepTop: Int,
    keepEnd: Int,
    count: Int,
    top: (Int) -> Int,
    end: (Int) -> Int,
    pastMax: Int = 0,
): Int? {
    val low = maxOf(min, keepEnd - window)
    val high = minOf(max, keepTop)
    if (low > high) return null
    val start = wanted.coerceIn(low, high)
    if (peeksShow(start, window, peek, min, max, count, top, end, TallRows.ShowMore)) return start
    // The tall row whole where that leaves the rest whole too; else as much of it as fits. Past the end only to take it out of the rest, never for a list opened with no rows laid out above its focus.
    var tailTop = Int.MIN_VALUE
    for (i in 0 until count) {
        val t = top(i)
        val e = end(i)
        if (t < start && e > start && e - t >= peek * TALL_ROW_PEEKS) tailTop = t
    }
    val leavesTail = tailTop != Int.MIN_VALUE
    if (leavesTail && tailTop in low..high && peeksShow(tailTop, window, peek, min, max, count, top, end, TallRows.ShowMore)) return tailTop
    nearestRest(start, window, peek, min, max, low, high, count, top, end, TallRows.ShowMore, lastEdge = 1)?.let { return it }
    nearestRest(start, window, peek, min, max, low, high, count, top, end, TallRows.ShowMore)?.let { return it }
    return nearestRest(start, window, peek, min, max, low, if (leavesTail) minOf(max + pastMax, keepTop) else high, count, top, end, TallRows.MayGo)
        ?: if (peeksShow(start, window, peek, min, max, count, top, end)) start else nearestRest(start, window, peek, min, max, low, high, count, top, end, TallRows.AsAnyRow)
}

/** The rest in [low]..[high] nearest [start] that [peeksShow] allows. */
internal inline fun nearestRest(
    start: Int,
    window: Int,
    peek: Int,
    min: Int,
    max: Int,
    low: Int,
    high: Int,
    count: Int,
    top: (Int) -> Int,
    end: (Int) -> Int,
    tall: TallRows,
    lastEdge: Int = 3,
): Int? {
    var best: Int? = null
    var distance = Int.MAX_VALUE
    // The candidates: each end of the range, each row showing exactly the peek at either edge, and for a tall row two peeks of it or none.
    for (i in -2 until count) {
        for (edge in 0..lastEdge) {
            val candidate =
                when {
                    i == -2 -> if (edge == 0) low else if (edge == 1) high else continue
                    i == -1 -> continue
                    edge == 0 -> end(i) - peek
                    edge == 1 -> top(i) + peek - window
                    edge == 2 -> end(i) - peek * TALL_ROW_SHOWN_PEEKS
                    else -> end(i)
                }
            if (candidate !in low..high) continue
            val from = abs(candidate - start)
            if (from < distance && peeksShow(candidate, window, peek, min, max, count, top, end, tall)) {
                best = candidate
                distance = from
            }
        }
    }
    return best
}
