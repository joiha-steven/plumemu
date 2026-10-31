package com.plumemu.launcher.play.battery

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A real trace of an AYN Thor (Android 13), read over adb once a second for
 * about 24 minutes with Plumemu's home screen up: elapsed seconds, `current_now` (uA, negative
 * discharging), `charge_counter` (uAh), level, status. Replayed through [PlayBatteryMeter] as the
 * in-game card's tick reads it (each read sees the newest line at or before its time); the truth
 * at each moment is the counter's drop from there to the end of the trace, timed between the
 * moments it changed.
 */
class ThorTraceReplayTest {
    private class Line(val atMs: Long, val current: Long, val counter: Long, val level: Int, val charging: Boolean)

    private val lines: List<Line> =
        requireNotNull(javaClass.getResourceAsStream("/battery/thor-2026-10-23.txt")).bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { row ->
                val f = row.trim().split(Regex("\\s+"))
                Line((f[0].toDouble() * 1_000).toLong(), f[1].toLong(), f[2].toLong(), f[3].toInt(), f[4] == "Charging")
            }

    private val startMs = lines.first().atMs
    private val endMs = lines.last().atMs

    /** The trace as the device: the newest line at or before [nowMs]. */
    private inner class TraceReader : BatteryReader {
        var nowMs = startMs
        private var index = 0
        var reads = 0
        private val line: Line get() {
            while (index + 1 < lines.size && lines[index + 1].atMs <= nowMs) index++
            return lines[index]
        }

        override fun nowMs() = nowMs

        override fun currentNow(): Long {
            reads++
            return line.current
        }

        override fun chargeCounter() = line.counter

        override fun level() = line.level

        override fun isCharging() = line.charging

        override fun chargeTimeRemainingMs() = BatteryReader.UNKNOWN
    }

    /** The counter's mean draw in uA from [fromMs] to the end, between the first and last change it shows in that time. */
    private fun truthFrom(fromMs: Long): Double {
        val changes = lines.zipWithNext().filter { (a, b) -> b.counter != a.counter && b.atMs >= fromMs }.map { it.second }
        val first = changes.first()
        val last = changes.last()
        return (first.counter - last.counter).toDouble() / (last.atMs - first.atMs) * 3_600_000
    }

    /** The meter's figures after reading the trace up to each of [minutes]; null usual pace (a first play). */
    private fun replay(minutes: List<Int>): Map<Int, BatteryEstimate> {
        val reader = TraceReader()
        val meter = PlayBatteryMeter(reader)
        val out = mutableMapOf<Int, BatteryEstimate>()
        var next = 0
        while (reader.nowMs <= endMs && next < minutes.size) {
            val wait = meter.read()
            if (reader.nowMs + wait > startMs + minutes[next] * 60_000L) {
                out[minutes[next]] = meter.estimate(null)!!
                next++
            }
            reader.nowMs += wait
        }
        return out
    }

    @Test
    fun theTraceIsTheThorsAsMeasured() {
        assertTrue("about 24 minutes: ${(endMs - startMs) / 60_000.0}", endMs - startMs > 23 * 60_000L)
        assertTrue("discharging throughout", lines.none { it.charging })
        assertTrue("current in uA, negative while discharging", lines.all { it.current in -2_000_000..-100_000 })
        val steps = lines.zipWithNext().map { (a, b) -> a.counter - b.counter }.filter { it != 0L }
        assertTrue("the counter moves in steps of 1/10,000 of 6.254 Ah: ${steps.distinct()}", steps.all { abs(it % 625.4) < 2 || abs(it % 625.4 - 625.4) < 2 })
    }

    @Test
    fun theTimeLeftIsWithinTenPercentAtOneMinuteEightAtFiveAndFiveAtTenAndFifteen() {
        val figures = replay(listOf(1, 5, 10, 15))
        for ((minute, estimate) in figures) {
            val atMs = startMs + minute * 60_000L
            val truth = truthFrom(atMs)
            // Time left is the charge over the pace, so its error is the pace's, the other way up.
            val timeLeftError = truth / estimate.drawMicroamps - 1
            println("Thor trace, minute $minute: pace ${estimate.drawMicroamps / 1_000} mA, counter's ${(truth / 1_000).toInt()} mA, time left off by ${"%+.1f".format(timeLeftError * 100)}%")
            // The Thor's counter dropped 7% faster in the trace's first 5 minutes than after (480 mA
            // against 446, the current flat at 360): no reading of the past can see that coming.
            val bound = when (minute) {
                1 -> 0.10
                5 -> 0.08
                else -> 0.05
            }
            assertTrue("minute $minute: time left off by ${timeLeftError * 100}%", abs(timeLeftError) < bound)
        }
        assertEquals(4, figures.size)
    }

    @Test
    fun theStoredDrawIsTheCountersOverThePlay() {
        val reader = TraceReader()
        val meter = PlayBatteryMeter(reader)
        while (reader.nowMs <= endMs) reader.nowMs += meter.read()
        val stored = meter.takeMeasured()!!
        val truth = truthFrom(startMs)
        println("Thor trace: stored draw ${stored.drawMicroamps / 1_000} mA, counter's ${(truth / 1_000).toInt()} mA (${"%+.1f".format((stored.drawMicroamps / truth - 1) * 100)}%)")
        assertTrue("stored ${stored.drawMicroamps} vs counter's $truth", abs(stored.drawMicroamps / truth - 1) < 0.03)
        // Reads: a second apart for 2 minutes, then every 10 s (the trace's lines are 1.17 s apart, so the first 2 minutes give ~103).
        val minutes = (endMs - startMs) / 60_000.0
        println("Thor trace: ${reader.reads} reads in ${"%.1f".format(minutes)} minutes")
        assertEquals(120 + (minutes - 2) * 6, reader.reads.toDouble(), 20.0)
    }
}
