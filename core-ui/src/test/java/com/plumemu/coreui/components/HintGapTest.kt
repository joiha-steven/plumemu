package com.plumemu.coreui.components

import androidx.compose.ui.unit.dp
import com.plumemu.coreui.theme.PlumDimens
import org.junit.Assert.assertEquals
import org.junit.Test

/** The hint bar's gap: the board's 28 px (18.67 dp) where the hints fit with it, shrinking toward 9 dp only when they do not. */
class HintGapTest {
    private val board = 56 // 18.67 dp at 3 px per dp
    private val tight = 27

    @Test
    fun boardGapIsTwentyEightBoardPixels() {
        assertEquals(17.33f, PlumDimens.hintGapBoard.value, 0.01f)
        assertEquals(9.dp, PlumDimens.hintGap)
    }

    @Test
    fun roomyBarKeepsTheBoardGap() = assertEquals(board, hintGapFor(sum = 600, count = 4, room = 1200, board = board, tight = tight))

    @Test
    fun barThatFitsOnlyTightShrinksToWhatIsLeft() = assertEquals(40, hintGapFor(sum = 600, count = 4, room = 720, board = board, tight = tight))

    @Test
    fun neverBelowTheTightGap() = assertEquals(tight, hintGapFor(sum = 600, count = 4, room = 650, board = board, tight = tight))

    @Test
    fun oneHintHasNoGap() = assertEquals(board, hintGapFor(sum = 100, count = 1, room = 120, board = board, tight = tight))
}
