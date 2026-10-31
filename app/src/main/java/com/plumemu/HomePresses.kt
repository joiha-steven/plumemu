package com.plumemu

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The device's Home button pressed while Plumemu is already in front. [MainActivity] sends one
 * [press] per such press; the main screen collects [presses] and returns to its landing point
 * (`HomeLanding.kt`), closing Settings, Search, the game panel and whatever else is open.
 * The library error screen collects them too, to close the Settings opened over it. Nothing is kept: a press
 * with nobody collecting (setup) is dropped.
 */
internal class HomePresses {
    private val sent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val presses: Flow<Unit> = sent

    fun press() {
        sent.tryEmit(Unit)
    }
}

/**
 * Whether [intent] delivered to `onNewIntent` is the Home button pressed while Plumemu is in front
 * (Launcher3's rule). The lifecycle cannot tell: Android never delivers a new intent to a RESUMED
 * activity (it pauses first), and a return from a game is STARTED too on Android 10+. What differs
 * is the window: when Home is pressed over Plumemu its window still has focus, and the intent
 * carries no `FLAG_ACTIVITY_BROUGHT_TO_FRONT`; coming back from a game or another app the window
 * has no focus yet and Android brought the task to the front. The second screen's window is
 * `FLAG_NOT_FOCUSABLE` (`SecondScreenHost`), so it never takes the focus.
 */
internal fun isHomePressInFront(
    intent: Intent?,
    hasWindowFocus: Boolean,
): Boolean =
    intent?.hasCategory(Intent.CATEGORY_HOME) == true &&
        hasWindowFocus &&
        (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
