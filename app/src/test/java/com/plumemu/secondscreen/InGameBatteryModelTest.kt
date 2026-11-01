package com.plumemu.secondscreen

import com.plumemu.home.BatteryReading
import com.plumemu.launcher.play.battery.BatteryEstimate
import com.plumemu.launcher.play.battery.BatterySnapshot
import com.plumemu.launcher.play.battery.LevelPoint
import com.plumemu.launcher.play.battery.TimeLeft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battery part's words and chart from the meter's figures: what shows in each state, the usual pace only when it differs noticeably, the
 * chart from the play's start to empty or full with its whole hours, this play's facts, and nothing outside the chart.
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

    /** [inGameBatteryOf] on a UTC wall clock. */
    private fun partOf(
        snapshot: BatterySnapshot,
        level: Int,
        now: Long,
        charging: Boolean = false,
        zoneOffset: (Long) -> Int = { 0 },
    ) = inGameBatteryOf(snapshot, BatteryReading(level, charging), now, driftNote = false, zoneOffset = zoneOffset)!!.figures!!

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
        // 21:47 and 45 minutes: 22:32, on the five minutes 22:30.
        assertEquals("empty at 22:30", nine + (47 + 43) * MINUTE, figures.endsAtMillis)
        assertEquals(48, figures.pacePercent)
        assertEquals(UsualPace.Differs(40), figures.usual)
        assertEquals(false, figures.isLow)
        assertEquals(38, figures.level)
        assertEquals("21:07 to 21:47", 40, figures.playedMinutes)
        assertEquals("70% to 38%", 32, figures.usedPercent)
        val chart = partOf(snapshot, 38, now).chart
        assertEquals("the chart starts with the play, at 21:07", nine + 7 * MINUTE, chart.startMillis)
        assertEquals("to empty: 40 minutes played and 45 ahead", 85f, chart.minutes)
        assertEquals(listOf(ChartPoint(0f, 70f), ChartPoint(13f, 60f), ChartPoint(26f, 49f), ChartPoint(40f, 38f)), chart.played)
        assertEquals("ahead: empty 45 minutes on", ChartPoint(85f, 0f), chart.aheadTo)
        assertEquals("the usual pace from the play's start", ChartPoint(0f, 70f), chart.usualFrom)
        // 70% at 40% an hour empties in 105 minutes: cut at the chart's end, 85 minutes in, at 13.3%.
        assertEquals(ChartPoint(85f, 13.25f), chart.usualTo)
        assertEquals("22:00 is the only whole hour from 21:07 to 22:32", listOf(53f), chart.hourMinutes)
    }

    @Test
    fun aGameAtItsUsualPaceSaysAsUsualWithoutADottedLine() {
        val (snapshot, now) =
            snapshot(nowMinute = 47, levels = listOf(7 to 70, 47 to 61), estimate = discharging(14.0, 261.0), usual = draw(13.0))
        val figures = inGameBatteryOf(snapshot, BatteryReading(61, false), now, driftNote = false)!!.figures!!
        assertEquals(UsualPace.AsUsual, figures.usual)
        assertNull(figures.chart.usualFrom)
        assertEquals("about 4 h 15: rounded to 15 minutes above an hour", TimeLeft(255, isApproximate = true), figures.left)
        assertEquals("the chart runs to empty", ChartPoint(figures.chart.minutes, 0f), figures.chart.aheadTo)
        assertEquals("40 minutes played and 261 ahead", 301f, figures.chart.minutes)
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
        // Mockup 3: "1 h 3 min to full", "+40% an hour", "since 21:20".
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
        assertEquals("a play from 21:20: the chart too", nine + 20 * MINUTE, figures.chart.startMillis)
        assertEquals("full 63 minutes after now (27 minutes in)", ChartPoint(90f, 100f), figures.chart.aheadTo)
        assertEquals("nothing used while charging", null, figures.usedPercent)
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
        assertEquals("17 minutes played: the chart is still an hour wide", MIN_CHART_MINUTES, figures.chart.minutes)
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
    fun theChartRunsFromThePlaysStartToEmptyHoweverLong() {
        // Mockup 4: a play from 22:47.
        val (fromLate, now) = snapshot(nowMinute = 167, levels = listOf(107 to 70, 167 to 12), estimate = discharging(48.0, 15.0))
        assertEquals(nine + 107 * MINUTE, partOf(fromLate, 12, now).chart.startMillis)

        // Two and a half hours into a play from 21:07, and two hours ahead: all of it.
        val (long, later) = snapshot(nowMinute = 157, levels = listOf(7 to 90, 60 to 70, 120 to 50, 157 to 38), estimate = discharging(20.0, 114.0))
        val chart = partOf(long, 38, later).chart
        assertEquals(nine + 7 * MINUTE, chart.startMillis)
        assertEquals(264f, chart.minutes)
        assertEquals(ChartPoint(0f, 90f), chart.played.first())
        assertEquals(ChartPoint(150f, 38f), chart.played.last())
        assertEquals("22:00 to 01:00", listOf(53f, 113f, 173f, 233f), chart.hourMinutes)
    }

    @Test
    fun overTwelveHoursAheadTheChartStopsAtTwelveWithNoEndTime() {
        // A 20 mA draw on 5,000 mAh (0.4% an hour): empty about 145 hours on, some day next week.
        val (snapshot, now) = snapshot(nowMinute = 30, levels = listOf(0 to 58, 30 to 58), estimate = discharging(0.4, 8_700.0))
        val figures = partOf(snapshot, 58, now)
        assertNull("no clock time for an end days away", figures.endsAtMillis)
        val chart = figures.chart
        assertEquals(30f + MAX_AHEAD_MINUTES, chart.minutes)
        assertFalse("the forecast is cut, not ended", chart.aheadEnds)
        assertEquals(30f + MAX_AHEAD_MINUTES, chart.aheadTo!!.minute)
        assertEquals(58f - 0.4f * MAX_AHEAD_MINUTES / 60, chart.aheadTo!!.percent, 0.3f)

        // Twelve hours and under: to empty, with its time.
        val (near, later) = snapshot(nowMinute = 30, levels = listOf(0 to 58, 30 to 58), estimate = discharging(5.0, 690.0))
        val within = partOf(near, 58, later)
        assertTrue(within.chart.aheadEnds)
        assertEquals(0f, within.chart.aheadTo!!.percent)
        assertTrue(within.endsAtMillis != null)
    }

    @Test
    fun aLongChartTicksEverySecondHour() {
        // 20 hours ahead: every second hour, at most about twelve ticks.
        val (snapshot, now) = snapshot(nowMinute = 30, levels = listOf(0 to 90, 30 to 89), estimate = discharging(4.0, 1_200.0))
        val chart = partOf(snapshot, 89, now).chart
        assertEquals(0f, chart.hourMinutes.first())
        assertEquals(120f, chart.hourMinutes[1])
        assertTrue(chart.hourMinutes.size <= MAX_HOUR_TICKS)
    }

    @Test
    fun theTicksAreOnTheWholeHoursOfTheLocalClock() {
        // Five and a half hours ahead of UTC: 21:07 UTC is 02:37 there, so 03:00 is 23 minutes in.
        val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(7 to 70, 47 to 61), estimate = discharging(14.0, 261.0))
        val chart = partOf(snapshot, 61, now, zoneOffset = { 330 * MINUTE.toInt() }).chart
        assertEquals(listOf(23f, 83f, 143f, 203f, 263f), chart.hourMinutes)
    }

    @Test
    fun aChargerInThePlayLeavesOutWhatItUsed() {
        val (snapshot, now) = snapshot(nowMinute = 47, levels = listOf(7 to 70, 20 to 60, 33 to 66), estimate = discharging(20.0, 100.0))
        val charged = snapshot.copy(levels = snapshot.levels.mapIndexed { i, point -> if (i == 2) point.copy(isCharging = true) else point })
        assertNull(partOf(charged, 64, now).usedPercent)
        assertEquals(6, partOf(snapshot, 64, now).usedPercent)
    }

    @Test
    fun everyPointIsInsideTheChart() {
        val (snapshot, now) =
            snapshot(nowMinute = 230, levels = (0..230 step 10).map { it to (95 - it / 3) }, estimate = discharging(20.0, 500.0), usual = draw(10.0))
        val chart = inGameBatteryOf(snapshot, BatteryReading(18, false), now, false)!!.figures!!.chart
        val all = chart.played + listOfNotNull(chart.aheadTo, chart.usualFrom, chart.usualTo)
        for (point in all) {
            assertTrue("$point", point.minute in 0f..chart.minutes)
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
