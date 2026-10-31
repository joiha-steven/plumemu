package com.plumemu.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The events waiting to be sent, on the device so they survive a restart and a time offline.
 *
 * One JSON event per line in `queue.jsonl` (`{"id","name","time","props"}`, the wire shape), read
 * once and then kept in memory too. Adding appends one line; removing writes the file again
 * (to a temporary file, then renamed, so a death half-way leaves the old file). A line cut short
 * by the process dying is skipped on the next read and the file rewritten without it.
 *
 * At most [cap] events: when full, the oldest [dropChunk] go at once (so a device offline for
 * weeks does not rewrite the file on every event) and are counted; the count goes to the server
 * as one `queue_overflow` event ([overflow]), with an id kept until it is delivered so a resend
 * is stored once.
 *
 * Every method is synchronized; callers are on background threads.
 */
internal class EventQueue(
    private val dir: File,
    private val log: (String) -> Unit,
    private val cap: Int = CAP,
    private val dropChunk: Int = DROP_CHUNK,
) {
    class Entry(
        val id: String,
        val json: JsonObject,
        /** The line's length in UTF-8 bytes, for the batch size limit. */
        val bytes: Int,
    )

    class Overflow(
        val dropped: Int,
        val id: String,
        val time: String,
    )

    private val file = File(dir, "queue.jsonl")
    private val overflowFile = File(dir, "queue-dropped.json")
    private var loaded: ArrayDeque<Entry>? = null

    @Synchronized
    fun size(): Int = entries().size

    @Synchronized
    fun append(
        event: JsonObject,
        newId: () -> String,
        now: () -> String,
    ) {
        val all = entries()
        if (all.size >= cap) {
            val drop = minOf(dropChunk, all.size)
            repeat(drop) { all.removeFirst() }
            addOverflow(drop, newId, now)
            rewrite(all)
        }
        val line = event.toString()
        val entry = Entry(idOf(event) ?: return, event, line.toByteArray(Charsets.UTF_8).size)
        try {
            dir.mkdirs()
            FileOutputStream(file, true).use { it.write((line + "\n").toByteArray(Charsets.UTF_8)) }
            all.addLast(entry)
        } catch (e: IOException) {
            // Kept in memory for this process; only a restart before the next send loses it.
            all.addLast(entry)
            log("Statistics queue could not be written (${e.javaClass.simpleName})")
        }
    }

    /** The oldest events, at most [maxCount] and [maxBytes] together (at least one if any). */
    @Synchronized
    fun peek(
        maxCount: Int,
        maxBytes: Int,
    ): List<Entry> {
        val out = ArrayList<Entry>()
        var bytes = 0
        for (entry in entries()) {
            if (out.size >= maxCount) break
            if (out.isNotEmpty() && bytes + entry.bytes > maxBytes) break
            out += entry
            bytes += entry.bytes + 1
        }
        return out
    }

    @Synchronized
    fun remove(ids: Set<String>) {
        if (ids.isEmpty()) return
        val all = entries()
        if (all.removeAll { it.id in ids }) rewrite(all)
    }

    /** Deletes every queued event and the overflow count (statistics switched off). */
    @Synchronized
    fun clear() {
        entries().clear()
        file.delete()
        overflowFile.delete()
    }

    @Synchronized
    fun overflow(): Overflow? {
        if (!overflowFile.exists()) return null
        return try {
            val json = Json.parseToJsonElement(overflowFile.readText()).jsonObject
            Overflow(
                dropped = json.getValue("dropped").jsonPrimitive.int,
                id = json.getValue("id").jsonPrimitive.content,
                time = json.getValue("time").jsonPrimitive.content,
            ).takeIf { it.dropped > 0 }
        } catch (e: Exception) {
            log("Statistics overflow count unreadable (${e.javaClass.simpleName}); dropped")
            overflowFile.delete()
            null
        }
    }

    /**
     * The server stored [sent] (the count of an [overflow] it accepted); what was dropped since stays,
     * with a new id. False when the file could not be changed (the caller must not send it again in
     * the same run).
     */
    @Synchronized
    fun overflowDelivered(
        sent: Overflow,
        newId: () -> String,
        now: () -> String,
    ): Boolean {
        val current = overflow() ?: return true
        if (current.id != sent.id) return true
        val left = current.dropped - sent.dropped
        if (left <= 0) return overflowFile.delete() || !overflowFile.exists()
        return writeOverflow(Overflow(left, newId(), now()))
    }

    private fun addOverflow(
        count: Int,
        newId: () -> String,
        now: () -> String,
    ) {
        val current = overflow()
        // A count not yet sent keeps its id: it may be in a batch in flight right now, and the server
        // stores an id once. The extra drops go in a new one after that is delivered (overflowDelivered).
        writeOverflow(if (current == null) Overflow(count, newId(), now()) else Overflow(current.dropped + count, current.id, current.time))
    }

    private fun writeOverflow(overflow: Overflow): Boolean {
        val json =
            buildJsonObject {
                put("dropped", JsonPrimitive(overflow.dropped))
                put("id", JsonPrimitive(overflow.id))
                put("time", JsonPrimitive(overflow.time))
            }
        return writeAtomically(overflowFile, json.toString())
    }

    private fun entries(): ArrayDeque<Entry> {
        loaded?.let { return it }
        val out = ArrayDeque<Entry>()
        var broken = 0
        if (file.exists()) {
            try {
                file.forEachLine(Charsets.UTF_8) { line ->
                    if (line.isBlank()) return@forEachLine
                    val entry =
                        try {
                            val json = Json.parseToJsonElement(line).jsonObject
                            idOf(json)?.let { Entry(it, json, line.toByteArray(Charsets.UTF_8).size) }
                        } catch (e: Exception) {
                            null
                        }
                    if (entry == null) broken++ else out.addLast(entry)
                }
            } catch (e: IOException) {
                log("Statistics queue could not be read (${e.javaClass.simpleName}); starting empty")
            }
        }
        loaded = out
        if (broken > 0) {
            log("Statistics queue: $broken unreadable line(s) skipped")
            rewrite(out)
        }
        return out
    }

    private fun rewrite(all: Collection<Entry>) {
        writeAtomically(file, all.joinToString(separator = "") { it.json.toString() + "\n" })
    }

    private fun writeAtomically(
        target: File,
        text: String,
    ): Boolean =
        try {
            dir.mkdirs()
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("rename failed")
            }
            true
        } catch (e: IOException) {
            log("Statistics queue could not be saved (${e.javaClass.simpleName})")
            false
        }

    private fun idOf(json: JsonObject): String? = (json["id"] as? JsonPrimitive)?.content

    companion object {
        /** docs/conventions/telemetry.md › Delivery. */
        const val CAP = 2_000
        const val DROP_CHUNK = 100
    }
}
