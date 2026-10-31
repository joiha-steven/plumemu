package com.plumemu.secondscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.plumemu.launcher.play.battery.BatteryReads
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reads the battery as often as [BatteryReads.read] asks (every second at first, then every 10 s)
 * while this is composed, its window is started and the screen is on, and not at all otherwise
 * The second screen composes it only beside the
 * in-game clock card with Show battery on. The wait is a delay on the window's own main-thread
 * dispatcher: no thread, timer, alarm or wake lock of its own, and it does not run while the
 * device sleeps.
 *
 * The window stays up with the screen off, and the CPU may stay awake then (an emulator holding a
 * wake lock for its audio), so the screen is followed too: the device's (the system's screen off
 * and on broadcasts) and the second display's own (its display listener). Both are events; nothing
 * asks again and again. When either goes off the loop ends and the meter is told
 * ([BatteryReads.stopReading]), so the time off is not measured as play; on again, the reads start
 * a new stretch.
 */
@Composable
internal fun MeasureBatteryWhileShown(
    reads: BatteryReads,
    /** After each read, on the same thread: the battery part takes its figures then, no wait of its own. */
    onRead: () -> Unit = {},
    /** The screen as followed here, on each change: the battery part takes nothing while it is off. */
    onScreen: (on: Boolean) -> Unit = {},
) {
    if (reads === BatteryReads.None) return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current
    val display = LocalView.current.display
    val afterRead by rememberUpdatedState(onRead)
    val screenChanged by rememberUpdatedState(onScreen)
    LaunchedEffect(reads, lifecycle, context, display) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            screenIsOn(context, display).collectLatest { on ->
                screenChanged(on)
                if (!on) {
                    reads.stopReading()
                    return@collectLatest
                }
                while (true) {
                    val wait = reads.read()
                    afterRead()
                    delay(wait)
                }
            }
        }
    }
}

/**
 * True while the device is interactive and [display] (the window's, null when not known) is on.
 * Registers for the screen broadcasts and [display]'s changes first, then asks `PowerManager` once:
 * a screen off between the two is not missed.
 */
private fun screenIsOn(
    context: Context,
    display: Display?,
): Flow<Boolean> =
    callbackFlow {
        // Asked below, once both are registered; a broadcast is delivered on this same main thread, after.
        var interactive = true

        fun isOn() = interactive && (display == null || display.state !in DISPLAY_OFF_STATES)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    interactive = intent.action == Intent.ACTION_SCREEN_ON
                    trySend(isOn())
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
        // A system broadcast: exported, or Android 6–12 refuse it.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        val displays = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager?
        val listener =
            object : DisplayManager.DisplayListener {
                override fun onDisplayChanged(displayId: Int) {
                    if (displayId == display?.displayId) trySend(isOn())
                }

                override fun onDisplayAdded(displayId: Int) = Unit

                override fun onDisplayRemoved(displayId: Int) = Unit
            }
        if (display != null) displays?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        interactive = (context.getSystemService(Context.POWER_SERVICE) as PowerManager?)?.isInteractive ?: true
        trySend(isOn())
        awaitClose {
            context.unregisterReceiver(receiver)
            displays?.unregisterDisplayListener(listener)
        }
    }.distinctUntilChanged()

/** The display shows nothing of the window: off, or dozing (the system's always-on picture). */
private val DISPLAY_OFF_STATES = setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
