package com.plumemu.coredata.backup

import com.plumemu.coredata.settings.PlumSettings
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One box-art file: its path under the box-art folder (`/` between folders) and its size when listed. */
class MediaFile(
    val relativePath: String,
    val file: File,
    val size: Long,
)

/** What goes into one backup; [BackupZip.write] streams it into a zip. */
class BackupContents(
    val manifest: BackupManifest,
    val settings: PlumSettings,
    val account: BackupAccount?,
    /** The snapshot's file ([DatabaseSnapshot]); null for a settings backup. */
    val database: File?,
    val media: List<MediaFile>,
)

/** How far the zip is: media files written of those listed. */
data class BackupProgress(
    val filesDone: Int,
    val filesTotal: Int,
)

/** One entry as [BackupZip.write] wrote it: what [BackupZip.verify] must read back. */
data class WrittenEntry(
    val name: String,
    val size: Long,
    val crc: Long,
)

/**
 * What [BackupZip.write] wrote: media files written, those gone before their turn (deleted
 * meanwhile), and every entry in order with its size and CRC, for [BackupZip.verify].
 */
data class BackupWritten(
    val mediaWritten: Int,
    val mediaMissing: Int,
    val entries: List<WrittenEntry>,
)

/** A written backup that does not read back whole ([BackupZip.verify]); the message names no path. */
class BackupDamagedException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/**
 * Writes a backup zip into a stream, one file at a time with a
 * 64 KB buffer: nothing is held in memory but the buffer, however large the box art. Checks for
 * cancellation between files and every buffer, so Cancel stops within one buffer's write.
 * The caller owns [out] and closes it (this never does); on any failure it deletes what was written.
 * The account is the last entry and the manifest the first.
 *
 * The JSON entries and the database are deflated at one level; box art is STORED (no Deflater at
 * all). The Deflater's level is never changed between entries: on an AYN Thor (build 380)
 * `setLevel(NO_COMPRESSION)` after the database made the next entry unreadable. Its
 * bytes were exactly the level-6 deflate of the picture written twice with the first 512 bytes
 * missing: the platform's Deflater took the input inside the level change, dropped that call's
 * output and was given the same input again.
 */
object BackupZip {
    suspend fun write(
        contents: BackupContents,
        out: OutputStream,
        onProgress: (BackupProgress) -> Unit = {},
    ): BackupWritten = write(contents, out, onProgress, ::ZipOutputStream)

    /** [write] with the zip stream made by [newZip]: tests watch what is asked of it. */
    internal suspend fun write(
        contents: BackupContents,
        out: OutputStream,
        onProgress: (BackupProgress) -> Unit,
        newZip: (OutputStream) -> ZipOutputStream,
    ): BackupWritten {
        val guard = KeepOpen(out)
        val zip = newZip(BufferedOutputStream(guard, BUFFER_BYTES))
        try {
            return writeEntries(contents, zip, onProgress).also {
                zip.finish()
                zip.flush()
            }
        } catch (e: Throwable) {
            // Closing writes the zip's end; after a cancel or a failure nothing more may reach the
            // document (a torn file must not look whole), but the Deflater must still be ended.
            guard.isDiscarding = true
            throw e
        } finally {
            try {
                zip.close()
            } catch (e: IOException) {
                // After a failure its own exception is the one thrown (the stream drops writes then); after
                // a success a failed close is a failed backup.
                if (!guard.isDiscarding) throw e
            }
        }
    }

    private suspend fun writeEntries(
        contents: BackupContents,
        zip: ZipOutputStream,
        onProgress: (BackupProgress) -> Unit,
    ): BackupWritten {
        val buffer = ByteArray(BUFFER_BYTES)
        val created = contents.manifest.createdAtMillis
        val entries = ArrayList<WrittenEntry>(contents.media.size + FIXED_ENTRIES)
        // Never call zip.setLevel here: see the class comment. The default level holds for every deflated entry.
        // The manifest first: a restore can say what the file is before reading the rest.
        entries += zip.putText(BackupEntries.MANIFEST, contents.manifest.toJson().toString(JSON_INDENT), created)
        entries += zip.putText(BackupEntries.SETTINGS, SettingsJson.encode(contents.settings).toString(JSON_INDENT), created)
        contents.database?.let { db -> FileInputStream(db).use { entries += zip.putStream(BackupEntries.DATABASE, it, created, buffer) } }
        val total = contents.media.size
        var written = 0
        var missing = 0
        onProgress(BackupProgress(0, total))
        contents.media.forEachIndexed { index, media ->
            currentCoroutineContext().ensureActive()
            val input =
                try {
                    FileInputStream(media.file)
                } catch (e: FileNotFoundException) {
                    // Deleted since it was listed (a re-download or a reset): the game shows no cover until the next run.
                    null
                }
            if (input == null) {
                missing++
            } else {
                // Box art is WebP or PNG already: deflating it again costs time on a weak device for nothing.
                input.use { entries += zip.putStored(BackupEntries.MEDIA_PREFIX + media.relativePath, it, media.file.lastModified(), buffer) }
                written++
            }
            onProgress(BackupProgress(index + 1, total))
        }
        // The account last: a file torn before the end (a killed process) is then unlikely to hold the password.
        contents.account?.let { entries += zip.putText(BackupEntries.ACCOUNT, it.toJson().toString(JSON_INDENT), created) }
        return BackupWritten(written, missing, entries)
    }

    /**
     * Reads a written backup back from [input] the way Restore does (`ZipInputStream`, local headers)
     * and checks that it holds exactly [written]'s entries, in order, each whole with its size and
     * CRC. Throws [BackupDamagedException] otherwise; any other [IOException] is a failed read of
     * the document. Cancellation is checked per buffer.
     */
    suspend fun verify(
        input: InputStream,
        written: BackupWritten,
    ) {
        val zip = ZipInputStream(BufferedInputStream(input, BUFFER_BYTES))
        val buffer = ByteArray(BUFFER_BYTES)
        written.entries.forEachIndexed { index, expected ->
            val entry =
                damagedOn(index) { zip.nextEntry }
                    ?: throw BackupDamagedException("the file ends after $index of ${written.entries.size} entries")
            if (entry.name != expected.name) throw BackupDamagedException("entry $index is not the one written")
            val crc = CRC32()
            var size = 0L
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = damagedOn(index) { zip.read(buffer) }
                if (read < 0) break
                crc.update(buffer, 0, read)
                size += read
            }
            if (size != expected.size || crc.value != expected.crc) throw BackupDamagedException("entry $index reads back different bytes")
        }
        if (damagedOn(written.entries.size) { zip.nextEntry } != null) throw BackupDamagedException("the file holds more entries than were written")
    }

    /** A zip error while reading back is a damaged file; a failed read of the document stays an [IOException]. */
    private inline fun <T> damagedOn(
        index: Int,
        read: () -> T,
    ): T =
        try {
            read()
        } catch (e: ZipException) {
            throw BackupDamagedException("entry $index does not read back (${e.javaClass.simpleName})", e)
        } catch (e: EOFException) {
            throw BackupDamagedException("the file ends inside entry $index", e)
        }

    /** The caller's stream, never closed from here; after a failure every write is dropped. */
    private class KeepOpen(out: OutputStream) : FilterOutputStream(out) {
        var isDiscarding = false

        override fun write(b: Int) {
            if (!isDiscarding) out.write(b)
        }

        override fun write(
            b: ByteArray,
            off: Int,
            len: Int,
        ) {
            if (!isDiscarding) out.write(b, off, len)
        }

        override fun flush() {
            if (!isDiscarding) out.flush()
        }

        override fun close() = flush()
    }

    /**
     * Every file under [root] with its path from there, sorted, leaving out the box-art run's
     * unfinished downloads (`download-*.part`, `*.tmp`): they are renamed into place when whole.
     */
    fun listMedia(root: File): List<MediaFile> {
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile && !isUnfinished(it.name) }
            .map { MediaFile(it.relativeTo(root).invariantSeparatorsPath, it, it.length()) }
            .sortedBy { it.relativePath }
            .toList()
    }

    private fun isUnfinished(name: String): Boolean = name.endsWith(".tmp") || name.endsWith(".part")

    private suspend fun ZipOutputStream.putText(
        name: String,
        text: String,
        time: Long,
    ): WrittenEntry {
        currentCoroutineContext().ensureActive()
        val entry = ZipEntry(name).also { it.time = time }
        putNextEntry(entry)
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
        return WrittenEntry(name, entry.size, entry.crc)
    }

    private suspend fun ZipOutputStream.putStream(
        name: String,
        input: InputStream,
        time: Long,
        buffer: ByteArray,
    ): WrittenEntry {
        val entry = ZipEntry(name).also { it.time = time }
        putNextEntry(entry)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            write(buffer, 0, read)
        }
        // closeEntry sets the deflated entry's size and CRC from what went through it.
        closeEntry()
        return WrittenEntry(name, entry.size, entry.crc)
    }

    /**
     * One file as a STORED entry, which needs its size and CRC before its bytes: the open file is read
     * once for them, then again from its start. Box art is replaced by renaming a whole new file over
     * it (MediaSaver), which leaves this open file as it was; a file changed in place in between
     * fails the backup (closeEntry checks the CRC of what was copied), never a wrong entry.
     */
    private suspend fun ZipOutputStream.putStored(
        name: String,
        input: FileInputStream,
        time: Long,
        buffer: ByteArray,
    ): WrittenEntry {
        val crc = CRC32()
        var size = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
            size += read
        }
        input.channel.position(0)
        val entry =
            ZipEntry(name).also {
                it.time = time
                it.method = ZipEntry.STORED
                it.size = size
                it.compressedSize = size
                it.crc = crc.value
            }
        putNextEntry(entry)
        var left = size
        while (left > 0) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
            if (read < 0) throw IOException("a box-art file got shorter while it was copied")
            write(buffer, 0, read)
            left -= read
        }
        closeEntry()
        return WrittenEntry(name, size, crc.value)
    }

    private const val BUFFER_BYTES = 64 * 1024
    private const val JSON_INDENT = 2

    /** The manifest, the settings, the database and the account. */
    private const val FIXED_ENTRIES = 4
}
