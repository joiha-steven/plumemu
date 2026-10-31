package com.plumemu.coreinput

import org.junit.Assert.assertEquals
import org.junit.Test

/** Repeat timing in whole display frames ([HoldRepeat.framesFor], [RepeatPacer]), without coroutines. */
class HoldRepeatTest {
    @Test
    fun theFeelInMillisecondsBecomesWholeFramesAtEveryRate() {
        val accelerated = HoldRepeat.intervals(accelerate = true).take(8).toList()
        assertEquals(listOf(7, 6, 5, 4, 3, 2, 2, 2), accelerated.map { HoldRepeat.framesFor(it, SIXTY_HZ) })
        assertEquals(listOf(14, 12, 9, 7, 6, 5, 4, 4), accelerated.map { HoldRepeat.framesFor(it, ONE_TWENTY_HZ) })
        assertEquals("constant 80 ms at 60 Hz", 5, HoldRepeat.framesFor(HoldRepeat.CONSTANT_INTERVAL_MS, SIXTY_HZ))
        assertEquals("constant 80 ms at 120 Hz", 10, HoldRepeat.framesFor(HoldRepeat.CONSTANT_INTERVAL_MS, ONE_TWENTY_HZ))
        assertEquals("the floor at 90 Hz is 3 frames (33 ms)", 3, HoldRepeat.framesFor(HoldRepeat.ACCELERATED_FLOOR_MS, NINETY_HZ))
        assertEquals("a 16 ms frame still makes 32 ms two frames", 2, HoldRepeat.framesFor(HoldRepeat.ACCELERATED_FLOOR_MS, 16_000_000L))
    }

    @Test
    fun repeatsComeOnEvenFramesAt60And120Hz() {
        assertEquals(listOf(5L), gaps(SIXTY_HZ, constant = true).distinct())
        assertEquals(listOf(10L), gaps(ONE_TWENTY_HZ, constant = true).distinct())
        assertEquals("first repeat 300 ms after the press", 18L, firstRepeat(SIXTY_HZ))
        assertEquals("first repeat 300 ms after the press", 36L, firstRepeat(ONE_TWENTY_HZ))
    }

    @Test
    fun aDroppedFrameDelaysTheNextRepeatInsteadOfFiringTwo() {
        val pacer = RepeatPacer(HoldRepeat.intervals(accelerate = false))
        val repeats = mutableListOf<Long>()
        // 60 Hz with frames 30 to 33 missing (a busy moment): no burst after it.
        for (frame in 1L..60L) {
            if (frame in 30L..33L) continue
            if (pacer.onFrame(frame * SIXTY_HZ)) repeats += frame
        }
        assertEquals(listOf(18L, 23L, 28L, 34L, 39L, 44L, 49L, 54L, 59L), repeats)
    }

    @Test
    fun timeSpentSinceTheFrameShortensTheRest() {
        val pacer = RepeatPacer(HoldRepeat.intervals(accelerate = false))
        (1L..18L).forEach { pacer.onFrame(it * ONE_TWENTY_HZ) }
        // 150 ms into the 300 ms before the first repeat, the period learnt as 120 Hz.
        val full = pacer.restNanos()
        assertEquals("a long frame (6 ms) is taken off the rest", full - 6_000_000L, pacer.restNanos(sinceFrameNanos = 6_000_000L))
        assertEquals("a nonsense gap (another time base) is ignored", full, pacer.restNanos(sinceFrameNanos = -5L))
    }

    private fun repeatFrames(
        period: Long,
        constant: Boolean,
    ): List<Long> {
        val pacer = RepeatPacer(HoldRepeat.intervals(accelerate = !constant))
        // Frame 0 is the press; the router sees frames from 1 on.
        return (1L..400L).filter { pacer.onFrame(it * period) }
    }

    private fun gaps(
        period: Long,
        constant: Boolean,
    ) = repeatFrames(period, constant).zipWithNext { a, b -> b - a }

    private fun firstRepeat(period: Long) = repeatFrames(period, constant = true).first()

    private companion object {
        const val SIXTY_HZ = 16_666_667L
        const val NINETY_HZ = 11_111_111L
        const val ONE_TWENTY_HZ = 8_333_333L
    }
}
