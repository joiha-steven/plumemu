package com.plumemu.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Info's facts scroll a row per Up or Down press ([nextFactsScroll]); the rows still hidden on a side
 * are always visibly cut at that edge ([showsMoreRows], [factsFit]); nothing moves when they fit.
 */
class InfoFactsScrollTest {
    /** Eight rows 30 px high, 10 apart: 0..29, 40..69, … 280..309 (content 310). A cut: 10 px shown, 8 hidden. */
    private val rows = (0 until 8).map { (it * 40) until (it * 40 + 30) }
    private val cut = FactsCut.of(rows)

    private fun walk(
        viewport: Int,
        isDown: Boolean,
    ): List<Int> {
        val max = 310 - viewport
        val stops = mutableListOf(if (isDown) 0 else max)
        while (true) {
            val next = nextFactsScroll(stops.last(), viewport, max, rows, isDown)
            if (next == stops.last()) return stops
            stops += next
        }
    }

    @Test
    fun aCutRowShowsAThirdOfALineAndHidesAQuarter() {
        assertEquals(10 to 8, cut.shown to cut.hidden)
        assertTrue("row 2 (80..109) cut at 100: 20 shown, 10 hidden", showsMoreRows(0, 100, rows, cut))
        // The reviewer's case: the table ends cleanly at row 2's bottom, row 3 starts in the gap after it.
        assertFalse("nothing of row 3 shows", showsMoreRows(10, 100, rows, cut))
        assertFalse("row 2 cut by 2 px only reads as whole", showsMoreRows(0, 108, rows, cut))
        assertFalse("row 2 shows 5 px only", showsMoreRows(0, 85, rows, cut))
        assertFalse("row 0 is hidden above with nothing of it showing", showsMoreRows(35, 100, rows, cut))
    }

    @Test
    fun theViewportEndsInsideARowSoEveryStopShowsMore() {
        // 100 high would end some stop in a gap at one edge or the other; 94 lets every stop cut a row at both.
        assertEquals(FactsFit(viewport = 94, endRoom = 0), factsFit(available = 100, spans = rows))
        assertEquals("the facts fit: all of it", FactsFit(400, 0), factsFit(available = 400, spans = rows))
    }

    /**
     * Measured: the facts and three lines of "Name from" (12 more apart than the facts' rows) at Larger in
     * Sans on 667 x 375. The end's place is set by where the content ends, out of step with the opening's;
     * no viewport alone suits both, so room after the last row lets the last stop cut the row above it.
     */
    @Test
    fun roomAfterTheLastRowLetsTheEndCutTheRowAbove() {
        val measured = listOf(0..36, 45..81, 90..126, 135..171, 180..216, 225..261, 270..306, 315..351, 376..412, 421..457, 466..502)
        val fit = factsFit(available = 318, spans = measured)
        val cut = FactsCut.of(measured)
        assertTrue("$fit gives up at most two rows", fit.viewport in (318 - 2 * 37)..318)
        val max = 503 + fit.endRoom - fit.viewport
        var at = 0
        val stops = mutableListOf(0)
        while (nextFactsScroll(at, fit.viewport, max, measured, isDown = true).also { at = it } != stops.last()) stops += at
        while (nextFactsScroll(at, fit.viewport, max, measured, isDown = false).also { at = it } != stops.last()) stops += at
        assertEquals("down to the end and back to the top", 0, stops.last())
        assertTrue("the end shows the last row whole: $stops", stops.max() + fit.viewport >= 503)
        stops.forEach { assertTrue("at $it of $stops ($fit)", showsMoreRows(it, fit.viewport, measured, cut)) }
    }

    @Test
    fun eachDownShowsTheNextRowWholeAndCutsTheOneAfter() {
        val down = walk(94, isDown = true)
        assertEquals(listOf(0, 48, 88, 128, 168, 208, 216), down)
        // 0 shows rows 0 and 1 whole and row 2 cut; each Down brings the cut row whole and cuts the next.
        down.forEach { assertTrue("at $it", showsMoreRows(it, 94, rows, cut)) }
        assertEquals("at the end Down does nothing", 216, nextFactsScroll(216, 94, 216, rows, isDown = true))
    }

    @Test
    fun eachUpShowsTheRowAboveWholeAndCutsTheOneBefore() {
        val up = walk(94, isDown = false)
        assertEquals(listOf(216, 168, 128, 88, 48, 8, 0), up)
        up.forEach { assertTrue("at $it", showsMoreRows(it, 94, rows, cut)) }
        assertEquals("at the top Up does nothing", 0, nextFactsScroll(0, 94, 216, rows, isDown = false))
    }

    @Test
    fun whereNoStopCanCutBothEdgesTheRowStillComesWhole() {
        // 100 high: no place shows a cut row at both edges; Down still brings row 2's bottom to the edge.
        assertEquals(10, nextFactsScroll(0, 100, 210, rows, isDown = true))
        assertEquals("and Up row 3's top", 120, nextFactsScroll(130, 100, 210, rows, isDown = false))
        assertEquals("near the top, the top itself: nothing is hidden above", 0, nextFactsScroll(45, 100, 210, rows, isDown = false))
    }

    @Test
    fun nothingMovesWhenTheFactsFit() {
        assertEquals(0, nextFactsScroll(0, viewport = 400, max = 0, spans = rows, isDown = true))
        assertEquals(0, nextFactsScroll(0, viewport = 400, max = 0, spans = rows, isDown = false))
    }

    @Test
    fun aRowTallerThanTheViewportShowsFromNearItsTop() {
        val tall = listOf(0 until 30, 40 until 200, 210 until 240)
        assertEquals("the tall row's top, cut as the edge's row", 48, nextFactsScroll(0, 100, 140, tall, isDown = true))
        assertEquals("then the next row, not the tall row again", 140, nextFactsScroll(48, 100, 140, tall, isDown = true))
    }
}
