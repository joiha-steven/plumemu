// Dolphin's `.rvz` and wit's `.wia` of a GameCube disc, read as one flat byte range. Written from the format
// description (Dolphin's docs/WiaAndRvz.md, text only); no Dolphin or wit code is used. Not in rcheevos.
package com.plumemu.achievements.hash

import com.plumemu.identify.codec.zstd.Zstd
import org.tukaani.xz.LZMA2InputStream
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.security.MessageDigest

private fun damagedWia(why: String) = DiscFormatException(RaCannotHashReason.BadImage, "Damaged .rvz/.wia: $why")

private fun be32(b: ByteArray, at: Int): Long =
    ((b[at].toLong() and 0xFF) shl 24) or ((b[at + 1].toLong() and 0xFF) shl 16) or ((b[at + 2].toLong() and 0xFF) shl 8) or (b[at + 3].toLong() and 0xFF)

private fun be64(b: ByteArray, at: Int): Long = (be32(b, at) shl 32) or be32(b, at + 4)

/**
 * A GameCube `.rvz` or `.wia`: a file head and a disc struct (SHA-1 checked), a table of raw-data entries (each a
 * disc range cut into groups) and a table of groups. Each group is one compressed chunk of the disc (none, zstd,
 * LZMA or LZMA2; RVZ may also pack junk runs, which [JunkGenerator] regenerates). A read decodes the one group it
 * touches, which stays decoded; the first 0x80 bytes come from the disc struct. A Wii disc stores each partition
 * decrypted, without hash blocks, with its title key: rebuilding the hash tree, applying the file's exceptions and
 * re-encrypting (AES-CBC) is not built, so Wii discs, bzip2 and "purge" are `NeedsDecoder`.
 *
 * Memory: the tables (at most 4 MiB decoded), and per group the chunk (at most 4 MiB, the largest Dolphin offers is
 * 2 MiB; a bigger valid one is `NeedsDecoder`) plus its stored and packed bytes (each at most a chunk and an eighth).
 * The LZMA dictionary is the group's own size, never the declared one: about 20 MiB at most, so no OutOfMemoryError.
 */
internal class WiaDiscFile private constructor(
    private val reader: RangeReader,
    private val fileSize: Long,
    private val rvz: Boolean,
    private val compression: Int,
    private val lzmaProps: Byte,
    private val chunkSize: Int,
    private val discHead: ByteArray,
    private val entries: List<RawEntry>,
    private val groups: ByteArray,
    override val size: Long,
) : DiscFile {
    class RawEntry(val start: Long, val length: Long, val groupIndex: Int)

    private val groupEntryBytes = if (rvz) RVZ_GROUP_BYTES else WIA_GROUP_BYTES
    private var chunk: ByteArray? = null
    private var cachedGroup = -1
    private var chunkLength = 0
    private var chunkStart = 0L

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        var done = 0
        var position = offset
        while (done < len && position < size) {
            val take: Int
            if (position < discHead.size) {
                take = minOf((len - done).toLong(), discHead.size - position).toInt()
                System.arraycopy(discHead, position.toInt(), buffer, off + done, take)
            } else {
                val entry = entries.firstOrNull { position >= it.start && position < it.start + it.length }
                if (entry == null) {
                    // A range no entry covers reads as zeros, up to the next entry.
                    val next = entries.filter { it.start > position }.minOfOrNull { it.start } ?: size
                    take = minOf((len - done).toLong(), next - position, size - position).toInt()
                    buffer.fill(0, off + done, off + done + take)
                } else {
                    val local = ((position - entry.start) / chunkSize).toInt()
                    load(entry, local)
                    val inChunk = (position - chunkStart).toInt()
                    take = minOf(len - done, chunkLength - inChunk)
                    if (take <= 0) break
                    System.arraycopy(chunk!!, inChunk, buffer, off + done, take)
                }
            }
            done += take
            position += take
        }
        return done
    }

    private fun load(entry: RawEntry, local: Int) {
        val index = entry.groupIndex + local
        if (index == cachedGroup) return
        cachedGroup = -1
        val start = entry.start + local.toLong() * chunkSize
        val length = minOf(chunkSize.toLong(), entry.start + entry.length - start).toInt()
        val buf = chunk ?: ByteArray(chunkSize).also { chunk = it }
        decodeGroup(index, start, length, buf)
        chunkStart = start
        chunkLength = length
        cachedGroup = index
    }

    private fun decodeGroup(index: Int, start: Long, length: Int, out: ByteArray) {
        val at = index * groupEntryBytes
        val dataOffset = be32(groups, at) * 4
        val word = be32(groups, at + 4)
        val compressed = !rvz || (word and HIGH_BIT) != 0L
        val dataSize = if (rvz) word and LOW_31_BITS else word
        val packedSize = if (rvz) be32(groups, at + 8) else 0L
        if (dataSize == 0L) {
            out.fill(0, 0, length)
            return
        }
        val decodedLength = if (packedSize != 0L) packedSize else length.toLong()
        if (packedSize > storedBound(chunkSize)) throw damagedWia("group $index packs to more than a chunk allows")
        val dataFloor = (FILE_HEAD_BYTES + DISC_STRUCT_MIN).toLong()
        if (dataSize > storedBound(chunkSize) || dataOffset < dataFloor || dataOffset + dataSize > fileSize) throw damagedWia("group $index points outside the file")
        val stored = ByteArray(dataSize.toInt())
        if (reader.readAt(dataOffset, stored) < stored.size) throw damagedWia("group $index is cut short")
        val decoded: ByteArray = if (compressed && compression != COMPRESSION_NONE) {
            decompress(stored, decodedLength.toInt())
        } else {
            if (dataSize < decodedLength) throw damagedWia("group $index is shorter than its data")
            stored
        }
        if (packedSize != 0L) unpack(decoded, packedSize.toInt(), out, length, start) else System.arraycopy(decoded, 0, out, 0, length)
    }

    private fun decompress(data: ByteArray, expected: Int): ByteArray = decompressWith(compression, lzmaProps, data, expected)

    override fun close() = reader.close()

    companion object {
        private const val FILE_HEAD_BYTES = 0x48
        private const val DISC_STRUCT_BYTES = 0xDC
        private const val DISC_STRUCT_MIN = 0xD5
        private const val DISC_STRUCT_MAX = 0x1000
        private const val DISC_HEAD_BYTES = 0x80
        private const val WIA_GROUP_BYTES = 8
        private const val RVZ_GROUP_BYTES = 12
        private const val RAW_ENTRY_BYTES = 24
        private const val MAX_TABLE_BYTES = 4L shl 20
        private const val MAX_RAW_ENTRIES = 4096
        private const val MAX_CHUNK_BYTES = 4 shl 20
        private const val MIN_CHUNK_BYTES = 0x8000
        private const val BIG_CHUNK_UNIT = 2 shl 20
        private const val SECTOR = 0x8000L
        private const val MAX_ISO_BYTES = 1L shl 40
        private const val SUPPORTED_VERSION = 1L shl 24
        private const val HIGH_BIT = 1L shl 31
        private const val LOW_31_BITS = HIGH_BIT - 1
        private const val COMPRESSION_NONE = 0
        private const val COMPRESSION_PURGE = 1
        private const val COMPRESSION_BZIP2 = 2
        private const val COMPRESSION_LZMA = 3
        private const val COMPRESSION_LZMA2 = 4
        private const val COMPRESSION_ZSTD = 5
        private const val ZSTD_MAX_WINDOW = 1 shl 27
        private const val JUNK_SEED_BYTES = 68

        /** Most a stored or packed group may be: a chunk, an eighth more for a stream that did not shrink, and framing. */
        private fun storedBound(chunk: Int): Long = chunk + chunk / 8L + 4096

        private val WII_MAGIC = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())

        @Throws(IOException::class, DiscFormatException::class)
        fun open(source: RaRomSource, check: () -> Unit): WiaDiscFile {
            val reader = RangeReader(source, check)
            try {
                return parse(reader, source.sizeBytes)
            } catch (e: Throwable) {
                reader.close()
                throw e
            }
        }

        private fun sha1(data: ByteArray, off: Int = 0, len: Int = data.size - off): ByteArray =
            MessageDigest.getInstance("SHA-1").also { it.update(data, off, len) }.digest()

        private fun parse(reader: RangeReader, fileSize: Long): WiaDiscFile {
            val head = ByteArray(FILE_HEAD_BYTES)
            if (reader.readAt(0, head) < head.size) throw damagedWia("the header is cut short")
            val magic = String(head, 0, 3, Charsets.ISO_8859_1)
            if ((magic != "WIA" && magic != "RVZ") || head[3].toInt() != 1) throw damagedWia("not a .rvz or .wia file (no magic word)")
            val rvz = magic == "RVZ"
            if (!sha1(head, 0, 0x34).contentEquals(head.copyOfRange(0x34, 0x48))) throw damagedWia("the file header does not match its checksum")
            val discSize = be32(head, 0x0C)
            val isoSize = be64(head, 0x24)
            if (be32(head, 0x08) > SUPPORTED_VERSION) {
                throw DiscFormatException(RaCannotHashReason.NeedsDecoder, "This .rvz/.wia is a newer version than Plumemu reads")
            }
            if (be64(head, 0x2C) != fileSize) throw damagedWia("the file is not the size it says (cut short?)")
            if (discSize < DISC_STRUCT_MIN || discSize > DISC_STRUCT_MAX) throw damagedWia("the disc header size is not valid ($discSize)")
            if (isoSize < DISC_HEAD_BYTES || isoSize > MAX_ISO_BYTES) throw damagedWia("the disc size is not valid ($isoSize)")
            val raw = ByteArray(discSize.toInt())
            if (reader.readAt(FILE_HEAD_BYTES.toLong(), raw) < raw.size) throw damagedWia("the disc header is cut short")
            if (!sha1(raw).contentEquals(head.copyOfRange(0x10, 0x24))) throw damagedWia("the disc header does not match its checksum")
            val disc = raw.copyOf(maxOf(raw.size, DISC_STRUCT_BYTES))

            val discType = be32(disc, 0x00)
            val compression = be32(disc, 0x04).let { if (it > COMPRESSION_ZSTD) throw damagedWia("unknown compression $it") else it.toInt() }
            val chunk = be32(disc, 0x0C)
            val discHead = disc.copyOfRange(0x10, 0x10 + DISC_HEAD_BYTES)
            if (chunk < MIN_CHUNK_BYTES || chunk % SECTOR != 0L ||
                (chunk < BIG_CHUNK_UNIT && (!rvz || chunk and (chunk - 1) != 0L)) || (chunk >= BIG_CHUNK_UNIT && chunk % BIG_CHUNK_UNIT != 0L)
            ) {
                throw damagedWia("the chunk size is not valid ($chunk)")
            }
            if (chunk > MAX_CHUNK_BYTES) {
                throw DiscFormatException(RaCannotHashReason.NeedsDecoder, "A .rvz/.wia chunk of $chunk bytes is larger than Plumemu reads ($MAX_CHUNK_BYTES)")
            }
            if (discType == 2L || discHead.copyOfRange(0x18, 0x1C).contentEquals(WII_MAGIC)) {
                throw DiscFormatException(
                    RaCannotHashReason.NeedsDecoder,
                    "A Wii disc in .rvz/.wia holds decrypted partitions; the hash needs them rebuilt with hash blocks and encrypted again, which Plumemu does not do",
                )
            }
            val lzmaProps = checkCodec(compression, rvz, disc, raw.size)

            if (be32(disc, 0x90) != 0L) throw damagedWia("a GameCube disc lists no partitions, this one does")
            val rawCount = be32(disc, 0xB4)
            val groupCount = be32(disc, 0xC4)
            val groupEntryBytes = if (rvz) RVZ_GROUP_BYTES else WIA_GROUP_BYTES
            if (rawCount <= 0 || rawCount > MAX_RAW_ENTRIES) throw damagedWia("the raw data count is not valid ($rawCount)")
            if (groupCount <= 0 || groupCount * groupEntryBytes > MAX_TABLE_BYTES) throw damagedWia("the group count is not valid ($groupCount)")
            val rawTable = readTable(reader, fileSize, compression, lzmaProps, be64(disc, 0xB8), be32(disc, 0xC0), rawCount * RAW_ENTRY_BYTES, "raw data")
            val groups = readTable(reader, fileSize, compression, lzmaProps, be64(disc, 0xC8), be32(disc, 0xD0), groupCount * groupEntryBytes, "group")

            val entries = ArrayList<RawEntry>()
            val spans = ArrayList<Pair<Long, Long>>()
            for (n in 0 until rawCount.toInt()) {
                val at = n * RAW_ENTRY_BYTES
                val offset = be64(rawTable, at)
                val length = be64(rawTable, at + 8)
                val first = be32(rawTable, at + 16)
                val count = be32(rawTable, at + 20)
                if (offset < 0 || length < 0 || offset > isoSize || length > isoSize - offset) throw damagedWia("raw data $n lies outside the disc")
                if (length == 0L) continue
                // The first entry starts at 0x80 but its first group holds the disc from the sector before.
                val skipped = offset % SECTOR
                val start = offset - skipped
                val total = length + skipped
                val needed = (total + chunk - 1) / chunk
                if (count < needed || first + count > groupCount) throw damagedWia("raw data $n points at groups that are not there")
                entries += RawEntry(start, total, first.toInt())
                spans += offset to length
            }
            if (entries.isEmpty()) throw damagedWia("the file holds no disc data")
            spans.sortBy { it.first }
            for (i in 1 until spans.size) {
                if (spans[i].first < spans[i - 1].first + spans[i - 1].second) throw damagedWia("raw data entries overlap")
            }
            return WiaDiscFile(reader, fileSize, rvz, compression, lzmaProps, chunk.toInt(), discHead, entries, groups, isoSize)
        }

        /** Refuses codecs Plumemu lacks and bad codec properties; returns the LZMA properties byte (0 for the others). */
        private fun checkCodec(compression: Int, rvz: Boolean, disc: ByteArray, discSize: Int): Byte {
            if (compression == COMPRESSION_PURGE || compression == COMPRESSION_BZIP2) {
                throw DiscFormatException(RaCannotHashReason.NeedsDecoder, "No decoder for ${if (compression == COMPRESSION_PURGE) "purge" else "bzip2"} in .rvz/.wia")
            }
            if (compression == COMPRESSION_ZSTD && !rvz) throw damagedWia("a .wia cannot use zstd")
            val comprLength = disc[0xD4].toInt() and 0xFF
            if (comprLength > 7 || discSize < DISC_STRUCT_MIN + comprLength) throw damagedWia("the compressor data does not fit the disc header")
            var lzmaProps: Byte = 0
            if (compression == COMPRESSION_LZMA) {
                if (comprLength != 5) throw damagedWia("the LZMA properties are not 5 bytes")
                lzmaProps = disc[0xD5]
                if ((lzmaProps.toInt() and 0xFF) >= 9 * 5 * 5) throw damagedWia("the LZMA properties are not valid")
            }
            if (compression == COMPRESSION_LZMA2) {
                if (comprLength != 1 || (disc[0xD5].toInt() and 0xFF) > 40) throw damagedWia("the LZMA2 properties are not valid")
            }

            return lzmaProps
        }

        /** A table stored as one compressed stream (or as is for compression none) that must hold [decodedLength] bytes. */
        private fun readTable(
            reader: RangeReader,
            fileSize: Long,
            compression: Int,
            props: Byte,
            offset: Long,
            storedSize: Long,
            decodedLength: Long,
            what: String,
        ): ByteArray {
            if (decodedLength > MAX_TABLE_BYTES) throw damagedWia("the $what table is too large")
            if (offset < FILE_HEAD_BYTES || storedSize <= 0 || offset + storedSize > fileSize) throw damagedWia("the $what table lies outside the file")
            if (storedSize > decodedLength + decodedLength / 8 + 4096) throw damagedWia("the $what table is larger than it can be")
            val stored = ByteArray(storedSize.toInt())
            if (reader.readAt(offset, stored) < stored.size) throw damagedWia("the $what table is cut short")
            if (compression == COMPRESSION_NONE) {
                if (stored.size < decodedLength) throw damagedWia("the $what table is cut short")
                return stored
            }
            return decompressWith(compression, props, stored, decodedLength.toInt())
        }

        /** Decodes one stream to exactly [expected] bytes; any fault in it is damage (the input is in memory, so it is not I/O). */
        private fun decompressWith(compression: Int, props: Byte, data: ByteArray, expected: Int): ByteArray {
            try {
                return when (compression) {
                    COMPRESSION_ZSTD -> Zstd.decompress(data, 0, data.size, maxOutput = expected, maxWindow = ZSTD_MAX_WINDOW).also {
                        if (it.size != expected) throw IOException("zstd gave ${it.size} bytes, not $expected")
                    }
                    COMPRESSION_LZMA -> LZMAInputStream(ByteArrayInputStream(data), -1L, props, dictionaryFor(expected)).use { readExactly(it, expected) }
                    COMPRESSION_LZMA2 -> LZMA2InputStream(ByteArrayInputStream(data), dictionaryFor(expected)).use { readExactly(it, expected) }
                    else -> throw IOException("no decoder for compression $compression")
                }
            } catch (e: IOException) {
                throw damagedWia("a block cannot be decompressed (${e.message})")
            }
        }

        private fun readExactly(input: java.io.InputStream, expected: Int): ByteArray {
            val out = ByteArray(expected)
            DataInputStream(input).readFully(out)
            return out
        }

        /** Each stream is one chunk compressed alone, so a dictionary of its own size is always enough. */
        private fun dictionaryFor(expected: Int): Int = maxOf(4096, (expected + 4095) / 4096 * 4096)

        /**
         * Undoes RVZ packing: a big-endian word, either a byte count with the top bit clear (that many bytes follow
         * as they are) or with it set (a count of junk bytes, regenerated from the 68-byte seed that follows).
         * [discOffset] is where the output starts on the disc; junk is aligned to 32 KiB sectors of the disc.
         */
        private fun unpack(src: ByteArray, srcLength: Int, out: ByteArray, outLength: Int, discOffset: Long) {
            var p = 0
            var o = 0
            while (o < outLength) {
                if (srcLength - p < 4) throw damagedWia("packed data ends early")
                val word = be32(src, p)
                p += 4
                val count = word and LOW_31_BITS
                if (count > outLength - o) throw damagedWia("packed data runs past the group")
                if (word and HIGH_BIT != 0L) {
                    if (srcLength - p < JUNK_SEED_BYTES) throw damagedWia("a junk seed is cut short")
                    JunkGenerator(src, p).also { it.skip(((discOffset + o) % SECTOR).toInt()) }.fill(out, o, count.toInt())
                    p += JUNK_SEED_BYTES
                } else {
                    if (srcLength - p < count) throw damagedWia("packed data ends early")
                    System.arraycopy(src, p, out, o, count.toInt())
                    p += count.toInt()
                }
                o += count.toInt()
            }
        }
    }
}

/**
 * The lagged Fibonacci generator (xor, lags 32 and 521) that makes the padding of GameCube and Wii discs, as the
 * format description gives it: 17 seed words widen to 521, the state is advanced four times, and it advances
 * once more after every 521 words. Byte two of each word is taken from bit 18 (as the description stresses).
 */
internal class JunkGenerator(seed: ByteArray, at: Int) {
    private val buffer = IntArray(WORDS)
    private var position = 0 // bytes into the buffer

    init {
        for (i in 0 until SEED_WORDS) buffer[i] = be32(seed, at + i * 4).toInt()
        for (i in SEED_WORDS until WORDS) buffer[i] = (buffer[i - 17] shl 23) xor (buffer[i - 16] ushr 9) xor buffer[i - 1]
        repeat(4) { advance() }
    }

    private fun advance() {
        for (i in 0 until 32) buffer[i] = buffer[i] xor buffer[i + WORDS - 32]
        for (i in 32 until WORDS) buffer[i] = buffer[i] xor buffer[i - 32]
    }

    fun skip(bytes: Int) {
        position += bytes
        while (position >= WORDS * 4) {
            advance()
            position -= WORDS * 4
        }
    }

    fun fill(out: ByteArray, off: Int, count: Int) {
        for (n in 0 until count) {
            val word = buffer[position shr 2]
            val shift = when (position and 3) { 0 -> 24; 1 -> 18; 2 -> 8; else -> 0 }
            out[off + n] = (word ushr shift).toByte()
            position++
            if (position == WORDS * 4) {
                advance()
                position = 0
            }
        }
    }

    private companion object {
        const val SEED_WORDS = 17
        const val WORDS = 521
    }
}
