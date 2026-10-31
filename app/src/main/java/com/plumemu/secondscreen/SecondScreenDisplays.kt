package com.plumemu.secondscreen

import android.util.Log
import android.view.Display
import androidx.compose.runtime.Immutable
import com.plumemu.coredata.settings.PlumSettings
import com.plumemu.coredata.settings.SecondScreenSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * Which display, if any, is the device's second screen.
 * The decision is a pure function over [DisplayFacts]; [SecondScreenDisplays] keeps it current from
 * a [DisplaySource] (Android's in `AndroidDisplaySource.kt`, a fake in tests).
 */

/** What Plumemu reads of one display. [type] is the hidden `Display.getType()`, null when it cannot be read. */
@Immutable
internal data class DisplayFacts(
    val id: Int,
    val name: String,
    val isPresentation: Boolean,
    val type: Int?,
)

/** How sure Plumemu is that a display is part of the device: decides the switch until the person does. */
internal enum class ScreenKind {
    /** Android says internal (type 1): on by default. */
    BuiltIn,

    /** The overlay test display or a type that cannot be read: off by default, asked about in setup. */
    Unsure,
}

/** The display chosen as the second screen. */
@Immutable
internal data class SecondScreen(
    val displayId: Int,
    val name: String,
    val kind: ScreenKind,
)

/**
 * The second screen among [displays], or null. The default display and displays that are not
 * presentation displays are skipped; an external, cast or virtual one (types 2, 3, 5: HDMI, Wi-Fi
 * display, a screen recorder) is never used. Built-in first, then Unsure; among equals the lowest id.
 */
internal fun chooseSecondScreen(displays: List<DisplayFacts>): SecondScreen? =
    displays
        .asSequence()
        .filter { it.id != Display.DEFAULT_DISPLAY && it.isPresentation }
        .mapNotNull { facts -> kindOf(facts.type)?.let { SecondScreen(facts.id, facts.name, it) } }
        .minWithOrNull(compareBy<SecondScreen> { it.kind.ordinal }.thenBy { it.displayId })

/** The kind of a display of hidden type [type]; null for one that is never used. */
internal fun kindOf(type: Int?): ScreenKind? =
    when (type) {
        TYPE_INTERNAL -> ScreenKind.BuiltIn
        TYPE_EXTERNAL, TYPE_WIFI, TYPE_VIRTUAL -> null
        // Android's developer-option test display: usable, so the emulator can test the feature.
        TYPE_OVERLAY -> ScreenKind.Unsure
        // A type this version does not know, or none read (the hidden call is blocked).
        else -> ScreenKind.Unsure
    }

/**
 * Whether the second screen is in use: the person's choice once made, else on for a built-in
 * screen and off when unsure. Always off without a screen. The second screen's window follows this.
 */
internal fun isSecondScreenOn(
    settings: SecondScreenSettings,
    screen: SecondScreen?,
): Boolean = screen != null && (settings.enabled ?: (screen.kind == ScreenKind.BuiltIn))

/**
 * The band over a system's games list is off by default where a second screen is in use, because the
 * system's card there shows the same facts. [settings] with that choice written once:
 * only while the person never set the switch ([com.plumemu.coredata.settings.HomeScreenSettings.showSystemInfo]
 * null) and the second screen is on; a stored value, either way, is never touched. Written, not
 * derived, so the choice stays if the display is missing at a later start; with the second screen
 * off the band stays on (nothing else shows the facts).
 */
internal fun withSystemInfoDecided(
    settings: PlumSettings,
    screen: SecondScreen?,
): PlumSettings =
    if (settings.homeScreen.showSystemInfo == null && isSecondScreenOn(settings.secondScreen, screen)) {
        settings.copy(homeScreen = settings.homeScreen.copy(showSystemInfo = false))
    } else {
        settings
    }

/**
 * Follows the settings and the second screen in [scope] for the Activity's life, and each time
 * [withSystemInfoDecided] has something to change (the first start, and again after Reset all
 * settings or a backup without the value) asks [write] to apply it. The transform re-checks against
 * the stored value, so a value the person sets while the write is pending wins; after the write the
 * value is no longer null, so nothing loops.
 */
internal fun decideSystemInfoOnce(
    scope: CoroutineScope,
    screens: StateFlow<SecondScreen?>,
    settings: StateFlow<PlumSettings?>,
    write: ((PlumSettings) -> PlumSettings) -> Unit,
) {
    scope.launch {
        combine(screens, settings.filterNotNull(), ::Pair)
            .filter { (screen, now) -> withSystemInfoDecided(now, screen) !== now }
            .collect { write { stored -> withSystemInfoDecided(stored, screens.value) } }
    }
}

/** Where the displays come from: Android's `DisplayManager`, or a fake in tests. Called on the main thread. */
internal interface DisplaySource {
    /** The presentation displays Android lists now. */
    fun presentationDisplays(): List<DisplayFacts>

    /**
     * Calls [onChange] (on the main thread) whenever a display is added, removed or changed. False
     * when the listener could not be registered (logged): then there is no second screen.
     */
    fun register(onChange: () -> Unit): Boolean

    fun unregister()
}

/**
 * The current second screen, kept up to date while started. One per process (`AppGraph.secondScreens`);
 * `MainActivity` starts it after its first content frame and stops it in `onDestroy`. Starts are counted, so a
 * recreated Activity whose new instance starts before the old one stops keeps the listener.
 * Stopping keeps the last answer: nobody reads it then, and a recreated Activity must not see the
 * screen "disappear" for a moment (setup would skip its question).
 */
internal class SecondScreenDisplays(
    private val source: DisplaySource,
    private val info: (String) -> Unit = { Log.i(TAG, it) },
) {
    private val chosen = MutableStateFlow<SecondScreen?>(null)
    private var starts = 0
    private var hasLogged = false
    private var isListening = false

    /** The second screen now; null when there is none (or before the first start). */
    val current: StateFlow<SecondScreen?> = chosen.asStateFlow()

    fun start() {
        starts++
        if (starts == 1) isListening = source.register(::recompute)
        if (isListening) recompute() else noScreenWithoutListener()
    }

    /** [start], and the home band's off-with-a-second-screen choice ([decideSystemInfoOnce]). */
    fun startAndDecideSystemInfo(
        scope: CoroutineScope,
        settings: StateFlow<PlumSettings?>,
        write: ((PlumSettings) -> PlumSettings) -> Unit,
    ) {
        start()
        decideSystemInfoOnce(scope, current, settings, write)
    }

    fun stop() {
        if (starts == 0) return
        starts--
        if (starts == 0 && isListening) source.unregister()
        if (starts == 0) isListening = false
    }

    /** Unwatched, a screen could vanish unnoticed and keep a window on it: none is safer. */
    private fun noScreenWithoutListener() {
        val previous = chosen.value
        chosen.value = null
        if (previous == null && hasLogged) return
        hasLogged = true
        info("Second screen: none (no display listener)")
    }

    /** Reads the displays again and logs the choice when it changed (ids, names, type and kind; nothing personal). */
    fun recompute() {
        val displays = source.presentationDisplays()
        val next = chooseSecondScreen(displays)
        val previous = chosen.value
        chosen.value = next
        if (next == previous && hasLogged) return
        hasLogged = true
        if (next == null) {
            info("Second screen: none (presentation displays: ${displays.map { it.id }})")
        } else {
            val type = displays.firstOrNull { it.id == next.displayId }?.type
            info("Second screen: display ${next.displayId} \"${next.name}\", type ${type ?: "unknown"}, kind ${next.kind}")
        }
    }

    private companion object {
        const val TAG = "PlumSecondScreen"
    }
}

/** `Display.TYPE_*`, hidden in the SDK; the values are fixed in AOSP since API 17. */
internal const val TYPE_INTERNAL = 1
internal const val TYPE_EXTERNAL = 2
internal const val TYPE_WIFI = 3
internal const val TYPE_OVERLAY = 4
internal const val TYPE_VIRTUAL = 5
