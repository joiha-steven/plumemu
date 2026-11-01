// CHD v5 container (MAME's compressed hunks of data), written from the format description. Not in rcheevos.
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage
import com.plumemu.achievements.hash.RaCannotHashReason.NeedsDecoder
import com.plumemu.achievements.hash.RaCannotHashReason.NotYetSupported
import org.tukaani.xz.LZMAInputStream
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** One track of a CD image as the CHD's `CHT2`/`CHTR` metadata describes it. */
internal class ChdCdTrack(val number: Int, val type: String, val frames: Int, val firstFrame: Int) {
    val isAudio: Boolean get() = type == "AUDIO"

    /** Bytes of each 2448-byte frame that hold the track's data (the rest is sync/ECC filler or subcode). */
    val dataBytes: Int
        get() = when (type) {
            "MODE1", "MODE2_FORM1" -> 2048
            "MODE2", "MODE2_FORM_MIX" -> 2336
            "MODE2_FORM2" -> 2324
            else -> 2352 // MODE1_RAW, MODE2_RAW, AUDIO
        }
}

/**
 * A `.chd` (version 5) opened for reading. Only the header, the map and the metadata are read up front;
 * a hunk is decoded when something asks for it and the last few stay in a small cache.
 * Codecs: `zlib`, `lzma` (XZ for Java), `cdzl`, `cdlz`, and uncompressed hunks. Anything else, and a
 * parent CHD, is [RaCannotHashReason.NeedsDecoder] when a hunk that needs it is read.
 */
internal class ChdImage private constructor(
    private val reader: RangeReader,
    private val fileSize: Long,
    private val codecs: IntArray,
    val hunkBytes: Int,
    val logicalBytes: Long,
    private val types: ByteArray,
    private val offsets: LongArray,
    private val lengths: IntArray,
    private val crcs: IntArray,
    private val check: () -> Unit,
    val cdTracks: List<ChdCdTrack>?,
) : AutoCloseable {
    val hunkCount: Int get() = types.size

    /** How many hunks were decoded (a cache hit does not count); tests use it to show that hashing reads only what it needs. */
    var hunksDecoded: Int = 0
        private set

    private val cache = object : LinkedHashMap<Int, ByteArray>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, ByteArray>?): Boolean = size > CACHE_HUNKS
    }

    @Throws(IOException::class, DiscFormatException::class)
    fun hunk(number: Int): ByteArray = hunk(number, 0)

    private fun hunk(number: Int, depth: Int): ByteArray {
        if (number < 0 || number >= hunkCount) throw corrupt("hunk $number does not exist")
        cache[number]?.let { return it }
        check()
        val data = when (val type = types[number].toInt()) {
            in TYPE_CODEC_0..TYPE_CODEC_3 -> verified(number, decodeCompressed(number, codecs[type - TYPE_CODEC_0]))
            TYPE_NONE -> verified(number, readStored(number))
            TYPE_ZERO -> ByteArray(hunkBytes)
            TYPE_SELF -> {
                // A self reference points at another hunk of this file; one that points at a reference is not made by any tool.
                val target = offsets[number]
                if (depth >= 1 || target >= hunkCount || types[target.toInt()].toInt().let { it == TYPE_SELF }) {
                    throw corrupt("hunk $number refers to hunk $target in a way no tool writes")
                }
                hunk(target.toInt(), depth + 1)
            }
            else -> throw DiscFormatException(NeedsDecoder, "A .chd with a parent file needs a decoder (parent references)")
        }
        hunksDecoded++
        cache[number] = data
        return data
    }

    /** The map keeps a CRC-16 of each hunk's decoded bytes (the uncompressed map has none); noise that still decodes shows up here. */
    private fun verified(number: Int, data: ByteArray): ByteArray {
        if (crcs[number] >= 0 && crc16(data) != crcs[number]) throw corrupt("hunk $number fails its CRC")
        return data
    }

    private fun readStored(number: Int): ByteArray = readAt(offsets[number], hunkBytes, "hunk $number")

    private fun decodeCompressed(number: Int, codec: Int): ByteArray {
        if (codec == 0) throw corrupt("hunk $number names a compressor the header does not have")
        val packed = readAt(offsets[number], lengths[number], "hunk $number")
        return try {
            when (codec) {
                CODEC_ZLIB -> inflate(packed, 0, packed.size, hunkBytes)
                CODEC_LZMA -> lzma(packed, 0, packed.size, hunkBytes)
                CODEC_CDLZ -> decodeCd(packed, useLzma = true)
                CODEC_CDZL -> decodeCd(packed, useLzma = false)
                else -> throw DiscFormatException(NeedsDecoder, "${tagName(codec)} hunks need a decoder")
            }
        } catch (e: DataFormatException) {
            throw corrupt("hunk $number cannot be decompressed (${e.message})")
        } catch (e: IOException) {
            // The input is in memory: an IOException here is the compressed data being wrong, not the disc failing.
            throw corrupt("hunk $number cannot be decompressed (${e.message})")
        }
    }

    /**
     * `cdlz` / `cdzl`: an ECC bitmap, the length of the sector part (2 bytes, 3 when the hunk is 64 KiB or more),
     * the sector part (LZMA or deflate) and the subcode part (deflate). Sectors whose ECC bit is set had their sync
     * and error-correction bytes cut out when the CHD was made; they are rebuilt here.
     */
    private fun decodeCd(packed: ByteArray, useLzma: Boolean): ByteArray {
        val frames = hunkBytes / CD_FRAME_BYTES
        val eccBytes = (frames + 7) / 8
        val lengthBytes = if (hunkBytes < 65536) 2 else 3
        if (packed.size < eccBytes + lengthBytes) throw corrupt("a CD hunk is cut short")
        var baseLength = 0
        for (i in 0 until lengthBytes) baseLength = (baseLength shl 8) or (packed[eccBytes + i].toInt() and 0xFF)
        val baseStart = eccBytes + lengthBytes
        if (baseLength < 0 || baseStart + baseLength > packed.size) throw corrupt("a CD hunk's sector data is cut short")
        val sectorBytes = frames * CD_SECTOR_BYTES
        val sectors = if (useLzma) lzma(packed, baseStart, baseLength, sectorBytes) else inflate(packed, baseStart, baseLength, sectorBytes)
        val subStart = baseStart + baseLength
        val subcode = inflate(packed, subStart, packed.size - subStart, frames * CD_SUBCODE_BYTES)
        val out = ByteArray(hunkBytes)
        for (f in 0 until frames) {
            val at = f * CD_FRAME_BYTES
            System.arraycopy(sectors, f * CD_SECTOR_BYTES, out, at, CD_SECTOR_BYTES)
            System.arraycopy(subcode, f * CD_SUBCODE_BYTES, out, at + CD_SECTOR_BYTES, CD_SUBCODE_BYTES)
            if ((packed[f / 8].toInt() shr (f % 8)) and 1 != 0) CdEcc.rebuild(out, at)
        }
        return out
    }

    private fun readAt(offset: Long, length: Int, what: String): ByteArray {
        if (offset < 0 || length < 0 || offset + length > fileSize) throw corrupt("$what points outside the file")
        val data = ByteArray(length)
        if (reader.readAt(offset, data) < length) throw corrupt("$what is cut short")
        return data
    }

    private fun corrupt(why: String) = DiscFormatException(BadImage, "Damaged .chd: $why")

    override fun close() = reader.close()

    companion object {
        internal const val HEADER_BYTES = 124
        private const val CACHE_HUNKS = 4
        private const val MAX_HUNK_BYTES = 16 shl 20
        private const val MAX_HUNKS = 2_200_000L
        // A compressed map can say "same as before" for about 270 hunks in two bits, so a file cannot be shorter than this ratio allows.
        private const val MAX_HUNKS_PER_FILE_BYTE = 64L
        private const val MAX_METADATA_BYTES = 1 shl 16
        private const val MAX_METADATA_ENTRIES = 1024

        internal const val CD_SECTOR_BYTES = 2352
        internal const val CD_SUBCODE_BYTES = 96
        internal const val CD_FRAME_BYTES = CD_SECTOR_BYTES + CD_SUBCODE_BYTES

        // Map entry types (the compressed v5 map's symbols, after the "repeat" forms are expanded).
        private const val TYPE_CODEC_0 = 0
        private const val TYPE_CODEC_3 = 3
        internal const val TYPE_NONE = 4
        private const val TYPE_ZERO = 7
        internal const val TYPE_SELF = 5
        internal const val TYPE_PARENT = 6

        private val CODEC_ZLIB = tag("zlib")
        private val CODEC_LZMA = tag("lzma")
        private val CODEC_CDLZ = tag("cdlz")
        private val CODEC_CDZL = tag("cdzl")

        private fun tag(text: String): Int = text.fold(0) { acc, c -> (acc shl 8) or c.code }

        private fun tagName(tag: Int): String = String(ByteArray(4) { (tag shr (24 - 8 * it)).toByte() }, Charsets.ISO_8859_1)

        @Throws(IOException::class, DiscFormatException::class)
        fun open(source: RaRomSource, check: () -> Unit): ChdImage {
            val reader = RangeReader(source, check)
            try {
                return parse(reader, source.sizeBytes, check)
            } catch (e: Throwable) {
                reader.close()
                throw e
            }
        }

        internal fun bad(why: String) = DiscFormatException(BadImage, "Damaged .chd: $why")

        internal fun be(b: ByteArray, at: Int, bytes: Int): Long {
            var v = 0L
            for (i in 0 until bytes) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
            return v
        }

        private fun parse(reader: RangeReader, fileSize: Long, check: () -> Unit): ChdImage {
            val header = ByteArray(HEADER_BYTES)
            if (reader.readAt(0, header) < 16) throw bad("the header is cut short")
            if (String(header, 0, 8, Charsets.ISO_8859_1) != "MComprHD") throw bad("not a CHD file (no MComprHD header)")
            val headerLength = be(header, 8, 4)
            val version = be(header, 12, 4)
            if (version in 1..4) throw DiscFormatException(NotYetSupported, "A .chd version $version file is not read; only version 5 is")
            if (version != 5L || headerLength != HEADER_BYTES.toLong()) throw bad("the header is not a version 5 header")
            if (fileSize < HEADER_BYTES || reader.readAt(0, header) < HEADER_BYTES) throw bad("the header is cut short")
            val codecs = IntArray(4) { be(header, 16 + 4 * it, 4).toInt() }
            val logicalBytes = be(header, 32, 8)
            val mapOffset = be(header, 40, 8)
            val metaOffset = be(header, 48, 8)
            val hunkBytesLong = be(header, 56, 4)
            val unitBytesLong = be(header, 60, 4)
            if (hunkBytesLong <= 0 || hunkBytesLong > MAX_HUNK_BYTES || unitBytesLong <= 0 || hunkBytesLong % unitBytesLong != 0L) {
                throw bad("hunk size $hunkBytesLong and unit size $unitBytesLong are not valid")
            }
            if (logicalBytes <= 0 || logicalBytes > Long.MAX_VALUE / 4) throw bad("the logical size is not valid")
            val hunkBytes = hunkBytesLong.toInt()
            val hunkCountLong = (logicalBytes + hunkBytes - 1) / hunkBytes
            if (hunkCountLong > MAX_HUNKS || hunkCountLong > fileSize * MAX_HUNKS_PER_FILE_BYTE) throw bad("too many hunks ($hunkCountLong) for a file of $fileSize bytes")
            val hunkCount = hunkCountLong.toInt()
            if (mapOffset < HEADER_BYTES || mapOffset >= fileSize) throw bad("the map is outside the file")

            val types = ByteArray(hunkCount)
            val offsets = LongArray(hunkCount)
            val lengths = IntArray(hunkCount)
            val crcs = IntArray(hunkCount) { -1 }
            if (codecs[0] == 0) {
                val hasParent = (104 until 124).any { header[it].toInt() != 0 }
                readUncompressedMap(reader, fileSize, mapOffset, hunkBytes, hasParent, types, offsets)
            } else {
                ChdMap.read(reader, fileSize, mapOffset, hunkBytes, unitBytesLong.toInt(), types, offsets, lengths, crcs)
            }
            val metadata = readMetadata(reader, fileSize, metaOffset, check)
            val tracks = cdTracksOf(metadata, hunkBytes, unitBytesLong, logicalBytes)
            return ChdImage(reader, fileSize, codecs, hunkBytes, logicalBytes, types, offsets, lengths, crcs, check, tracks)
        }

        /** Without a compressor the map is one 4-byte big-endian number per hunk: where the hunk is, in hunks. */
        private fun readUncompressedMap(reader: RangeReader, fileSize: Long, at: Long, hunkBytes: Int, hasParent: Boolean, types: ByteArray, offsets: LongArray) {
            val raw = ByteArray(types.size * 4)
            if (at + raw.size > fileSize || reader.readAt(at, raw) < raw.size) throw bad("the map is cut short")
            for (n in types.indices) {
                val offset = be(raw, n * 4, 4) * hunkBytes
                // Offset 0 is a hunk of zeros, or in the parent file when there is one, as libchdr reads it.
                if (offset + hunkBytes > fileSize) throw bad("hunk $n points outside the file")
                types[n] = (if (offset != 0L) TYPE_NONE else if (hasParent) TYPE_PARENT else TYPE_ZERO).toByte()
                offsets[n] = offset
            }
        }

        private fun readMetadata(reader: RangeReader, fileSize: Long, first: Long, check: () -> Unit): List<Pair<String, ByteArray>> {
            val found = ArrayList<Pair<String, ByteArray>>()
            var at = first
            var seen = 0
            while (at != 0L) {
                check()
                if (++seen > MAX_METADATA_ENTRIES || at < HEADER_BYTES || at + 16 > fileSize) throw bad("the metadata chain is not valid")
                val head = ByteArray(16)
                if (reader.readAt(at, head) < 16) throw bad("the metadata is cut short")
                val length = be(head, 5, 3).toInt()
                if (length > MAX_METADATA_BYTES || at + 16 + length > fileSize) throw bad("a metadata entry is not valid")
                val body = ByteArray(length)
                if (reader.readAt(at + 16, body) < length) throw bad("the metadata is cut short")
                found += String(head, 0, 4, Charsets.ISO_8859_1) to body
                at = be(head, 8, 8)
            }
            return found
        }

        private val TRACK_LINE = Regex("TRACK:(\\d+) TYPE:(\\S+) SUBTYPE:(\\S+) FRAMES:(\\d+)(?: PREGAP:(\\d+) PGTYPE:(\\S+))?")

        /** The CD tracks, or null for an image that is not a CD (a DVD or a plain disk image). */
        private fun cdTracksOf(metadata: List<Pair<String, ByteArray>>, hunkBytes: Int, unitBytes: Long, logicalBytes: Long): List<ChdCdTrack>? {
            if (metadata.any { it.first == "CHGT" || it.first == "CHGD" }) {
                throw DiscFormatException(NotYetSupported, "A GD-ROM .chd is not read")
            }
            val lines = metadata.filter { it.first == "CHT2" || it.first == "CHTR" }
            if (lines.isEmpty()) return null
            if (unitBytes != CD_FRAME_BYTES.toLong() || hunkBytes % CD_FRAME_BYTES != 0) throw bad("a CD image whose frames are not $CD_FRAME_BYTES bytes")
            val totalFrames = logicalBytes / CD_FRAME_BYTES
            var start = 0L
            val tracks = ArrayList<ChdCdTrack>()
            for ((_, body) in lines) {
                val m = TRACK_LINE.find(String(body, Charsets.ISO_8859_1)) ?: throw bad("a track line cannot be read")
                val frames = m.groupValues[4].toLongOrNull() ?: throw bad("a track length cannot be read")
                if (frames <= 0 || start + frames > totalFrames) throw bad("track ${m.groupValues[1]} runs past the end of the image")
                // A virtual pregap ("V" before its type) is counted in FRAMES; the track's own sectors come after it.
                val pregap = if (m.groupValues[6].startsWith("V")) m.groupValues[5].toLongOrNull() ?: 0L else 0L
                if (pregap >= frames) throw bad("track ${m.groupValues[1]} is all pregap")
                tracks += ChdCdTrack(m.groupValues[1].toInt(), m.groupValues[2], (frames - pregap).toInt(), (start + pregap).toInt())
                // Every track starts on a multiple of four frames in the image.
                start += (frames + 3) / 4 * 4
            }
            return tracks
        }


        private val CRC_TABLE = IntArray(256) { n ->
            var crc = n shl 8
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
            crc and 0xFFFF
        }

        /** CRC-16/CCITT-FALSE (poly 0x1021, start 0xFFFF), the checksum of the map and of each hunk. */
        internal fun crc16(data: ByteArray): Int {
            var crc = 0xFFFF
            for (b in data) crc = ((crc shl 8) and 0xFFFF) xor CRC_TABLE[((crc shr 8) xor b.toInt()) and 0xFF]
            return crc
        }

        private fun inflate(data: ByteArray, off: Int, len: Int, expected: Int): ByteArray {
            val inflater = Inflater(true)
            try {
                inflater.setInput(data, off, len)
                val out = ByteArray(expected)
                var got = 0
                while (got < expected) {
                    val n = inflater.inflate(out, got, expected - got)
                    if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                    got += n
                }
                if (got < expected) throw DataFormatException("$got of $expected bytes")
                return out
            } finally {
                inflater.end()
            }
        }

        /** Raw LZMA with the properties CHD fixes: lc 3, lp 0, pb 2, and the dictionary size LZMA's encoder picks for the hunk size. */
        private fun lzma(data: ByteArray, off: Int, len: Int, expected: Int): ByteArray {
            LZMAInputStream(ByteArrayInputStream(data, off, len), expected.toLong(), 3, 0, 2, lzmaDictionary(expected), null).use { input ->
                val out = ByteArray(expected)
                DataInputStream(input).readFully(out)
                return out
            }
        }

        private fun lzmaDictionary(reduceTo: Int): Int {
            for (i in 11..24) {
                if (reduceTo <= (2 shl i)) return 2 shl i
                if (reduceTo <= (3 shl i)) return 3 shl i
            }
            return 1 shl 25
        }
    }
}
