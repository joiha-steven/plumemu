package com.plumemu.secondscreen

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.R
import com.plumemu.game.daysAgo
import com.plumemu.home.currentLocale
import com.plumemu.home.rememberCountFormat
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/** How long ago a game was played, as the covers' captions word it. */
internal sealed interface Ago {
    data object Now : Ago

    data class Minutes(val count: Int) : Ago

    data class Hours(val count: Int) : Ago

    data object Yesterday : Ago

    /** Two to six days ago: the day's name, or [days] where the name would be taken for the next one. */
    data class Days(val days: Int, val dayOfWeek: Int) : Ago

    data object LastWeek : Ago

    /** Two weeks ago or more: the date. */
    data class On(val at: Long) : Ago
}

/** [at] seen from [now], by calendar days in [zone] as the game's Info panel counts them ([daysAgo]). */
internal fun agoOf(
    at: Long,
    now: Long,
    zone: TimeZone = TimeZone.getDefault(),
): Ago {
    val days = daysAgo(at, now, zone)
    val passed = (now - at).coerceAtLeast(0)
    return when {
        days == 0 && passed < TimeUnit.MINUTES.toMillis(1) -> Ago.Now
        days == 0 && passed < TimeUnit.HOURS.toMillis(1) -> Ago.Minutes(TimeUnit.MILLISECONDS.toMinutes(passed).toInt())
        days == 0 -> Ago.Hours(TimeUnit.MILLISECONDS.toHours(passed).toInt().coerceAtLeast(1))
        days == 1 -> Ago.Yesterday
        days < DAYS_IN_WEEK -> Ago.Days(days, Calendar.getInstance(zone).apply { timeInMillis = at }.get(Calendar.DAY_OF_WEEK))
        days < 2 * DAYS_IN_WEEK -> Ago.LastWeek
        else -> Ago.On(at)
    }
}

/**
 * [ago] in words: [isShort] ("2 h ago", "Sunday", "Oct 12", under a cover on the system card) or whole ("2 hours ago", "3 days ago",
 * on Recent's). Whole sentences from resources, so a translation orders its own words.
 */
@Composable
internal fun agoText(
    ago: Ago,
    isShort: Boolean,
): String {
    val numbers = rememberCountFormat()
    val locale = currentLocale()
    return when (ago) {
        Ago.Now -> stringResource(R.string.second_screen_when_now)
        is Ago.Minutes -> pluralStringResource(R.plurals.second_screen_when_minutes, ago.count, numbers.format(ago.count))
        is Ago.Hours ->
            if (isShort) {
                stringResource(R.string.second_screen_when_hours_short, numbers.format(ago.count))
            } else {
                pluralStringResource(R.plurals.second_screen_when_hours, ago.count, numbers.format(ago.count))
            }
        Ago.Yesterday -> stringResource(R.string.second_screen_ago_yesterday)
        is Ago.Days ->
            if (isShort) {
                remember(locale, ago.dayOfWeek) { DateFormatSymbols(locale).weekdays[ago.dayOfWeek] }
            } else {
                pluralStringResource(R.plurals.second_screen_ago_days, ago.days, numbers.format(ago.days))
            }
        Ago.LastWeek -> stringResource(R.string.second_screen_when_last_week)
        is Ago.On -> remember(locale, ago.at) { SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(Date(ago.at)) }
    }
}

private const val DAYS_IN_WEEK = 7
