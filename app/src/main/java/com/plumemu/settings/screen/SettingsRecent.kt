package com.plumemu.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.plumemu.home.rememberCountFormat

/**
 * Settings › Library › Clear Recent…: what Settings asks of the library once the person confirmed.
 * Play time and statistics are not touched; only the list empties.
 */
internal interface SettingsRecentActions {
    /** Empties Recent; [onDone] (main thread) gets how many games left it, or null when it could not be written. */
    fun clearRecent(onDone: (Int?) -> Unit)

    object None : SettingsRecentActions {
        override fun clearRecent(onDone: (Int?) -> Unit) = onDone(null)
    }
}

/** A notice's text: a count notice ([SettingsNotice.plural]) is a plural of [count] games, any other a plain line. */
@Composable
internal fun SettingsNotice.lineText(count: Int): String =
    if (plural != 0) pluralStringResource(plural, count, rememberCountFormat().format(count)) else stringResource(text)
