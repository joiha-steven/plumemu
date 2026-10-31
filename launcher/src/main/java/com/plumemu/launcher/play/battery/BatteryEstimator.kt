package com.plumemu.launcher.play.battery

import com.plumemu.coredata.db.SessionBattery
import kotlin.math.abs

/**
 * The battery use of one play, from the reads [PlayBatteryMeter] feeds it
 * (tuned on numbers measured on an AYN Thor). Pure: no Android, no
 * clock of its own; one instance per play, used from one thread at a time.
 *
 * The idea in three lines:
 * - **The charge counter is the truth** (it is what the level is made of), so the pace is the
 *   counter's drop over the last [BatteryTuning.PACE_WINDOW_MS], timed between the moments it was
 *   seen to change (a counter only updates every 20 s or so on the Thor).
 * - **The current fills in** until the counter has changed twice, scaled by how far the counter
 *   has disagreed with it so far in this play (the Thor's current reads about a quarter low).
 * - **The game's usual pace** counts as [BatteryTuning.USUAL_WEIGHT_MS] of measurement, so a game
 *   played before has a figure from the start and the figure never jumps.
 *
 * Reads more than [BatteryTuning.GAP_MS] apart (sleep), [endStretch] (screen off) and a change
 * between charging and not end a stretch: nothing is measured across them. Charging time is not
 * measured. A current outside what a handheld can draw is no read.
 */
class BatteryEstimator {
    // What the device's numbers mean, learned from its reads.
    private var currentUnit = 0L // microamperes per raw unit: 0 not known yet, 1, or 1000 (mA)
    private var dischargeSign = 0 // sign of the raw current while discharging: 0 not seen, -1 (documented) or +1
    private val unitVotes = LongArray(UNIT_READS) // the first discharging reads' sizes, raw: their median decides the unit
    private var unitVoteCount = 0
    private var negativeVotes = 0
    private var counterUnit = 0L // microampere-hours per raw unit: 0 not known yet, 1, or 1000 (mAh)

    // The stretch being read: reads no more than GAP_MS apart, all charging or all not.
    private var lastReadMs = NONE
    private var stretchStartMs = NONE
    private var lastDraw = NONE // microamperes, positive = discharging; net into the battery while charging is negative
    private var charging = false
    private var stretchCurrentIntegral = 0.0 // microampere-milliseconds since the stretch began ...
    private var stretchCurrentMs = 0L // ... over this much of it (the steps with a current at both ends)
    private var stretchEnded = false // [endStretch] was called: the next read starts a new stretch
    private var lastCounter = NONE // microampere-hours, as last read
    private var lastChangeMs = NONE // when the counter was last seen to change in this stretch
    private var lastChangeCounter = NONE
    private var lastChangeIntegral = 0.0
    private var lastChangeCurrentMs = 0L
    private var stretchFirstChangeMs = NONE // the first seen change of this stretch (the charging rise starts there)
    private var stretchFirstChangeCounter = NONE

    // Spans between two seen counter changes, newest last: the pace window (discharging only).
    private val spanEndMs = LongArray(MAX_SPANS)
    private val spanMs = LongArray(MAX_SPANS)
    private val spanDrop = LongArray(MAX_SPANS)
    private var spanCount = 0
    private var spanNext = 0

    // Per-minute current, for the window's mean when the counter has not moved yet (discharging only).
    private val minuteStartMs = LongArray(WINDOW_MINUTES)
    private val minuteIntegral = DoubleArray(WINDOW_MINUTES)
    private val minuteMs = LongArray(WINDOW_MINUTES)
    private var minuteIndex = -1

    // The whole play.
    private var measuredMs = 0L // discharging time inside stretches
    private var currentMs = 0L // the part of it with a current read at both ends ...
    private var measuredIntegral = 0.0 // ... and the current's integral over it, in microampere-milliseconds
    private var spanDropTotal = 0L // the counter's drop over every span ...
    private var spanMsTotal = 0L // ... and their length
    private var checkedDrop = 0.0 // the counter's drop over the parts of its spans with a current ...
    private var checkedIntegral = 0.0 // ... and the current's integral over the same parts
    private var firstCounter = NONE
    private var level = BatteryReader.UNKNOWN_LEVEL
    private var fullCharge = 0.0 // microampere-hours, from the read at the highest level seen (0: not known)
    private var fullChargeLevel = 0

    /** True when the last read was charging. */
    val isCharging: Boolean get() = lastReadMs != NONE && charging

    /** True before the first read and in the first [BatteryTuning.START_MS] of a stretch. */
    val isStarting: Boolean get() = lastReadMs == NONE || lastReadMs - stretchStartMs < BatteryTuning.START_MS

    /**
     * One read at [nowMs] (elapsed time): the raw [current] and [counter] as the device gives them
     * ([BatteryReader.UNKNOWN] when it gives none), the [level] (0 to 100 or
     * [BatteryReader.UNKNOWN_LEVEL]) and whether it is [isCharging]. Reads out of order are ignored.
     */
    fun add(nowMs: Long, current: Long, counter: Long, level: Int, isCharging: Boolean) {
        if (lastReadMs != NONE && nowMs <= lastReadMs) return
        if (level in 1..PERCENT) this.level = level
        learnUnits(current, counter, isCharging)
        val draw = drawOf(current, isCharging)
        val microampHours = microampHoursOf(counter)
        noteFullCharge(microampHours, level)
        val continues = !stretchEnded && lastReadMs != NONE && nowMs - lastReadMs <= BatteryTuning.GAP_MS && isCharging == charging
        stretchEnded = false
        if (continues) {
            val stepMs = nowMs - lastReadMs
            // The mean of the two ends: a spike counts for the steps on each side of it, no more.
            val stepIntegral = if (draw != NONE && lastDraw != NONE) (draw + lastDraw) / 2.0 * stepMs else 0.0
            stretchCurrentIntegral += stepIntegral
            if (draw != NONE && lastDraw != NONE) stretchCurrentMs += stepMs
            if (!charging) measuredMs += stepMs
            if (!charging && draw != NONE && lastDraw != NONE) {
                currentMs += stepMs
                measuredIntegral += stepIntegral
                addToMinute(nowMs, stepMs, stepIntegral)
            }
        } else {
            startStretch(nowMs, isCharging)
        }
        lastReadMs = nowMs
        lastDraw = draw
        if (microampHours != NONE) noteCounter(nowMs, microampHours)
    }

    /**
     * The reads stop here for now (the screen went off): the next read starts a new stretch, so the
     * time until then is not measured, however short.
     */
    fun endStretch() {
        stretchEnded = true
    }

    /**
     * The pace and time left now, or null when there is nothing to say yet: a game with no
     * [usualDrawMicroamps] (null) before its first minute measured, or no read at all.
     * [chargeTimeRemainingMs] is Android's answer while charging ([BatteryReader.UNKNOWN] when none).
     */
    fun estimate(usualDrawMicroamps: Long?, chargeTimeRemainingMs: Long = BatteryReader.UNKNOWN): BatteryEstimate? {
        if (lastReadMs == NONE) return null
        if (charging) return chargingEstimate(chargeTimeRemainingMs)
        val measured = measuredPace()
        val usual = usualDrawMicroamps?.takeIf { it > 0 }
        val pace = when {
            usual == null && (measured == null || measuredMs < BatteryTuning.FIRST_FIGURE_MS) -> return null
            usual == null -> measured!!
            measured == null || measuredMs == 0L -> usual.toDouble()
            else -> (BatteryTuning.USUAL_WEIGHT_MS * usual + measuredMs * measured) / (BatteryTuning.USUAL_WEIGHT_MS + measuredMs)
        }
        val minutesLeft = lastCounter.takeIf { it != NONE && pace > 0 }?.let { it / pace * MINUTES_PER_HOUR }
        return BatteryEstimate(
            isCharging = false, drawMicroamps = pace.toLong(), minutesLeft = minutesLeft, measuredMs = measuredMs,
            fullChargeMicroampHours = fullChargeOrNull(),
        )
    }

    /**
     * What this play measured, for its session row: the current's mean put right by the counter
     * ([counterScale]), or the counter's own mean on a device that reports no current; null when
     * under a minute was measured or neither moved.
     */
    fun measured(): SessionBattery? {
        if (measuredMs < BatteryTuning.FIRST_FIGURE_MS) return null
        val draw = when {
            currentMs > 0 && measuredIntegral > 0.0 -> measuredIntegral / currentMs * counterScale()
            spanMsTotal > 0 -> spanDropTotal * MILLIS_PER_HOUR / spanMsTotal
            else -> return null
        }
        return SessionBattery(
            measuredSeconds = measuredMs / MILLIS_PER_SECOND,
            chargeStartMicroampHours = firstCounter.takeIf { it != NONE },
            chargeEndMicroampHours = lastCounter.takeIf { it != NONE },
            drawMicroamps = draw.toLong(),
        )
    }

    /**
     * Discharging pace in microamperes: the counter's drop over its spans in the window, or, before
     * the counter has changed twice, the current's mean over the window scaled by [counterScale].
     */
    private fun measuredPace(): Double? {
        var drop = 0L
        var ms = 0L
        for (i in 0 until spanCount) {
            val at = (spanNext - 1 - i + MAX_SPANS) % MAX_SPANS
            // Newest first: those ending inside the window, and older ones until MIN_COUNTER_MS is covered.
            if (spanEndMs[at] < lastReadMs - BatteryTuning.PACE_WINDOW_MS && ms >= BatteryTuning.MIN_COUNTER_MS) break
            drop += spanDrop[at]
            ms += spanMs[at]
        }
        if (ms > 0 && drop > 0) return drop.toDouble() / ms * MILLIS_PER_HOUR
        val current = windowCurrent() ?: return null
        return current * counterScale()
    }

    private fun windowCurrent(): Double? {
        var integral = 0.0
        var ms = 0L
        for (i in 0 until WINDOW_MINUTES) {
            if (minuteMs[i] == 0L || minuteStartMs[i] < lastReadMs - BatteryTuning.PACE_WINDOW_MS) continue
            integral += minuteIntegral[i]
            ms += minuteMs[i]
        }
        return if (ms > 0) integral / ms else null
    }

    /**
     * How much the counter says was used for each unit of current read, over the parts of this
     * play's spans that have a current: 1 until the counter has changed twice while discharging. A
     * device whose current reads another sign or unit than its counter is put right by this too.
     */
    private fun counterScale(): Double = if (checkedDrop > 0.0 && checkedIntegral > 0.0) checkedDrop * MILLIS_PER_HOUR / checkedIntegral else 1.0

    private fun chargingEstimate(chargeTimeRemainingMs: Long): BatteryEstimate {
        val full = lastCounter.takeIf { it != NONE && level in 1..PERCENT }?.let { it * PERCENT.toDouble() / level }
        // Net into the battery: the counter's rise over this stretch once it moved, else the current.
        val rate = chargingRate()
        val fromAndroid = level < PERCENT && chargeTimeRemainingMs != BatteryReader.UNKNOWN && chargeTimeRemainingMs >= 0
        val minutesLeft = when {
            level >= PERCENT -> null
            fromAndroid -> chargeTimeRemainingMs.toDouble() / MILLIS_PER_MINUTE
            full != null && rate != null && rate > 0 -> (full - lastCounter) / rate * MINUTES_PER_HOUR
            else -> null
        }
        return BatteryEstimate(
            isCharging = true, drawMicroamps = -(rate ?: 0.0).toLong(), minutesLeft = minutesLeft, measuredMs = measuredMs,
            fullChargeMicroampHours = fullChargeOrNull(), isMinutesLeftExact = fromAndroid,
        )
    }

    private fun chargingRate(): Double? {
        if (lastChangeMs != NONE && stretchFirstChangeMs != NONE && lastChangeMs > stretchFirstChangeMs) {
            val rise = lastChangeCounter - stretchFirstChangeCounter
            if (rise > 0) return rise.toDouble() / (lastChangeMs - stretchFirstChangeMs) * MILLIS_PER_HOUR
        }
        return if (lastDraw != NONE && lastDraw < 0) -lastDraw.toDouble() else null
    }

    /**
     * The full charge (counter x 100 / level) from the read at the highest level seen: the level is
     * a whole percent, so the higher it is the smaller its rounding's share of the figure.
     */
    private fun noteFullCharge(microampHours: Long, level: Int) {
        if (microampHours == NONE || level !in 1..PERCENT || level < fullChargeLevel) return
        fullCharge = microampHours * PERCENT.toDouble() / level
        fullChargeLevel = level
    }

    private fun fullChargeOrNull(): Double? = fullCharge.takeIf { it > 0.0 }

    private fun startStretch(nowMs: Long, isCharging: Boolean) {
        stretchStartMs = nowMs
        charging = isCharging
        stretchCurrentIntegral = 0.0
        stretchCurrentMs = 0L
        lastChangeMs = NONE
        lastChangeCounter = NONE
        stretchFirstChangeMs = NONE
        stretchFirstChangeCounter = NONE
        lastCounter = NONE
    }

    /** A counter value at [nowMs]: a change starts or ends a span (time known to one read's step). */
    private fun noteCounter(nowMs: Long, value: Long) {
        if (firstCounter == NONE) firstCounter = value
        val previous = lastCounter
        lastCounter = value
        if (previous == NONE || previous == value) return
        if (lastChangeMs != NONE) {
            val drop = lastChangeCounter - value
            val integral = stretchCurrentIntegral - lastChangeIntegral
            val currentMs = stretchCurrentMs - lastChangeCurrentMs
            if (!charging && drop > 0) addSpan(nowMs, nowMs - lastChangeMs, drop, integral, currentMs)
        } else {
            stretchFirstChangeMs = nowMs
            stretchFirstChangeCounter = value
        }
        lastChangeMs = nowMs
        lastChangeCounter = value
        lastChangeIntegral = stretchCurrentIntegral
        lastChangeCurrentMs = stretchCurrentMs
    }

    /** A span of [ms] in which the counter dropped [drop]; the current's [integral] covers [currentMs] of it. */
    private fun addSpan(endMs: Long, ms: Long, drop: Long, integral: Double, currentMs: Long) {
        spanEndMs[spanNext] = endMs
        spanMs[spanNext] = ms
        spanDrop[spanNext] = drop
        spanNext = (spanNext + 1) % MAX_SPANS
        if (spanCount < MAX_SPANS) spanCount++
        spanDropTotal += drop
        spanMsTotal += ms
        // A read without a current leaves the steps on each side of it out of the integral: the
        // counter's drop is compared over the same share of the span, or the scale grows with every
        // missing read (with a third missing, the stored draw was 2.3 times too high).
        if (integral > 0.0 && currentMs > 0) {
            checkedDrop += drop.toDouble() * currentMs / ms
            checkedIntegral += integral
        }
    }

    private fun addToMinute(nowMs: Long, stepMs: Long, integral: Double) {
        if (minuteIndex < 0 || nowMs - minuteStartMs[minuteIndex] >= MILLIS_PER_MINUTE) {
            minuteIndex = (minuteIndex + 1) % WINDOW_MINUTES
            minuteStartMs[minuteIndex] = nowMs
            minuteIntegral[minuteIndex] = 0.0
            minuteMs[minuteIndex] = 0L
        }
        minuteIntegral[minuteIndex] += integral
        minuteMs[minuteIndex] += stepMs
    }

    /** Units from the first plausible reads. */
    private fun learnUnits(current: Long, counter: Long, isCharging: Boolean) {
        // Only discharging reads decide the unit (a full battery on the charger may trickle a few mA),
        // and not one alone: the median size of the first UNIT_READS, and the sign most of them had.
        if (currentUnit == 0L && hasCurrent(current) && !isCharging) {
            unitVotes[unitVoteCount++] = abs(current)
            if (current < 0) negativeVotes++
            if (unitVoteCount == UNIT_READS) {
                unitVotes.sort()
                currentUnit = if (unitVotes[UNIT_READS / 2] < MILLIAMPERE_DEVICE_BELOW) MICRO_PER_MILLI else 1L
                dischargeSign = if (negativeVotes > UNIT_READS / 2) -1 else 1
            }
        }
        if (counterUnit == 0L && counter != BatteryReader.UNKNOWN && counter > 0 && level in 1..PERCENT) {
            val full = counter * PERCENT / level
            counterUnit = if (full < MILLIAMPERE_HOURS_FULL_BELOW) MICRO_PER_MILLI else 1L
        }
    }

    /**
     * Microamperes, positive while discharging, negative into the battery while charging; NONE when
     * unknown, outside [MIN_DRAW]..[MAX_DRAW] (over [MAX_DRAW] while charging), or discharging before
     * the unit is decided.
     */
    private fun drawOf(current: Long, isCharging: Boolean): Long {
        // Over MAX_DRAW raw is over it in any unit (and multiplying it could overflow).
        if (!hasCurrent(current) || abs(current) > MAX_DRAW) return NONE
        if (!isCharging) {
            if (currentUnit == 0L) return NONE
            val draw = abs(current) * currentUnit
            return if (draw in MIN_DRAW..MAX_DRAW) draw else NONE
        }
        // Charging before any discharging read: as documented, microamperes positive into the battery.
        val micro = current * (if (currentUnit == 0L) 1L else currentUnit)
        if (abs(micro) > MAX_DRAW) return NONE
        // Documented positive into the battery; a device seen discharging positive is the other way.
        return if (dischargeSign > 0) micro else -micro
    }

    /** 0 and Int.MIN_VALUE are how some devices say "not supported"; [BatteryReader.UNKNOWN] is the documented one. */
    private fun hasCurrent(current: Long): Boolean = current != BatteryReader.UNKNOWN && current != 0L && current != Int.MIN_VALUE.toLong()

    private fun microampHoursOf(counter: Long): Long =
        if (counter == BatteryReader.UNKNOWN || counter <= 0 || counterUnit == 0L) NONE else counter * counterUnit

    private companion object {
        const val NONE = Long.MIN_VALUE
        const val PERCENT = 100
        const val MILLIS_PER_SECOND = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000.0
        const val MINUTES_PER_HOUR = 60.0
        const val MICRO_PER_MILLI = 1_000L

        /** A handheld in use never draws under 20 mA: a median read smaller than this number is in milliamperes. */
        const val MILLIAMPERE_DEVICE_BELOW = 20_000L

        /** How many discharging reads decide the current's unit and sign (their median size, most of their signs). */
        const val UNIT_READS = 5

        /** A handheld with its screen on draws between 20 mA and 20 A; a read outside is no read. */
        const val MIN_DRAW = 20_000L
        const val MAX_DRAW = 20_000_000L

        /** A full battery under 100,000 (0.1 Ah in uAh, 100 Ah in mAh) is counted in milliampere-hours. */
        const val MILLIAMPERE_HOURS_FULL_BELOW = 100_000L

        /** Spans kept: a window's worth at the Thor's ~22 s counter updates, with room. */
        const val MAX_SPANS = 64
        val WINDOW_MINUTES = (BatteryTuning.PACE_WINDOW_MS / MILLIS_PER_MINUTE).toInt() + 1
    }
}

/**
 * The battery now: [drawMicroamps] is the pace (discharging) or minus the rate into the battery
 * (charging), [minutesLeft] the minutes until empty or full, unrounded ([roundedMinutesLeft] for
 * people), null when unknown or full; [measuredMs] how long this play has been measured.
 */
data class BatteryEstimate(
    val isCharging: Boolean,
    val drawMicroamps: Long,
    val minutesLeft: Double?,
    val measuredMs: Long,
    /**
     * The battery's full charge in microampere-hours (counter x 100 / level, from the read at the
     * highest level), null without a counter or a level: what turns a draw into "% an hour".
     */
    val fullChargeMicroampHours: Double? = null,
    /** [minutesLeft] is Android's own time to full, exact to the minute ("1 h 03 to full"), not an estimate to round. */
    val isMinutesLeftExact: Boolean = false,
)
