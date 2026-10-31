package com.plumemu.telemetry

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** How [ProblemReports.submit] ended; the screen says one plain sentence for each. */
public enum class ReportResult {
    /** The server stored it. */
    Sent,

    /** Kept on the device: no network, or the server asked to wait (429, 5xx). It goes when online. */
    Saved,

    /** The server refused it for good (400, 413); it is not kept. */
    Refused,

    /** It could not be written to the device (full or read-only storage), so nothing was kept or sent. */
    NotSaved,

    /** The text was empty. */
    Empty,

    /** This build has no endpoint it may send to: nothing was kept, and nothing can be sent. */
    Unavailable,
}

/**
 * Problem reports (Settings › About › Report a problem, `/api/v1/reports`). The person presses Send, so a report goes
 * whatever the statistics switch says. It is written to `reports/` in the module's no-backup folder first, then sent at
 * once; if that cannot be done now it stays and [Telemetry.sendPending] (WorkManager, when a
 * network is there) sends it. At most [MAX_KEPT] wait, the newest. 2xx, 400 and 413 and every other
 * 4xx but 429 remove the file; 429, 5xx and no network keep it. Pending reports survive restarts.
 *
 * What goes: the text (at most [MAX_TEXT_CHARS]), the e-mail if given, the install id only while
 * statistics are on, and, only when [submit] is told to include details, `common` and the newest
 * [MAX_LOG_BYTES] of the log. Never logs the text, the e-mail or the log.
 */
public class ProblemReports internal constructor(
    private val dir: File,
    private val sender: Sender?,
    private val scheduler: SendScheduler,
    /** Completes once the module has read its state (the install id and the switch). */
    private val ready: suspend () -> Unit,
    /** The install id while statistics are on; null while they are off. */
    private val installIdIfOn: () -> String?,
    private val statisticsOn: () -> Boolean,
    private val common: () -> JsonObject,
    /** The log text to attach (blocking); null when it cannot be read. */
    private val readLog: () -> String?,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
) {
    private val lock = Mutex()

    /**
     * Queues one report and tries to send it now. Never throws. [email] is optional; check it with
     * [emailProblem] before. Safe to call from the main thread.
     */
    public suspend fun submit(
        text: String,
        email: String?,
        includeDetails: Boolean,
    ): ReportResult =
        withContext(Dispatchers.IO) {
            val trimmed = text.trim().take(MAX_TEXT_CHARS).trim()
            if (trimmed.isEmpty()) return@withContext ReportResult.Empty
            try {
                ready()
                // A build with no endpoint would keep the report for ever: say so instead of promising a send.
                if (sender == null) {
                    log("Problem report not kept: this build has no endpoint to send to")
                    return@withContext ReportResult.Unavailable
                }
                val body = bodyOf(trimmed, email?.trim()?.takeIf { it.isNotEmpty() }, includeDetails)
                lock.withLock {
                    val file = write(body) ?: return@withLock ReportResult.NotSaved
                    when (sendQueued()[file.name]) {
                        Answer.Accepted -> ReportResult.Sent
                        Answer.Dropped -> ReportResult.Refused
                        else -> {
                            // Not delivered now: WorkManager sends it when a network is there.
                            scheduler.sendNow()
                            ReportResult.Saved
                        }
                    }
                }
            } catch (e: IOException) {
                log("Problem report not saved (${e.javaClass.simpleName})")
                ReportResult.NotSaved
            }
        }

    /** Sends the reports kept on the device, oldest first; stops at the first that must wait. */
    internal suspend fun sendPending(): SendOutcome =
        withContext(Dispatchers.IO) {
            lock.withLock {
                // No endpoint this build may send to: the reports stay, as the events do, and nothing retries.
                if (sender == null || files().isEmpty()) return@withLock SendOutcome.Done
                if (sendQueued().values.any { it == Answer.Retry }) SendOutcome.Retry else SendOutcome.Done
            }
        }

    private fun bodyOf(
        text: String,
        email: String?,
        includeDetails: Boolean,
    ): JsonObject {
        val attached = if (includeDetails) readLog()?.takeIf { it.isNotEmpty() } else null
        var logText = attached?.let { newestBytes(it, MAX_LOG_BYTES) }
        fun build(): JsonObject =
            buildJsonObject {
                put("v", JsonPrimitive(Telemetry.API_VERSION))
                put("source", JsonPrimitive("app"))
                put("text", JsonPrimitive(text))
                email?.let { put("email", JsonPrimitive(it)) }
                installIdIfOn()?.let { put("install_id", JsonPrimitive(it)) }
                if (includeDetails) {
                    put("common", common())
                    logText?.let { put("log", JsonPrimitive(it)) }
                }
            }
        var body = build()
        // Control characters in a log escape to six bytes each: shorten the log until the whole request fits the server's 1 MB.
        while (logText != null && body.toString().toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            val shorter = newestBytes(logText, logText.toByteArray(Charsets.UTF_8).size * 3 / 4)
            logText = shorter.takeIf { it.isNotEmpty() && it.length < logText!!.length }
            body = build()
        }
        return body
    }

    /** Writes [body] as the newest file and keeps at most [MAX_KEPT]; null when it could not be written. */
    private fun write(body: JsonObject): File? {
        dir.mkdirs()
        val name = "${clock().toString().padStart(TIME_DIGITS, '0')}-${newId()}"
        val temporary = File(dir, "$name.tmp")
        return try {
            temporary.writeText(body.toString(), Charsets.UTF_8)
            val file = File(dir, "$name$SUFFIX")
            if (!temporary.renameTo(file)) throw IOException("report file not renamed")
            val all = files()
            if (all.size > MAX_KEPT) {
                all.take(all.size - MAX_KEPT).forEach { it.delete() }
                log("Problem reports: ${all.size - MAX_KEPT} older ones deleted (at most $MAX_KEPT kept)")
            }
            file
        } catch (e: IOException) {
            temporary.delete()
            log("Problem report not saved (${e.javaClass.simpleName})")
            null
        }
    }

    /** Sends the files oldest first and returns the server's answer for each one reached. Under [lock]. */
    private fun sendQueued(): Map<String, Answer> {
        val sender = sender ?: return emptyMap()
        val answers = LinkedHashMap<String, Answer>()
        for (file in files()) {
            val body =
                try {
                    Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
                } catch (e: Exception) {
                    log("Problem report unreadable (${e.javaClass.simpleName}); deleted")
                    file.delete()
                    continue
                }
            // Statistics switched off since it was written: the install id stays out of it.
            val toSend = if (statisticsOn() || "install_id" !in body) body else JsonObject(body - "install_id")
            val answer = sender.post(Sender.REPORTS, toSend)
            answers[file.name] = answer
            when (answer) {
                Answer.Accepted -> file.delete()
                Answer.Dropped -> {
                    log("Problem report refused by the server; deleted")
                    file.delete()
                }
                Answer.Retry -> return answers
            }
        }
        return answers
    }

    private fun files(): List<File> = dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.sortedBy { it.name }.orEmpty()

    public companion object {
        /** API limit: the report's text, in characters. The screen stops typing here. */
        public const val MAX_TEXT_CHARS: Int = 4_000

        /** API limit: 512 KB of log; the newest part is kept. */
        public const val MAX_LOG_BYTES: Int = 512 * 1024

        /** API limit: 1 MB a request; a margin is left for the envelope. */
        internal const val MAX_BODY_BYTES: Int = 1000 * 1024

        /** The e-mail's most characters (the server refuses a longer one). */
        public const val MAX_EMAIL_CHARS: Int = 254

        /** Pending reports kept on the device. */
        internal const val MAX_KEPT: Int = 5

        private const val SUFFIX = ".json"
        private const val TIME_DIGITS = 15

        /** The folder of the module's directory [telemetryDir] that holds pending reports. */
        internal fun dirIn(telemetryDir: File): File = File(telemetryDir, "reports")

        /**
         * Whether [email] cannot be sent: null when it is fine or empty (the field is optional).
         * Plain checks only (one "@" with text around it, no spaces); the server stores it as typed.
         */
        public fun emailProblem(email: String): EmailProblem? {
            val trimmed = email.trim()
            return when {
                trimmed.isEmpty() -> null
                trimmed.length > MAX_EMAIL_CHARS -> EmailProblem.TooLong
                trimmed.any { it.isWhitespace() || it.isISOControl() } -> EmailProblem.NotAnAddress
                trimmed.indexOf('@').let { at -> at < 1 || at != trimmed.lastIndexOf('@') || at == trimmed.length - 1 } -> EmailProblem.NotAnAddress
                else -> null
            }
        }

        /** The last [maxBytes] of [text] (UTF-8), starting at a line when it is cut and never in the middle of a character. */
        internal fun newestBytes(
            text: String,
            maxBytes: Int,
        ): String {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return text
            var start = bytes.size - maxBytes
            while (start < bytes.size && (bytes[start].toInt() and 0xC0) == 0x80) start++
            val lineStart = (start until bytes.size).firstOrNull { bytes[it] == '\n'.code.toByte() }
            // A line start is used only when it leaves most of the budget; otherwise the cut stays where it is.
            if (lineStart != null && lineStart - start < MAX_LINE_SKIP) start = lineStart + 1
            return String(bytes, start, bytes.size - start, Charsets.UTF_8)
        }

        private const val MAX_LINE_SKIP = 4096

        /** The log as one text for a report: [AppLog.snapshot]'s file read back; null when it cannot be made or read. */
        internal fun snapshotText(
            context: Context,
            header: String,
            log: (String) -> Unit,
        ): String? {
            val file = AppLog.snapshot(context, header) ?: return null
            return try {
                file.readText(Charsets.UTF_8)
            } catch (e: IOException) {
                log("Problem report: the log could not be read (${e.javaClass.simpleName})")
                null
            }
        }
    }
}

/** Why an e-mail cannot be sent with a report. */
public enum class EmailProblem { TooLong, NotAnAddress }
