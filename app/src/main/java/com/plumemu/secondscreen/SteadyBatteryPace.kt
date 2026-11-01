package com.plumemu.secondscreen

import com.plumemu.launcher.play.battery.BatterySnapshot
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * The in-game card's figures held steady. The meter's pace follows the last 10 minutes of the
 * counter (or the current before it moved), so a game's loading, a busy scene or a menu moves the
 * time left by an hour from one read to the next. Here, per play:
 * - the first [START_IGNORE_MS] of reads are not shown (loading draws far more than play): the
 *   game's usual pace stands in when there is one, else [steady] says [Steady.Starting];
 * - the pace after that is an exponential average over about [SMOOTH_MS];
 * - once the level has dropped [CHECK_AFTER_DROP] whole percent since then, the real drop rate is
 *   compared, and the figure leans towards it as far as the two differ (by more than
 *   [CHECK_DIFFERS_FROM]) and the drop is long enough to trust ([checkWeight]); a gap in the reads
 *   (the card hidden while the game ran) starts that check again.
 *
 * Charging passes through untouched (Android's own time to full). One instance per card; a new play
 * (the meter's read time going back) starts it again. Pure: the clock is the snapshot's own.
 */
internal class SteadyBatteryPace {
    private var lastReadMs = -1L
    private var lastAtMs = 0L
    private var lastMeasuredMs = 0L
    private var smoothed = Double.NaN // microamperes

    // The level check: the first and latest level drops seen after the start, on the measured-time clock.
    private var lastLevel = -1
    private var firstDropLevel = -1
    private var firstDropMeasuredMs = 0L
    private var latestDropLevel = -1
    private var latestDropMeasuredMs = 0L

    sealed interface Steady {
        /** In the first minutes of a play with no usual pace: "Measuring battery use…". */
        data object Starting : Steady

        /** [snapshot] with its estimate's pace and time left made steady (or untouched: charging, none yet). */
        data class Figures(val snapshot: BatterySnapshot?) : Steady
    }

    /** [snapshot] as the card should show it; [levelNow] the level the clock card shows. */
    fun steady(
        snapshot: BatterySnapshot?,
        levelNow: Int?,
    ): Steady {
        if (snapshot == null) return Steady.Figures(null)
        if (snapshot.readMs < lastReadMs) reset()
        lastReadMs = snapshot.readMs
        val estimate = snapshot.estimate ?: return Steady.Figures(snapshot)
        if (estimate.isCharging) {
            forgetDischarge()
            return Steady.Figures(snapshot)
        }
        val usual = snapshot.usualDrawMicroamps?.takeIf { it > 0 }?.toDouble()
        if (smoothed.isNaN() && snapshot.readMs < START_IGNORE_MS) {
            return if (usual == null) Steady.Starting else Steady.Figures(withPace(snapshot, usual))
        }
        val draw = estimate.drawMicroamps.toDouble().takeIf { it > 0 } ?: return Steady.Figures(snapshot)
        smoothed =
            if (smoothed.isNaN()) {
                draw
            } else {
                val step = (snapshot.nowMs - lastAtMs).coerceIn(0L, MAX_STEP_MS)
                smoothed + (draw - smoothed) * (1 - exp(-step.toDouble() / SMOOTH_MS))
            }
        // The reads stopped (the card hidden, the screen off) while the clock went on: a level drop
        // across that gap has no measured time to divide by, so the check starts again after it.
        val unmeasured = (snapshot.nowMs - lastAtMs) - (estimate.measuredMs - lastMeasuredMs)
        if (unmeasured > MAX_STEP_MS) forgetLevels()
        lastAtMs = snapshot.nowMs
        lastMeasuredMs = estimate.measuredMs
        noteLevel(levelNow, estimate.measuredMs)
        val pace = checkedPace(smoothed, estimate.fullChargeMicroampHours)
        return Steady.Figures(withPace(snapshot, pace))
    }

    private fun noteLevel(
        level: Int?,
        measuredMs: Long,
    ) {
        if (level == null || level !in 1..PERCENT) return
        if (lastLevel != -1 && level < lastLevel) {
            if (firstDropLevel == -1) {
                firstDropLevel = level
                firstDropMeasuredMs = measuredMs
            }
            latestDropLevel = level
            latestDropMeasuredMs = measuredMs
        }
        // A rise (a charger in and out between two looks) starts the check again.
        if (lastLevel != -1 && level > lastLevel) firstDropLevel = -1
        lastLevel = level
    }

    /** [smoothed] leaned towards the level's real drop rate once there is one to trust. */
    private fun checkedPace(
        smoothed: Double,
        fullCharge: Double?,
    ): Double {
        val drop = firstDropLevel - latestDropLevel
        val ms = latestDropMeasuredMs - firstDropMeasuredMs
        if (firstDropLevel == -1 || drop < CHECK_AFTER_DROP || ms <= 0 || fullCharge == null || fullCharge <= 0) return smoothed
        val real = drop / PERCENT.toDouble() * fullCharge / ms * MILLIS_PER_HOUR
        return blendTowards(smoothed, real, drop)
    }

    private fun withPace(
        snapshot: BatterySnapshot,
        pace: Double,
    ): BatterySnapshot {
        val estimate = snapshot.estimate ?: return snapshot
        // What is left in the battery is the meter's own (its time left x its pace); only the pace changes.
        val remaining = estimate.minutesLeft?.let { it * estimate.drawMicroamps }
        val minutesLeft = remaining?.takeIf { pace > 0 }?.let { it / pace }
        return snapshot.copy(estimate = estimate.copy(drawMicroamps = pace.toLong(), minutesLeft = minutesLeft))
    }

    private fun forgetDischarge() {
        smoothed = Double.NaN
        forgetLevels()
    }

    private fun forgetLevels() {
        lastLevel = -1
        firstDropLevel = -1
        latestDropLevel = -1
    }

    private fun reset() {
        forgetDischarge()
        lastReadMs = -1L
        lastAtMs = 0L
        lastMeasuredMs = 0L
    }

    companion object {
        /** Loading a game draws far more than playing it: its first reads are not shown. */
        const val START_IGNORE_MS = 150_000L

        /** The exponential average's time constant. */
        const val SMOOTH_MS = 5 * 60_000L

        /** A step longer than this (the screen was off) counts as this, so the figure catches up but does not jump. */
        const val MAX_STEP_MS = 60_000L

        /** The level's drop rate is compared once it has dropped this many whole percent. */
        const val CHECK_AFTER_DROP = 2

        /** Under this share apart the two agree; the lean grows to its whole at [CHECK_FULLY_DIFFERS]. */
        const val CHECK_DIFFERS_FROM = 0.15
        const val CHECK_FULLY_DIFFERS = 0.4

        private const val PERCENT = 100
        private const val MILLIS_PER_HOUR = 3_600_000.0

        /**
         * How far to lean from [smoothed] towards [real] after a level drop of [drop] percent: nothing
         * while they agree, up to [checkWeight] when they differ by [CHECK_FULLY_DIFFERS] or more.
         */
        fun blendTowards(
            smoothed: Double,
            real: Double,
            drop: Int,
        ): Double {
            if (real <= 0) return smoothed
            val differs = abs(smoothed - real) / real
            val share = ((differs - CHECK_DIFFERS_FROM) / (CHECK_FULLY_DIFFERS - CHECK_DIFFERS_FROM)).coerceIn(0.0, 1.0)
            val weight = share * checkWeight(drop)
            return smoothed + (real - smoothed) * weight
        }

        /**
         * How much a drop of [drop] whole percent is trusted: a percent step hides up to a percent, so
         * 2% says little (0.25) and 18% a lot (0.75, never all: the counter is finer).
         */
        fun checkWeight(drop: Int): Double = min(MAX_CHECK_WEIGHT, drop / (drop + CHECK_HALF_AT))

        private const val MAX_CHECK_WEIGHT = 0.75
        private const val CHECK_HALF_AT = 6.0
    }
}
