package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.codec.lzma.LzmaException
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32be
import com.plumemu.identify.fingerprint.u64be
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * The header of a CHD (MAME `chd.h`), big-endian. Version 5, 124 bytes: "MComprHD" at 0, header
 * length at 8, version at 12, four codec tags at 16 (4 ASCII characters each, 0 for none),
 * logical size u64 at 32, map offset u64 at 40, metadata offset u64 at 48, hunk size at 56, unit
 * size at 60, SHA-1 of the raw data at 0x40, SHA-1 of data and metadata at 0x54, parent SHA-1 at
 * 0x68. Version 4 (108 bytes) has the raw-data SHA-1 at 88; only that is read from it.
 */
internal class ChdHeader private constructor(
    val version: Int,
    val rawSha1: String,
    val codecs: List<String>,
    val logicalBytes: Long,
    val mapOffset: Long,
    val metaOffset: Long,
    val hunkBytes: Int,
    val unitBytes: Int,
) {
    val hunkCount: Long get() = (logicalBytes + hunkBytes - 1) / hunkBytes

    /** Compressed CHDs list a first codec; an uncompressed one has none and a plain map. */
    val compressed: Boolean get() = codecs[0].isNotEmpty()

    /** A CD image: frames of a 2352-byte sector and 96 bytes of subcode. */
    val isCd: Boolean get() = unitBytes == CD_FRAME

    companion object {
        const val CD_FRAME = 2448
        const val V5_LENGTH = 124
        private const val V4_LENGTH = 108
        private const val MAX_HUNK = 1 shl 20

        /** 1 TB: far beyond any disc; a larger size is a damaged header. */
        private const val MAX_LOGICAL = 1L shl 40
        private val TAG = ascii("MComprHD")

        fun isChd(start: ByteArray): Boolean = start.startsWith(TAG)

        /** The header; [NotRead] for other versions or values out of range. */
        @Throws(IOException::class)
        fun read(source: RandomAccessSource): ChdHeader {
            val bytes = source.readFully(0, V5_LENGTH)
            if (bytes.size < 16 || !isChd(bytes)) throw NotRead("chd: header is truncated")
            val version = bytes.u32be(12).toInt()
            if (version == 4 && bytes.size >= V4_LENGTH) {
                return ChdHeader(4, hex(bytes.copyOfRange(88, 108)), listOf("", "", "", ""), 0, 0, 0, 1, 1)
            }
            if (version != 5 || bytes.size < V5_LENGTH || bytes.u32be(8) != V5_LENGTH.toLong()) {
                throw NotRead("chd: version $version not read (only version 5, and the SHA-1 of version 4)")
            }
            val codecs = (0 until 4).map { codecName(bytes, 16 + it * 4) }
            val hunkBytes = bytes.u32be(56)
            val unitBytes = bytes.u32be(60)
            if (hunkBytes !in 1..MAX_HUNK || unitBytes !in 1..hunkBytes || hunkBytes % unitBytes != 0L) {
                throw NotRead("chd: hunk size $hunkBytes or unit size $unitBytes is out of range")
            }
            val logicalBytes = bytes.u64be(32)
            val mapOffset = bytes.u64be(40)
            val metaOffset = bytes.u64be(48)
            if (logicalBytes !in 1..MAX_LOGICAL || mapOffset !in V5_LENGTH until source.size || metaOffset !in 0 until source.size) {
                throw NotRead("chd: logical size, map or metadata offset is out of range")
            }
            return ChdHeader(
                version = 5,
                rawSha1 = hex(bytes.copyOfRange(0x40, 0x54)),
                codecs = codecs,
                logicalBytes = logicalBytes,
                mapOffset = mapOffset,
                metaOffset = metaOffset,
                hunkBytes = hunkBytes.toInt(),
                unitBytes = unitBytes.toInt(),
            )
        }

        private fun codecName(bytes: ByteArray, at: Int): String =
            if (bytes.u32be(at) == 0L) "" else String(bytes, at, 4, Charsets.ISO_8859_1)
    }
}

/**
 * The logical data of a CHD v5, one hunk decompressed at a time on demand. Codecs read: hunks
 * stored as is, `zlib` (raw deflate), `cdzl` (CD: deflate of the sectors and, separately, of
 * the subcode), `lzma` and `cdlz` (the same with LZMA, [ChdLzma]). Every other codec (`cdfl`,
 * `flac`, `zstd`, `cdzs`, `huff`, ...) throws [NotRead]: their decoders are not in the allowed
 * libraries. Hunks in a parent CHD are not read. Used by one disc read on one thread; keeps the
 * last hunk decoded.
 *
 * An LZMA hunk is decoded only as far as the read needs (a CD hunk in whole frames), and again
 * further, at least twice as far, when a later read needs more; its compressed bytes are kept,
 * so going further fetches only what the longer prefix adds. Damaged LZMA data is a [NotRead].
 */
internal class ChdSource(private val file: RandomAccessSource, private val header: ChdHeader) : RandomAccessSource {
    private val map: ChdMap = if (header.compressed) {
        CompressedChdMap.open(file, header.mapOffset, header.hunkCount, header.hunkBytes, header.unitBytes)
    } else {
        UncompressedChdMap(file, header.mapOffset, header.hunkBytes)
    }
    private var cachedHunk = -1L

    /** Hunk [cachedHunk], or the start of it when an LZMA hunk was decoded only that far. */
    private var cached = ByteArray(0)

    /** Compressed bytes of the last LZMA hunk read, kept for a longer prefix of it. */
    private var lzmaBytes: HunkBytes? = null
    private var lzmaBytesHunk = -1L

    override val size: Long get() = header.logicalBytes

    @Throws(IOException::class)
    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val index = position / header.hunkBytes
        val inHunk = (position - index * header.hunkBytes).toInt()
        val n = minOf(length, header.hunkBytes - inHunk)
        if (index != cachedHunk) {
            // A stored hunk is read in place: only the bytes asked for, not the whole hunk.
            val entry = map.hunk(index)
            if (entry.type == ChdHunk.NONE) {
                checkRange(entry)
                return file.readFully(entry.offset + inHunk, n).also { it.copyInto(dst, offset) }.size
            }
        }
        val data = hunk(index, 0, inHunk + n)
        System.arraycopy(data, inHunk, dst, offset, n)
        return n
    }

    /** At least the first [needed] bytes of hunk [index]; the whole hunk except for LZMA codecs. */
    @Throws(IOException::class)
    private fun hunk(index: Long, depth: Int, needed: Int): ByteArray {
        if (index == cachedHunk && cached.size >= needed) return cached
        val entry = map.hunk(index)
        val data = when (entry.type) {
            in 0..ChdHunk.CODEC_3 -> decode(index, entry, header.codecs[entry.type], needed)
            ChdHunk.NONE -> whole(index, read(entry))
            ChdHunk.ZEROS -> ByteArray(header.hunkBytes)
            ChdHunk.SELF -> {
                if (entry.offset >= index || depth > MAX_SELF_DEPTH) throw NotRead("chd: hunk $index refers to hunk ${entry.offset}")
                hunk(entry.offset, depth + 1, needed)
            }
            ChdHunk.PARENT -> throw NotRead("chd: hunk $index is in a parent CHD, not read")
            else -> throw NotRead("chd: hunk $index has type ${entry.type}")
        }
        if (data.size < needed || data.size > header.hunkBytes) throw NotRead("chd: hunk $index decompressed to ${data.size} bytes")
        cachedHunk = index
        cached = data
        return data
    }

    private fun whole(index: Long, data: ByteArray): ByteArray {
        if (data.size != header.hunkBytes) throw NotRead("chd: hunk $index decompressed to ${data.size} bytes")
        return data
    }

    @Throws(IOException::class)
    private fun read(entry: ChdHunk): ByteArray {
        checkRange(entry)
        return file.readFully(entry.offset, entry.length.toInt())
    }

    private fun checkRange(entry: ChdHunk) {
        if (entry.length !in 1..header.hunkBytes.toLong() + MAX_OVERHEAD || entry.offset + entry.length > file.size) {
            throw NotRead("chd: hunk at ${entry.offset} (${entry.length} bytes) is out of range")
        }
    }

    @Throws(IOException::class)
    private fun decode(index: Long, entry: ChdHunk, codec: String, needed: Int): ByteArray = when (codec) {
        "zlib" -> whole(index, inflate(read(entry), header.hunkBytes, nowrap = true, what = "chd zlib"))
        "cdzl" -> whole(index, decodeCdZlib(read(entry)))
        "lzma", "cdlz" -> decodeLzma(index, entry, codec, needed)
        else -> throw NotRead("chd: hunk codec ${codec.ifEmpty { "(none)" }} not read")
    }

    /**
     * The first [needed] bytes of an `lzma` / `cdlz` hunk, or more: twice what the last decode
     * of the same hunk gave, so reading a hunk piece by piece decodes it a few times, not once
     * per piece. [LzmaException] is not an IOException or a
     * RuntimeException: it is turned into a [NotRead] here, or it would escape every catch.
     */
    @Throws(IOException::class)
    private fun decodeLzma(index: Long, entry: ChdHunk, codec: String, needed: Int): ByteArray {
        checkRange(entry)
        val bytes = lzmaBytes.takeIf { lzmaBytesHunk == index }
            ?: HunkBytes(file, entry.offset, entry.length.toInt()).also {
                lzmaBytes = it
                lzmaBytesHunk = index
            }
        val before = if (cachedHunk == index) cached.size else 0
        val wanted = maxOf(needed, minOf(header.hunkBytes, before * 2))
        try {
            if (codec == "lzma") return ChdLzma.lzmaHunk(bytes, header.hunkBytes, minOf(wanted, header.hunkBytes))
            val frames = header.hunkBytes / ChdHeader.CD_FRAME
            val wantedFrames = (wanted + ChdHeader.CD_FRAME - 1) / ChdHeader.CD_FRAME
            return ChdLzma.cdlzHunk(bytes, header.hunkBytes, minOf(wantedFrames, frames))
        } catch (e: LzmaException) {
            throw NotRead("chd: hunk $index: LZMA data damaged (${e.message})")
        }
    }

    /**
     * `cdzl` (MAME `chd_cd_decompressor`): a bitmap with one bit per frame (set: the sync header
     * and ECC were removed and must be put back), the length of the sector part (2 bytes, 3 when
     * the hunk is 64 KB or more), the deflated sectors (2352 bytes per frame), the deflated
     * subcode (96 per frame). The subcode is not needed here and is left as zeros; the ECC is
     * not regenerated (the user data does not need it), but the sync header is.
     */
    private fun decodeCdZlib(src: ByteArray): ByteArray {
        val frames = header.hunkBytes / ChdHeader.CD_FRAME
        val eccBytes = (frames + 7) / 8
        val lengthBytes = if (header.hunkBytes < 65536) 2 else 3
        if (src.size < eccBytes + lengthBytes) throw NotRead("chd cdzl: hunk is truncated")
        var baseLength = 0
        for (i in 0 until lengthBytes) baseLength = (baseLength shl 8) or src.u8(eccBytes + i)
        val start = eccBytes + lengthBytes
        if (start + baseLength > src.size) throw NotRead("chd cdzl: sector data length is out of range")
        val sectors = inflate(src.copyOfRange(start, start + baseLength), frames * SECTOR, nowrap = true, what = "chd cdzl")
        if (sectors.size != frames * SECTOR) throw NotRead("chd cdzl: sector data is short")
        val out = ByteArray(header.hunkBytes)
        for (frame in 0 until frames) {
            System.arraycopy(sectors, frame * SECTOR, out, frame * ChdHeader.CD_FRAME, SECTOR)
            if (src.u8(frame / 8) and (1 shl (frame % 8)) != 0) {
                System.arraycopy(ChdLzma.CD_SYNC, 0, out, frame * ChdHeader.CD_FRAME, ChdLzma.CD_SYNC.size)
            }
        }
        return out
    }

    override fun close() = Unit

    private companion object {
        const val SECTOR = 2352
        const val MAX_OVERHEAD = 64L
        const val MAX_SELF_DEPTH = 8
    }
}
