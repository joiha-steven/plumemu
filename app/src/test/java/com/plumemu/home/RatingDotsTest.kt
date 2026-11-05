package com.plumemu.home

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rating as dots: out of ten, two points a dot, rounded to half a dot, ties down. */
class RatingDotsTest {
    @Test
    fun theBoardsValuesRoundToHalfDots() {
        assertEquals(1.0f, ratingDots(2.5), 0f)
        assertEquals(2.0f, ratingDots(3.7), 0f)
        assertEquals(2.5f, ratingDots(5.0), 0f)
        assertEquals(4.0f, ratingDots(8.5), 0f)
        assertEquals(5.0f, ratingDots(10.0), 0f)
    }

    @Test
    fun noRatingDrawsNoDots() {
        assertEquals(0f, ratingDots(null), 0f)
        assertEquals(0f, ratingDots(0.0), 0f)
        assertEquals(0f, ratingDots(-1.0), 0f)
        assertEquals(0f, ratingDots(11.0), 0f)
    }
}
