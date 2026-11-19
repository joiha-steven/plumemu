package com.plumemu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.content.HomeState
import com.plumemu.telemetry.ScreenId
import com.plumemu.usage.LocalScreenTracker
import com.plumemu.usage.SETTLE_MS
import kotlinx.coroutines.delay

// Helpers of MainScreen's main screen kept composed, hidden and deaf, under Settings.

/** What the main screen is composed from and Settings could change: held as it was when Settings opened ([HomeWithSettings]). */
internal class HomeInputs(
    val state: HomeState.Ready,
    val settings: PlumSettings,
    val isSecondScreenInUse: Boolean,
)

/** [live] while Settings is closed; the inputs of the last composition before it opened while it shows. */
@Composable
internal fun heldHomeInputs(
    live: HomeInputs,
    showsSettings: Boolean,
): HomeInputs {
    val held = remember { arrayOfNulls<HomeInputs>(1) }
    if (!showsSettings || held[0] == null) held[0] = live
    return if (showsSettings) held[0] ?: live else live
}

/**
 * The tracker the main screen reports its screen to. It reports once it rests; kept composed under Settings it would not
 * report again on the close, and a report made while Settings shows would name the wrong screen. So reports are held back
 * there ([homeLeaving] is raised) and the last one is told once the close has settled. Also lowers [homeLeaving] on the close.
 */
@Composable
internal fun rememberHomeScreenTracker(
    showsSettings: Boolean,
    homeLeaving: MutableState<Boolean>,
    hasOpenedSettings: BooleanArray,
): (ScreenId) -> Unit {
    val tracker by rememberUpdatedState(LocalScreenTracker.current)
    val lastHomeScreen = remember { arrayOfNulls<ScreenId>(1) }
    // Raised when the main screen itself reports after the close (its screen changed under Settings): the old one must not be told then.
    val reportedSinceClose = remember { BooleanArray(1) }
    LaunchedEffect(showsSettings) {
        if (showsSettings) return@LaunchedEffect
        homeLeaving.value = false
        reportedSinceClose[0] = false
        if (hasOpenedSettings[0]) {
            delay(SETTLE_MS)
            // The main screen's own report is due at the same moment; one frame later it has been made.
            withFrameNanos { }
            if (!reportedSinceClose[0]) lastHomeScreen[0]?.let(tracker)
        }
    }
    return remember {
        { screen: ScreenId ->
            lastHomeScreen[0] = screen
            if (!homeLeaving.value) {
                reportedSinceClose[0] = true
                tracker(screen)
            }
        }
    }
}

/** How many main screens are composed (never more than one, also across Settings' openings); for tests. */
internal object HomeTreeProbe {
    var live = 0
}

/** Counts what the main screen's root does per frame; off in the app, switched on by a test ([homeProbed]). */
internal object HomeFrameProbe {
    var isOn = false
    var measures = 0
    var places = 0
    var draws = 0

    fun reset() {
        measures = 0
        places = 0
        draws = 0
    }
}

internal fun Modifier.homeProbed(): Modifier =
    if (!HomeFrameProbe.isOn) {
        this
    } else {
        this
            .layout { measurable, constraints ->
                HomeFrameProbe.measures++
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    HomeFrameProbe.places++
                    placeable.place(0, 0)
                }
            }.drawWithContent {
                HomeFrameProbe.draws++
                drawContent()
            }
    }
