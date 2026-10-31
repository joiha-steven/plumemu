package com.plumemu.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import java.text.NumberFormat
import java.util.Locale

/** The person's locale, read from the configuration so a language change recomposes. */
@Composable
@ReadOnlyComposable
internal fun currentLocale(): Locale = localeOf(LocalConfiguration.current)

/**
 * The first language of [configuration]: what [currentLocale] reads, and what the main screen's
 * A–Z follows (`AppGraph.locale`), so Settings › Library › Systems and the system column always
 * sort alike.
 */
internal fun localeOf(configuration: Configuration): Locale = ConfigurationCompat.getLocales(configuration)[0] ?: Locale.ROOT

/** Counts formatted for the person's locale ("1,284" in English, "1.284" in Vietnamese). */
@Composable
internal fun rememberCountFormat(): NumberFormat {
    val locale = currentLocale()
    return remember(locale) { NumberFormat.getIntegerInstance(locale) }
}

/**
 * A system row's count: up to 999 as it is, from 1,000 in whole thousands, rounded down
 * ("1k" for 1,284, "99k" for 99,999). Four digits ("1,000") were 41 dp wide: in the peek they started 1.6 from the
 * screen edge, in the open column they took the room a name such as "Game Boy Advance" needs. The exact count is in the
 * top bar once a system's games are open.
 */
internal fun NumberFormat.formatSystemCount(count: Int): String = if (count < THOUSAND) format(count) else format(count / THOUSAND) + THOUSANDS_SUFFIX

private const val THOUSAND = 1000
private const val THOUSANDS_SUFFIX = "k"
