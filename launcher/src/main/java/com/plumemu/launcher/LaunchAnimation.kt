package com.plumemu.launcher

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle

/**
 * How a game or app starts when the library is on screen: the activity that shows it sets [host], which starts the
 * Intent itself (Android applies a start's animation only when an activity asks, not the application) after its own
 * launch motion. [host] may be called on any thread and must throw what `startActivity` throws. Without a host
 * (Settings or setup in front) the application starts it plainly.
 *
 * A start may name a display (an app opened on the second screen, API 26+; below, the display is dropped and the
 * start is a plain one). The host then gets the id and starts with [displayOptions] and no motion of its own:
 * Plumemu stays in front on the first screen.
 */
object LaunchAnimation {
    @Volatile
    var host: ((Intent, Int?) -> Unit)? = null

    /**
     * Counts the starts that leave the library, so it knows a return to the front came from one ([starts] changed).
     * A start on another display is not counted: Plumemu never left the first screen, and its next start would play
     * the return motion for nothing.
     */
    @Volatile
    var starts: Int = 0
        private set

    /** The options that put a start on display [displayId]: no animation of its own. Null below API 26. */
    fun displayOptions(displayId: Int): Bundle? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle() else null

    internal fun start(
        app: Context,
        intent: Intent,
        displayId: Int? = null,
    ) {
        val display = displayId?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.O }
        val from = host
        if (from != null) from(intent, display) else app.startActivity(intent, display?.let(::displayOptions))
        if (display == null) starts++
    }
}
