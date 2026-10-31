package com.plumemu.scraper.gate

import java.util.Calendar
import java.util.TimeZone

/**
 * ScreenScraper resets daily quotas at midnight in Paris.
 *
 * Calendar and TimeZone, not java.time: java.time is API 26 and the app is minSdk 23 without
 * library desugaring (lint NewApi fails the build).
 */
internal object ParisTime {
    private val PARIS: TimeZone = TimeZone.getTimeZone("Europe/Paris")

    /** The first Paris midnight strictly after [nowMillis]; right on a DST night too. */
    fun nextMidnight(nowMillis: Long): Long {
        val calendar = Calendar.getInstance(PARIS)
        calendar.timeInMillis = nowMillis
        // Paris changes its clocks at 02:00 or 03:00, so midnight always exists exactly once.
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
