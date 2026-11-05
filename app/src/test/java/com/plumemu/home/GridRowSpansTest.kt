package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The row positions the grid's scroll rules read; plain JVM, units are px. Rows may differ in height. */
class GridRowSpansTest {
    private fun spans(vararg heights: Int, gap: Int = 10): RowSpans {
        val tops = IntArray(heights.size)
        var top = 0
        heights.forEachIndexed { i, h ->
            tops[i] = top
            top += h + gap
        }
        return RowSpans(tops, intArrayOf(*heights))
    }

    @Test
    fun rowsOfUnequalHeightsStandOneAfterTheOtherAGapApart() {
        val rows = spans(100, 140, 120)
        assertEquals(listOf(0, 110, 260), rows.tops.toList())
        assertEquals(380, rows.bottom(2))
    }

    @Test
    fun theFirstRowShownMakesTheFocusedRowTheLastWholeOne() {
        val rows = spans(100, 140, 120, 100, 100)
        // A window of 300 holds rows 1 and 2 (140 + 10 + 120 = 270) but not 0 as well.
        assertEquals(0, rows.firstShowing(1, 300))
        assertEquals(1, rows.firstShowing(2, 300))
        // Row 3 ends 480: the rows that fit above it in 300 are 2 and 3 (120 + 10 + 100 = 230); 1 would make 380.
        assertEquals(2, rows.firstShowing(3, 300))
        // A row taller than the window is shown on its own.
        assertEquals(1, rows.firstShowing(1, 50))
    }

    @Test
    fun theEndOfTheListRestsOnARowsTopWithTheRoomLeftUnderIt() {
        val rows = spans(100, 140, 120, 100, 100)
        // The last rows that fit 300: rows 3 and 4 (100 + 10 + 100 = 210); the 90 left over is the padding under the list.
        assertEquals(3, rows.lastFirst(300))
        assertEquals(90, rows.leftover(300))
        // Rows 2 to 4 fit 350 (120 + 10 + 100 + 10 + 100 = 340).
        assertEquals(2, rows.lastFirst(350))
        assertEquals(10, rows.leftover(350))
    }

    @Test
    fun aShortListNeedsNoRoom() {
        assertEquals(0, spans(100).lastFirst(300))
        assertEquals(200, spans(100).leftover(300))
        assertEquals(0, RowSpans(IntArray(0), IntArray(0)).leftover(300))
        assertEquals(0, RowSpans(IntArray(0), IntArray(0)).firstShowing(0, 300))
    }

    @Test
    fun equalRowsAreTheOldRowCountRule() {
        // Four rows of 100 with 10 between fit a window of 430 (a fifth would need 540): firstShowing is `row - fit + 1`.
        val rows = RowSpans.uniform(count = 20, height = 100, gap = 10)
        assertEquals(0, rows.firstShowing(3, 430))
        assertEquals(1, rows.firstShowing(4, 430))
        assertEquals(16, rows.lastFirst(430))
        assertTrue(rows.leftover(430) == 0)
    }
}
