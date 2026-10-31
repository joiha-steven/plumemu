package com.plumemu.library.scan

import android.util.Log

/**
 * One info line per phase of a folder's scan (walk, database writes, naming), so a scan's time can
 * be read from any device's log. Ids and numbers only,
 * never a file or folder name.
 */
internal object ScanPhaseLog {
    const val WALK = "walk"
    const val DATABASE_WRITES = "database writes"
    const val NAMING = "naming"

    /**
     * `Folder 3 phase naming: 9120 ms, 1430 files, 156 files/s; ...`. [files] per second over the
     * wall time; "-" when the phase took under a millisecond. [detail] follows after a semicolon.
     */
    fun line(folderId: Long, phase: String, nanos: Long, files: Int, detail: String = ""): String {
        val millis = nanos / NANOS_PER_MILLI
        val rate = if (millis > 0) (files * MILLIS_PER_SECOND / millis).toString() else "-"
        val suffix = if (detail.isEmpty()) "" else "; $detail"
        return "Folder $folderId phase $phase: $millis ms, $files files, $rate files/s$suffix"
    }

    fun log(folderId: Long, phase: String, nanos: Long, files: Int, detail: String = "") {
        Log.i(LOG_TAG, line(folderId, phase, nanos, files, detail))
    }

    fun millis(nanos: Long): Long = nanos / NANOS_PER_MILLI

    private const val NANOS_PER_MILLI = 1_000_000L
    private const val MILLIS_PER_SECOND = 1_000L
}
