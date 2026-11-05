package com.plumemu.launcher.play

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager

/**
 * How long the screen has been off in this process, on the awake clock ([PlayClock.uptimeMs]).
 * A play subtracts what grew between its start and its return: a handheld folded or locked with
 * the game in front is not play, even when something keeps it awake. In memory only: a new process
 * knows nothing of an earlier one's screen, and the awake clock alone then leaves out deep sleep.
 */
internal class ScreenOffTime(private val clock: PlayClock) {
    private var offSinceMs: Long? = null
    private var totalMs = 0L

    @Synchronized
    fun screenChanged(on: Boolean) {
        val since = offSinceMs
        if (on && since != null) {
            totalMs += (clock.uptimeMs() - since).coerceAtLeast(0)
            offSinceMs = null
        } else if (!on && since == null) {
            offSinceMs = clock.uptimeMs()
        }
    }

    /** The screen-off time so far, the stretch still going on included. */
    @Synchronized
    fun totalMs(): Long = totalMs + (offSinceMs?.let { (clock.uptimeMs() - it).coerceAtLeast(0) } ?: 0)

    /** Listens to the screen broadcasts for the life of the process; call once, with the application context. */
    fun watch(app: Context) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) = screenChanged(intent.action == Intent.ACTION_SCREEN_ON)
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        // A system broadcast: exported, or Android 6–12 refuse it.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            app.registerReceiver(receiver, filter)
        }
        // Asked after registering, so a change between the two is not missed.
        val interactive = (app.getSystemService(Context.POWER_SERVICE) as PowerManager?)?.isInteractive ?: true
        if (!interactive) screenChanged(false)
    }
}
