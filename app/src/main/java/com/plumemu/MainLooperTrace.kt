package com.plumemu

import android.os.Looper
import android.os.Trace
import android.util.Printer

/**
 * Benchmark builds only: names every message the main thread runs in a system trace (section "Msg:<target and callback>"), so work
 * posted between frames (a coroutine resuming, a Handler callback) shows beside the frames it delays. Costs a string per message.
 */
internal object MainLooperTrace {
    fun start() {
        Looper.getMainLooper().setMessageLogging(
            object : Printer {
                override fun println(x: String) {
                    if (x.startsWith(DISPATCH)) {
                        Trace.beginSection("Msg:" + x.substring(DISPATCH.length).take(MAX_NAME))
                    } else if (x.startsWith(FINISHED)) {
                        Trace.endSection()
                    }
                }
            },
        )
    }

    private const val DISPATCH = ">>>>> Dispatching to "
    private const val FINISHED = "<<<<< Finished to "
    private const val MAX_NAME = 100
}
