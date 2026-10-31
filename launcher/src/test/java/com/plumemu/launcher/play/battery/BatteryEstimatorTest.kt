package com.plumemu.launcher.play.battery

import com.plumemu.launcher.play.battery.SyntheticPlay.Companion.THOR_COUNTER_RATIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pace, blend, counter, gaps and charging, on made-up
 * plays shaped like the Thor's reads ([SyntheticPlay]) read through [PlayBatteryMeter] on a test
 * clock, as the in-game card's tick reads them.
 */
class BatteryEstimatorTest {
    private val steady = 400_000.0
    private val minute = 60_000L

    /** What the counter says is used per hour at [draw]: the truth the estimate aims at. */
    private fun truth(draw: Double) = draw * THOR_COUNTER_RATIO

    private fun relativeError(estimate: Number, truth: Double) = abs(estimate.toDouble() / truth - 1)

    private class Played(val play: SyntheticPlay, val meter: PlayBatteryMeter, val reader: SyntheticPlay.FakeBatteryReader)

    private fun played(play: SyntheticPlay, untilMs: Long? = null): Played {
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        if (untilMs == null) play.play(meter, reader) else play.play(meter, reader, untilMs)
        return Played(play, meter, reader)
    }

    @Test
    fun aFirstPlayShowsNothingBeforeItsFirstMinuteThenTheCountersPace() {
        val play = SyntheticPlay(minutes = 6) { steady }
        assertNull("no usual pace: nothing for the first minute", played(play, untilMs = 58_000).meter.estimate(usualDrawMicroamps = null))
        val after = played(play).run { meter.estimate(null)!! to reader.playMs }
        assertTrue("the counter's pace, not the current's lower one: ${after.first.drawMicroamps}", relativeError(after.first.drawMicroamps, truth(steady)) < 0.05)
        val left = play.chargeAt(after.second) / truth(steady) * 60
        assertTrue("time left ${after.first.minutesLeft} vs $left", abs(after.first.minutesLeft!! / left - 1) < 0.05)
    }

    @Test
    fun aFirstPlaysFirstFigureIsWithinThirteenPercentInNineOfTenPlays() {
        val errors = (1..40).map { seed ->
            val estimate = played(SyntheticPlay(minutes = 2, seed = seed) { steady }, untilMs = minute).meter.estimate(null)!!
            relativeError(estimate.drawMicroamps, truth(steady))
        }.sorted()
        // Two counter spans of about 11 steps of 625 uAh: one step either way is 9%.
        assertTrue("at one minute: $errors", errors[35] < 0.13)
    }

    @Test
    fun aGamePlayedBeforeHasItsUsualPaceFromTheFirstReadAndMovesTowardTheMeasurementWithoutJumping() {
        val usual = 600_000L
        val play = SyntheticPlay(minutes = 30) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        reader.playMs += meter.read()
        assertEquals("the usual pace before anything is measured", usual, meter.estimate(usual)!!.drawMicroamps)
        var previous = usual
        while (reader.playMs < 30 * minute - 1_000) {
            reader.playMs += meter.read()
            val now = meter.estimate(usual)!!.drawMicroamps
            assertTrue("at ${reader.playMs} ms: $previous to $now is a jump", abs(now - previous) < 0.04 * previous)
            previous = now
        }
        assertTrue("after 30 minutes the measurement leads: $previous", relativeError(previous, truth(steady)) < 0.06)
    }

    @Test
    fun theUsualPaceCountsAsFiveMinutesOfMeasurement() {
        val meter = played(SyntheticPlay(minutes = 6) { steady }, untilMs = 5 * minute).meter
        val measured = meter.estimate(null)!!
        val usual = 300_000L
        val weight = measured.measuredMs / (5.0 * minute + measured.measuredMs)
        assertEquals(usual + weight * (measured.drawMicroamps - usual), meter.estimate(usual)!!.drawMicroamps.toDouble(), 1.0)
    }

    @Test
    fun aSleepInTheMiddleIsNotMeasuredAndDoesNotLowerThePace() {
        val sleep = 10 * minute until 40 * minute
        val p = played(SyntheticPlay(minutes = 50, asleep = { it in sleep }, sleepDraw = 15_000.0) { steady })
        val estimate = p.meter.estimate(null)!!
        // Each stretch is measured from its first read to its last (reads 10 s apart).
        assertEquals("20 minutes measured, not 50", 20.0, estimate.measuredMs / 60_000.0, 0.4)
        assertTrue("the sleep's 15 mA is not in the pace: ${estimate.drawMicroamps}", relativeError(estimate.drawMicroamps, truth(steady)) < 0.05)
        val stored = p.meter.takeMeasured()!!
        assertEquals(1_200.0, stored.measuredSeconds.toDouble(), 25.0)
        assertTrue("stored draw ${stored.drawMicroamps}", relativeError(stored.drawMicroamps, truth(steady)) < 0.04)
    }

    @Test
    fun aScreenOffShorterThanTheGapIsNotMeasuredWhenTheReadsAreToldItStopped() {
        // The CPU may stay awake with the screen off (an emulator's audio): the card stops its reads and says so.
        val screenOff = 10 * minute until 11 * minute
        val play = SyntheticPlay(minutes = 21, asleep = { it in screenOff }, sleepDraw = 15_000.0) { steady }
        val reader = play.reader()
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader, toldScreenOff = true)
        val stored = meter.takeMeasured()!!
        assertEquals("20 minutes measured, not 21", 1_200.0, stored.measuredSeconds.toDouble(), 25.0)
        assertTrue("the screen-off minute's 15 mA is not in the draw: ${stored.drawMicroamps}", relativeError(stored.drawMicroamps, truth(steady)) < 0.04)
    }

    @Test
    fun rightAfterASleepThePaceIsTheCountersFromBeforeIt() {
        val p = played(SyntheticPlay(minutes = 31, asleep = { it in 10 * minute until 30 * minute }) { steady }, untilMs = 30 * minute + 5_000)
        val estimate = p.meter.estimate(null)!!
        assertTrue("${estimate.drawMicroamps}", relativeError(estimate.drawMicroamps, truth(steady)) < 0.06)
    }

    @Test
    fun aHeavySceneOfHalfAMinuteMovesThePaceLittleAndAHeavierPartIsFollowedWithinTheWindow() {
        val spike = played(SyntheticPlay(minutes = 13) { ms -> if (ms in 12 * minute until 12 * minute + 30_000) 3 * steady else steady })
        val calm = played(SyntheticPlay(minutes = 13) { steady })
        val withSpike = spike.meter.estimate(null)!!.drawMicroamps
        val without = calm.meter.estimate(null)!!.drawMicroamps
        assertTrue("30 s at 3x moves a 10-minute pace by about a tenth, not 3x: $withSpike vs $without", withSpike < without * 1.15)
        val heavier = played(SyntheticPlay(minutes = 21) { ms -> if (ms < 10 * minute) steady else 1.5 * steady })
        val followed = heavier.meter.estimate(null)!!.drawMicroamps
        assertTrue("ten minutes after the change the pace is the new one: $followed", relativeError(followed, truth(1.5 * steady)) < 0.05)
    }

    @Test
    fun aDeviceReportingTheOtherSignGivesTheSameFigures() {
        val documented = played(SyntheticPlay(minutes = 12) { steady }).meter
        val otherSign = played(SyntheticPlay(minutes = 12, asDevice = { it.toLong() }) { steady }).meter
        assertEquals(documented.estimate(null), otherSign.estimate(null))
        assertEquals(documented.takeMeasured(), otherSign.takeMeasured())
    }

    @Test
    fun aDeviceReportingMilliamperesGivesTheSameFigures() {
        val micro = played(SyntheticPlay(minutes = 12) { steady }).meter.takeMeasured()!!
        val milli = played(SyntheticPlay(minutes = 12, asDevice = { -(it / 1_000).toLong() }) { steady }).meter.takeMeasured()!!
        assertEquals(micro.drawMicroamps.toDouble(), milli.drawMicroamps.toDouble(), 0.01 * micro.drawMicroamps)
        assertEquals(micro.chargeStartMicroampHours, milli.chargeStartMicroampHours)
    }

    @Test
    fun aDeviceCountingInMilliampereHoursGivesTheSameTimeLeft() {
        val micro = played(SyntheticPlay(minutes = 12) { steady }).meter.estimate(null)!!
        val milli = played(SyntheticPlay(minutes = 12, counterAsDevice = { it / 1_000 }) { steady }).meter.estimate(null)!!
        assertEquals(micro.minutesLeft!!, milli.minutesLeft!!, 0.03 * micro.minutesLeft!!)
    }

    @Test
    fun aDeviceWithoutACounterHasThePaceOfItsCurrentAndNoTimeLeft() {
        val meter = played(SyntheticPlay(minutes = 6, counterAsDevice = { BatteryReader.UNKNOWN }) { steady }).meter
        val estimate = meter.estimate(null)!!
        // The current's mean over 6 noisy minutes (29% a read): within 8%.
        assertTrue("the current's own pace: ${estimate.drawMicroamps}", relativeError(estimate.drawMicroamps, steady) < 0.08)
        assertNull(estimate.minutesLeft)
        val stored = meter.takeMeasured()!!
        assertNull(stored.chargeStartMicroampHours)
        assertNull(stored.chargeEndMicroampHours)
    }

    @Test
    fun aDeviceWithoutACurrentHasTheCountersPaceAndStoresIt() {
        val meter = played(SyntheticPlay(minutes = 6, asDevice = { BatteryReader.UNKNOWN }) { steady }).meter
        assertTrue(relativeError(meter.estimate(null)!!.drawMicroamps, truth(steady)) < 0.05)
        assertTrue("the counter's mean is stored", relativeError(meter.takeMeasured()!!.drawMicroamps, truth(steady)) < 0.05)
    }

    @Test
    fun missingCurrentReadsDoNotInflateTheStoredDraw() {
        // Every third current read missing stored 2.3 times the counter's draw.
        val play = SyntheticPlay(minutes = 20) { steady }
        val reader = play.reader().apply { currentAt = { read, device -> if (read % 3 == 0) BatteryReader.UNKNOWN else device } }
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        val stored = meter.takeMeasured()!!
        assertTrue("stored ${stored.drawMicroamps} vs the counter's ${truth(steady)}", relativeError(stored.drawMicroamps, truth(steady)) < 0.05)
    }

    @Test
    fun aCurrentOfIntMinValueIsNoReadNotTwoThousandAmperes() {
        val alone = played(SyntheticPlay(minutes = 3, asDevice = { Int.MIN_VALUE.toLong() }, counterAsDevice = { BatteryReader.UNKNOWN }) { steady }).meter
        assertNull("nothing plausible was read: no figure", alone.estimate(null))
        assertNull("nothing plausible was read: nothing stored", alone.takeMeasured())
        val withCounter = played(SyntheticPlay(minutes = 1, asDevice = { Int.MIN_VALUE.toLong() }) { steady }, untilMs = 10_000).meter
        assertEquals("the usual pace stands until the counter moves", 500_000L, withCounter.estimate(500_000)!!.drawMicroamps)
    }

    @Test
    fun aReadOutsideTwentyMilliamperesToTwentyAmperesIsIgnored() {
        // Reads 125 to 127 come after the first 2 minutes' 120, 10 s apart.
        val wild = mapOf(125 to -50_000_000L, 126 to -5_000L, 127 to -900_000_000L)
        val play = SyntheticPlay(minutes = 6, counterAsDevice = { BatteryReader.UNKNOWN }) { steady }
        val reader = play.reader().apply { currentAt = { read, device -> wild[read] ?: device } }
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        // The same 8% as the same play without wild reads (aDeviceWithoutACounterHasThePaceOfItsCurrentAndNoTimeLeft).
        val pace = meter.estimate(null)!!.drawMicroamps
        assertTrue("one 50 A, one 5 mA and one 900 A read are not a draw: $pace", relativeError(pace, steady) < 0.08)
        assertTrue(relativeError(meter.takeMeasured()!!.drawMicroamps, steady) < 0.08)
    }

    @Test
    fun theCurrentsUnitIsDecidedFromSeveralReadsNotTheFirst() {
        // Without a counter nothing puts a wrong unit right: a microampere device whose first read is
        // a quiet 15 mA was taken for milliamperes and stored 1,000 times its draw.
        val noCounter = { _: Long -> BatteryReader.UNKNOWN }
        val play = SyntheticPlay(minutes = 6, counterAsDevice = noCounter) { steady }
        val reader = play.reader().apply { currentAt = { read, device -> if (read == 1) -15_000L else device } }
        val meter = PlayBatteryMeter(reader)
        play.play(meter, reader)
        val stored = meter.takeMeasured()!!.drawMicroamps
        assertTrue("microamperes: $stored", relativeError(stored, steady) < 0.08)
        // And a milliampere device whose first read is a large one is still in milliamperes.
        val milli = SyntheticPlay(minutes = 6, asDevice = { -(it / 1_000).toLong() }, counterAsDevice = noCounter) { steady }
        val milliReader = milli.reader().apply { currentAt = { read, device -> if (read == 1) -25_000L else device } }
        val milliMeter = PlayBatteryMeter(milliReader)
        milli.play(milliMeter, milliReader)
        val milliStored = milliMeter.takeMeasured()!!.drawMicroamps
        assertTrue("milliamperes: $milliStored", relativeError(milliStored, steady) < 0.08)
    }

    @Test
    fun aPlayStoresItsMeasuredTimeItsChargeAtStartAndEndAndTheCountersMeanDraw() {
        val play = SyntheticPlay(minutes = 20) { steady }
        val p = played(play)
        val start = p.play.reader().chargeCounter()
        val stored = p.meter.takeMeasured()!!
        assertEquals(1_200.0, stored.measuredSeconds.toDouble(), 12.0)
        assertEquals(start, stored.chargeStartMicroampHours)
        assertEquals(p.reader.lastCounter, stored.chargeEndMicroampHours)
        assertTrue("the current's mean put right by the counter: ${stored.drawMicroamps}", relativeError(stored.drawMicroamps, truth(steady)) < 0.03)
        assertNull("taken: nothing left for the next play", p.meter.takeMeasured())
        assertNull("under a minute measured: nothing stored", played(play, untilMs = 50_000).meter.takeMeasured())
    }

    @Test
    fun chargingUsesAndroidsTimeToFullWhenItAnswersElseTheCountersRiseAndIsNotMeasured() {
        val p = played(SyntheticPlay(minutes = 20, charging = { it >= 10 * minute }) { steady })
        p.reader.toFullMs = 63 * minute
        val android = p.meter.estimate(null)!!
        assertTrue(android.isCharging)
        assertEquals(63.0, android.minutesLeft!!, 0.001)
        p.reader.toFullMs = BatteryReader.UNKNOWN
        val own = p.meter.estimate(null)!!
        val charge = p.play.chargeAt(p.reader.playMs)
        val rise = (1_500_000.0 - steady) * THOR_COUNTER_RATIO
        val expected = (charge / p.reader.level() * 100 - charge) / rise * 60
        assertTrue("charge missing / rise: ${own.minutesLeft} vs $expected", abs(own.minutesLeft!! / expected - 1) < 0.06)
        assertEquals("the ten minutes on the charger are not measured", 10.0, p.meter.takeMeasured()!!.measuredSeconds / 60.0, 0.25)
    }

    @Test
    fun aFullBatteryOnTheChargerHasNoTimeLeft() {
        val estimator = BatteryEstimator()
        estimator.add(0, 5_000, 6_254_000, 100, isCharging = true)
        estimator.add(5_000, 4_000, 6_254_000, 100, isCharging = true)
        assertNull(estimator.estimate(null)!!.minutesLeft)
    }

    @Test
    fun unpluggingStartsAFreshStretchThatTheCounterTakesOverWithinAMinute() {
        val p = played(SyntheticPlay(minutes = 12, charging = { it < 10 * minute }) { steady })
        val estimate = p.meter.estimate(null)!!
        assertFalse(estimate.isCharging)
        assertTrue("${estimate.drawMicroamps}", relativeError(estimate.drawMicroamps, truth(steady)) < 0.10)
    }

    @Test
    fun readsComeEverySecondForTwoMinutesThenEveryTenSecondsAndAgainAfterASleep() {
        val p = played(SyntheticPlay(minutes = 40, asleep = { it in 20 * minute until 30 * minute }) { steady })
        // Stretches of 20 and 10 minutes: 120 reads a second apart each, then every 10 s.
        assertEquals(120 + 108 + 120 + 48, p.reader.reads, 3.0)
        assertEquals("the level once a minute", 31.0, p.reader.levelReads.toDouble(), 2.0)
    }

    @Test
    fun aReadAllocatesNothingInTheMeterOrTheEstimator() {
        val reader = object : BatteryReader {
            var now = 0L

            override fun nowMs() = now

            override fun currentNow() = -400_000L - now % 7_000

            override fun chargeCounter() = 2_000_000L - now / 8_000 * 625

            override fun level() = 30

            override fun isCharging() = false

            override fun chargeTimeRemainingMs() = BatteryReader.UNKNOWN
        }
        val meter = PlayBatteryMeter(reader)
        val threads = java.lang.management.ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        repeat(20_000) { reader.now += meter.read() } // warm up
        val before = threads.getThreadAllocatedBytes(Thread.currentThread().id)
        repeat(10_000) { reader.now += meter.read() }
        val bytes = threads.getThreadAllocatedBytes(Thread.currentThread().id) - before
        assertTrue("10,000 reads allocated $bytes bytes", bytes < 1_024)
    }

    @Test
    fun readsOutOfOrderOrRepeatedAreIgnored() {
        val estimator = BatteryEstimator()
        estimator.add(10_000, -400_000, 2_000_000, 30, false)
        estimator.add(70_000, -400_000, 1_990_000, 30, false)
        val before = estimator.estimate(null)
        estimator.add(70_000, -9_000_000, 1_500_000, 30, false)
        estimator.add(69_999, -9_000_000, 1_500_000, 30, false)
        assertEquals(before, estimator.estimate(null))
    }

    @Test
    fun noReadsNoFigure() {
        assertNull(BatteryEstimator().estimate(500_000))
        assertNull(BatteryEstimator().measured())
    }

    @Test
    fun timeLeftIsRoundedToFiveUnderAnHourAndFifteenAboveSayingWhenItHidMinutes() {
        val cases =
            mapOf(
                45.0 to TimeLeft(45, false), 47.0 to TimeLeft(45, true), 48.0 to TimeLeft(50, true), 2.0 to TimeLeft(5, true),
                0.0 to TimeLeft(5, true), 58.0 to TimeLeft(60, true), 60.0 to TimeLeft(60, false), 67.0 to TimeLeft(60, true),
                68.0 to TimeLeft(75, true), 270.0 to TimeLeft(270, false), 277.4 to TimeLeft(270, true), 59.6 to TimeLeft(60, false),
            )
        cases.forEach { (minutes, expected) -> assertEquals("$minutes", expected, roundedMinutesLeft(minutes)) }
        assertEquals("Android's time to full is shown to the minute", TimeLeft(63, false), exactMinutesLeft(63.2))
        assertEquals(TimeLeft(1, false), exactMinutesLeft(0.2))
    }

    private fun assertEquals(expected: Int, actual: Int, delta: Double) = assertEquals(expected.toDouble(), actual.toDouble(), delta)
}
