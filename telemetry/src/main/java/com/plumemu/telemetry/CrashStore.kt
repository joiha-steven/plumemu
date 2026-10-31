package com.plumemu.telemetry

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File

/**
 * Crash reports waiting to be sent: one JSON file per crash in [dir], already in the shape of the `/api/v1/crashes` item. Written by the crash handler as the app dies and by the start's
 * read of Android's exit reasons; sent and deleted by [Telemetry]'s send; deleted unsent when
 * statistics are off. At most [MAX_KEPT] are kept, the newest.
 */
internal class CrashStore(
    val dir: File,
    private val log: (String) -> Unit,
) {
    class Entry(
        val file: File,
        val json: JsonObject,
        /** Its size in the request, in UTF-8 bytes. */
        val bytes: Int,
    )

    /**
     * Writes one crash; a temporary file renamed into place, so a half-written file is never read.
     * Plain blocking file calls: the crash handler uses it on the dying thread.
     */
    fun write(
        crash: JsonObject,
        timeMillis: Long,
        id: String,
    ): File {
        dir.mkdirs()
        val name = "${timeMillis.toString().padStart(TIME_DIGITS, '0')}-$id"
        val temporary = File(dir, "$name.tmp")
        temporary.writeText(crash.toString(), Charsets.UTF_8)
        val file = File(dir, "$name$SUFFIX")
        if (!temporary.renameTo(file)) {
            temporary.delete()
            throw java.io.IOException("crash file not renamed")
        }
        return file
    }

    /** The crash files, oldest first; an unreadable one is deleted with a log line. */
    fun entries(): List<Entry> =
        files().mapNotNull { file ->
            try {
                val text = file.readText(Charsets.UTF_8)
                Entry(file, Json.parseToJsonElement(text).jsonObject, text.toByteArray(Charsets.UTF_8).size)
            } catch (e: Exception) {
                log("Crash report unreadable (${e.javaClass.simpleName}); deleted")
                file.delete()
                null
            }
        }

    fun isEmpty(): Boolean = files().isEmpty()

    fun delete(entries: Collection<Entry>) {
        entries.forEach { if (!it.file.delete() && it.file.exists()) log("Crash report not deleted") }
    }

    /** Statistics off: every crash goes, sent or not. */
    fun clear() {
        files().forEach { it.delete() }
        dir.listFiles { file -> file.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    /** Keeps the newest [max]; the older ones are deleted. */
    fun prune(max: Int = MAX_KEPT) {
        val all = files()
        if (all.size > max) {
            all.take(all.size - max).forEach { it.delete() }
            log("Crash reports: ${all.size - max} older ones deleted (at most $max kept)")
        }
    }

    private fun files(): List<File> = dir.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }?.sortedBy { it.name }.orEmpty()

    companion object {
        const val MAX_KEPT = 10
        const val SUFFIX = ".json"

        /** Milliseconds padded, so names sort by time. */
        private const val TIME_DIGITS = 15

        /** Under the module's folder (`noBackupFilesDir/telemetry`). */
        fun dirIn(telemetryDir: File): File = File(telemetryDir, "crashes")
    }
}
