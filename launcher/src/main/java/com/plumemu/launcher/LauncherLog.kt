package com.plumemu.launcher

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * The only way this module logs; tests swap it to assert the line. Lines carry ids (game id,
 * system id, emulator id, package) and an exception's class at most, never game titles, file names
 * or paths. There is deliberately no Throwable parameter: a provider's exception message
 * holds the document URI with the file name ("Permission Denial: reading ... ROMs%2Fsnes%2F<name>"),
 * and so does a stack trace.
 */
internal interface LauncherLog {
    fun info(message: String)

    fun warn(message: String)
}

/** How a line names an exception: its class only (see [LauncherLog]). */
internal fun Throwable.kind(): String = javaClass.name

/**
 * Passes on [CancellationException] only when the caller itself was cancelled. Room 2.8 throws one
 * from its own scope once the database is closed; that is a failure to report, not a cancellation.
 */
internal suspend fun CancellationException.rethrowIfCallerCancelled() {
    if (!currentCoroutineContext().isActive) throw this
}

internal object AndroidLauncherLog : LauncherLog {
    private const val TAG = "PlumemuLauncher"

    override fun info(message: String) {
        Log.i(TAG, message)
    }

    override fun warn(message: String) {
        Log.w(TAG, message)
    }
}
