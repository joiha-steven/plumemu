package com.plumemu.launcher.play.battery

import com.plumemu.launcher.play.battery.SyntheticPlay.Companion.FULL
import com.plumemu.launcher.play.battery.SyntheticPlay.Companion.START_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * What the in-game battery part reads from the meter: the usual pace given at the
 * play's start, the full charge, Android's exact time to full, and the level over the play.
 */
class PlayBatteryMeterSnapshotTest {
    private val steady = 450_000.0

    @Test
    fun nothingBeforeTheFirstRead() {
        val meter = PlayBatteryMeter(SyntheticPlay(minutes = 1) { steady }.reader())
        meter.onPlayStarting(usualDrawMicroamps = 500_000)
        assertNull(meter.snapshot())
    }

    @Test
    fun theUsualPaceGivenAtTheStartIsUsedFromTheFirstRead() {
        val play = SyntheticPlay(minutes = 1) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        meter.onPlayStarting(usualDrawMicroamps = 500_000)
        reader.playMs += meter.read()
        val snapshot = meter.snapshot()!!
        assertEquals(500_000L, snapshot.usualDrawMicroamps)
        assertEquals("the usual pace before anything is measured", 500_000L, snapshot.estimate!!.drawMicroamps)
        assertEquals(START_MS + reader.playMs, snapshot.nowMs)
    }

    @Test
    fun aFirstPlayHasLevelsButNoEstimateInItsFirstMinute() {
        val play = SyntheticPlay(minutes = 2) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        meter.onPlayStarting(usualDrawMicroamps = null)
        play.play(meter, reader, untilMs = 30_000)
        val snapshot = meter.snapshot()!!
        assertNull(snapshot.estimate)
        assertEquals(1, snapshot.levels.size)
    }

    @Test
    fun theFullChargeComesFromTheCounterAndTheLevel() {
        val play = SyntheticPlay(minutes = 12) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        val full = meter.snapshot()!!.estimate!!.fullChargeMicroampHours!!
        // The level is a whole percent (31 here): its rounding is up to 1/31 of the figure.
        assertTrue("full $full against $FULL", abs(full / FULL - 1) < 0.04)
    }

    @Test
    fun aPointEachTimeTheLevelChangesOldestFirst() {
        // A heavy draw so the level moves several times in 40 minutes.
        val play = SyntheticPlay(minutes = 40, startCharge = 3_000_000.0) { 2_500_000.0 }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        val levels = meter.snapshot()!!.levels
        assertTrue("several points: $levels", levels.size >= 10)
        assertEquals("the first read's level starts the line", START_MS, levels.first().atMs)
        for ((a, b) in levels.zipWithNext()) {
            assertTrue("in time order: $a $b", b.atMs > a.atMs)
            assertTrue("a point only on a change: $a $b", a.level != b.level)
        }
        assertTrue(levels.none { it.isCharging })
    }

    @Test
    fun aNewPlayForgetsTheLevelsAndTheUsualPace() {
        val play = SyntheticPlay(minutes = 3) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        meter.onPlayStarting(usualDrawMicroamps = 500_000)
        play.play(meter, reader)
        meter.onPlayStarting(usualDrawMicroamps = null)
        assertNull(meter.snapshot())
    }

    @Test
    fun theReadTimeLeavesOutTheScreenOffAndStartsAgainWithANewPlay() {
        // Read for 3 minutes, the screen off for 5 (the meter told), then read for 2 more.
        val play = SyntheticPlay(minutes = 10, asleep = { it in 180_000L until 480_000L }) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        meter.onPlayStarting(usualDrawMicroamps = null)
        play.play(meter, reader, toldScreenOff = true)
        val readMs = meter.snapshot()!!.readMs
        // Up to the last read of each stretch: within a read interval (10 s) of the 5 minutes on, each.
        assertTrue("about 5 minutes read: $readMs", readMs in 280_000L..300_000L)
        meter.onPlayStarting(usualDrawMicroamps = null)
        reader.playMs += meter.read()
        assertEquals("a new play has read nothing yet", 0L, meter.snapshot()!!.readMs)
    }

    @Test
    fun chargingSaysWhenTheTimeToFullIsAndroidsOwn() {
        val play = SyntheticPlay(minutes = 3, charging = { true }) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        val estimated = meter.snapshot()!!.estimate!!
        assertTrue(estimated.isCharging)
        assertFalse("the counter's rise: an estimate", estimated.isMinutesLeftExact)
        reader.toFullMs = 63 * 60_000L
        val android = meter.snapshot()!!.estimate!!
        assertTrue(android.isMinutesLeftExact)
        assertEquals(63.0, android.minutesLeft!!, 1e-9)
        assertTrue(meter.snapshot()!!.levels.all { it.isCharging })
    }
}
