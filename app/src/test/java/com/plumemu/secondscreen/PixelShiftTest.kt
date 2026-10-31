package com.plumemu.secondscreen

import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.InGameDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The pixel shift's path: a column-major serpentine over a 5 x 7 grid, one point a minute, from the minute alone. */
class PixelShiftTest {
    @Test
    fun aPeriodVisitsEveryPointAndRepeats() {
        val start = 29_878_320L
        val points = (0 until PixelShift.PERIOD_MINUTES).map { PixelShift.pointAt(start + it) }
        assertEquals(PixelShift.COLUMNS * PixelShift.ROWS, points.toSet().size)
        assertEquals("the next period repeats it", points, (0 until PixelShift.PERIOD_MINUTES).map { PixelShift.pointAt(start + PixelShift.PERIOD_MINUTES + it) })
    }

    /** Also across the turns at both ends and from one period to the next: never a jump. */
    @Test
    fun everyMinuteMovesExactlyOneStep() {
        val periodStart = PixelShift.PERIOD_MINUTES * 1_000L
        for (i in -2 until PixelShift.PERIOD_MINUTES + 2) {
            val a = PixelShift.pointAt(periodStart + i)
            val b = PixelShift.pointAt(periodStart + i + 1)
            assertEquals("minute $i: $a to $b", 1, abs(a.column - b.column) + abs(a.row - b.row))
        }
        assertEquals("a period starts top left", GridPoint(0, 0), PixelShift.pointAt(periodStart))
        assertEquals("the first move is vertical (down)", GridPoint(0, 1), PixelShift.pointAt(periodStart + 1))
        assertEquals("columns alternate: the second column starts at the bottom", GridPoint(1, PixelShift.ROWS - 1), PixelShift.pointAt(periodStart + PixelShift.ROWS))
        val last = GridPoint(PixelShift.COLUMNS - 1, PixelShift.ROWS - 1)
        assertEquals("the far end (odd column count: the last column runs down)", last, PixelShift.pointAt(periodStart + PixelShift.POINTS - 1))
        assertEquals("then back the same way", PixelShift.pointAt(periodStart + PixelShift.POINTS - 2), PixelShift.pointAt(periodStart + PixelShift.POINTS))
    }

    @Test
    fun theMiddlePointIsNoShiftAndTheCornersAreInsideTheDriftBox() {
        assertEquals(DpOffset.Zero, PixelShift.offsetOf(GridPoint(PixelShift.COLUMNS / 2, PixelShift.ROWS / 2)))
        assertEquals(2.dp, InGameDimens.shiftStepX)
        assertEquals(4.dp, InGameDimens.shiftStepY)
        assertEquals(DpOffset(-4.dp, -12.dp), PixelShift.offsetOf(GridPoint(0, 0)))
        assertEquals(DpOffset(4.dp, 12.dp), PixelShift.offsetOf(GridPoint(PixelShift.COLUMNS - 1, PixelShift.ROWS - 1)))
        assertEquals(68, PixelShift.PERIOD_MINUTES)
    }

    /** Centred drift: a few dp each way, mostly up and down. */
    @Test
    fun aPeriodDriftsInsideTheBoxAndMostlyVertically() {
        val offsets = (0L until PixelShift.PERIOD_MINUTES).map { PixelShift.offsetAt(it) }
        assertTrue(offsets.all { abs(it.x.value) <= 4f && abs(it.y.value) <= 12f })
        assertEquals("every column's x", 5, offsets.map { it.x }.toSet().size)
        assertEquals("every row's y", 7, offsets.map { it.y }.toSet().size)
        val sideways = (0 until PixelShift.PERIOD_MINUTES).count { offsets[it].x != offsets[(it + 1) % PixelShift.PERIOD_MINUTES].x }
        assertEquals("a column change is 1 move in 7, twice a lap per column boundary", 8, sideways)
    }

    @Test
    fun thePositionComesFromTheMinuteOnly() {
        val millis = 1_792_699_200_000L
        assertEquals(PixelShift.epochMinuteOf(millis), PixelShift.epochMinuteOf(millis + 59_999))
        assertEquals(PixelShift.epochMinuteOf(millis) + 1, PixelShift.epochMinuteOf(millis + 60_000))
        // Before the epoch (a clock set wrong) still lands on the grid.
        val early = PixelShift.pointAt(-1)
        assertTrue(early.toString(), early.column in 0 until PixelShift.COLUMNS && early.row in 0 until PixelShift.ROWS)
        assertEquals(PixelShift.pointAt(PixelShift.PERIOD_MINUTES - 1L), early)
    }
}
