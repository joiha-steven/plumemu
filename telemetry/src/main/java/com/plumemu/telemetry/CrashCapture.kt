package com.plumemu.telemetry

import android.content.Context
import android.os.SystemClock
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The uncaught-exception handler: writes one crash file ([CrashStore]) on the dying
 * thread, synchronously, then hands the exception to the handler that was there before (Android's,
 * which shows the crash and ends the process). No coroutine, no network, nothing that waits: the
 * file is sent by the next launch, while statistics are on ([Telemetry]).
 */
public object CrashCapture {
    /** The screen last opened (`screen_opened`'s value), kept for the report; set by [Telemetry.track]. */
    @Volatile
    internal var screen: String? = null

    private val installed = AtomicBoolean(false)

    /** Installs the handler once per process; called first thing by the Application. */
    public fun install(
        context: Context,
        config: TelemetryConfig,
    ) {
        if (!installed.compareAndSet(false, true)) return
        val store = CrashStore(CrashStore.dirIn(File(context.applicationContext.noBackupFilesDir, Telemetry.DIR_NAME))) { AppLog.w(Telemetry.LOG_TAG, it) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(
            Handler(
                store = store,
                header = CrashReports.header(config),
                startedAt = SystemClock.elapsedRealtime(),
                uptime = SystemClock::elapsedRealtime,
                previous = previous,
            ),
        )
    }

    internal class Handler(
        private val store: CrashStore,
        private val header: String,
        private val startedAt: Long,
        private val uptime: () -> Long,
        private val previous: Thread.UncaughtExceptionHandler?,
        private val clock: () -> Long = System::currentTimeMillis,
        private val newId: () -> String = { UUID.randomUUID().toString() },
        /** The report of [Thread] dying of [Throwable] with this id at this time; the tests make it fail. */
        private val report: (Thread, Throwable, String, Long) -> JsonObject = { thread, error, id, now ->
            CrashReports.ofThrowable(
                id = id,
                time = Telemetry.isoTime(now),
                header = header,
                error = error,
                thread = thread.name,
                screen = screen,
                uptimeSeconds = (uptime() - startedAt) / 1000,
            )
        },
    ) : Thread.UncaughtExceptionHandler {
        /** Only the first crash of the process is written; a second thread dying meanwhile goes straight on. */
        private val writing = AtomicBoolean(false)

        override fun uncaughtException(
            thread: Thread,
            error: Throwable,
        ) {
            try {
                if (writing.compareAndSet(false, true)) writeReport(thread, error)
            } catch (e: Throwable) {
                // Whatever failed (out of memory, disk), Android's own handler below still runs.
            } finally {
                previous?.uncaughtException(thread, error)
            }
        }

        private fun writeReport(
            thread: Thread,
            error: Throwable,
        ) {
            try {
                val now = clock()
                val id = newId()
                store.write(report(thread, error, id, now), now, id)
                AppLog.writeNow(Telemetry.LOG_TAG, "Crash: ${error.javaClass.name} on ${thread.name}; report kept for the next launch")
            } catch (e: Throwable) {
                // A fixed text: building one here could fail the same way.
                AppLog.writeNow(Telemetry.LOG_TAG, REPORT_NOT_WRITTEN)
            }
        }
    }

    private const val REPORT_NOT_WRITTEN = "Crash report not written"
}
