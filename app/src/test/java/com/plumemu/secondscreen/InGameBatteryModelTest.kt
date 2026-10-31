package com.plumemu.secondscreen

import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.BatteryEstimate
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.LevelPoint
import com.plumemu.launcher.play.battery.TimeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battery part's words and chart from the meter's figures: what shows in each state, the usual pace only when it differs noticeably, the
 * fixed three hours and how they slide, and nothing outside the chart.
 */
class InGameBatteryModelTest {
    /** 22 October 2026, 21:00 UTC. */
    private val nine = 29_878_380L * MINUTE

    /** The elapsed clock: the play's first read at 1,000 s. */
    private val elapsedStart = 1_000_000L

    private val full = 6_254_000.0

    /** A draw that is [percentPerHour] of the full charge an hour. */
    private fun draw(percentPerHour: Double) = (percentPerHour / 100 * full).toLong()

    /** Levels at minutes after 21:00 (the play starting at its first), now at [nowMinute]. */
    private fun snapshot(
        nowMinute: Int,
        levels: List<Pair<Int, Int>>,
        estimate: BatteryEstimate?,
        usual: Long? = null,
        charging: Boolean = false,
        readMs: Long = (nowMinute - levels.first().first) * MINUTE,
    ): Pair<BatterySnapshot, Long> {
        val firstMinute = levels.first().first
        val points = levels.map { (minute, level) -> LevelPoint(elapsedStart + (minute - firstMinute) * MINUTE, level, charging) }
        val nowElapsed = elapsedStart + (nowMinute - firstMinute) * MINUTE
        return BatterySnapshot(estimate, usual, nowElapsed, points, readMs) to nine + nowMinute * MINUTE
    }

    private fun discharging(
        percentPerHour: Double,
        minutesLeft: Double?,
    ) = BatteryEstimate(isCharging = false, drawMicroamps = draw(percentPerHour), minutesLeft = minutesLeft, measuredMs = 40 * MINUTE, fullChargeMicroampHours = full)

    @Test
    fun nothingBeforeTheFirstReadNotEvenANote() {
        assertNull(inGameBatteryOf(null, BatteryReading(70, false), nine, driftNote = false))
    }

    @Test
    fun theDriftNoteShowsFromTheFirstFrame() {
        assertEquals(InGameBattery(null, InGameNote.Drift), inGameBatteryOf(null, BatteryReading(70, false), nine, driftNote = true))
    }

    @Test
    fun aFirstPlaysFirstMinuteIsTheClockCardWithMeasuring() {
        val (snapshot, now) = snapshot(nowMinute = 8, levels = listOf(8 to 70), estimate = null)
        assertEquals(InGameBattery(null, InGameNote.Measuring), inGameBatteryOf(snapshot, BatteryReading(70, false), now, driftNote = false))
        assertEquals("the drift note wins its minute", InGameBattery(null, InGameNote.Drift), inGameBatteryOf(snapshot, BatteryReading(70, false), now, driftNote = true))
    }

    @Test
    fun aDeviceThatGivesNoFigureShowsTheClockCardAloneAfterAMinuteOfReads() {
        // Neither current nor counter readable: the level points come, the estimate never does.
        val (firstMinute, firstNow) = snapshot(nowMinute = 9, levels = listOf(8 to 70), estimate = null, readMs = 60_000)
        assertEquals(InGameBattery(null, InGameNote.Measuring), inGameBatteryOf(firstMinute, BatteryReading(70, false), firstNow, driftNote = false))
        val (later, laterNow) = snapshot(nowMinute = 30, levels = listOf(8 to 70, 25 to 69), estimate = null)
        assertNull("the clock card alone, as with Show battery off", inGameBatteryOf(later, BatteryReading(69, false), laterNow, driftNote = false))
        assertEquals("the drift note still has its minute", InGameBattery(null, InGameNote.Drift), inGameBatteryOf(later, BatteryReading(69, false), laterNow, driftNote = true))
    }

    @Test
    fun aMinuteAfterTheFirstPointWithTheScreenOffInBetweenStillSaysMeasuring() {
        // 20 minutes since the play's first point, but read for 40 s of it: not given up.
        val (snapshot, now) = snapshot(nowMinute = 28, levels = listOf(8 to 70), estimate = null, readMs = 40_000)
        assertEquals(InGameBattery(null, InGameNote.Measuring), inGameBatteryOf(snapshot, BatteryReading(70, false), now, driftNote = false))
    }

    @Test
    fun aHeavyGameFasterThanUsualShowsTheUsualPaceDotted() {
        // Mockup 1: 40 minutes in at 48% an hour, usually 40%.
        val (snapshot, now) =
            snapshot(nowMinute = 47, levels = listOf(7 to 70, 20 to 60, 33 to 49, 47 to 38), estimate = discharging(48.0, 45.0), usual = draw(40.0))
        val figures = inGameBatteryOf(snapshot, BatteryReading(38, false), now, driftNote = false)!!.figures!!
        assertEquals(TimeLeft(45, isApproximate = false), figures.left)
        assertEquals("empty at 22:32", nine + (47 + 45) * MINUTE, figures.endsAtMillis)
        assertEquals(48, figures.pacePercent)
        assertEquals(UsualPace.Differs(40), figures.usual)
        assertEquals(false, figures.isLow)
        val chart = figures.chart
        assertEquals("the window starts at 21:00 for a play from 21:07", nine, chart.startMillis)
        assertEquals(listOf(ChartPoint(7f, 70f), ChartPoint(20f, 60f), ChartPoint(33f, 49f), ChartPoint(47f, 38f)), chart.played)
        assertEquals("ahead: empty 45 minutes on", ChartPoint(92f, 0f), chart.aheadTo)
        assertEquals("the usual pace from the play's start", ChartPoint(7f, 70f), chart.usualFrom)
        assertEquals("70% at 40% an hour: 105 minutes", ChartPoint(112f, 0f), chart.usualTo)
    }

    @Test
    fun aGameAtItsUsualPaceSaysAsUsualWithoutADottedLine() {
        val (snapshot, now) =
            snapshot(nowMinute = 47, levels = listOf(7 to 70, 47 to 61), estimate = discharging(14.0, 261.0), usual = draw(13.0))
        val figures = inGameBatteryOf(snapshot, BatteryReading(61, false), now, driftNote = false)!!.figures!!
        assertEquals(UsualPace.AsUsual, figures.usual)
        assertNull(figures.chart.usualFrom)
        assertEquals("about 4 h 15: rounded to 15 minutes above an hour", TimeLeft(255, isApproximate = true), figures.left)
        assertEquals("the line ahead stops at the chart's right edge", 180f, figures.chart.aheadTo!!.minute)
        assertTrue(figures.chart.aheadTo!!.percent > 0f)
    }

    @Test
    fun aGameNeverMeasuredHasNoUsualPace() {
        val (snapshot, now) = snapshot(nowMinute = 20, levels = listOf(7 to 70, 20 to 64), estimate = discharging(30.0, 120.0))
        assertEquals(UsualPace.None, inGameBatteryOf(snapshot, BatteryReading(64, false), now, driftNote = false)!!.figures!!.usual)
    }

    @Test
    fun aSmallDifferenceIsNotNoticeable() {
        val (snapshot, now) = snapshot(nowMinute = 20, levels = listOf(7 to 70, 20 to 64), estimate = discharging(45.0, 80.0), usual = draw(40.0))
        assertEquals("12.5% off: as usual", UsualPace.AsUsual, inGameBatteryOf(snapshot, BatteryReading(64, false), now, driftNote = false)!!.figures!!.usual)
    }

    @Test
    fun chargingWithAndroidsTimeToFull() {
        // Mockup 3: "1 h 03 to full", "+40% an hour", "since 21:20".
        val estimate =
            BatteryEstimate(
                isCharging = true, drawMicroamps = -draw(40.0), minutesLeft = 63.0, measuredMs = 0, fullChargeMicroampHours = full,
                isMinutesLeftExact = true,
            )
        val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(20 to 40, 33 to 49, 47 to 58), estimate = estimate, usual = draw(30.0), charging = true)
        val figures = inGameBatteryOf(snapshot, BatteryReading(58, true), now, driftNote = false)!!.figures!!
        assertTrue(figures.isCharging)
        assertEquals(TimeLeft(63, isApproximate = false), figures.left)
        assertEquals(40, figures.pacePercent)
        assertEquals("no usual pace while charging", UsualPace.None, figures.usual)
        assertEquals(nine + 20 * MINUTE, figures.chargingSinceMillis)
        assertEquals("a play from 21:20: the window too", nine + 20 * MINUTE, figures.chart.startMillis)
        assertEquals("full 63 minutes after now (27 minutes in)", ChartPoint(90f, 100f), figures.chart.aheadTo)
    }

    @Test
    fun chargingWithTheCountersEstimateIsRounded() {
        val estimate = BatteryEstimate(isCharging = true, drawMicroamps = -draw(40.0), minutesLeft = 63.0, measuredMs = 0, fullChargeMicroampHours = full)
        val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(47 to 58), estimate = estimate, charging = true)
        assertEquals(TimeLeft(60, isApproximate = true), inGameBatteryOf(snapshot, BatteryReading(58, true), now, false)!!.figures!!.left)
    }

    @Test
    fun onlyAndroidsTimeToFullIsExact() {
        fun leftOf(minutes: Double, androids: Boolean): Pair<TimeLeft?, Boolean> {
            val estimate =
                BatteryEstimate(
                    isCharging = true, drawMicroamps = -draw(40.0), minutesLeft = minutes, measuredMs = 0, fullChargeMicroampHours = full,
                    isMinutesLeftExact = androids,
                )
            val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(47 to 58), estimate = estimate, charging = true)
            val figures = inGameBatteryOf(snapshot, BatteryReading(58, true), now, false)!!.figures!!
            return figures.left to figures.isLeftExact
        }
        assertEquals("Android's 90 minutes", TimeLeft(90, isApproximate = false) to true, leftOf(90.0, androids = true))
        assertEquals("the counter's 90: whole after rounding, still an estimate", TimeLeft(90, isApproximate = false) to false, leftOf(90.0, androids = false))
        assertEquals("the counter's 91", TimeLeft(90, isApproximate = true) to false, leftOf(91.0, androids = false))
    }

    @Test
    fun fullOnTheChargerHasNoTimeAndNoLineAhead() {
        val estimate = BatteryEstimate(isCharging = true, drawMicroamps = 0, minutesLeft = null, measuredMs = 0, fullChargeMicroampHours = full)
        val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(30 to 100), estimate = estimate, charging = true)
        val figures = inGameBatteryOf(snapshot, BatteryReading(100, true), now, false)!!.figures!!
        assertNull(figures.left)
        assertNull(figures.endsAtMillis)
        assertNull("no pace into a full battery", figures.pacePercent)
        assertNull(figures.chart.aheadTo)
    }

    @Test
    fun aDeviceWithoutACounterHasTheChartAlone() {
        val estimate = BatteryEstimate(isCharging = false, drawMicroamps = 500_000, minutesLeft = null, measuredMs = 5 * MINUTE)
        val (snapshot, now) = snapshot(nowMinute = 20, levels = listOf(7 to 70, 20 to 66), estimate = estimate, usual = 400_000)
        val figures = inGameBatteryOf(snapshot, BatteryReading(66, false), now, false)!!.figures!!
        assertNull(figures.left)
        assertNull(figures.pacePercent)
        assertEquals(UsualPace.None, figures.usual)
        assertEquals(2, figures.chart.played.size)
    }

    @Test
    fun fifteenPercentAndBelowNotChargingIsLow() {
        val (snapshot, now) = snapshot(nowMinute = 167, levels = listOf(107 to 70, 167 to 12), estimate = discharging(48.0, 15.0))
        assertTrue(inGameBatteryOf(snapshot, BatteryReading(12, false), now, false)!!.figures!!.isLow)
        val (atSixteen, later) = snapshot(nowMinute = 167, levels = listOf(107 to 70, 167 to 16), estimate = discharging(48.0, 20.0))
        assertEquals(false, inGameBatteryOf(atSixteen, BatteryReading(16, false), later, false)!!.figures!!.isLow)
    }

    @Test
    fun theWindowStartsOnTheTenMinutesBeforeThePlayAndSlidesByAnHourPastTwoHours() {
        // Mockup 4: a play from 22:47 shows 22:40 to 01:40.
        val (fromLate, now) = snapshot(nowMinute = 167, levels = listOf(107 to 70, 167 to 12), estimate = discharging(48.0, 15.0))
        assertEquals(nine + 100 * MINUTE, inGameBatteryOf(fromLate, BatteryReading(12, false), now, false)!!.figures!!.chart.startMillis)

        // Two and a half hours into a play from 21:07: the window has moved on an hour, to 22:00.
        val (long, later) = snapshot(nowMinute = 157, levels = listOf(7 to 90, 60 to 70, 120 to 50, 157 to 38), estimate = discharging(20.0, 114.0))
        val chart = inGameBatteryOf(long, BatteryReading(38, false), later, false)!!.figures!!.chart
        assertEquals(nine + 60 * MINUTE, chart.startMillis)
        assertEquals("the line before the window starts at its left edge", ChartPoint(0f, 70f), chart.played.first())
        assertEquals(ChartPoint(97f, 38f), chart.played.last())
    }

    @Test
    fun everyPointIsInsideTheChart() {
        val (snapshot, now) =
            snapshot(nowMinute = 230, levels = (0..230 step 10).map { it to (95 - it / 3) }, estimate = discharging(20.0, 500.0), usual = draw(10.0))
        val chart = inGameBatteryOf(snapshot, BatteryReading(18, false), now, false)!!.figures!!.chart
        val all = chart.played + listOfNotNull(chart.aheadTo, chart.usualFrom, chart.usualTo)
        for (point in all) {
            assertTrue("$point", point.minute in 0f..CHART_MINUTES.toFloat())
            assertTrue("$point", point.percent in 0f..100f)
        }
    }

    @Test
    fun anEstimateMovingByUnderAQuarterMinuteLeavesTheChartEqual() {
        val (a, now) = snapshot(nowMinute = 47, levels = listOf(7 to 70, 47 to 38), estimate = discharging(48.0, 45.02))
        val (b, _) = snapshot(nowMinute = 47, levels = listOf(7 to 70, 47 to 38), estimate = discharging(48.0, 45.08))
        val reading = BatteryReading(38, false)
        assertEquals(inGameBatteryOf(a, reading, now, false), inGameBatteryOf(b, reading, now, false))
    }

    @Test
    fun theDriftNoteRidesWithTheFigures() {
        val (snapshot, now) = snapshot(nowMinute = 8, levels = listOf(8 to 70), estimate = discharging(40.0, 105.0), usual = draw(40.0))
        val shown = inGameBatteryOf(snapshot, BatteryReading(70, false), now, driftNote = true)!!
        assertNull(shown.note)
        assertNotNull(shown.figures)
        assertTrue(shown.figures!!.driftNote)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
