package com.plumemu.telemetry

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The app's own log: every line goes to logcat and to a
 * rotating file in the app's no-backup files folder ([LogFile]: two files of 1 MB at most).
 * [snapshot] makes the text Settings › Advanced › Export logs shares (and, later, a problem report
 * attaches): that file plus this app's own lines of Android's log.
 *
 * Lines are written on one background thread ([LogWriter], at most [LogWriter.CAPACITY] waiting),
 * so a call never touches the disk on the caller's thread; [writeNow] is for the crash handler
 * only, whose process is about to end. Before [install] lines go to logcat only.
 */
public object AppLog {
    @Volatile
    private var writer: LogWriter? = null

    /** Opens the file under the app's no-backup folder; called once by the Application. Safe to call again. */
    @Synchronized
    public fun install(context: Context) {
        if (writer != null) return
        writer = LogWriter(LogFile(File(context.applicationContext.noBackupFilesDir, LOG_DIR)))
    }

    public fun i(
        tag: String,
        message: String,
    ) {
        Log.i(tag, message)
        writer?.enqueue(LogFile.line(System.currentTimeMillis(), "I", tag, message))
    }

    public fun w(
        tag: String,
        message: String,
    ) {
        Log.w(tag, message)
        writer?.enqueue(LogFile.line(System.currentTimeMillis(), "W", tag, message))
    }

    public fun e(
        tag: String,
        message: String,
        error: Throwable? = null,
    ) {
        Log.e(tag, message, error)
        val text = if (error == null) message else "$message\n${Log.getStackTraceString(error)}"
        writer?.enqueue(LogFile.line(System.currentTimeMillis(), "E", tag, text))
    }

    /** Appends at once, on the calling thread (the crash handler; nothing else). Never throws. */
    internal fun writeNow(
        tag: String,
        message: String,
    ) {
        try {
            writer?.file?.append(LogFile.line(System.currentTimeMillis(), "E", tag, message))
        } catch (e: Throwable) {
            // The process is dying; the crash file is what matters.
        }
    }

    /**
     * One text file in the cache folder ([EXPORT_DIR]): [header], then Plumemu's log file (older
     * file first), then this app's own lines of Android's log (`logcat -d -v threadtime`; an app
     * reads its own lines without a permission), its newest [Logcat.MAX_BYTES] only, 5 s at most ([Logcat]). A logcat that
     * cannot be read is said in one line; the rest is still written. Waits for the lines still
     * queued. Blocking (it starts a process): call it on a background thread. Null when the file
     * could not be written (logged).
     */
    public fun snapshot(
        context: Context,
        header: String,
    ): File? {
        val current = writer
        current?.flush()
        val out = File(File(context.applicationContext.cacheDir, EXPORT_DIR), EXPORT_NAME)
        return try {
            writeSnapshot(
                out = out,
                header = "$header\nAndroid ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
                file = current?.file,
                logcat = ::readOwnLogcat,
            )
            out
        } catch (e: IOException) {
            w(TAG, "Log export failed (${e.javaClass.simpleName})")
            null
        }
    }

    internal fun writeSnapshot(
        out: File,
        header: String,
        file: LogFile?,
        logcat: () -> String,
    ) {
        out.parentFile?.mkdirs()
        val logcatText =
            try {
                logcat()
            } catch (e: Exception) {
                "Android's log could not be read (${e.javaClass.simpleName}); only Plumemu's own log is above.\n"
            }
        out.outputStream().use { stream ->
            fun text(value: String) = stream.write(value.toByteArray(Charsets.UTF_8))
            text("$header\n\n== Plumemu's log (${LogFile.PREVIOUS}, then ${LogFile.CURRENT}) ==\n")
            if (file == null) text("(no log file in this process)\n") else file.copyTo(stream)
            text("\n== Android's log, this app's lines only (logcat -d -v threadtime, newest ${Logcat.MAX_BYTES / 1024} KB) ==\n")
            text(logcatText)
        }
    }

    /** This app's lines of Android's log ([Logcat]); throws when logcat cannot be started. */
    private fun readOwnLogcat(): String {
        val pid = Process.myPid()
        val command = Logcat.command(pid, Build.VERSION.SDK_INT)
        return Logcat.read(start = { ProcessBuilder(command).redirectErrorStream(true).start() }, pid = pid)
    }

    private const val TAG = "AppLog"

    /** Under `noBackupFilesDir`: kept across updates, never in a backup. */
    internal const val LOG_DIR = "logs"


    /** Under `cacheDir`; the app's FileProvider shares this folder only. */
    public const val EXPORT_DIR: String = "logs-export"
    public const val EXPORT_NAME: String = "plumemu-log.txt"
}

/**
 * Writes [file]'s lines on one daemon thread. At most [capacity] lines wait; past that a line is
 * dropped and counted, and the next line written is preceded by one line saying how many went.
 */
internal class LogWriter(
    val file: LogFile,
    capacity: Int = CAPACITY,
) {
    private val dropped = AtomicInteger()
    private val executor =
        ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(capacity)) { runnable ->
            Thread(runnable, "plumemu-log").apply { isDaemon = true }
        }.apply {
            rejectedExecutionHandler =
                RejectedExecutionHandler { task, _ ->
                    // A flush that finds the queue full is not a lost line: it just does not wait.
                    if (task is Flush) task.rejected() else dropped.incrementAndGet()
                }
        }

    /** A flush's marker task: done once the lines queued before it are written, or at once when refused. */
    private inner class Flush : Runnable {
        val done = CountDownLatch(1)

        @Volatile
        var wasRejected = false

        override fun run() {
            write(null)
            done.countDown()
        }

        fun rejected() {
            wasRejected = true
            done.countDown()
        }
    }

    fun enqueue(line: String) {
        executor.execute { write(line) }
    }

    /**
     * Waits (5 s at most) for the lines queued before this call. With the queue full it returns at
     * once (the snapshot then misses the newest lines, which logcat still has) and counts nothing.
     * True when everything queued before was written.
     */
    fun flush(): Boolean {
        val flush = Flush()
        executor.execute(flush)
        if (flush.wasRejected) return false
        return try {
            flush.done.await(5, TimeUnit.SECONDS) && !flush.wasRejected
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /** Runs [block] on the writer's thread (tests hold the writer with it). */
    fun onWriter(block: () -> Unit) {
        executor.execute(block)
    }

    private fun write(line: String?) {
        try {
            val lost = dropped.getAndSet(0)
            if (lost > 0) file.append(LogFile.line(System.currentTimeMillis(), "W", TAG, "$lost log lines dropped (the writer was behind)"))
            if (line != null) file.append(line)
        } catch (e: IOException) {
            // Logcat already has the line; a full disk must not loop through the log itself.
            Log.w(TAG, "Log file not written (${e.javaClass.simpleName})")
        }
    }

    companion object {
        /** Lines waiting to be written at most. */
        const val CAPACITY = 1_000
        private const val TAG = "AppLog"
    }
}

/**
 * Two files in [dir]: `plumemu.log` written to, `plumemu.log.1` the one before. When a line would
 * take the current file past [maxBytes], it becomes the old one (the previous old one is deleted)
 * and a new file starts, so the log never holds more than twice [maxBytes].
 */
internal class LogFile(
    private val dir: File,
    private val maxBytes: Long = MAX_BYTES,
) {
    private val current = File(dir, CURRENT)
    private val previous = File(dir, PREVIOUS)

    @Synchronized
    fun append(line: String) {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("log folder not made")
        if (current.length() > 0 && current.length() + bytes.size > maxBytes) rotate()
        current.appendBytes(bytes)
    }

    /** Both files, the older first, into [stream]. */
    @Synchronized
    fun copyTo(stream: OutputStream) {
        for (part in listOf(previous, current)) {
            if (part.isFile) part.inputStream().use { it.copyTo(stream) }
        }
    }

    private fun rotate() {
        if (previous.exists() && !previous.delete()) throw IOException("old log not deleted")
        if (!current.renameTo(previous)) throw IOException("log not rotated")
    }

    companion object {
        const val MAX_BYTES: Long = 1024 * 1024
        const val CURRENT = "plumemu.log"
        const val PREVIOUS = "plumemu.log.1"

        fun line(
            millis: Long,
            level: String,
            tag: String,
            message: String,
        ): String = "${Telemetry.isoTime(millis)} $level/$tag: $message\n"
    }
}
