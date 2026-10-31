package com.plumemu.telemetry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.PrintWriter
import java.io.StringWriter

/** The kinds of the `/api/v1/crashes` API. */
internal enum class CrashKind(override val wire: String) : WireValue {
    Exception("exception"),
    Anr("anr"),
    Native("native"),
}

/**
 * How one crash report reads (`/api/v1/crashes`). Every stack starts with
 * [header], `Plumemu 0.0.3-beta (version code 321, release)`, which the retrace tooling
 * reads to pick the right R8 mapping; the server's signature reads only the `at` lines after it.
 */
internal object CrashReports {
    /** The server keeps 1,000 characters of the type, 16,000 of the message, 200,000 of the stack. */
    const val MAX_EXCEPTION_CHARS = 1_000
    const val MAX_MESSAGE_CHARS = 8_000

    /** Keeps a report well under the 256 KB of a request, even with ten of them split in several requests. */
    const val MAX_STACK_CHARS = 64 * 1024

    fun header(config: TelemetryConfig): String = "Plumemu ${config.appVersion} (version code ${config.versionCode}, ${config.buildType.wire})"

    /** An uncaught exception: type, message, the full stack with its causes, the thread, the screen and the uptime. */
    fun ofThrowable(
        id: String,
        time: String,
        header: String,
        error: Throwable,
        thread: String,
        screen: String?,
        uptimeSeconds: Long,
    ): JsonObject {
        val trace = StringWriter().also { PrintWriter(it).use { writer -> error.printStackTrace(writer) } }.toString()
        return item(
            id = id,
            kind = CrashKind.Exception,
            time = time,
            exception = error.javaClass.name,
            message = error.message,
            stack = "$header\n$trace",
            thread = thread,
            screen = screen,
            uptimeSeconds = uptimeSeconds,
        )
    }

    fun item(
        id: String,
        kind: CrashKind,
        time: String,
        exception: String,
        message: String?,
        stack: String,
        thread: String? = null,
        screen: String? = null,
        uptimeSeconds: Long? = null,
    ): JsonObject =
        buildJsonObject {
            put("id", JsonPrimitive(id))
            put("kind", JsonPrimitive(kind.wire))
            put("time", JsonPrimitive(time))
            put("exception", JsonPrimitive(cut(exception, MAX_EXCEPTION_CHARS)))
            message?.let { put("message", JsonPrimitive(cut(it, MAX_MESSAGE_CHARS))) }
            put("stack", JsonPrimitive(cut(stack, MAX_STACK_CHARS)))
            thread?.let { put("thread", JsonPrimitive(cut(it, MAX_THREAD_CHARS))) }
            screen?.let { put("screen", JsonPrimitive(it)) }
            uptimeSeconds?.let { put("uptime_s", JsonPrimitive(it.coerceAtLeast(0))) }
        }

    fun cut(
        text: String,
        max: Int,
    ): String = if (text.length <= max) text else "${text.take(max)}\n[cut at $max characters]"

    private const val MAX_THREAD_CHARS = 200
}
