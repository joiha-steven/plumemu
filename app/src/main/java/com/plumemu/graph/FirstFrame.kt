package com.plumemu.graph

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver

/**
 * Runs [action] once, on the main thread, right after this view's window draws its first frame:
 * the draw listener fires during the draw, and the action is posted to run after it. Register it
 * when the view is attached (`Activity.onAttachedToWindow`), so the listener sits on the window's
 * real tree observer.
 */
internal fun View.doAfterFirstDraw(action: () -> Unit) {
    val main = Handler(Looper.getMainLooper())
    val listener = object : ViewTreeObserver.OnDrawListener {
        private var fired = false

        override fun onDraw() {
            if (fired) return
            fired = true
            // A draw listener may not remove itself during the draw; do it in the posted step.
            main.post {
                val observer = viewTreeObserver
                if (observer.isAlive) observer.removeOnDrawListener(this)
                action()
            }
        }
    }
    viewTreeObserver.addOnDrawListener(listener)
}
