package com.plumemu.launcher.play.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A game's usual draw as a share of a full battery per hour (`UsualPace`). */
class UsualPaceTest {
    private class Reader(var counter: Long, var level: Int) : BatteryReader {
        var reads = 0

        override fun nowMs() = 0L

        override fun currentNow() = BatteryReader.UNKNOWN

        override fun chargeCounter(): Long = counter.also { reads++ }

        override fun level() = level

        override fun isCharging() = false

        override fun chargeTimeRemainingMs() = BatteryReader.UNKNOWN
    }

    // The Thor: 6.254 Ah full, at 50% the counter holds half.
    private val thor = Reader(counter = 3_127_000, level = 50)

    @Test
    fun theUsualDrawIsAShareOfTheFullChargeAndTheCounterLastsItsTime() {
        // 625.4 mA of 6,254 mAh is 10% an hour; the 3,127 mAh left last 5 hours.
        val figures = UsualPace(thor).of(625_400)!!
        assertEquals(10.0, figures.percentPerHour!!, 0.01)
        assertEquals(300.0, figures.minutesOnCharge!!, 0.01)
    }

    @Test
    fun theTimeIsTheCounterNowNotTheLevel() {
        val pace = UsualPace(thor)
        pace.of(625_400)
        // The gauge moved a little; the level still reads 50.
        thor.counter = 3_000_000
        assertEquals(3_000_000.0 / 625_400 * 60, pace.of(625_400)!!.minutesOnCharge!!, 1e-6)
        thor.level = 49
        assertEquals("the level has no part in it", 3_000_000.0 / 625_400 * 60, pace.of(625_400)!!.minutesOnCharge!!, 1e-6)
    }

    @Test
    fun aGameNeverMeasuredHasNoPace() {
        assertNull(UsualPace(thor).of(null))
        assertNull(UsualPace(thor).of(0))
        assertEquals(0, thor.reads)
    }

    @Test
    fun aDeviceWithoutACounterOrALevelHasNothingToSay() {
        assertNull("no counter", UsualPace(Reader(BatteryReader.UNKNOWN, 50)).of(500_000))
        assertNull("no level: the counter's unit is not known", UsualPace(Reader(3_127_000, BatteryReader.UNKNOWN_LEVEL)).of(500_000))
        assertNull(UsualPace(Reader(3_127_000, 0)).of(500_000))
    }

    @Test
    fun aCounterInMilliampereHoursIsRead() {
        val figures = UsualPace(Reader(counter = 3_127, level = 50)).of(625_400)!!
        assertEquals(10.0, figures.percentPerHour!!, 0.01)
        assertEquals(300.0, figures.minutesOnCharge!!, 0.5)
    }

    @Test
    fun theFullChargeFromAHighLevelIsKeptWhenTheLevelFalls() {
        val pace = UsualPace(thor)
        pace.of(625_400)
        thor.level = 5
        thor.counter = 312_700
        val low = pace.of(625_400)!!
        assertEquals(10.0, low.percentPerHour!!, 0.01)
        assertEquals("the time follows the counter", 30.0, low.minutesOnCharge!!, 0.01)
    }

    /** A device whose gauge holds [soc] percent of [FULL] and reports the level as a whole number (Android's level is the gauge's, floored). */
    private fun Reader.at(soc: Double) {
        counter = (soc / 100 * FULL).toLong()
        level = soc.toInt()
    }

    private fun percentOf(
        pace: UsualPace,
        draw: Long,
    ): Double? = pace.of(draw)?.percentPerHour

    /** The pace's error against the truth (625.4 mA of 6,254 mAh is 10% an hour). */
    private fun error(percent: Double) = kotlin.math.abs(percent - 10.0) / 10.0

    @Test
    fun aFirstReadAt21PercentIsReplacedByAHigherLevel() {
        val reader = Reader(0, 0)
        val pace = UsualPace(reader)
        reader.at(21.9)
        val atLow = percentOf(pace, 625_400)!!
        assertTrue("at 21%: within one level step, 1/21 (${error(atLow)})", error(atLow) <= 1.0 / 21)
        reader.at(100.0)
        val atFull = percentOf(pace, 625_400)!!
        assertTrue("at 100%: within 1/100 (${error(atFull)})", error(atFull) <= 1.0 / 100)
        reader.at(64.7)
        assertEquals("a lower level later does not undo it", atFull, percentOf(pace, 625_400)!!, 1e-9)
    }

    @Test
    fun aLevelUnder20PercentGivesNoPaceUntilAHigherOneIsSeen() {
        // After a reboot at 5%: one level step is a fifth of it.
        val reader = Reader(0, 0)
        val pace = UsualPace(reader)
        reader.at(5.9)
        assertNull("at 5%", percentOf(pace, 625_400))
        assertEquals("the time still shows", 5.9 / 100 * FULL / 625_400 * 60, pace.of(625_400)!!.minutesOnCharge!!, 0.01)
        reader.at(19.99)
        assertNull("at 19%", percentOf(pace, 625_400))
        reader.at(20.5)
        val at20 = percentOf(pace, 625_400)!!
        assertTrue("at 20%: within 1/20 (${error(at20)})", error(at20) <= 1.0 / 20)
    }

    @Test
    fun aFullBatteryIsWithinOnePercent() {
        val reader = Reader(0, 0)
        reader.at(100.0)
        assertTrue(error(percentOf(UsualPace(reader), 625_400)!!) <= 1.0 / 100)
    }

    private companion object {
        /** The Thor's full charge, microampere-hours. */
        const val FULL = 6_254_000.0
    }
}
