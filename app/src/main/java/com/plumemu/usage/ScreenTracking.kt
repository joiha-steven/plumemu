package com.plumemu.usage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import com.plumemu.telemetry.ScreenId
import kotlinx.coroutines.delay

/** Told each screen the person settles on (`screen_opened`); `MainActivity` provides `UsageSession.onScreen`. */
internal val LocalScreenTracker = staticCompositionLocalOf<(ScreenId) -> Unit> { {} }

/**
 * Says [screen] is shown once it has stayed for [SETTLE_MS]: walking through the systems or
 * Settings' sections with the shoulder keys passes many screens a second, and only the one the
 * person stops on is a screen opened (docs/conventions/telemetry.md: never per focus move). Null
 * says nothing. Costs one effect per change of [screen], nothing per frame.
 */
@Composable
internal fun TrackScreen(screen: ScreenId?) {
    val tracker by rememberUpdatedState(LocalScreenTracker.current)
    LaunchedEffect(screen) {
        if (screen == null) return@LaunchedEffect
        delay(SETTLE_MS)
        tracker(screen)
    }
}

/** Told each error message shown (`error_shown`): the screen and the app's code for it, never its words. */
internal val LocalErrorTracker = staticCompositionLocalOf<(ScreenId, Enum<*>) -> Unit> { { _, _ -> } }

/** Says the error [code] is shown on [where], once each time it appears; null says nothing. */
@Composable
internal fun TrackError(
    where: ScreenId,
    code: Enum<*>?,
) {
    val tracker by rememberUpdatedState(LocalErrorTracker.current)
    LaunchedEffect(code) {
        if (code != null) tracker(where, code)
    }
}

/** How long a screen must stay shown to count as opened. */
internal const val SETTLE_MS = 1_000L
