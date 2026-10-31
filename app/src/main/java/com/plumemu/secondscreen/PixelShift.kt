package com.plumemu.secondscreen

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import com.plumemu.coreui.theme.InGameDimens

/**
 * The in-game card's slow drift against OLED burn-in: a fixed serpentine path over a [COLUMNS] x [ROWS] grid of points
 * [InGameDimens.shiftStepX] apart sideways and [InGameDimens.shiftStepY] apart up and down, one point per minute, forward along the path and then back along
 * it, so every move is one step (a path that started again would jump across the grid once a lap).
 * The point is a function of the minute since the epoch, so nothing is stored and nothing wakes
 * up for it: it moves on the same minute tick that redraws the clock.
 *
 * Column-major: down column 0, one step right, up column 1, and so on to the last point of the last
 * column, then the same way back, so most moves are up or down and a few are sideways. A round trip
 * is [PERIOD_MINUTES] (68 minutes).
 */
internal object PixelShift {
    /**
     * 5 x 7 points, 2 dp apart sideways and 4 dp apart up and down: the card drifts within
     * x in [-4, +4] dp and y in [-12, +12] dp of its centre. The card stays centred and
     * drifts a few pixels, mostly up and down.
     * The earlier 29 x 21 grid (112 x 80 dp) cut the burn-in wear to a third but spent hours far
     * from the centre and read as off to the right (InGameBurnInTest).
     */
    const val COLUMNS = 5
    const val ROWS = 7
    const val POINTS = COLUMNS * ROWS

    /** There and back: every point but the two ends twice. */
    const val PERIOD_MINUTES = 2 * (POINTS - 1)

    /** The grid point of [epochMinute]: its column (0 until [COLUMNS]) and row (0 until [ROWS]). */
    fun pointAt(epochMinute: Long): GridPoint {
        val inPeriod = epochMinute.mod(PERIOD_MINUTES.toLong()).toInt()
        val step = if (inPeriod < POINTS) inPeriod else PERIOD_MINUTES - inPeriod
        val column = step / ROWS
        val along = step % ROWS
        val row = if (column % 2 == 0) along else ROWS - 1 - along
        return GridPoint(column, row)
    }

    /** How far the card sits from its centred place at [epochMinute]: the grid's middle point is no shift. */
    fun offsetAt(
        epochMinute: Long,
        stepX: Dp = InGameDimens.shiftStepX,
        stepY: Dp = InGameDimens.shiftStepY,
    ): DpOffset = offsetOf(pointAt(epochMinute), stepX, stepY)

    fun offsetOf(
        point: GridPoint,
        stepX: Dp = InGameDimens.shiftStepX,
        stepY: Dp = InGameDimens.shiftStepY,
    ): DpOffset = DpOffset(stepX * (point.column - COLUMNS / 2), stepY * (point.row - ROWS / 2))

    fun epochMinuteOf(epochMillis: Long): Long = epochMillis.floorDiv(MILLIS_PER_MINUTE)

    private const val MILLIS_PER_MINUTE = 60_000L
}

internal data class GridPoint(
    val column: Int,
    val row: Int,
)
