package com.plumemu.secondscreen

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.view.Display

/**
 * The quick tries of a refused window are used up: nothing else would try again while the same
 * state stands (the bottom screen kept Android's launcher). One more try on each display change (a
 * display changing state is when Android refuses) and each [periodMs] on a timer, which does not
 * run while the device sleeps; none while the display is off. The tries end when [isWanted] says
 * no, and the host ends them when the window is shown, the wanted state changes or it stops. Main thread only.
 */
internal class LateShowTries(
    private val context: Context,
    private val handler: Handler,
    private val warn: (String) -> Unit,
    private val periodMs: Long,
    private val isWanted: (displayId: Int) -> Boolean,
    private val tryNow: (displayId: Int) -> Unit,
) {
    private var displayId: Int? = null
    private var listener: DisplayManager.DisplayListener? = null
    private var timer: Runnable? = null

    val isActive: Boolean get() = displayId != null

    fun start(id: Int) {
        if (displayId == id) return
        stop()
        displayId = id
        val manager = context.getSystemService(DisplayManager::class.java)
        val next =
            object : DisplayManager.DisplayListener {
                override fun onDisplayAdded(displayId: Int) = attempt(id, displayId)

                override fun onDisplayRemoved(displayId: Int) = Unit

                override fun onDisplayChanged(displayId: Int) = attempt(id, displayId)
            }
        try {
            manager?.registerDisplayListener(next, handler)
            listener = next
        } catch (e: RuntimeException) {
            warn("Second screen window: late tries have no display listener (${e.javaClass.simpleName}); the timer only")
        }
        val tick =
            object : Runnable {
                override fun run() {
                    attempt(id, id)
                    if (displayId == id) handler.postDelayed(this, periodMs)
                }
            }
        timer = tick
        handler.postDelayed(tick, periodMs)
    }

    fun stop() {
        displayId = null
        timer?.let(handler::removeCallbacks)
        timer = null
        val old = listener ?: return
        listener = null
        try {
            context.getSystemService(DisplayManager::class.java)?.unregisterDisplayListener(old)
        } catch (e: RuntimeException) {
            warn("Second screen window: late tries' display listener could not be removed (${e.javaClass.simpleName})")
        }
    }

    private fun attempt(
        wantedId: Int,
        changedId: Int,
    ) {
        if (displayId != wantedId || changedId != wantedId) return
        if (!isWanted(wantedId)) return stop()
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(wantedId)
        // A screen that is off wakes nothing: the display change that turns it on tries again.
        if (display == null || display.state == Display.STATE_OFF) return
        tryNow(wantedId)
    }
}
