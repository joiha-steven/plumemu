package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a Settings list rests: the kept rows whole, and a part of the next row past each edge it continues over. */
class RestWithPeeksTest {
    private val rows = 10
    private val stride = 42
    private val height = 40

    private fun top(i: Int) = i * stride

    private fun end(i: Int) = top(i) + height

    private val max = end(rows - 1) - 100

    private fun rest(
        wanted: Int,
        keepTop: Int,
        keepEnd: Int,
        window: Int = 100,
        peek: Int = 20,
        count: Int = rows,
        last: Int = max,
    ) = restWithPeeks(wanted, window, peek, 0, last, keepTop, keepEnd, count, ::top, ::end)

    @Test
    fun aRestThatShowsBothNeighboursStays() {
        assertEquals(18, rest(18, top(1), end(1)))
    }

    @Test
    fun aRowEndingOnTheEdgeMovesSoTheNextShows() {
        // At 40 the window ends in the gap after row 2 and row 0 is wholly gone at the top.
        val at = rest(42, top(2), end(2))
        assertTrue("row 1 or 2 shows its part above at $at", (0 until rows).any { top(it) < at!! && end(it) - at >= 20 })
        assertTrue("a row shows its part below at $at", (0 until rows).any { end(it) > at!! + 100 && at + 100 - top(it) >= 20 })
    }

    @Test
    fun theEndsNeedNoPeek() {
        assertEquals(0, rest(0, top(0), end(1), window = 110))
        assertEquals(end(9) - 110, rest(end(9) - 110, top(8), end(9), window = 110, last = end(9) - 110))
    }

    @Test
    fun theKeptRowsStayWholeWhateverItCosts() {
        val at = rest(100, top(3), end(3))
        assertTrue("row 3 is whole at $at", at!! <= top(3) && at + 100 >= end(3))
    }

    @Test
    fun aWindowTooShortForRowAndPeeksHasNoRest() {
        assertNull(rest(top(3), top(3), end(3), window = 60))
    }

    @Test
    fun aRowTallerThanTheWindowHasNoRest() {
        assertNull(rest(0, 0, 200))
    }

    /** A heading of two lines, a card, a one-line heading and a card: heights 80, 100, 60, 60, 2 px apart; a peek of 26 px is a lone line of the first. */
    private val tops = intArrayOf(0, 82, 184, 246)
    private val heights = intArrayOf(80, 100, 60, 60)

    private fun restOnCard(window: Int) =
        restWithPeeks(50, window, 26, 0, 306 - window, 82, 182, tops.size, { tops[it] }, { tops[it] + heights[it] })

    @Test
    fun aTallRowLeftAsALoneLineGoesWhollyWhenNoMoreOfItFits() {
        assertEquals(80, restOnCard(150))
    }

    @Test
    fun aTallRowLeftAsALoneLineShowsMoreWhenThatFits() {
        val at = restOnCard(200)!!
        assertTrue("at least two peeks of the row show at $at", 80 - at >= 52 && at > 0)
    }

    @Test
    fun aRestPastTheEndIsTakenOnlyWhereTheListCanAddRoom() {
        // The window ends on the last row: resting there leaves 26 px of the first row, and the list cannot go on without room at its foot.
        val noRoom = restWithPeeks(54, 252, 26, 0, 54, 184, 306, tops.size, { tops[it] }, { tops[it] + heights[it] })
        assertEquals(54, noRoom)
        val room = restWithPeeks(54, 252, 26, 0, 54, 184, 306, tops.size, { tops[it] }, { tops[it] + heights[it] }, pastMax = 252)
        assertEquals(80, room)
    }
}
