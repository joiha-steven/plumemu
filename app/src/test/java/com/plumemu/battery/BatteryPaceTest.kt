package com.plumemu.battery

import com.plumemu.launcher.play.battery.TimeLeft
import com.plumemu.launcher.play.battery.UsualPace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The picking line's figures: the time on the charge at the usual pace, the pace, the glyph's steps. */
class BatteryPaceTest {
    private fun parts(
        minutes: Double?,
        percentPerHour: Double?,
        isCharging: Boolean = false,
    ) = pickingBatteryParts(BatteryPace(minutes, percentPerHour), isCharging)

    @Test
    fun theTimeIsRoundedLikeTheTimeLeftInGame() {
        // Under an hour to 5, from an hour to 15; "About" only where the rounding hid minutes.
        assertEquals(TimeLeft(45, isApproximate = false), parts(45.0, 60.0)!!.onCharge)
        assertEquals("47 hides 2 minutes", TimeLeft(45, isApproximate = true), parts(47.0, 60.0)!!.onCharge)
        assertEquals(TimeLeft(60, isApproximate = false), parts(60.0, 60.0)!!.onCharge)
        assertEquals("62 is shown as an hour", TimeLeft(60, isApproximate = true), parts(62.0, 60.0)!!.onCharge)
        assertEquals(TimeLeft(75, isApproximate = false), parts(75.0, 60.0)!!.onCharge)
        assertEquals("never under 5 minutes", TimeLeft(5, isApproximate = true), parts(1.0, 60.0)!!.onCharge)
    }

    @Test
    fun chargingGivesThePaceOnly() {
        val charging = parts(120.0, 30.0, isCharging = true)!!
        assertNull(charging.onCharge)
        assertEquals(30, charging.percentPerHour)
    }

    @Test
    fun chargingWithoutAKnownPaceSaysNothing() {
        assertNull(parts(120.0, null, isCharging = true))
    }

    @Test
    fun withoutAKnownPaceTheTimeStillShows() {
        val timeOnly = parts(120.0, null)!!
        assertEquals(TimeLeft(120, isApproximate = false), timeOnly.onCharge)
        assertNull(timeOnly.percentPerHour)
    }

    @Test
    fun thePaceIsWholePercentAndUnderOneIsSaidSo() {
        assertEquals(30, parts(100.0, 29.6)!!.percentPerHour)
        assertEquals(8, parts(100.0, 7.5)!!.percentPerHour)
        assertEquals(1, parts(100.0, 1.0)!!.percentPerHour)
        assertEquals("0.2% an hour is under 1%", UNDER_ONE_PERCENT, parts(100.0, 0.2)!!.percentPerHour)
    }

    @Test
    fun underOnePercentKeepsTheTimeOfTheRealPace() {
        // A full battery at 0.32% an hour: 312 h 30 min, not the 100 h a pace held at 1% would say.
        val slow = parts(100 / 0.32 * 60, 0.32)!!
        assertEquals(TimeLeft(18_750, isApproximate = false), slow.onCharge)
        assertEquals(UNDER_ONE_PERCENT, slow.percentPerHour)
    }

    @Test
    fun onlyRealFiguresAreKept() {
        assertNull("never measured", BatteryPace.of(null))
        assertNull(BatteryPace.of(UsualPace.Figures(0.0, -4.0)))
        assertNull(BatteryPace.of(UsualPace.Figures(Double.NaN, Double.POSITIVE_INFINITY)))
        assertEquals(BatteryPace(null, 12.5), BatteryPace.of(UsualPace.Figures(0.0, 12.5)))
        assertEquals(BatteryPace(90.0, null), BatteryPace.of(UsualPace.Figures(90.0, null)))
        assertEquals(BatteryPace(90.0, 12.5), BatteryPace.of(UsualPace.Figures(90.0, 12.5)))
    }

    @Test
    fun theGlyphShowsFourSteps() {
        val steps = (0..100).map(::glyphSteps)
        assertEquals("empty under 5%", List(5) { 0 }, steps.subList(0, 5))
        assertEquals("one bar from 5 to 37", List(33) { 1 }, steps.subList(5, 38))
        assertEquals("two from 38 to 62", List(25) { 2 }, steps.subList(38, 63))
        assertEquals("three from 63 to 87", List(25) { 3 }, steps.subList(63, 88))
        assertEquals("four from 88", List(13) { 4 }, steps.subList(88, 101))
        assertEquals("a level outside is held", listOf(0, 4), listOf(glyphSteps(-5), glyphSteps(130)))
    }
}
