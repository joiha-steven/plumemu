package com.plumemu.home

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Moves and scroll over a justified grid whose rows differ in height; plain JVM, units are dp (px at density 2 for the spans). */
class GridMixedRowsTest {
    private val aspects = List(120) { listOf(0.9f, 1.5f, 1.0f, 0.7f, 1.4f, 1.0f, 0.9f)[(it * 5 + it / 3) % 7] }
    private val grid = packRows(aspects, columns = 4, rowHeight = 135f, gap = 6.667f, maxWidth = 741f, chromeX = 8f, chromeY = 12f)

    @Test
    fun theRowsDifferInHeightForTheseMoves() {
        assertTrue("rows of different heights: ${grid.rows.map { it.height.toInt() }.distinct()}", grid.rows.dropLast(1).map { Math.round(it.height) }.toSet().size >= 2)
        assertTrue("rows of different box counts", grid.rows.map { it.boxes.size }.toSet().size >= 2)
    }

    @Test
    fun upAndDownKeepTheColumnByTheXCentreAcrossRowsOfDifferentHeights() {
        val table = grid.toRows()
        for (row in 0 until grid.rows.size - 1) for (game in grid.rows[row].start until grid.rows[row + 1].start) {
            val centre = table.centres[game]
            val below = table.nearestIn(row + 1, centre)
            val candidates = grid.rows[row + 1].boxes.mapIndexed { k, box -> grid.rows[row + 1].start + k to abs(box.x + box.width / 2f - centre) }
            val nearest = candidates.minOf { it.second }
            assertEquals("Down from $game lands on the nearest centre", nearest, candidates.first { it.first == below }.second, 0.001f)
            val back = table.nearestIn(row, table.centres[below])
            val backGap = abs(table.centres[back] - table.centres[below])
            assertTrue("Up from $below stays within its row's nearest centre", backGap <= abs(centre - table.centres[below]) + 0.001f)
            assertEquals("Up from $below lands in row $row", row, table.rowOf(back))
        }
    }

    @Test
    fun theFocusedRowIsWholeInTheWindowAfterTheScrollWhateverItsHeight() {
        val density = Density(2.29375f)
        val spans = RowSpans.of(grid, 9.333f.dp, density)
        for (viewport in listOf(ROW_PX_MIN, 900, 1000, 1200)) for (row in 0 until spans.count) {
            val first = spans.firstShowing(row, viewport)
            assertTrue("row $row is not above the window's first row $first", first <= row)
            val shown = spans.bottom(row) - spans.tops[first]
            // A row taller than the window can only be shown from its top; any other ends whole inside the window.
            assertTrue("row $row ($viewport px window, first $first) ends ${shown} px below the window's top", shown <= viewport || first == row)
            // The rows above the first shown are those that no longer fit: the first shown one is the lowest that still does.
            if (first > 0 && shown <= viewport) assertTrue("row $row: one row earlier would not fit", spans.bottom(row) - spans.tops[first - 1] > viewport)
        }
    }

    @Test
    fun spansFollowEachRowsOwnHeight() {
        val spans = RowSpans.of(grid, 9.333f.dp, Density(2f))
        for (row in 1 until spans.count) {
            assertEquals("row $row stands a row gap under row ${row - 1}", spans.bottom(row - 1) + 19, spans.tops[row])
            assertEquals("row $row is as tall as packed", Math.round(grid.rows[row].height * 2f), spans.heights[row])
        }
    }

    private companion object {
        /** A window no taller than a tall row, so a row may stand alone. */
        const val ROW_PX_MIN = 400
    }
}
