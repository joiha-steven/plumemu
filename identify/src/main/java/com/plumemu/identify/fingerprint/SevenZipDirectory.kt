package com.plumemu.identify.fingerprint

import com.plumemu.identify.codec.lzma.LzmaException
import com.plumemu.identify.codec.lzma.LzmaProperties
import com.plumemu.identify.codec.lzma.SourceInput
import com.plumemu.identify.codec.lzma.lzma2Decode
import com.plumemu.identify.codec.lzma.lzmaDecode
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.zip.CRC32

/**
 * [ZipEntryInfo.crc32] of a 7z file whose CRC the header does not list (7z lists one for every
 * file with data in practice, but the format makes it optional).
 */
internal const val CRC_NOT_LISTED = -1L

/**
 * Lists a 7z archive from its header only (7-Zip `DOC/7zFormat.txt`, `CPP/7zip/Archive/7z/7zIn.cpp`):
 * the name, size and CRC of every file, in the same [ZipListing] shape as [ZipDirectory] so the
 * ROM-entry choice ([pickRomEntry]) is the same. File data is never decompressed; a solid archive
 * lists its CRCs in the header too.
 *
 * What is read:
 * 1. The 32-byte signature header: signature, version (major 0), the CRC of the next 20 bytes,
 *    then the offset (from byte 32), size and CRC of the next header.
 * 2. The next header, its CRC checked. 7-Zip compresses it: it is then a `kEncodedHeader` whose
 *    streams info names one folder of one coder, LZMA (`030101`), LZMA2 (`21`) or Copy (`00`).
 *    That packed stream (decoded to at most [decodedLimit]) is decoded and its CRC checked. The
 *    next header and the packed stream share one budget: at most [readLimit] bytes together.
 *    Any other coder (BCJ, PPMd, Deflate, AES for an encrypted header) gives a note; LZMA
 *    properties with lc + lp above 4 too, so a hostile header cannot make the decoder allocate
 *    its 6 MB literal table.
 * 3. The real `kHeader`: the main streams info (folder sizes and CRCs, `kSubStreamsInfo` with
 *    the size and CRC of each file) and the files info (`kName` UTF-16LE names, `kEmptyStream`,
 *    `kEmptyFile`; every other property, whatever its id, is skipped by its size). An empty stream that is not an
 *    empty file is a directory.
 *
 * Every count read from the file is checked against the bytes left in the header before
 * anything is allocated. Bad input never throws: the listing ends with a note. Only an
 * [IOException] from the source escapes. [ZipEntryInfo.compressedSize] is -1 (7z does not list
 * it per file) and [ZipEntryInfo.crc32] is [CRC_NOT_LISTED] when the header has no CRC.
 */
internal class SevenZipDirectory(
    private val readLimit: Int = DEFAULT_READ_LIMIT,
    private val decodedLimit: Int = DEFAULT_DECODED_LIMIT,
) {
    /** Broken or unsupported archive; the message becomes the note after "7z: ". */
    private class Unread(message: String) : Exception(message)

    private class Coder(val id: String, val props: ByteArray, val inStreams: Int, val outStreams: Int)

    private class Folder(val coders: List<Coder>, val mainOut: Int, val outStreams: Int) {
        var unpackSizes = LongArray(0)
        var crc: Long? = null
        val unpackSize: Long get() = unpackSizes[mainOut]
    }

    private class Stream(val size: Long, val crc: Long?)

    private class StreamsInfo(val packPos: Long, val packSizes: LongArray, val folders: List<Folder>, val streams: List<Stream>)

    @Throws(IOException::class)
    fun read(source: RandomAccessSource): ZipListing {
        val notes = mutableListOf<String>()
        val entries = try {
            readEntries(source, notes)
        } catch (e: Unread) {
            notes += "7z: ${e.message}"
            emptyList()
        } catch (e: LzmaException) {
            notes += "7z: header LZMA data damaged (${e.message})"
            emptyList()
        }
        return ZipListing(entries, notes)
    }

    @Throws(IOException::class, Unread::class, LzmaException::class)
    private fun readEntries(source: RandomAccessSource, notes: MutableList<String>): List<ZipEntryInfo> {
        val start = source.readFully(0, SIGNATURE_HEADER)
        if (start.size < SIGNATURE_HEADER || !start.startsWith(SIGNATURE)) throw Unread("archive is truncated (signature header)")
        if (start.u8(6) != 0) throw Unread("format version ${start.u8(6)}.${start.u8(7)} not read")
        if (crc(start, 12, 20) != start.u32le(8)) throw Unread("signature header CRC does not match")
        val nextOffset = start.u64le(12)
        val nextSize = start.u64le(20)
        if (nextSize == 0L) return emptyList()
        val available = source.size - SIGNATURE_HEADER
        if (nextOffset !in 0..available || nextSize !in 1..available - nextOffset) {
            throw Unread("archive is truncated (the header lies past the end of the file)")
        }
        if (nextSize > readLimit) throw Unread("header of $nextSize bytes is over the $readLimit-byte read limit")
        val raw = source.readFully(SIGNATURE_HEADER + nextOffset, nextSize.toInt())
        if (crc(raw, 0, raw.size) != start.u32le(28)) throw Unread("header CRC does not match")
        val header = if (raw[0].toInt() == K_ENCODED_HEADER) decodeHeader(source, raw) else raw
        if (header.isEmpty() || header[0].toInt() != K_HEADER) throw Unread("the next header is not a header (property ${header.firstOrNull()})")
        return parseHeader(HeaderBytes(header, 1), notes)
    }

    /** Decodes the packed stream a `kEncodedHeader` describes; returns the real header. */
    @Throws(IOException::class, Unread::class, LzmaException::class)
    private fun decodeHeader(source: RandomAccessSource, raw: ByteArray): ByteArray {
        val info = readStreamsInfo(HeaderBytes(raw, 1))
        val folder = info.folders.singleOrNull() ?: throw Unread("encoded header has ${info.folders.size} folders, not 1")
        val coder = folder.coders.singleOrNull()?.takeIf { it.inStreams == 1 && it.outStreams == 1 }
        if (coder == null || coder.id !in HEADER_CODERS) {
            val names = folder.coders.joinToString("+") { coderName(it.id) }
            if (folder.coders.any { it.id == AES }) throw Unread("header is encrypted ($names), not read")
            throw Unread("header coder $names not read (only LZMA, LZMA2 and Copy)")
        }
        val packSize = info.packSizes.firstOrNull() ?: throw Unread("encoded header lists no packed stream")
        val unpackSize = folder.unpackSize
        // One read budget for the next header and the packed header together.
        if (packSize > readLimit - raw.size) {
            throw Unread("compressed header of $packSize bytes after a ${raw.size}-byte next header is over the $readLimit-byte read limit")
        }
        if (unpackSize > decodedLimit) throw Unread("header of $unpackSize bytes is over the $decodedLimit-byte decoded limit")
        val packStart = SIGNATURE_HEADER + info.packPos
        if (info.packPos !in 0..source.size || packSize > source.size - packStart) {
            throw Unread("archive is truncated (the compressed header lies past the end of the file)")
        }
        val size = unpackSize.toInt()
        val out = when (coder.id) {
            COPY -> if (packSize == unpackSize) source.readFully(packStart, size) else throw Unread("stored header sizes differ")
            LZMA -> {
                val props = LzmaProperties.fromBlob(coder.props)
                if (props.lc + props.lp > MAX_LC_PLUS_LP) throw Unread("header LZMA properties lc=${props.lc} lp=${props.lp} not read")
                lzmaDecode(SourceInput(source, packStart, packSize), props, size, knownSize = false)
            }
            else -> {
                if (coder.props.size != 1) throw Unread("header LZMA2 properties are ${coder.props.size} bytes, not 1")
                lzma2Decode(SourceInput(source, packStart, packSize), coder.props[0].toInt() and 0xFF, size)
            }
        }
        if (out.size != size) throw Unread("header decodes to ${out.size} of $size bytes")
        folder.crc?.let { if (crc(out, 0, out.size) != it) throw Unread("decoded header CRC does not match") }
        return out
    }

    @Throws(Unread::class)
    private fun parseHeader(r: HeaderBytes, notes: MutableList<String>): List<ZipEntryInfo> {
        var type = r.id()
        if (type == K_ARCHIVE_PROPERTIES) {
            while (r.id() != K_END) r.skipData()
            type = r.id()
        }
        if (type == K_ADDITIONAL_STREAMS_INFO) {
            readStreamsInfo(r)
            type = r.id()
        }
        var streams = emptyList<Stream>()
        if (type == K_MAIN_STREAMS_INFO) {
            val main = readStreamsInfo(r)
            if (main.folders.any { folder -> folder.coders.any { it.id == AES } }) {
                notes += "7z: file data is encrypted; the CRCs listed are of the files before encryption"
            }
            streams = main.streams
            type = r.id()
        }
        var entries = emptyList<ZipEntryInfo>()
        if (type == K_FILES_INFO) {
            entries = readFiles(r, streams)
            type = r.id()
        }
        if (type != K_END) throw Unread("unexpected property $type in the header")
        return entries
    }

    private fun readStreamsInfo(r: HeaderBytes): StreamsInfo {
        var packPos = 0L
        var packSizes = LongArray(0)
        var folders = emptyList<Folder>()
        var type = r.id()
        if (type == K_PACK_INFO) {
            packPos = r.size()
            packSizes = readPackSizes(r)
            type = r.id()
        }
        if (type == K_UNPACK_INFO) {
            folders = readFolders(r)
            type = r.id()
        }
        var streams = folders.map { Stream(it.unpackSize, it.crc) }
        if (type == K_SUBSTREAMS_INFO) {
            streams = readSubStreams(r, folders)
            type = r.id()
        }
        if (type != K_END) throw Unread("unexpected property $type in a streams info")
        return StreamsInfo(packPos, packSizes, folders, streams)
    }

    private fun readPackSizes(r: HeaderBytes): LongArray {
        val count = r.count("packed streams")
        r.waitFor(K_SIZE)
        val sizes = LongArray(count) { r.size() }
        while (true) {
            val type = r.id()
            if (type == K_END) break
            if (type == K_CRC) r.digests(count) else r.skipData()
        }
        return sizes
    }

    private fun readFolders(r: HeaderBytes): List<Folder> {
        r.waitFor(K_FOLDER)
        val count = r.count("folders")
        if (r.u8() != 0) throw Unread("folders stored outside the header not read")
        val folders = List(count) { readFolder(r) }
        r.waitFor(K_CODERS_UNPACK_SIZE)
        for (folder in folders) folder.unpackSizes = LongArray(folder.outStreams) { r.size() }
        while (true) {
            val type = r.id()
            if (type == K_END) break
            if (type == K_CRC) {
                r.digests(count).forEachIndexed { i, crc -> folders[i].crc = crc }
            } else {
                r.skipData()
            }
        }
        return folders
    }

    private fun readFolder(r: HeaderBytes): Folder {
        val numCoders = r.count("coders", MAX_FOLDER_STREAMS)
        if (numCoders == 0) throw Unread("a folder has no coder")
        val coders = List(numCoders) {
            val flags = r.u8()
            if (flags and FLAG_ALTERNATIVES != 0) throw Unread("alternative coder methods not read")
            val id = hex(r.bytes(flags and ID_SIZE_MASK))
            val complex = flags and FLAG_COMPLEX != 0
            val inStreams = if (complex) r.count("coder inputs", MAX_FOLDER_STREAMS) else 1
            val outStreams = if (complex) r.count("coder outputs", MAX_FOLDER_STREAMS) else 1
            val props = if (flags and FLAG_PROPERTIES != 0) r.bytes(r.count("coder property bytes")) else ByteArray(0)
            Coder(id, props, inStreams, outStreams)
        }
        val totalIn = coders.sumOf { it.inStreams }
        val totalOut = coders.sumOf { it.outStreams }
        if (totalOut == 0 || totalIn > MAX_FOLDER_STREAMS || totalOut > MAX_FOLDER_STREAMS) {
            throw Unread("a folder has $totalIn coder inputs and $totalOut outputs")
        }
        val boundOut = BooleanArray(totalOut)
        repeat(totalOut - 1) {
            if (r.count("bind pair input") >= totalIn) throw Unread("a bind pair names a missing input")
            val out = r.count("bind pair output")
            if (out >= totalOut) throw Unread("a bind pair names a missing output")
            boundOut[out] = true
        }
        val packed = totalIn - (totalOut - 1)
        if (packed < 1) throw Unread("a folder has no packed stream")
        if (packed > 1) repeat(packed) { if (r.count("packed stream index") >= totalIn) throw Unread("a packed stream names a missing input") }
        val mainOut = boundOut.indexOfFirst { !it }
        if (mainOut < 0) throw Unread("a folder has no unbound output")
        return Folder(coders, mainOut, totalOut)
    }

    /** One [Stream] per file with data: sizes and CRCs from `kSubStreamsInfo`. */
    private fun readSubStreams(r: HeaderBytes, folders: List<Folder>): List<Stream> {
        val perFolder = IntArray(folders.size) { 1 }
        var type = r.id()
        while (type != K_SIZE && type != K_CRC && type != K_END) {
            if (type == K_NUM_UNPACK_STREAM) {
                for (i in folders.indices) perFolder[i] = r.count("streams in a folder")
            } else {
                r.skipData()
            }
            type = r.id()
        }
        // Each stream but a folder's last needs at least one byte for its size.
        val total = perFolder.fold(0L) { sum, n -> sum + n }
        if (total > r.remaining.toLong() + folders.size) throw Unread("$total streams listed in a header of ${r.size} bytes")
        val sizes = ArrayList<Long>(total.toInt())
        for ((i, folder) in folders.withIndex()) {
            val n = perFolder[i]
            if (n == 0) continue
            if (n > 1 && type != K_SIZE) throw Unread("stream sizes missing in a folder of $n streams")
            var sum = 0L
            if (type == K_SIZE) {
                repeat(n - 1) {
                    val size = r.size()
                    sum += size
                    if (sum !in 0..folder.unpackSize) throw Unread("stream sizes exceed their folder")
                    sizes += size
                }
            }
            sizes += folder.unpackSize - sum
        }
        if (type == K_SIZE) type = r.id()
        // Folders of one stream whose CRC the folder already lists need no digest here.
        val known = folders.indices.map { perFolder[it] == 1 && folders[it].crc != null }
        val crcs = arrayOfNulls<Long>(sizes.size)
        var at = 0
        for ((i, folder) in folders.withIndex()) {
            if (known[i]) crcs[at] = folder.crc
            at += perFolder[i]
        }
        while (type != K_END) {
            if (type == K_CRC) {
                val digests = r.digests(sizes.size - known.count { it })
                var d = 0
                at = 0
                for (i in folders.indices) {
                    if (!known[i]) repeat(perFolder[i]) { crcs[at + it] = digests[d++] }
                    at += perFolder[i]
                }
            } else {
                r.skipData()
            }
            type = r.id()
        }
        return sizes.mapIndexed { i, size -> Stream(size, crcs[i]) }
    }

    private fun readFiles(r: HeaderBytes, streams: List<Stream>): List<ZipEntryInfo> {
        val numFiles = r.count("files", maxOf(r.remaining.toLong() * 8, streams.size.toLong()))
        if (numFiles > MAX_FILES) throw Unread("$numFiles files listed; more than $MAX_FILES not read")
        var emptyStream = BooleanArray(numFiles)
        var emptyFile = BooleanArray(0)
        var names: List<String>? = null
        while (true) {
            // Any id is a file property, skipped by its size when unknown, as 7-Zip does (newer
            // versions add properties; ids above 0x7F included).
            val type = r.number()
            if (type == K_END.toLong()) break
            val size = r.count("bytes of a file property")
            val end = r.position + size
            when (type) {
                K_EMPTY_STREAM.toLong() -> {
                    emptyStream = r.bits(numFiles)
                    emptyFile = BooleanArray(emptyStream.count { it })
                }
                K_EMPTY_FILE.toLong() -> emptyFile = r.bits(emptyFile.size)
                K_NAME.toLong() -> names = readNames(r, numFiles, end)
            }
            if (r.position > end) throw Unread("file property $type runs past its size")
            r.position = end
        }
        val entries = ArrayList<ZipEntryInfo>(numFiles)
        var stream = 0
        var empty = 0
        for (i in 0 until numFiles) {
            val name = names?.get(i) ?: "file$i"
            if (emptyStream[i]) {
                val isFile = emptyFile[empty++]
                entries += ZipEntryInfo(name, 0, -1, 0, isDirectory = !isFile)
            } else {
                val s = streams.getOrNull(stream++) ?: throw Unread("more files with data than streams")
                entries += ZipEntryInfo(name, s.crc ?: CRC_NOT_LISTED, -1, s.size, isDirectory = false)
            }
        }
        return entries
    }

    private fun readNames(r: HeaderBytes, numFiles: Int, end: Int): List<String> {
        if (r.u8() != 0) throw Unread("names stored outside the header not read")
        val names = ArrayList<String>(numFiles)
        val chars = StringBuilder()
        while (names.size < numFiles) {
            if (r.position + 2 > end) throw Unread("names end before the last file")
            val unit = r.u8() or (r.u8() shl 8)
            if (unit == 0) {
                names += chars.toString()
                chars.setLength(0)
            } else {
                chars.append(unit.toChar())
            }
        }
        return names
    }

    /** A cursor over header bytes; reading past the end is an [Unread], never an exception. */
    private class HeaderBytes(private val bytes: ByteArray, var position: Int) {
        val size: Int get() = bytes.size
        val remaining: Int get() = bytes.size - position

        fun u8(): Int {
            if (position >= bytes.size) throw Unread("header ends early")
            return bytes[position++].toInt() and 0xFF
        }

        fun bytes(count: Int): ByteArray {
            if (count > remaining) throw Unread("header ends early")
            return bytes.copyOfRange(position, position + count).also { position += count }
        }

        /** A 7z number: the leading 1 bits of the first byte say how many bytes follow. */
        fun number(): Long {
            val first = u8()
            var mask = 0x80
            var value = 0L
            for (i in 0 until 8) {
                if (first and mask == 0) return value or ((first and (mask - 1)).toLong() shl (8 * i))
                value = value or (u8().toLong() shl (8 * i))
                mask = mask ushr 1
            }
            return value
        }

        /** A size or position: a number below 2^63. */
        fun size(): Long = number().takeIf { it >= 0 } ?: throw Unread("a size above 2^63")

        fun id(): Int {
            val id = number()
            return if (id in 0..MAX_ID) id.toInt() else throw Unread("property id $id out of range")
        }

        /** A count read from the file, refused above [max] (by default the bytes left). */
        fun count(what: String, max: Long = remaining.toLong()): Int {
            val n = number()
            if (n !in 0..max || n > Int.MAX_VALUE) throw Unread("$n $what in a header of ${bytes.size} bytes")
            return n.toInt()
        }

        fun skipData() {
            val n = count("property bytes")
            position += n
        }

        fun waitFor(id: Int) {
            while (true) {
                val type = id()
                if (type == id) return
                if (type == K_END) throw Unread("property $id missing")
                skipData()
            }
        }

        /** [count] bits, the first in the top bit of the first byte. */
        fun bits(count: Int): BooleanArray {
            if ((count + 7) / 8 > remaining) throw Unread("header ends early")
            val out = BooleanArray(count)
            for (i in 0 until count) out[i] = bytes[position + i / 8].toInt() and (0x80 ushr (i % 8)) != 0
            position += (count + 7) / 8
            return out
        }

        /** [count] optional CRCs: an all-defined byte, else a bit field, then 4 bytes per CRC. */
        fun digests(count: Int): Array<Long?> {
            val defined = if (u8() != 0) BooleanArray(count) { true } else bits(count)
            val listed = defined.count { it }
            if (listed.toLong() * 4 > remaining) throw Unread("header ends early")
            return Array(count) { i -> if (defined[i]) bytes(4).u32le(0) else null }
        }
    }

    companion object {
        /** Bytes read from the file for the header, compressed or not. */
        const val DEFAULT_READ_LIMIT = 64 * 1024

        /** The most a compressed header may decode to. */
        const val DEFAULT_DECODED_LIMIT = 1 shl 20

        private const val SIGNATURE_HEADER = 32
        private val SIGNATURE = byteArrayOf(0x37, 0x7A, 0xBC.toByte(), 0xAF.toByte(), 0x27, 0x1C)
        private const val MAX_FILES = 1 shl 16
        private const val MAX_FOLDER_STREAMS = 64L
        private const val MAX_LC_PLUS_LP = 4
        private const val MAX_ID = 0x7F
        private const val ID_SIZE_MASK = 0x0F
        private const val FLAG_COMPLEX = 0x10
        private const val FLAG_PROPERTIES = 0x20
        private const val FLAG_ALTERNATIVES = 0x80

        private const val K_END = 0x00
        private const val K_HEADER = 0x01
        private const val K_ARCHIVE_PROPERTIES = 0x02
        private const val K_ADDITIONAL_STREAMS_INFO = 0x03
        private const val K_MAIN_STREAMS_INFO = 0x04
        private const val K_FILES_INFO = 0x05
        private const val K_PACK_INFO = 0x06
        private const val K_UNPACK_INFO = 0x07
        private const val K_SUBSTREAMS_INFO = 0x08
        private const val K_SIZE = 0x09
        private const val K_CRC = 0x0A
        private const val K_FOLDER = 0x0B
        private const val K_CODERS_UNPACK_SIZE = 0x0C
        private const val K_NUM_UNPACK_STREAM = 0x0D
        private const val K_EMPTY_STREAM = 0x0E
        private const val K_EMPTY_FILE = 0x0F
        private const val K_NAME = 0x11
        private const val K_ENCODED_HEADER = 0x17

        private const val COPY = "00"
        private const val LZMA = "030101"
        private const val LZMA2 = "21"
        private const val AES = "06f10701"
        private val HEADER_CODERS = setOf(COPY, LZMA, LZMA2)
        private val CODER_NAMES = mapOf(
            COPY to "Copy", LZMA to "LZMA", LZMA2 to "LZMA2", AES to "AES",
            "03030103" to "BCJ", "0303011b" to "BCJ2", "030401" to "PPMd", "040108" to "Deflate",
            "040109" to "Deflate64", "040202" to "BZip2",
        )

        private fun coderName(id: String): String = CODER_NAMES[id] ?: "coder $id"

        private fun hex(bytes: ByteArray): String = bytes.joinToString("") { String.format("%02x", it.toInt() and 0xFF) }

        private fun crc(bytes: ByteArray, offset: Int, length: Int): Long =
            CRC32().apply { update(bytes, offset, length) }.value
    }
}
