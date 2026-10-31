package com.plumemu.launcher.play.battery

/**
 * The numbers of the battery measurement. Tuned on a trace of an AYN Thor
 * (the trace file in `src/test/resources/battery`) and on made-up plays
 * (`BatteryEstimatorTest`); change them there first.
 */
object BatteryTuning {
    /** One read every 10 s while the in-game card is up ... */
    const val READ_EVERY_MS = 10_000L

    /** ... and every second for the first 2 minutes of a stretch, so the counter's first changes are timed to the second. */
    const val START_READ_EVERY_MS = 1_000L
    const val START_MS = 2 * 60_000L

    /** The counter's pace looks back at least this far, past the window if it must (right after a sleep). */
    const val MIN_COUNTER_MS = 2 * 60_000L

    /** The level is read once a minute (it only feeds the counter's unit check and the charging estimate). */
    const val LEVEL_EVERY_MS = 60_000L

    /** The pace is the counter's drop (or the current's mean) over the last 10 minutes. */
    const val PACE_WINDOW_MS = 10 * 60_000L

    /** The game's usual pace counts as 5 minutes of measurement. */
    const val USUAL_WEIGHT_MS = 5 * 60_000L

    /** A game with no usual pace shows nothing before its first measured minute. */
    const val FIRST_FIGURE_MS = 60_000L

    /** Reads further apart than 2 minutes (sleep, screen off) are not measured across. */
    const val GAP_MS = 2 * 60_000L
}
