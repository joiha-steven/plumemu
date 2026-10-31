package com.plumemu.telemetry

import org.junit.Assert.assertEquals
import org.junit.Test

/** The ranges of docs/conventions/telemetry.md at each edge. */
class BucketsTest {
    private val d = "–"

    @Test
    fun games() {
        val expected = mapOf(0 to "0", 1 to "1${d}99", 99 to "1${d}99", 100 to "100${d}999", 999 to "100${d}999", 1_000 to "1k${d}5k", 4_999 to "1k${d}5k", 5_000 to "5k${d}10k", 9_999 to "5k${d}10k", 10_000 to "10k+")
        expected.forEach { (count, bucket) -> assertEquals("$count", bucket, Buckets.games(count)) }
    }

    @Test
    fun searchLengthAndResults() {
        assertEquals("1${d}2", Buckets.queryLength(1))
        assertEquals("1${d}2", Buckets.queryLength(2))
        assertEquals("3${d}5", Buckets.queryLength(3))
        assertEquals("3${d}5", Buckets.queryLength(5))
        assertEquals("6+", Buckets.queryLength(6))
        assertEquals("0", Buckets.results(0))
        assertEquals("1${d}9", Buckets.results(9))
        assertEquals("10+", Buckets.results(10))
    }

    @Test
    fun memoryIsBucketedByTheSizeOnTheBox() {
        val gib = 1024L * 1024 * 1024
        assertEquals("<2", Buckets.ram(gib))
        assertEquals("2${d}4", Buckets.ram((1.8 * gib).toLong()))
        assertEquals("4${d}8", Buckets.ram((3.6 * gib).toLong()))
        assertEquals("8+", Buckets.ram((7.6 * gib).toLong()))
        assertEquals("8+", Buckets.ram(15 * gib))
    }

    @Test
    fun enumNamesBecomeSnakeCase() {
        assertEquals("home_screen", ScreenLike.HomeScreen.snakeName())
        assertEquals("show_clock", ScreenLike.SHOW_CLOCK.snakeName())
        assertEquals("a", ScreenLike.A.snakeName())
    }

    private enum class ScreenLike { HomeScreen, SHOW_CLOCK, A }
}
