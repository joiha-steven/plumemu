package com.plumemu.secondscreen

import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.BatteryReads
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.LevelPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When the battery part takes its figures: never with the screen off, and on a device
 * that gives no figure, the clock card alone after a minute of reads, said once in the log.
 */
class InGameBatteryFeedTest {
    /** A device whose current and counter give nothing: the level only, read for [readMs]. */
    private class NoPace : BatteryReads {
        var readMs = 0L
        var snapshots = 0

        override fun read(): Long = 1_000

        override fun stopReading() = Unit

        override fun snapshot(): BatterySnapshot {
            snapshots++
            return BatterySnapshot(null, null, 2_000_000L, listOf(LevelPoint(1_000_000L, 70, false)), readMs)
        }
    }

    private val reads = NoPace()
    private val logs = mutableListOf<String>()
    private var elapsed = 0L
    private val feed = InGameBatteryFeed(reads, wallClock = { 1_792_705_620_000L }, elapsedClock = { elapsed }, info = { logs += it })
    private val reading = BatteryReading(70, false)

    @Test
    fun aDeviceThatGivesNoFigureShowsTheClockAloneAfterAMinuteAndSaysSoOnce() {
        feed.update(reading, driftNote = false)
        assertEquals(InGameBattery(null, InGameNote.Measuring), feed.shown.value)
        reads.readMs = NO_FIGURE_AFTER_MS
        repeat(5) {
            elapsed += 1_000
            feed.afterRead()
        }
        feed.update(reading, driftNote = false)
        assertNull("the clock card alone", feed.shown.value)
        assertEquals("said once: $logs", 1, logs.size)
        assertEquals(
            "In-game battery: no figure after 70 s of reads (current and charge counter give no pace); the clock card shows alone",
            logs.single(),
        )
    }

    @Test
    fun nothingIsTakenWithTheScreenOffAndTheFiguresComeAtOnceWhenItIsOn() {
        feed.update(reading, driftNote = false)
        val atOff = reads.snapshots
        feed.onScreen(false)
        repeat(5) { feed.update(BatteryReading(70 - it, false), driftNote = false) }
        assertEquals("the minute tick and the battery broadcast take nothing", atOff, reads.snapshots)
        feed.onScreen(true)
        assertEquals("on again: taken at once", atOff + 1, reads.snapshots)
        feed.onScreen(true)
        assertEquals("still on: nothing more", atOff + 1, reads.snapshots)
    }
}
