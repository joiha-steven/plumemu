package com.plumemu.telemetry

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.IOException
import java.io.InputStream

/**
 * One process end Android recorded (`ApplicationExitInfo`, Android 11+), as far as a report needs.
 * [trace] reads the ANR's thread dump when asked (capped at [ExitReasons.MAX_TRACE_BYTES]).
 */
internal class ExitRecord(
    val kind: CrashKind,
    val timestamp: Long,
    val description: String?,
    /** For a native crash, the signal number. */
    val status: Int,
    /** Android's `RunningAppProcessInfo.importance` when the process ended: 325 is the top app with the screen off. */
    val importance: Int,
    val trace: () -> String?,
)

/**
 * App-not-responding and native crashes read from Android's record of why the app's processes
 * ended. Java crashes are left out: the handler ([CrashCapture]) already wrote them.
 * Only exits after the newest one handled before count; the time of that one is kept in
 * [markerFile], moved by [markSeen] only after the caller stored the reports, so a failed write
 * reports them again at the next start (their ids come from the exit, so the server keeps one).
 * The first time (no marker), nothing is reported: those exits came before this version could
 * say so.
 */
internal class ExitReasons(
    private val markerFile: File,
    private val read: () -> List<ExitRecord>,
    private val log: (String) -> Unit,
) {
    /**
     * What Android recorded since the marker: [items] (oldest first; none when [report] is false,
     * statistics off) and [Taken.seenUpTo], to give [markSeen] once they are stored. Writes nothing.
     */
    class Taken(
        val items: List<Pair<Long, JsonObject>>,
        /** The marker's next value, or null when it stays. */
        val seenUpTo: Long?,
    )

    fun since(
        header: String,
        idFor: (ExitRecord) -> String,
        report: Boolean,
    ): Taken {
        val records =
            try {
                read()
            } catch (e: RuntimeException) {
                log("Exit reasons not read (${e.javaClass.simpleName})")
                return Taken(emptyList(), null)
            }
        val lastSeen = readMarker()
        val newest = records.maxOfOrNull { it.timestamp }
        // The first read only starts the marker (at 0 when Android has nothing yet).
        if (lastSeen == null) return Taken(emptyList(), newest ?: 0)
        val seenUpTo = newest?.takeIf { it > lastSeen }
        if (!report) return Taken(emptyList(), seenUpTo)
        return Taken(newSince(records, lastSeen).map { it.timestamp to item(it, header, idFor(it)) }, seenUpTo)
    }

    /** The reports up to [timestamp] are stored (or statistics are off): they are not read again. */
    fun markSeen(timestamp: Long) = writeMarker(timestamp)

    private fun readMarker(): Long? =
        try {
            if (markerFile.isFile) markerFile.readText().trim().toLongOrNull() else null
        } catch (e: IOException) {
            null
        }

    private fun writeMarker(value: Long) {
        try {
            markerFile.parentFile?.mkdirs()
            markerFile.writeText(value.toString())
        } catch (e: IOException) {
            log("Exit reasons: marker not written (${e.javaClass.simpleName})")
        }
    }

    companion object {
        /** An ANR's trace text is capped at 64 KB. */
        const val MAX_TRACE_BYTES = 64 * 1024

        const val MARKER_NAME = "exits-seen.txt"

        /** The records after [lastSeen], oldest first. */
        fun newSince(
            records: List<ExitRecord>,
            lastSeen: Long,
        ): List<ExitRecord> = records.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }

        /** One crash item for [record]: `anr` with the thread dump as its stack, `native` with the signal. */
        fun item(
            record: ExitRecord,
            header: String,
            id: String,
        ): JsonObject =
            when (record.kind) {
                CrashKind.Anr ->
                    CrashReports.item(
                        id = id,
                        kind = CrashKind.Anr,
                        time = Telemetry.isoTime(record.timestamp),
                        exception = if (isBackground(record)) "BackgroundANR" else "ANR",
                        message = record.description,
                        stack = header + "\n" + importanceLine(record.importance) + "\n" + (record.trace() ?: "(no trace)"),
                        thread = "main",
                    )
                else ->
                    CrashReports.item(
                        id = id,
                        kind = CrashKind.Native,
                        time = Telemetry.isoTime(record.timestamp),
                        exception = "Native crash (signal ${record.status})",
                        message = record.description,
                        stack = "$header\nsignal ${record.status}",
                    )
            }

        /**
         * Android ended the process for an ANR it did not show, Plumemu not in use (its description
         * starts with `bg anr`). A key sent while the screen was off raises one against the home
         * app: grouped apart from ANRs while in use.
         */
        private fun isBackground(record: ExitRecord) = record.description?.startsWith("bg anr") == true

        /** The importance with what it means for the values a report is read for; any other as a number. */
        private fun importanceLine(importance: Int): String {
            val meaning =
                when (importance) {
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> " (on screen)"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> " (visible, not in front)"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING -> " (top app, screen off)"
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> " (in the background)"
                    else -> ""
                }
            return "Importance $importance$meaning"
        }

        /** At most [max] bytes of [stream] as UTF-8 text, marked when cut. */
        fun readCapped(
            stream: InputStream,
            max: Int = MAX_TRACE_BYTES,
        ): String =
            stream.use {
                val buffer = ByteArray(max + 1)
                var length = 0
                while (length < buffer.size) {
                    val read = it.read(buffer, length, buffer.size - length)
                    if (read < 0) break
                    length += read
                }
                val text = String(buffer, 0, minOf(length, max), Charsets.UTF_8)
                if (length > max) "$text\n[cut at $max bytes]" else text
            }

        /** Android's records for this package (empty before Android 11). */
        fun reader(
            context: Context,
            log: (String) -> Unit,
        ): () -> List<ExitRecord> =
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) readAndroid(context, log) else emptyList()
            }

        @RequiresApi(Build.VERSION_CODES.R)
        private fun readAndroid(
            context: Context,
            log: (String) -> Unit,
        ): List<ExitRecord> {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getHistoricalProcessExitReasons(context.packageName, 0, 0).mapNotNull { info ->
                val kind =
                    when (info.reason) {
                        ApplicationExitInfo.REASON_ANR -> CrashKind.Anr
                        ApplicationExitInfo.REASON_CRASH_NATIVE -> CrashKind.Native
                        else -> return@mapNotNull null
                    }
                ExitRecord(kind, info.timestamp, info.description, info.status, info.importance) {
                    try {
                        info.traceInputStream?.let { readCapped(it) }
                    } catch (e: IOException) {
                        log("ANR trace not read (${e.javaClass.simpleName})")
                        null
                    }
                }
            }
        }
    }
}
