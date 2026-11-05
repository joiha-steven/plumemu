package com.plumemu.secondscreen

import androidx.compose.runtime.Immutable
import com.plumemu.coredata.db.PlaySessionEntity
import com.plumemu.graph.weekStartOf
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * A game's play over the last [WEEKS] weeks, for the game card's play tile: seconds per week (oldest first, the last is the
 * week [now] falls in), and the sessions that started inside those weeks.
 */
@Immutable
internal data class PlayWeeks(
    val weekSeconds: List<Long>,
    val sessions: Int,
    val totalSeconds: Long,
) {
    /** "About N min a session": the whole minutes of the average session (rounded down, as the board's 15 for 15.6), at least 1; null with no session. */
    val averageMinutes: Int?
        get() = if (sessions <= 0) null else maxOf(1, (totalSeconds / sessions / SECONDS_PER_MINUTE).toInt())

    companion object {
        const val WEEKS = 8
        private const val SECONDS_PER_MINUTE = 60L
    }
}

/**
 * [sessions] summed per week over the last [PlayWeeks.WEEKS] weeks ending with the week of [now], weeks as Statistics counts them
 * ([weekStartOf]: the locale's first day, local midnight). A session counts in the week it started in; one before the first week
 * or after the current one is left out.
 */
internal fun playWeeksOf(
    sessions: List<PlaySessionEntity>,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
    locale: Locale = Locale.getDefault(),
): PlayWeeks {
    val thisWeek = weekStartOf(now, zone, locale)
    // Calendar steps, not 7 * 24 h: a week with a clock change is an hour longer or shorter.
    val starts =
        List(PlayWeeks.WEEKS + 1) { index ->
            Calendar.getInstance(zone, locale).apply {
                timeInMillis = thisWeek
                add(Calendar.WEEK_OF_YEAR, index - (PlayWeeks.WEEKS - 1))
            }.timeInMillis
        }
    val seconds = LongArray(PlayWeeks.WEEKS)
    var count = 0
    for (session in sessions) {
        val week = starts.indexOfLast { it <= session.startedAt }
        if (week !in 0 until PlayWeeks.WEEKS || session.startedAt >= starts.last()) continue
        seconds[week] += session.seconds
        count++
    }
    return PlayWeeks(seconds.toList(), count, seconds.sum())
}
