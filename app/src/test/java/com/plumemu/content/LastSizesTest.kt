package com.plumemu.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The last decoded size of each picture, kept for a bounded number of pictures. */
class LastSizesTest {
    @Test
    fun aPictureNeverDecodedHasNoSize() = assertNull(LastSizes(4).last("a"))

    @Test
    fun theLatestSizeWins() {
        val sizes = LastSizes(4)
        sizes.record("a", 200, 300)
        sizes.record("a", 260, 390)
        assertEquals(260 to 390, sizes.last("a"))
    }

    @Test
    fun neverMoreThanTheLimitIsKept() {
        val sizes = LastSizes(3)
        repeat(10) { sizes.record("p$it", it + 1, it + 2) }
        assertEquals(3, sizes.count)
        assertNull(sizes.last("p0"))
        assertEquals(10 to 11, sizes.last("p9"))
    }

    @Test
    fun theLeastRecentlyUsedGoesFirst() {
        val sizes = LastSizes(2)
        sizes.record("a", 1, 1)
        sizes.record("b", 2, 2)
        sizes.last("a")
        sizes.record("c", 3, 3)
        assertEquals(1 to 1, sizes.last("a"))
        assertNull(sizes.last("b"))
    }
}
