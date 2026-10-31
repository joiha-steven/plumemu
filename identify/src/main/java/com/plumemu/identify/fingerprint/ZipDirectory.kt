package com.plumemu.identify.fingerprint

import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * One file or directory listed in a zip's central directory (or a 7z header, which leaves the
 * last three fields at their defaults).
 *
 * @property method zip compression method (0 stored, 8 deflate), -1 when not a zip entry.
 * @property localHeaderOffset where the entry's local header starts in the source, already
 *   moved by any data in front of the archive; -1 when not known.
 * @property encrypted general-purpose flag bit 0: the entry's data is encrypted.
 */
internal data class ZipEntryInfo(
    val name: String,
    val crc32: Long,
    val compressedSize: Long,
    val uncompressedSize: Long,
    val isDirectory: Boolean,
    val method: Int = -1,
    val localHeaderOffset: Long = -1,
    val encrypted: Boolean = false,
)

/** What [ZipDirectory.read] found: the entries it could read and why it stopped early, if it did. */
internal data class ZipListing(
    val entries: List<ZipEntryInfo>,
    val notes: List<String>,
)

/**
 * Lists a zip from its central directory only (PKWARE APPNOTE.TXT sections 4.3.12-4.3.16): the
 * End Of Central Directory record, searched backwards in the last 1 KB, 8 KB and, only when a
 * long comment hides it, 65,557 bytes (22-byte record plus the longest comment); the Zip64
 * locator and record when present; then the directory. Local headers are never read and
 * nothing is decompressed, so a zip costs about 1 KB plus its directory (about 50-100 bytes per
 * entry), whatever its size. (At `Depth.FULL`, `ZipEntryContent` opens one entry's local header
 * and data; this class never does.)
 *
 * Names are UTF-8 when general-purpose flag bit 11 is set; otherwise they are code page 437,
 * decoded here as ISO-8859-1. That keeps ASCII exact and maps the few accented CP437 letters to
 * the wrong accented letter, which is acceptable for picking the ROM entry by extension.
 *
 * Bad input never throws: a missing or broken record ends the listing with a note. Only an
 * [IOException] from the source escapes.
 */
internal class ZipDirectory(private val directoryLimit: Int = DEFAULT_DIRECTORY_LIMIT) {

    @Throws(IOException::class)
    fun read(source: RandomAccessSource): ZipListing {
        val notes = mutableListOf<String>()
        val end = findEnd(source, notes) ?: return ZipListing(emptyList(), notes)
        if (end.entryCount == 0L) {
            notes += "zip: the archive lists no entries"
            return ZipListing(emptyList(), notes)
        }
        val start = directoryStart(end)
        if (start != end.directoryOffset) {
            notes += "zip: ${start - end.directoryOffset} bytes before the archive (self-extracting?)"
        }
        if (start + end.directorySize > end.recordPosition || end.directorySize < 0) {
            notes += "zip: central directory runs past its end record; archive truncated or corrupt"
            return ZipListing(emptyList(), notes)
        }
        val length = if (end.directorySize > directoryLimit) {
            notes += "zip: central directory is ${end.directorySize} bytes; read only the first $directoryLimit"
            directoryLimit
        } else {
            end.directorySize.toInt()
        }
        val directory = source.readFully(start, length)
        val entries = parseEntries(directory, start - end.directoryOffset, notes)
        if (entries.size.toLong() != end.entryCount && length.toLong() == end.directorySize) {
            notes += "zip: end record lists ${end.entryCount} entries, directory holds ${entries.size}"
        }
        if (entries.isEmpty() && notes.isEmpty()) notes += "zip: no entry could be read"
        return ZipListing(entries, notes)
    }

    private class EndRecord(
        val recordPosition: Long,
        val entryCount: Long,
        val directorySize: Long,
        val directoryOffset: Long,
    )

    /** An end record found in the tail, with the notes reading it produced. */
    private class EndCandidate(val record: EndRecord?, val notes: List<String>)

    /**
     * Where the central directory really starts. Data prepended to the archive (a
     * self-extracting stub) shifts every stored offset; the directory ends where the end record
     * (or the Zip64 end record) starts, which pins its real position.
     */
    private fun directoryStart(end: EndRecord): Long =
        end.directoryOffset + (end.recordPosition - end.directoryOffset - end.directorySize).coerceAtLeast(0)

    /**
     * Finds the end record. Most zips have no comment, so it is in the last 22 bytes: the last
     * 1 KB is searched first, then 8 KB, then the whole 65,557-byte window, each step fetching
     * only the bytes in front of what it already has. A comment may itself contain the end
     * record's signature, so every candidate is checked before it is taken:
     * - candidates whose comment ends exactly at the end of the file ("exact") are tried in every
     *   step, nearest the end first; the others ("loose", e.g. a zip with bytes appended) only
     *   after the whole window has been searched;
     * - a candidate listing entries is taken only when its central directory fits before it and
     *   starts with an entry signature;
     * - a candidate listing no entries is taken only when it is exact, its directory is empty
     *   and it points at itself.
     * When no candidate passes, the first one tried is used so the listing can say what is
     * wrong with it.
     */
    @Throws(IOException::class)
    private fun findEnd(source: RandomAccessSource, notes: MutableList<String>): EndRecord? {
        var fallback: EndCandidate? = null
        var tail = ByteArray(0)
        var searched = -1
        val loose = mutableListOf<Long>()
        fun tryAt(at: Int, exact: Boolean): EndRecord? {
            val candidateNotes = mutableListOf<String>()
            val record = parseEnd(source, tail, source.size - tail.size, at, candidateNotes)
            if (record != null && isPlausible(source, record, exact)) {
                notes += candidateNotes
                return record
            }
            if (fallback == null) fallback = EndCandidate(record, candidateNotes)
            return null
        }
        for (window in END_SEARCH_WINDOWS) {
            val tailLength = minOf(source.size, window.toLong()).toInt()
            if (tailLength <= tail.size && searched >= 0) break
            tail = source.readFully(source.size - tailLength, tailLength - tail.size) + tail
            // Candidates inside the smaller window were seen already: a comment that fits in
            // the file from there also fitted in that window.
            val firstSeen = tail.size - searched.coerceAtLeast(0)
            searched = tail.size
            val (exactHere, looseHere) = endSignatures(tail, before = firstSeen)
            for (at in exactHere) tryAt(at, exact = true)?.let { return it }
            looseHere.forEach { loose += source.size - tail.size + it }
        }
        for (position in loose) tryAt((position - (source.size - tail.size)).toInt(), exact = false)?.let { return it }
        val chosen = fallback
        if (chosen == null) {
            notes += "zip: no end of central directory record; not a zip, or truncated"
            return null
        }
        notes += chosen.notes
        return chosen.record
    }

    @Throws(IOException::class)
    private fun parseEnd(
        source: RandomAccessSource,
        tail: ByteArray,
        tailStart: Long,
        at: Int,
        notes: MutableList<String>,
    ): EndRecord? {
        if (tail.u16le(at + 4) != 0 || tail.u16le(at + 6) != 0) {
            notes += "zip: split or spanned archive; not read"
            return null
        }
        val position = tailStart + at
        val count = tail.u16le(at + 10).toLong()
        val size = tail.u32le(at + 12)
        val offset = tail.u32le(at + 16)
        val zip64 = readZip64End(source, position, notes)
        if (zip64 != null) return zip64
        if (count == 0xFFFFL || size == MAX_U32 || offset == MAX_U32) {
            notes += "zip: Zip64 values without a readable Zip64 record"
            return null
        }
        return EndRecord(position, count, size, offset)
    }

    @Throws(IOException::class)
    private fun isPlausible(source: RandomAccessSource, end: EndRecord, exact: Boolean): Boolean {
        if (end.entryCount == 0L) {
            return exact && end.directorySize == 0L && end.directoryOffset == end.recordPosition
        }
        val start = directoryStart(end)
        if (end.directorySize < ENTRY_FIXED_SIZE || start < 0 || start + end.directorySize > end.recordPosition) {
            return false
        }
        return source.readFully(start, ENTRY_SIGNATURE.size).startsWith(ENTRY_SIGNATURE)
    }

    /**
     * Positions before [before] of end-record signatures whose comment fits in [tail], nearest
     * the end first: those whose comment ends exactly at the end, and the others.
     */
    private fun endSignatures(tail: ByteArray, before: Int): Pair<List<Int>, List<Int>> {
        val exact = mutableListOf<Int>()
        val loose = mutableListOf<Int>()
        var at = minOf(tail.size - END_RECORD_SIZE, before - 1)
        while (at >= 0) {
            if (tail.startsWith(END_SIGNATURE, at)) {
                val end = at + END_RECORD_SIZE + tail.u16le(at + 20)
                if (end == tail.size) exact += at else if (end < tail.size) loose += at
            }
            at--
        }
        return exact to loose
    }

    /** The Zip64 locator sits right before the end record and points at the Zip64 end record. */
    @Throws(IOException::class)
    private fun readZip64End(source: RandomAccessSource, endPosition: Long, notes: MutableList<String>): EndRecord? {
        if (endPosition < ZIP64_LOCATOR_SIZE) return null
        val locator = source.readFully(endPosition - ZIP64_LOCATOR_SIZE, ZIP64_LOCATOR_SIZE)
        if (locator.size < ZIP64_LOCATOR_SIZE || !locator.startsWith(ZIP64_LOCATOR_SIGNATURE)) return null
        // Data prepended to the archive shifts every offset; the record normally ends where the
        // locator starts, so that position is tried when the stored offset finds nothing.
        val expected = endPosition - ZIP64_LOCATOR_SIZE - ZIP64_RECORD_SIZE
        val candidates = listOf(locator.u64le(8), expected).filter { it in 0..expected }.distinct()
        var found: ByteArray? = null
        var recordPosition = expected
        for (candidate in candidates) {
            val record = source.readFully(candidate, ZIP64_RECORD_SIZE)
            if (record.size == ZIP64_RECORD_SIZE && record.startsWith(ZIP64_RECORD_SIGNATURE)) {
                found = record
                recordPosition = candidate
                break
            }
        }
        if (found == null) {
            notes += "zip: Zip64 locator points at no Zip64 end record"
            return null
        }
        val count = found.u64le(32)
        val size = found.u64le(40)
        val offset = found.u64le(48)
        if (count < 0 || size < 0 || offset < 0) {
            notes += "zip: Zip64 end record holds impossible values"
            return null
        }
        return EndRecord(recordPosition, count, size, offset)
    }

    /** [shift]: bytes in front of the archive, added to every stored local header offset. */
    private fun parseEntries(directory: ByteArray, shift: Long, notes: MutableList<String>): List<ZipEntryInfo> {
        val entries = mutableListOf<ZipEntryInfo>()
        var at = 0
        while (at + ENTRY_FIXED_SIZE <= directory.size) {
            if (!directory.startsWith(ENTRY_SIGNATURE, at)) {
                notes += "zip: central directory entry ${entries.size} has a bad signature; listing stops"
                return entries
            }
            val nameLength = directory.u16le(at + 28)
            val extraLength = directory.u16le(at + 30)
            val commentLength = directory.u16le(at + 32)
            val next = at + ENTRY_FIXED_SIZE + nameLength + extraLength + commentLength
            if (next > directory.size) {
                notes += "zip: central directory entry ${entries.size} is cut off; listing stops"
                return entries
            }
            entries += parseEntry(directory, at, nameLength, extraLength, shift)
            at = next
        }
        return entries
    }

    private fun parseEntry(directory: ByteArray, at: Int, nameLength: Int, extraLength: Int, shift: Long): ZipEntryInfo {
        val flags = directory.u16le(at + 8)
        val nameStart = at + ENTRY_FIXED_SIZE
        val charset = if (flags and FLAG_UTF8 != 0) Charsets.UTF_8 else Charsets.ISO_8859_1
        val name = String(directory, nameStart, nameLength, charset)
        var compressed = directory.u32le(at + 20)
        var uncompressed = directory.u32le(at + 24)
        var localOffset = directory.u32le(at + 42)
        // Zip64 extended information (APPNOTE 4.5.3): only the fields that are 0xFFFFFFFF in the
        // fixed part are present, in this order: uncompressed size, compressed size, local
        // header offset.
        var extra = nameStart + nameLength
        val extraEnd = extra + extraLength
        while (extra + 4 <= extraEnd) {
            val id = directory.u16le(extra)
            val length = directory.u16le(extra + 2)
            val dataEnd = minOf(extra + 4 + length, extraEnd)
            if (id == ZIP64_EXTRA_ID) {
                var field = extra + 4
                if (uncompressed == MAX_U32 && field + 8 <= dataEnd) {
                    uncompressed = directory.u64le(field)
                    field += 8
                }
                if (compressed == MAX_U32 && field + 8 <= dataEnd) {
                    compressed = directory.u64le(field)
                    field += 8
                }
                if (localOffset == MAX_U32 && field + 8 <= dataEnd) localOffset = directory.u64le(field)
            }
            extra += 4 + length
        }
        val isDirectory = name.endsWith("/") || name.endsWith("\\")
        return ZipEntryInfo(
            name = name,
            crc32 = directory.u32le(at + 16),
            compressedSize = compressed,
            uncompressedSize = uncompressed,
            isDirectory = isDirectory,
            method = directory.u16le(at + 10),
            localHeaderOffset = if (localOffset < 0) -1 else localOffset + shift,
            encrypted = flags and FLAG_ENCRYPTED != 0,
        )
    }

    companion object {
        /** Central directories bigger than this are read only this far (4 MB, about 40,000 entries). */
        const val DEFAULT_DIRECTORY_LIMIT = 4 * 1024 * 1024

        /** End record (22 bytes) plus the longest possible comment (65,535 bytes). */
        const val MAX_END_SEARCH = 22 + 0xFFFF

        /** Tail lengths searched for the end record, smallest first. */
        private val END_SEARCH_WINDOWS = intArrayOf(1024, 8 * 1024, MAX_END_SEARCH)

        // Record signatures: "PK" and two bytes, as written in the file.
        private val END_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x05, 0x06)
        private const val END_RECORD_SIZE = 22
        private val ZIP64_LOCATOR_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x06, 0x07)
        private const val ZIP64_LOCATOR_SIZE = 20
        private val ZIP64_RECORD_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x06, 0x06)
        private const val ZIP64_RECORD_SIZE = 56
        private val ENTRY_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x01, 0x02)
        private const val ENTRY_FIXED_SIZE = 46
        private const val ZIP64_EXTRA_ID = 0x0001
        private const val FLAG_UTF8 = 1 shl 11
        private const val FLAG_ENCRYPTED = 1
        private val MAX_U32 = UInt.MAX_VALUE.toLong()
    }
}

/**
 * The entry that is the game in a console ROM zip: directories, macOS resource forks and
 * text, image, link and checksum files are skipped, then the largest remaining entry wins (the
 * first one listed on a tie). Null when nothing is left.
 */
internal fun pickRomEntry(entries: List<ZipEntryInfo>): ZipEntryInfo? =
    // maxByOrNull keeps the first of equal elements.
    entries.filter { isRomCandidate(it) }.maxByOrNull { it.uncompressedSize }

/** Files, directories not counted: many of them means an arcade set, not one console ROM. */
internal fun countFiles(entries: List<ZipEntryInfo>): Int = entries.count { !it.isDirectory }

private fun isRomCandidate(entry: ZipEntryInfo): Boolean {
    if (entry.isDirectory) return false
    val name = entry.name.replace('\\', '/')
    if (name.startsWith("__MACOSX/") || name.substringAfterLast('/') == ".DS_Store") return false
    return extensionOf(name) !in JUNK_EXTENSIONS
}

/** Lower-case extension of the last path segment, or "" when there is none. */
internal fun extensionOf(fileName: String): String {
    val last = fileName.substringAfterLast('/').substringAfterLast('\\')
    val dot = last.lastIndexOf('.')
    return if (dot <= 0) "" else last.substring(dot + 1).lowercase()
}

private val JUNK_EXTENSIONS = setOf(
    "txt", "nfo", "diz", "jpg", "jpeg", "png", "gif", "url", "htm", "html", "sfv", "md5", "sha1",
)
