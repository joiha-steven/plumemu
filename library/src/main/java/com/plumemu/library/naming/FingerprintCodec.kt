package com.plumemu.library.naming

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.TitleKind
import org.json.JSONException
import org.json.JSONObject

/**
 * `game.fingerprint`: the [Fingerprints] the identifier read, as a small JSON object, so a rescan
 * passes them back as `IdentifyRequest.known` and reads nothing. Notes are left out (they explain
 * a read, they do not name a game). A stored value this version cannot read (damaged, or written
 * by a later format) decodes to null and the file is simply read again.
 */
internal object FingerprintCodec {
    /**
     * Bumped when a field changes meaning; older values are then read again from the file. Not
     * for a new field: the scrape hints decode these values too, and a bump would leave every
     * row not named again without hints. `titleId` and `titleKind` are optional.
     * A reader that learns to read more for one system raises `FingerprintReader.readerVersion`
     * instead: stored as `rv`, it makes [isCurrent] false for that system's older values only.
     */
    private const val VERSION = 1

    /**
     * [fingerprints] as stored for a file of [systemId]; the reader version of that system goes
     * with it (`rv`, left out when 1, so values of other systems are unchanged).
     */
    fun encode(fingerprints: Fingerprints, systemId: String? = null, contentReadDone: Boolean = false): String = JSONObject().apply {
        put("v", VERSION)
        if (contentReadDone) put(CONTENT_READ, CONTENT_READ_DONE)
        FingerprintReader.readerVersion(systemId).takeIf { it > 1 }?.let { put("rv", it) }
        put("size", fingerprints.size)
        put("container", fingerprints.container.name)
        putOpt("inner", fingerprints.innerName)
        putOpt("entries", fingerprints.entryCount)
        putOpt("contentSize", fingerprints.contentSize)
        putOpt("crc", fingerprints.crc32)
        putOpt("crcSource", fingerprints.crcSource?.name)
        if (fingerprints.headerBytesSkipped != 0) put("header", fingerprints.headerBytesSkipped)
        putOpt("code", fingerprints.productCode)
        putOpt("codeKind", fingerprints.codeKind?.name)
        putOpt("title", fingerprints.internalTitle)
        if (fingerprints.titleIsCanonical) put("canonical", true)
        putOpt("chd", fingerprints.chdSha1)
        putOpt("archiveCrc", fingerprints.archiveCrc32)
        putOpt("titleId", fingerprints.titleId)
        putOpt("titleKind", fingerprints.titleKind?.name)
    }.toString()

    /**
     * False when [stored] was read by an older reader of [systemId] than this app's (a Switch or
     * 3DS file cached by an older version): the file is then read again, once. Unreadable values
     * count as current here; [decode] rejects them.
     */
    fun isCurrent(stored: String, systemId: String): Boolean {
        val version = try {
            JSONObject(stored).optInt("rv", 1)
        } catch (e: JSONException) {
            return true
        }
        return version >= FingerprintReader.readerVersion(systemId)
    }

    /**
     * The content pass's stamp (`full`, [com.plumemu.library.scan.ContentPass]): `"done"` once it
     * read the file whole (with or without a CRC to show for it) or gave up on it, else the number
     * of reads that failed. Optional, so values without it decode as before; a new QUICK read (the
     * file changed) writes a value without it, and the file is read whole again.
     */
    private const val CONTENT_READ = "full"
    private const val CONTENT_READ_DONE = "done"

    /** What the content pass's SQL looks for in a stored value (core-data `ContentReadFilter`). */
    const val LOOSE_MARK = "\"container\":\"NONE\""
    const val ZIP_MARK = "\"container\":\"ZIP\""
    const val WHOLE_FILE_CRC_MARK = "\"crcSource\":\"WHOLE_FILE\""
    const val ZIP_CONTENT_CRC_MARK = "\"crcSource\":\"ZIP_ENTRY_CONTENT\""

    /** Any string value of the stamp: done. A count of failures is a number, so it does not match. */
    const val CONTENT_READ_DONE_MARK = "\"" + CONTENT_READ + "\":\""

    /**
     * [stored] with one more failed content read counted, or stamped done once [maxFailures] reads
     * failed (a broken file is not read on every run); null when [stored] is not readable JSON.
     */
    fun withContentReadFailure(stored: String, maxFailures: Int): String? = try {
        val json = JSONObject(stored)
        val failures = json.optInt(CONTENT_READ, 0) + 1
        if (failures >= maxFailures) json.put(CONTENT_READ, CONTENT_READ_DONE) else json.put(CONTENT_READ, failures)
        json.toString()
    } catch (e: JSONException) {
        null
    }

    /** The fingerprints in [stored], or null when there are none or they cannot be read. */
    fun decode(stored: String?): Fingerprints? {
        if (stored.isNullOrEmpty()) return null
        return try {
            val json = JSONObject(stored)
            if (json.optInt("v") != VERSION) return null
            Fingerprints(
                size = json.getLong("size"),
                container = Container.valueOf(json.getString("container")),
                innerName = json.optStringOrNull("inner"),
                entryCount = json.optLongOrNull("entries")?.toInt(),
                contentSize = json.optLongOrNull("contentSize"),
                crc32 = json.optLongOrNull("crc"),
                crcSource = json.optStringOrNull("crcSource")?.let { CrcSource.valueOf(it) },
                headerBytesSkipped = json.optInt("header", 0),
                productCode = json.optStringOrNull("code"),
                codeKind = json.optStringOrNull("codeKind")?.let { CodeKind.valueOf(it) },
                internalTitle = json.optStringOrNull("title"),
                titleIsCanonical = json.optBoolean("canonical", false),
                chdSha1 = json.optStringOrNull("chd"),
                archiveCrc32 = json.optLongOrNull("archiveCrc"),
                titleId = json.optStringOrNull("titleId"),
                titleKind = json.optStringOrNull("titleKind")?.let { TitleKind.valueOf(it) },
            )
        } catch (e: JSONException) {
            null
        } catch (e: IllegalArgumentException) {
            // An enum name this version does not know.
            null
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) getLong(key) else null
}
