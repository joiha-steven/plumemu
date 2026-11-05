package com.plumemu.telemetry

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * New `game_unidentified` fingerprints queued per [Telemetry.reportUnidentified] call (the app calls
 * it after each scan): half a batch, a twentieth of the queue's cap, so a large library never
 * pushes the usage events out of the queue nor fills the server's daily cap at once; the rest go
 * after the next scans.
 */
public const val MAX_UNIDENTIFIED_PER_REPORT: Int = 100

/**
 * The `game_unidentified` fingerprints the server accepted (a 2xx answer to the batch holding them;
 * [TelemetryEvent.GameUnidentified.key], one per line in [file]), so each goes once. A fingerprint
 * still in the queue is not offered again either ([fresh]). One the server refused (4xx), or deleted
 * unsent (statistics switched off, the queue full), is in neither, so a later scan offers it again,
 * at most [MAX_UNIDENTIFIED_PER_REPORT] a scan. Read once, then kept in memory; a lost write only
 * means a resend, which the server stores once.
 *
 * Read by [Telemetry]'s command coroutine and written by its send: every method is synchronized.
 */
internal class UnidentifiedSent(
    private val file: File,
    private val log: (String) -> Unit,
) {
    private var loaded: MutableSet<String>? = null

    /**
     * The valid games of [games] neither accepted before nor waiting in [queued], each fingerprint
     * once, at most [MAX_UNIDENTIFIED_PER_REPORT].
     */
    @Synchronized
    fun fresh(
        games: List<TelemetryEvent.GameUnidentified>,
        queued: List<EventQueue.Entry>,
    ): List<TelemetryEvent.GameUnidentified> {
        val waiting = queued.mapNotNullTo(HashSet()) { keyOf(it.json) }
        val sent = keys()
        return games.filter { it.isValid() }.distinctBy { it.key() }.filter { it.key() !in sent && it.key() !in waiting }.take(MAX_UNIDENTIFIED_PER_REPORT)
    }

    /** The server accepted the batch of [entries]: its fingerprints are sent. */
    @Synchronized
    fun accepted(entries: List<EventQueue.Entry>) {
        val added = entries.mapNotNull { keyOf(it.json) }
        if (added.isNotEmpty()) addAll(added)
    }

    private fun addAll(added: Collection<String>) {
        val all = keys()
        val fresh = added.filter(all::add)
        if (fresh.isEmpty()) return
        try {
            file.parentFile?.mkdirs()
            FileOutputStream(file, true).use { out -> out.write(fresh.joinToString(separator = "") { "$it\n" }.toByteArray(Charsets.UTF_8)) }
        } catch (e: IOException) {
            log("Unidentified fingerprints sent could not be written (${e.javaClass.simpleName})")
        }
    }

    @Synchronized
    fun size(): Int = keys().size

    private fun keys(): MutableSet<String> {
        loaded?.let { return it }
        val out = LinkedHashSet<String>()
        try {
            if (file.exists()) file.forEachLine(Charsets.UTF_8) { line -> if (line.isNotBlank()) out += line }
        } catch (e: IOException) {
            log("Unidentified fingerprints sent could not be read (${e.javaClass.simpleName}); starting empty")
        }
        loaded = out
        return out
    }

    companion object {
        const val FILE_NAME = "unidentified-sent.txt"

        /** The fingerprint key of a queued `game_unidentified` event, null for any other event. */
        fun keyOf(json: JsonObject): String? {
            if ((json["name"] as? JsonPrimitive)?.content != "game_unidentified") return null
            val props = json["props"] as? JsonObject ?: return null
            return TelemetryEvent.GameUnidentified.keyOf(props.mapValues { (_, value) -> value as? JsonPrimitive ?: JsonPrimitive("") })
        }
    }
}
