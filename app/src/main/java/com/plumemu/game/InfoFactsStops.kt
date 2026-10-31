package com.plumemu.game

import kotlin.math.ceil

/*
 * Where Info's facts stop when Up and Down scroll them (InfoFacts). Pure: rows are given as their top and
 * bottom (px, exclusive) in the scrolled content, top to bottom.
 *
 * Each press shows the next row whole. Wherever rows stay hidden on a side, the first of them is visibly
 * cut at that edge: part of it shows and part of it is missing ([FactsCut]), so it is plain there is more
 * that way. Lists keep the row past the focus whole for the same reason (design/rules.md › Focus and
 * current); Info has no focus to keep a row past, so it keeps a cut row at the edge instead.
 */

/**
 * How a row at an edge of Info's scroll must be cut to say "more this way": at least [shown] px of it in
 * view (a third of a line) and at least [hidden] px of it out of view (a quarter of a line), so it reads as
 * cut, not as whole. A line is the shortest row (one line of the table's text).
 */
internal class FactsCut(
    val shown: Int,
    val hidden: Int,
) {
    companion object {
        fun of(spans: List<IntRange>): FactsCut {
            val line = spans.minOfOrNull { it.last + 1 - it.first } ?: 0
            return FactsCut(ceil(line * SHOWN_OF_LINE).toInt(), ceil(line * HIDDEN_OF_LINE).toInt())
        }
    }
}

/**
 * Whether the scroll at [at] with a [viewport] that high says where more rows are: the first row not
 * whole below the bottom edge, and the last not whole above the top edge, are each cut by [cut] (or there
 * is none that way).
 */
internal fun showsMoreRows(
    at: Int,
    viewport: Int,
    spans: List<IntRange>,
    cut: FactsCut,
): Boolean {
    val bottom = at + viewport
    val below = spans.firstOrNull { it.last + 1 > bottom }
    if (below != null && (bottom - below.first < cut.shown || below.last + 1 - bottom < cut.hidden)) return false
    val above = spans.lastOrNull { it.first < at }
    return above == null || (above.last + 1 - at >= cut.shown && at - above.first >= cut.hidden)
}

/**
 * Where Info's facts scroll for one press, from [now]: Down shows the first row not whole below the
 * [viewport] (a row taller than the viewport from its top), Up the last row not whole above; each the
 * least distance that also leaves the rows still hidden visibly cut at both edges ([showsMoreRows]),
 * never past the row (no row is skipped). When no such place exists, the row is brought to the edge.
 * Within 0..[max]; [now] when there is nothing more that way (the facts fit, every row below has been
 * shown, or the top is shown).
 */
internal fun nextFactsScroll(
    now: Int,
    viewport: Int,
    max: Int,
    spans: List<IntRange>,
    isDown: Boolean,
): Int {
    if (viewport <= 0 || max <= 0) return now
    val cut = FactsCut.of(spans)
    fun isTall(row: IntRange) = row.last + 1 - row.first > viewport
    return if (isDown) {
        // A row taller than the viewport is reached once its top is at the top edge.
        fun reached(row: IntRange, at: Int) = if (isTall(row)) row.first <= at else row.last + 1 <= at + viewport
        // Every row shown: what is left below is the room after the last row (FactsFit.endRoom), not a stop.
        val row = spans.firstOrNull { !reached(it, now) } ?: return now
        val edge = if (isTall(row)) row.first else row.last + 1 - viewport
        // Never past the row: it stays whole (a tall one still fills the viewport).
        val last = minOf(max, if (isTall(row)) row.last + 1 - viewport else row.first)
        (maxOf(edge, now + 1)..last).firstOrNull { showsMoreRows(it, viewport, spans, cut) } ?: edge.coerceIn(now, max)
    } else {
        val row = spans.lastOrNull { it.first < now } ?: return if (now > 0) 0 else now
        val first = maxOf(0, if (isTall(row)) row.first - viewport else row.last + 1 - viewport)
        (minOf(row.first, now - 1) downTo first).firstOrNull { showsMoreRows(it, viewport, spans, cut) } ?: row.first.coerceIn(0, now)
    }
}

/**
 * Info's scroll in a given height: its own height ([viewport]) and the room after the last row
 * ([endRoom], px) that lets the last stop cut the row above it.
 */
internal data class FactsFit(
    val viewport: Int,
    val endRoom: Int,
)

/**
 * How Info's scroll fits [available] px: all of it, no room, when the rows fit. Otherwise the highest
 * viewport whose bottom edge cuts a row as Info opens (not a gap after one, which would end the table
 * cleanly with rows still hidden) and from which every Down to the end, and every Up back, stops where
 * [showsMoreRows] holds; the last Down's place decides the room after the last row (the end's place is
 * set by the content's height, not by the viewport). At most two rows of [available] are given up; when
 * no height works, all of it, and the stops bring each row to the edge as best they can.
 */
internal fun factsFit(
    available: Int,
    spans: List<IntRange>,
): FactsFit {
    val all = FactsFit(available, 0)
    if (spans.isEmpty() || available <= 0) return all
    val end = spans.last().last + 1
    if (end <= available) return all
    val cut = FactsCut.of(spans)
    val tallest = spans.maxOf { it.last + 1 - it.first }
    for (viewport in available downTo maxOf(1, available - 2 * tallest)) {
        if (!showsMoreRows(0, viewport, spans, cut)) continue
        // Room for the last stop to go past the content's end, if it must.
        val roomy = end - viewport + 2 * tallest
        val bottom = lastStop(0, viewport, roomy, spans, cut, isDown = true) ?: continue
        if (lastStop(bottom, viewport, bottom, spans, cut, isDown = false) == null) continue
        return FactsFit(viewport, maxOf(0, bottom + viewport - end))
    }
    return all
}

/** Where presses from [from] that way stop at last, or null when one stops where [showsMoreRows] does not hold. */
private fun lastStop(
    from: Int,
    viewport: Int,
    max: Int,
    spans: List<IntRange>,
    cut: FactsCut,
    isDown: Boolean,
): Int? {
    var at = from
    // One press per row is always enough; the bound keeps a bad layout from spinning.
    repeat(spans.size + 1) {
        val next = nextFactsScroll(at, viewport, max, spans, isDown)
        if (next == at) return at
        if (!showsMoreRows(next, viewport, spans, cut)) return null
        at = next
    }
    return null
}

private const val SHOWN_OF_LINE = 1f / 3
private const val HIDDEN_OF_LINE = 1f / 4
