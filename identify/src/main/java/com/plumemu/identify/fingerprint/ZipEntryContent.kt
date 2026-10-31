package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.cart.CartridgeReader
import com.plumemu.identify.fingerprint.cart.CartridgeReaders
import com.plumemu.identify.fingerprint.cart.ContentLayout
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import com.plumemu.identify.source.readInto
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * `Depth.FULL` only: the content CRC of one zip entry whose format may carry a header or a byte
 * order name databases leave out (iNES, fwNES, LNX, SNES copier, SMD, N64 v64 / n64). A
 * zip's directory CRC is of the entry as stored, header included, so a zipped `.nes` would never
 * match a name pack; this unpacks the entry once to fix that.
 *
 * Read: the entry's 30-byte local header (PKWARE APPNOTE 4.3.7, for the name and extra lengths
 * only: sizes and CRC come from the central directory, which is right even when the local header
 * defers them to a data descriptor), then the data, stored or deflated (`java.util.zip.Inflater`,
 * raw), streamed 64 KB at a time. The first 128 KB (every header offset the cartridge readers
 * use is below 66 KB) go through the same header detection as a loose file; when it finds no
 * header and no byte order to undo, the directory CRC already is the content CRC and nothing
 * more is read. Otherwise the rest is inflated, the header skipped and the content normalised
 * as for a loose file ([ContentCrc]).
 *
 * Bounded: only entries up to the whole-file limit; inflating stops past the declared size;
 * the inflated length and the CRC of the whole stream must match the directory. Anything that
 * does not (damaged data, an encrypted entry, another method, a truncated archive) is a note,
 * and the directory CRC is kept.
 */
internal object ZipEntryContent {

    /**
     * The header-less CRC of an entry and the header it found. [storedFormIsContent] is true
     * when the entry was opened and has no header or byte order to undo: [crc32] is then the
     * directory CRC, and the notes that guessed a header from the length no longer apply.
     */
    class Result(
        val crc32: Long,
        val contentSize: Long,
        val headerBytesSkipped: Int,
        val notes: List<String>,
        val storedFormIsContent: Boolean = false,
    )

    /** Thrown inside this object only: the entry's data is not what its directory says. */
    private class Damaged(message: String) : Exception(message)

    /**
     * The content CRC of [entry] read with [readers] ([Result.storedFormIsContent] when no
     * header was found), or null when the entry could not be read (a note in [notes]): the
     * directory CRC then stands, with its doubts.
     *
     * @throws IOException when [source] itself fails to read.
     */
    @Throws(IOException::class)
    fun read(
        source: RandomAccessSource,
        entry: ZipEntryInfo,
        readers: List<CartridgeReader>,
        limit: Long,
        notes: MutableList<String>,
    ): Result? {
        val what = "zip: ${entry.name}"
        val skipped = "the directory CRC (header included) is kept"
        when {
            entry.encrypted -> notes += "$what is encrypted; $skipped"
            entry.method != STORED && entry.method != DEFLATED -> notes += "$what uses method ${entry.method}, not unpacked; $skipped"
            entry.uncompressedSize > limit -> notes += "$what is ${entry.uncompressedSize} bytes, over the $limit-byte limit; $skipped"
            else -> return try {
                inflate(source, entry, readers)
            } catch (e: Damaged) {
                notes += "$what: ${e.message}; $skipped"
                null
            }
        }
        return null
    }

    @Throws(IOException::class, Damaged::class)
    private fun inflate(source: RandomAccessSource, entry: ZipEntryInfo, readers: List<CartridgeReader>): Result? {
        val declared = entry.uncompressedSize
        if (declared < 0) throw Damaged("lists a size above 2^63 bytes")
        val local = entry.localHeaderOffset
        if (local < 0 || local > source.size - LOCAL_HEADER) throw Damaged("local header lies past the end of the archive")
        val header = source.readFully(local, LOCAL_HEADER)
        if (!header.startsWith(LOCAL_SIGNATURE)) throw Damaged("local header has a bad signature")
        val dataStart = local + LOCAL_HEADER + header.u16le(26) + header.u16le(28)
        if (entry.compressedSize < 0 || dataStart > source.size - entry.compressedSize) throw Damaged("data runs past the end of the archive")
        if (entry.method == STORED && entry.compressedSize != declared) throw Damaged("stored entry lists two different sizes")
        val stream = EntryStream(source, dataStart, entry.compressedSize, declared, stored = entry.method == STORED)
        try {
            val prefixLength = minOf(declared, PREFIX.toLong()).toInt()
            val prefix = ByteArray(prefixLength)
            val got = stream.fill(prefix, 0, prefixLength)
            if (got < prefixLength) throw Damaged("inflates to $got of $declared bytes")
            val found = CartridgeReaders.detect(readers, PrefixSource(prefix, declared))
            val skip = found?.headerBytesSkipped ?: 0
            val layout = found?.layout ?: ContentLayout.PLAIN
            if (found == null || (skip == 0 && layout == ContentLayout.PLAIN)) {
                return Result(entry.crc32, declared, 0, found?.notes.orEmpty(), storedFormIsContent = true)
            }
            val content = ContentCrc(skip.toLong(), layout)
            val whole = CRC32()
            content.update(prefix, 0, prefixLength)
            whole.update(prefix, 0, prefixLength)
            val buffer = ByteArray(CONTENT_CRC_BUFFER)
            while (true) {
                val n = stream.fill(buffer, 0, buffer.size)
                content.update(buffer, 0, n)
                whole.update(buffer, 0, n)
                if (n < buffer.size) break
            }
            if (stream.produced != declared) throw Damaged("inflates to ${stream.produced} of $declared bytes")
            if (whole.value != entry.crc32) throw Damaged("inflated data does not match its directory CRC")
            return Result(content.value(), content.contentLength, skip, found.notes)
        } finally {
            stream.close()
        }
    }

    /**
     * An entry's bytes as a stream, stored or raw deflate, never more than [declared] (one byte
     * past it is [Damaged]); compressed bytes come from [source] 64 KB at a time.
     */
    private class EntryStream(
        private val source: RandomAccessSource,
        private var position: Long,
        compressedSize: Long,
        private val declared: Long,
        stored: Boolean,
    ) {
        private val end = position + compressedSize
        private val inflater = if (stored) null else Inflater(true)
        private val input = if (stored) ByteArray(0) else ByteArray(CONTENT_CRC_BUFFER)
        private var dummyGiven = false

        /** Bytes produced so far. */
        var produced = 0L
            private set

        /** Fills [dst] from [offset] with up to [length] bytes; fewer only at the end of the entry. */
        @Throws(IOException::class, Damaged::class)
        fun fill(dst: ByteArray, offset: Int, length: Int): Int {
            var done = 0
            while (done < length) {
                val n = if (inflater == null) readStored(dst, offset + done, length - done) else inflate(inflater, dst, offset + done, length - done)
                if (n < 0) break
                done += n
            }
            return done
        }

        fun close() {
            inflater?.end()
        }

        private fun readStored(dst: ByteArray, offset: Int, length: Int): Int {
            if (produced >= declared) return -1
            val wanted = minOf(length.toLong(), declared - produced).toInt()
            val n = source.readAt(position, dst, offset, wanted)
            if (n <= 0) throw Damaged("stored data ends after $produced of $declared bytes")
            position += n
            produced += n
            return n
        }

        private fun inflate(inflater: Inflater, dst: ByteArray, offset: Int, length: Int): Int {
            // Each pass either produces bytes, ends, or feeds input: bounded by the compressed size.
            while (true) {
                if (inflater.finished()) return -1
                if (inflater.needsInput()) feed(inflater)
                val n = try {
                    inflater.inflate(dst, offset, length)
                } catch (e: DataFormatException) {
                    throw Damaged("deflate data damaged (${e.message})")
                }
                if (n > 0) {
                    produced += n
                    if (produced > declared) throw Damaged("inflates past its declared $declared bytes")
                    return n
                }
                if (inflater.needsDictionary()) throw Damaged("deflate data asks for a preset dictionary")
                if (!inflater.finished() && !inflater.needsInput()) throw Damaged("deflate data stalls")
            }
        }

        private fun feed(inflater: Inflater) {
            if (position < end) {
                val wanted = minOf(input.size.toLong(), end - position).toInt()
                val got = source.readInto(position, input, wanted)
                if (got <= 0) throw Damaged("compressed data ends early")
                position += got
                inflater.setInput(input, 0, got)
            } else if (!dummyGiven) {
                // Raw deflate may need one byte past the end of the stream (Inflater docs).
                dummyGiven = true
                inflater.setInput(ByteArray(1), 0, 1)
            } else {
                throw Damaged("compressed data ends after $produced of $declared bytes")
            }
        }
    }

    /** The first bytes of an entry, sized as the whole entry: reads past them find nothing. */
    private class PrefixSource(private val bytes: ByteArray, override val size: Long) : RandomAccessSource {
        override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val n = minOf(length.toLong(), bytes.size - position).toInt()
            System.arraycopy(bytes, position.toInt(), dst, offset, n)
            return n
        }

        override fun close() = Unit
    }

    private const val STORED = 0
    private const val DEFLATED = 8
    private const val LOCAL_HEADER = 30
    private val LOCAL_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

    /** 128 KB: past the SNES HiROM header behind a copier header (0x101C0), the furthest any reader looks. */
    private const val PREFIX = 128 * 1024
}
