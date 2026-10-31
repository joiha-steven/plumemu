package com.plumemu.platforms

import android.util.Log

/** The only way this module logs; tests swap it to assert the line. */
internal fun interface PlatformLog {
    fun error(message: String)
}

internal object AndroidPlatformLog : PlatformLog {
    private const val TAG = "PlumemuPlatforms"
    override fun error(message: String) {
        Log.e(TAG, message)
    }
}
