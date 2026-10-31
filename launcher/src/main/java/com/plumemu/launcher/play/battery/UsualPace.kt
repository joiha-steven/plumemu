package com.plumemu.launcher.play.battery

/**
 * What the picking line says of a game's usual pace while
 * picking, from the stored usual draw (`PlayDao.usualDraw`, microamperes). Read on the loader's
 * thread beside the game's other facts, not per frame: two quick calls into `BatteryManager` (counter,
 * level) each time.
 *
 * - **Time on this charge** = the charge counter now over the usual draw. No level in it: the counter
 *   is what the level is made of, in far finer steps (1/10,000 of a full battery on the Thor).
 * - **Percent an hour** = the usual draw over the full charge. The full charge is the counter x 100 /
 *   level, as the estimator works it out, and the level is a whole number: one step is up to 1/level
 *   of it (1/21 at 21%, 1/100 at 100%). So it is worked out again whenever a higher level than before
 *   is seen, and converges as the battery is seen fuller. From a level under 20% alone (after a reboot
 *   at 5%: up to a fifth off) there is no percent until a higher level comes along; the time still
 *   shows.
 *
 * The counter's unit: microampere-hours, or milliampere-hours when the full charge it gives comes out
 * under 100,000 (as the estimator decides); decided at the first read with a level, and kept.
 */
class UsualPace(private val reader: BatteryReader) {
    /** The figures for one game: either may be null, never both. */
    data class Figures(
        /** How long the charge there is now lasts at the usual draw, in minutes; null without a counter. */
        val minutesOnCharge: Double?,
        /** The usual draw in percent of a full battery per hour; null until a level of 20% or more was seen. */
        val percentPerHour: Double?,
    )

    private val lock = Any()
    private var isMilliampereHours: Boolean? = null
    private var fullMicroampHours = NONE
    private var fullFromLevel = 0

    /** The figures for a game whose usual draw is [usualDrawMicroamps]; null for a game never measured, or when nothing is known of the battery. */
    fun of(usualDrawMicroamps: Long?): Figures? {
        val draw = usualDrawMicroamps?.takeIf { it > 0 } ?: return null
        val counter = reader.chargeCounter().takeIf { it != BatteryReader.UNKNOWN && it > 0 }
        val level = reader.level().takeIf { it in 1..PERCENT_INT }
        val left: Double?
        val full: Double?
        synchronized(lock) {
            if (counter != null && level != null && isMilliampereHours == null) {
                isMilliampereHours = counter * PERCENT / level < MILLIAMPERE_HOURS_FULL_BELOW
            }
            left = counter?.let { raw -> isMilliampereHours?.let { isMilli -> if (isMilli) raw * MICRO_PER_MILLI else raw.toDouble() } }
            if (left != null && level != null && level >= GOOD_LEVEL && level > fullFromLevel) {
                fullMicroampHours = left * PERCENT / level
                fullFromLevel = level
            }
            full = fullMicroampHours.takeIf { it != NONE }
        }
        val minutes = left?.let { it / draw * MINUTES_PER_HOUR }
        val percent = full?.let { draw * PERCENT / it }
        return if (minutes == null && percent == null) null else Figures(minutes, percent)
    }

    private companion object {
        const val NONE = -1.0
        const val PERCENT = 100.0
        const val PERCENT_INT = 100
        const val MINUTES_PER_HOUR = 60.0

        /** Under this level one whole-number step is more than a twentieth of it: no percent from it alone. */
        const val GOOD_LEVEL = 20
        const val MICRO_PER_MILLI = 1_000.0

        /** As the estimator's: a full battery under 0.1 Ah in microampere-hours is a count in milliampere-hours. */
        const val MILLIAMPERE_HOURS_FULL_BELOW = 100_000.0
    }
}
