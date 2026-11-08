package com.plumemu.launcher

import android.content.Context
import android.content.Intent

/**
 * How a game or app starts when the library is on screen: the activity that shows it sets [host], which starts the
 * Intent itself (Android applies a start's animation only when an activity asks, not the application) after its own
 * launch motion. [host] may be called on any thread and must throw what `startActivity` throws. Without a host
 * (Settings or setup in front) the application starts it plainly.
 */
object LaunchAnimation {
    @Volatile
    var host: ((Intent) -> Unit)? = null

    /** Counts the starts made, so the library knows a return to the front came from one ([starts] changed). */
    @Volatile
    var starts: Int = 0
        private set

    internal fun start(
        app: Context,
        intent: Intent,
    ) {
        val from = host
        if (from != null) from(intent) else app.startActivity(intent)
        starts++
    }
}
