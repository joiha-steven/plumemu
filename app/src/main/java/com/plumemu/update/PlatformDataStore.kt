package com.plumemu.update

import android.util.AtomicFile
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** A downloaded copy of the platform data kept on the device. [sha256] is the manifest's (of the `.json.gz`); [json] is the gunzipped file. */
data class StoredPlatformData(
    val dataVersion: Int,
    val date: String,
    val sha256: String,
    val json: String,
)

/**
 * The downloaded platform data under `noBackupFilesDir/platform-data/`: the data file and a small
 * record (dataVersion, date, sha256), each written whole through [AtomicFile], data first. The record
 * also names the data file's own SHA-256, so a data file that does not belong to the record (a crash
 * between the two writes, a damaged file) reads as nothing. Not in a backup: a restored copy could be
 * older than the APK's.
 */
class PlatformDataStore(
    private val dir: File,
    private val log: (String) -> Unit,
) {
    private val dataFile get() = File(dir, DATA_FILE)
    private val recordFile get() = File(dir, RECORD_FILE)

    /** The one lock for this folder in the whole process: start selection, the updater and a delete never interleave. */
    private val lock = locks.getOrPut(dir.absoluteFile.normalize().path) { Any() }

    /** Only the stored version (reads the small record, not the data file); null when there is none or it is unreadable. */
    fun readVersion(): Int? =
        synchronized(lock) {
            try {
                if (recordFile.exists()) JSONObject(String(AtomicFile(recordFile).readFully(), Charsets.UTF_8)).getInt("dataVersion") else null
            } catch (e: IOException) {
                null
            } catch (e: JSONException) {
                null
            }
        }

    /** Null when there is no copy or it is unreadable (one log line then). */
    fun read(): StoredPlatformData? =
        synchronized(lock) { readLocked() }

    private fun readLocked(): StoredPlatformData? {
        if (!recordFile.exists()) return null
        return try {
            val record = JSONObject(String(AtomicFile(recordFile).readFully(), Charsets.UTF_8))
            val bytes = AtomicFile(dataFile).readFully()
            if (sha256Hex(bytes) != record.getString("jsonSha256")) {
                log("Stored platform data does not match its record; ignoring it")
                return null
            }
            StoredPlatformData(record.getInt("dataVersion"), record.getString("date"), record.getString("sha256"), String(bytes, Charsets.UTF_8))
        } catch (e: IOException) {
            log("Stored platform data unreadable (${e.javaClass.simpleName}); ignoring it")
            null
        } catch (e: JSONException) {
            log("Stored platform data record malformed; ignoring it")
            null
        }
    }

    /** Throws [IOException] when it cannot be written; the previous copy stays then. */
    fun write(data: StoredPlatformData) = synchronized(lock) {
        dir.mkdirs()
        val bytes = data.json.toByteArray(Charsets.UTF_8)
        writeAtomically(dataFile, bytes)
        val record =
            JSONObject()
                .put("dataVersion", data.dataVersion)
                .put("date", data.date)
                .put("sha256", data.sha256)
                .put("jsonSha256", sha256Hex(bytes))
        writeAtomically(recordFile, record.toString().toByteArray(Charsets.UTF_8))
    }

    /** Forgets the copy (it is not newer than the APK's any more). The record goes first: without it the data file is never read. */
    fun delete() =
        synchronized(lock) {
            AtomicFile(recordFile).delete()
            AtomicFile(dataFile).delete()
        }

    private fun writeAtomically(
        file: File,
        bytes: ByteArray,
    ) {
        val atomic = AtomicFile(file)
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (e: IOException) {
            atomic.failWrite(out)
            throw e
        }
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private companion object {
        val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
        const val DATA_FILE = "platforms.json"
        const val RECORD_FILE = "record.json"
    }
}
