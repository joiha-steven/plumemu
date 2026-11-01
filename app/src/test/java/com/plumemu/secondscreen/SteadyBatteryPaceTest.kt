package com.plumemu.secondscreen

import com.plumemu.launcher.play.battery.BatteryEstimate
import com.plumemu.launcher.play.battery.BatterySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.random.Random

/** The in-game card's figures held steady: the start-up wait, the average, the level's cross-check. */
class SteadyBatteryPaceTest {
    /** 5,000 mAh full; at 500 mA, 10% an hour. */
    private val full = 5_000_000.0

    private fun snapshot(
        atMs: Long,
        draw: Long,
        minutesLeft: Double? = 300.0,
        charging: Boolean = false,
        usual: Long? = null,
        measuredMs: Long = atMs,
    ) = BatterySnapshot(
        estimate = BatteryEstimate(charging, draw, minutesLeft, measuredMs, fullChargeMicroampHours = full),
        usualDrawMicroamps = usual,
        nowMs = atMs,
        levels = emptyList(),
        readMs = atMs,
    )

    private fun SteadyBatteryPace.drawAt(
        atMs: Long,
        draw: Long,
        level: Int? = 60,
        usual: Long? = null,
    ): Double {
        val shown = steady(snapshot(atMs, draw, usual = usual), level) as SteadyBatteryPace.Steady.Figures
        return shown.snapshot!!.estimate!!.drawMicroamps.toDouble()
    }

    @Test
    fun theFirstMinutesWithoutAUsualPaceAreMeasuringNotShown() {
        val pace = SteadyBatteryPace()
        assertEquals(SteadyBatteryPace.Steady.Starting, pace.steady(snapshot(60_000, 1_500_000), 60))
        assertEquals(SteadyBatteryPace.Steady.Starting, pace.steady(snapshot(SteadyBatteryPace.START_IGNORE_MS - 1, 1_500_000), 60))
        assertTrue(pace.steady(snapshot(SteadyBatteryPace.START_IGNORE_MS, 500_000), 60) is SteadyBatteryPace.Steady.Figures)
    }

    @Test
    fun theFirstMinutesWithAUsualPaceShowTheUsualOneNotTheLoadingSpike() {
        val pace = SteadyBatteryPace()
        assertEquals(400_000.0, pace.drawAt(30_000, 1_500_000, usual = 400_000), 1.0)
    }

    @Test
    fun theTimeLeftFollowsTheSteadyPaceOnTheSameCharge() {
        val pace = SteadyBatteryPace()
        // The meter: 300 min left at 1,000 mA; shown at the usual 500 mA, the same charge lasts twice as long.
        val shown = pace.steady(snapshot(30_000, 1_000_000, minutesLeft = 300.0, usual = 500_000), 60) as SteadyBatteryPace.Steady.Figures
        assertEquals(600.0, shown.snapshot!!.estimate!!.minutesLeft!!, 0.01)
    }

    @Test
    fun aSpikeMovesTheFigureByItsShareOfFiveMinutesNotAllTheWay() {
        val pace = SteadyBatteryPace()
        val start = SteadyBatteryPace.START_IGNORE_MS
        assertEquals(500_000.0, pace.drawAt(start, 500_000), 1.0)
        // Ten seconds of a draw three times higher.
        val after = pace.drawAt(start + 10_000, 1_500_000)
        val expected = 500_000 + 1_000_000 * (1 - exp(-10_000.0 / SteadyBatteryPace.SMOOTH_MS))
        assertEquals(expected, after, 2.0)
        assertTrue("under 7% up, not 200%", after < 535_000)
    }

    @Test
    fun aLastingChangeIsFollowedOverAboutFiveMinutes() {
        val pace = SteadyBatteryPace()
        var at = SteadyBatteryPace.START_IGNORE_MS
        pace.drawAt(at, 500_000)
        var shown = 0.0
        while (at < SteadyBatteryPace.START_IGNORE_MS + SteadyBatteryPace.SMOOTH_MS) {
            at += 10_000
            shown = pace.drawAt(at, 1_000_000)
        }
        // One time constant: 63% of the way.
        assertEquals(500_000 + 500_000 * (1 - exp(-1.0)), shown, 3_000.0)
    }

    @Test
    fun aLongGapCountsAsAMinuteSoTheFigureDoesNotJump() {
        val pace = SteadyBatteryPace()
        val start = SteadyBatteryPace.START_IGNORE_MS
        pace.drawAt(start, 500_000)
        val after = pace.drawAt(start + 30 * 60_000, 1_500_000)
        assertEquals(500_000 + 1_000_000 * (1 - exp(-60_000.0 / SteadyBatteryPace.SMOOTH_MS)), after, 2.0)
    }

    @Test
    fun theLevelsRealDropPullsAWrongFigureTowardsIt() {
        val pace = SteadyBatteryPace()
        var at = SteadyBatteryPace.START_IGNORE_MS
        // The meter says 250 mA (5% an hour); the level really drops 1% every 6 minutes (10% an hour, 500 mA).
        var level = 60
        pace.drawAt(at, 250_000, level)
        var shown = 0.0
        repeat(6 * 6 * 4) { step ->
            at += 10_000
            if (step % 36 == 35) level--
            shown = pace.drawAt(at, 250_000, level)
        }
        // Three drops after the first: 3% in 18 min = 10% an hour, weight 3 / 9 of the way.
        assertEquals(250_000 + 250_000 * SteadyBatteryPace.checkWeight(3), shown, 5_000.0)
    }

    @Test
    fun aDropWhileTheCardWasHiddenIsNotCountedAgainstTheMeasuredTime() {
        // 10% an hour (500 mA) throughout. 20 minutes shown, then the bottom screen off for an hour:
        // the level drops 10% while the meter measures nothing, then the card is shown again.
        val pace = SteadyBatteryPace()
        var at = SteadyBatteryPace.START_IGNORE_MS
        var measured = at
        var level = 60
        fun read(): Double {
            val shown = pace.steady(snapshot(at, 500_000, measuredMs = measured), level) as SteadyBatteryPace.Steady.Figures
            return shown.snapshot!!.estimate!!.drawMicroamps.toDouble()
        }
        read()
        repeat(6 * 20) { step ->
            at += 10_000
            measured += 10_000
            if (step % 36 == 35) level--
            read()
        }
        at += 60 * 60_000
        level -= 10
        var shown = 0.0
        repeat(6 * 5) {
            at += 10_000
            measured += 10_000
            shown = read()
        }
        assertEquals("the drop seen across the gap left out", 500_000.0, shown, 25_000.0)
    }

    @Test
    fun aFigureThatAgreesWithTheLevelIsLeftAlone() {
        assertEquals(500_000.0, SteadyBatteryPace.blendTowards(500_000.0, 540_000.0, drop = 10), 0.01)
    }

    @Test
    fun theLeanGrowsWithHowFarApartAndHowLongTheDrop() {
        // 27.5% apart: half of the lean; 2% dropped: a quarter trusted.
        assertEquals(362_500 + 0.5 * 0.25 * (500_000 - 362_500), SteadyBatteryPace.blendTowards(362_500.0, 500_000.0, drop = 2), 1.0)
        // Far apart and a long drop: three quarters of the way, never all.
        assertEquals(250_000 + 0.75 * 250_000, SteadyBatteryPace.blendTowards(250_000.0, 500_000.0, drop = 30), 1.0)
        assertEquals(0.25, SteadyBatteryPace.checkWeight(2), 1e-9)
        assertEquals(0.75, SteadyBatteryPace.checkWeight(18), 1e-9)
    }

    @Test
    fun underTwoPercentDroppedNothingIsChecked() {
        val pace = SteadyBatteryPace()
        var at = SteadyBatteryPace.START_IGNORE_MS
        pace.drawAt(at, 250_000, 60)
        at += 10_000
        pace.drawAt(at, 250_000, 59)
        at += 60_000
        assertEquals(250_000.0, pace.drawAt(at, 250_000, 58), 1.0)
    }

    @Test
    fun chargingPassesThroughAndUnpluggingStartsAgainWithoutTheWait() {
        val pace = SteadyBatteryPace()
        val start = SteadyBatteryPace.START_IGNORE_MS
        pace.drawAt(start, 500_000)
        val charging = snapshot(start + 10_000, -2_000_000, minutesLeft = 40.0, charging = true)
        assertEquals(SteadyBatteryPace.Steady.Figures(charging), pace.steady(charging, 61))
        assertEquals(900_000.0, pace.drawAt(start + 20_000, 900_000), 1.0)
    }

    @Test
    fun aNewPlayStartsWithTheWaitAgain() {
        val pace = SteadyBatteryPace()
        pace.drawAt(SteadyBatteryPace.START_IGNORE_MS + 60_000, 500_000)
        assertEquals(SteadyBatteryPace.Steady.Starting, pace.steady(snapshot(5_000, 1_500_000), 60))
    }

    @Test
    fun noEstimateYetIsPassedOn() {
        val pace = SteadyBatteryPace()
        val none = BatterySnapshot(estimate = null, usualDrawMicroamps = null, nowMs = 1_000, levels = emptyList(), readMs = 1_000)
        assertEquals(SteadyBatteryPace.Steady.Figures(none), pace.steady(none, 60))
        assertEquals(SteadyBatteryPace.Steady.Figures(null), pace.steady(null, 60))
    }

    @Test
    fun aMeasuredPlayOfFifteenMinutesWithLoadingAndNoisyReadsStaysWithinFivePercent() {
        // Loading at 1.6 A for 2 minutes, then the meter's pace around 600 mA, each figure up to 10% off.
        val pace = SteadyBatteryPace()
        val noise = Random(7)
        val shown = mutableListOf<Double>()
        var at = 0L
        while (at <= 15 * 60_000) {
            val draw = if (at < 120_000) 1_600_000L else (600_000 * (1 + (noise.nextDouble() - 0.5) * 0.2)).toLong()
            val result = pace.steady(snapshot(at, draw), 60)
            if (result is SteadyBatteryPace.Steady.Figures && at >= 8 * 60_000) shown += result.snapshot!!.estimate!!.drawMicroamps.toDouble()
            at += 10_000
        }
        val spread = (shown.max() - shown.min()) / shown.average()
        assertTrue("shown figures move by ${"%.1f".format(spread * 100)}% from minute 8 on", spread < 0.05)
        assertTrue("the loading left no trace: ${shown.last()}", abs(shown.last() - 600_000) < 30_000)
    }
}
