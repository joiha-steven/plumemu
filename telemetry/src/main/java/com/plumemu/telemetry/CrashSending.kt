package com.plumemu.telemetry

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** How one crash request ended. */
internal enum class CrashBatch {
    /** No crash file waits. */
    Nothing,

    /** A request went and its files were removed (accepted, or refused for good and logged). */
    Sent,

    /** 429 (the day's crash cap), 5xx or no network: the files stay; the send tries again later. */
    Wait,
}

/**
 * Sends the oldest crash files in one `POST /api/v1/crashes`: at most
 * [MAX_CRASHES] and [MAX_BODY_BYTES], so a request stays under the server's 10 crashes and 256 KB.
 * Crash ids make a resend harmless; a file is deleted only after the server answered.
 */
internal fun sendCrashBatch(
    sender: Sender,
    store: CrashStore,
    installId: String,
    common: JsonObject,
    log: (String) -> Unit,
): CrashBatch {
    val entries = store.entries()
    if (entries.isEmpty()) return CrashBatch.Nothing
    val budget = MAX_BODY_BYTES - common.toString().toByteArray(Charsets.UTF_8).size - ENVELOPE_BYTES
    val tooBig = entries.filter { it.bytes > budget }
    if (tooBig.isNotEmpty()) {
        // Cannot happen with the caps of CrashReports; a file changed by hand would block the rest forever.
        log("Crash reports: ${tooBig.size} too big to send; deleted")
        store.delete(tooBig)
    }
    val batch = ArrayList<CrashStore.Entry>()
    var bytes = 0
    for (entry in entries - tooBig.toSet()) {
        if (batch.size == MAX_CRASHES || bytes + entry.bytes + 1 > budget) break
        batch += entry
        bytes += entry.bytes + 1
    }
    if (batch.isEmpty()) return if (tooBig.isEmpty()) CrashBatch.Nothing else CrashBatch.Sent
    val body =
        buildJsonObject {
            put("v", JsonPrimitive(Telemetry.API_VERSION))
            put("install_id", JsonPrimitive(installId))
            put("common", common)
            put("crashes", JsonArray(batch.map { it.json }))
        }
    return when (sender.post(Sender.CRASHES, body)) {
        Answer.Accepted -> {
            store.delete(batch)
            CrashBatch.Sent
        }
        Answer.Dropped -> {
            log("Crash reports: ${batch.size} refused by the server; deleted")
            store.delete(batch)
            CrashBatch.Sent
        }
        Answer.Retry -> CrashBatch.Wait
    }
}

/** API limit: 10 crashes a request. */
internal const val MAX_CRASHES = 10

/** API limit: 256 KB a request; a margin is left for the envelope's own bytes. */
internal const val MAX_BODY_BYTES = 250 * 1024

private const val ENVELOPE_BYTES = 200
