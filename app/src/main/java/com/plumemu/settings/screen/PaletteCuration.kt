package com.plumemu.settings.screen

import com.plumemu.coreui.theme.Rgb
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The swatches the "+" page shows (48 tiles had near-duplicates): [ROWS] rows of
 * [COLUMNS] out of the full 8 x 6 palette of `CustomPalette`, which stays the source because every cell
 * of it already reads on its page (`CustomPaletteTest`). Each hue column gives its lightest, middle and
 * darkest cell; a cell within [MIN_DISTANCE] of one already kept is replaced by the next-best cell of its
 * column or, when none is far enough, left out, so the grid never shows two tiles that look alike.
 * Readability is not re-tested here: this only ever picks from cells that passed.
 */
internal object PaletteCuration {
    const val COLUMNS = 8
    const val ROWS = 3

    /** Smallest CIE76 colour difference between two tiles; about "clearly different at a glance" (2.3 is just noticeable). */
    const val MIN_DISTANCE = 12.0

    // The page asks for the same palette on every frame; the source list is cached by CustomPalette, so identity is a fair key.
    private val last = arrayOfNulls<Pair<List<Rgb>, List<Rgb>>>(1)

    fun curated(full: List<Rgb>): List<Rgb> {
        synchronized(last) {
            last[0]?.takeIf { it.first === full }?.let { return it.second }
            return pick(full).also { last[0] = full to it }
        }
    }

    /** The curated tiles of [full] (row by row, [COLUMNS] a row; the last row may be short). */
    fun pick(full: List<Rgb>): List<Rgb> {
        val rows = full.size / COLUMNS
        if (rows == 0) return full.distinct()
        // Each column, lightest first.
        val columns = (0 until COLUMNS).map { c -> (0 until rows).map { full[it * COLUMNS + c] }.distinct().sortedByDescending(::lightness) }
        val kept = mutableListOf<Rgb>()
        for (slot in 0 until ROWS) {
            for (column in columns) {
                if (column.isEmpty()) continue
                val wanted = Math.round(slot * (column.size - 1).toDouble() / (ROWS - 1)).toInt()
                val pick = column.sortedBy { abs(column.indexOf(it) - wanted) }.firstOrNull { c -> kept.none { distance(it, c) < MIN_DISTANCE } }
                if (pick != null) kept += pick
            }
        }
        return kept
    }

    /** CIE76 colour difference of two colours. */
    fun distance(
        a: Rgb,
        b: Rgb,
    ): Double {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun lightness(c: Rgb): Double = lab(c).first

    private fun lab(c: Rgb): Triple<Double, Double, Double> {
        fun linear(v: Int): Double = (v / 255.0).let { if (it <= 0.04045) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        val r = linear(c.red)
        val g = linear(c.green)
        val bl = linear(c.blue)
        val x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * bl) / 0.95047
        val y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * bl
        val z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * bl) / 1.08883
        fun f(t: Double) = if (t > 0.008856) Math.cbrt(t) else 7.787 * t + 16.0 / 116
        return Triple(116 * f(y) - 16, 500 * (f(x) - f(y)), 200 * (f(y) - f(z)))
    }
}
