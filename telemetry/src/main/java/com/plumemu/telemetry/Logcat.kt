package com.plumemu.telemetry

import android.os.Build
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * This app's own lines of Android's log for a snapshot ([AppLog.snapshot]): `logcat -d -v
 * threadtime`, with `--pid` from Android 7. Whatever Android hands out, only lines of this
 * process are kept ([ownLines]): a ROM that grants READ_LOGS, or Android 6 without `--pid`, must
 * not put other apps' lines in a file the person shares. At most [maxBytes], the newest; at most
 * [timeoutMillis] in all, after which the process is ended and what was read is kept.
 */
internal object Logcat {
    const val MAX_BYTES = 384 * 1024
    const val TIMEOUT_MILLIS = 5_000L
    const val TIMED_OUT = "Android's log timed out; the lines above are what was read."
    const val LEFT_OUT = "[older lines left out]"

    /** `--pid` exists from Android 7 (API 24). */
    fun command(
        pid: Int,
        sdkInt: Int,
    ): List<String> = listOf("logcat", "-d", "-v", "threadtime") + (if (sdkInt >= Build.VERSION_CODES.N) listOf("--pid=$pid") else emptyList())

    /** `10-24 08:00:00.123  1234  1250 I Tag: text`: the date, the time, then the process id. */
    private val THREADTIME = Regex("""^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+(\d+)\s+\d+\s+[VDIWEFA]\s""")

    /** True for a line of process [pid], and for logcat's own `--------- beginning of …` separators. */
    fun isOwnLine(
        line: String,
        pid: Int,
    ): Boolean {
        if (line.startsWith("--------- beginning of")) return true
        val match = THREADTIME.find(line) ?: return false
        return match.groupValues[1].toIntOrNull() == pid
    }

    /** [text] with only [pid]'s lines (and separators); everything else is left out. */
    fun ownLines(
        text: String,
        pid: Int,
    ): String = text.lineSequence().filter { isOwnLine(it, pid) }.joinToString("") { "$it\n" }

    /**
     * Reads the output of the process [start] makes, on its own thread, keeping [pid]'s newest
     * lines up to [maxBytes]. After [timeoutMillis] the process is ended and the text says so.
     * Throws only when the process cannot be started.
     */
    fun read(
        start: () -> Process,
        pid: Int,
        maxBytes: Int = MAX_BYTES,
        timeoutMillis: Long = TIMEOUT_MILLIS,
    ): String {
        val process = start()
        val kept = NewestLines(maxBytes)
        val reader =
            Thread({
                try {
                    process.outputStream.close()
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { lines ->
                        while (true) {
                            val line = lines.readLine() ?: break
                            if (isOwnLine(line, pid)) kept.add(line)
                        }
                    }
                } catch (e: Exception) {
                    // Ended by the timeout below, or logcat failed: what was read is kept.
                }
            }, "plumemu-logcat").apply { isDaemon = true }
        reader.start()
        reader.join(timeoutMillis)
        val timedOut = reader.isAlive
        end(process)
        return kept.text() + if (timedOut) "$TIMED_OUT\n" else ""
    }

    private fun end(process: Process) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) process.destroyForcibly() else process.destroy()
        } catch (e: RuntimeException) {
            // Already gone.
        }
    }

    /** The newest lines whose UTF-8 size stays within [maxBytes]; safe from two threads. */
    class NewestLines(
        private val maxBytes: Int,
    ) {
        private val lines = ArrayDeque<String>()
        private var bytes = 0
        private var leftOut = false

        @Synchronized
        fun add(line: String) {
            val size = line.toByteArray(Charsets.UTF_8).size + 1
            lines.addLast(line)
            bytes += size
            while (bytes > maxBytes && lines.isNotEmpty()) {
                bytes -= lines.removeFirst().toByteArray(Charsets.UTF_8).size + 1
                leftOut = true
            }
        }

        @Synchronized
        fun text(): String = (if (leftOut) "$LEFT_OUT\n" else "") + lines.joinToString("") { "$it\n" }
    }
}
