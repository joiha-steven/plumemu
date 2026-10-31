package com.plumemu.graph

import com.plumemu.coredata.backup.BackupKind
import java.io.File
import org.json.JSONException
import org.json.JSONObject

/**
 * A backup is being written into [documentId] (the picked document's URI), a [kind] backup started
 * at [startedAtMillis], named [name] by the picker's app. Written before the first byte and removed
 * at every normal end: one still there at the next start means the process died mid-backup, and the
 * torn file (which may hold the password) is deleted then.
 */
internal data class BackupMarker(
    val documentId: String,
    val kind: BackupKind,
    val startedAtMillis: Long,
    val name: String?,
) {
    // The URI and the name are the person's file, not for logs.
    override fun toString(): String = "BackupMarker(kind=$kind, startedAtMillis=$startedAtMillis)"
}

/** Where the marker lives; the graph's is [FileBackupMarkers]. */
internal interface BackupMarkers {
    fun write(marker: BackupMarker)

    /** Null when no backup was running; a marker that cannot be read is null too, and is logged by the caller's store. */
    fun read(): BackupMarker?

    fun clear()
}

/** The marker as a small JSON file in app storage, written whole (a temporary file renamed into place). */
internal class FileBackupMarkers(
    private val file: File,
    private val log: (String) -> Unit,
) : BackupMarkers {
    override fun write(marker: BackupMarker) {
        file.parentFile?.mkdirs()
        val temp = File(file.path + ".tmp")
        val json =
            JSONObject()
                .put("document", marker.documentId)
                .put("kind", marker.kind.code)
                .put("startedAtMillis", marker.startedAtMillis)
                .put("name", marker.name ?: JSONObject.NULL)
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            temp.delete()
            throw java.io.IOException("the backup marker could not be stored")
        }
    }

    override fun read(): BackupMarker? {
        if (!file.isFile) return null
        return try {
            val o = JSONObject(file.readText())
            val kind = BackupKind.entries.firstOrNull { it.code == o.getString("kind") } ?: BackupKind.Everything
            BackupMarker(o.getString("document"), kind, o.getLong("startedAtMillis"), if (o.isNull("name")) null else o.getString("name"))
        } catch (e: JSONException) {
            // Torn while being written: no document is known, so there is nothing to delete.
            log("Backup: the marker of an interrupted backup could not be read (${e.javaClass.simpleName}); removed")
            clear()
            null
        }
    }

    override fun clear() {
        // A marker that outlives a finished backup would delete the person's good file at the next start.
        if (file.exists() && !file.delete()) log("Backup: the marker could not be removed; the next start will treat the backup as interrupted")
        File(file.path + ".tmp").delete()
    }
}
