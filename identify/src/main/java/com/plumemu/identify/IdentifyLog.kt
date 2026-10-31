package com.plumemu.identify

/**
 * Where the identify module writes its log lines. The app passes one that writes to Android's
 * log; the default writes to standard error, which is what JVM tests see.
 *
 * Lines name system ids, pack names and exception classes, never game titles, file names or
 * paths.
 */
fun interface IdentifyLog {
    fun log(message: String)

    companion object {
        /** Writes `identify: <message>` to standard error. */
        val STDERR: IdentifyLog = IdentifyLog { System.err.println("identify: $it") }
    }
}
