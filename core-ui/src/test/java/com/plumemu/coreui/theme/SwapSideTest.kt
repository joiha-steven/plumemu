package com.plumemu.coreui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** The side a sliding swap comes in from: the step's own direction, the shorter way round a column that wraps. */
class SwapSideTest {
    @Test
    fun aStepDownComesFromBelowAndAStepUpFromAbove() {
        assertEquals(1f, ContentFade.sideOf(1, 0))
        assertEquals(-1f, ContentFade.sideOf(-1, 0))
        assertEquals(0f, ContentFade.sideOf(0, 12))
    }

    @Test
    fun theLastSystemsNextIsTheFirstAndStillComesFromBelow() {
        assertEquals(1f, ContentFade.sideOf(-11, 12))
        assertEquals(-1f, ContentFade.sideOf(11, 12))
    }

    @Test
    fun aLongJumpWithoutWrappingKeepsItsDirection() {
        assertEquals(1f, ContentFade.sideOf(7, 0))
        assertEquals(1f, ContentFade.sideOf(5, 12))
    }
}
