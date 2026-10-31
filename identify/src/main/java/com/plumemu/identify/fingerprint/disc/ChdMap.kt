package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.u16be
import com.plumemu.identify.fingerprint.u32be
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/** Where one CHD hunk is and how it is stored. */
internal data class ChdHunk(val type: Int, val length: Long, val offset: Long) {
    companion object {
        /** Codec 0-3 of the header: [type] indexes the header's codec list. */
        const val CODEC_3 = 3

        /** Stored as is, [length] = hunk size. */
        const val NONE = 4

        /** A copy of the hunk numbered [offset]. */
        const val SELF = 5

        /** In the parent CHD. */
        const val PARENT = 6

        /** All zeros (uncompressed map entry 0). */
        const val ZEROS = 100
    }
}

/** The hunk map of a CHD v5 (MAME `chd.cpp`). */
internal interface ChdMap {
    @Throws(IOException::class)
    fun hunk(index: Long): ChdHunk
}

/** Uncompressed CHD: one u32 big-endian per hunk, the hunk's file offset divided by the hunk size. */
internal class UncompressedChdMap(
    private val source: RandomAccessSource,
    private val mapOffset: Long,
    private val hunkBytes: Int,
) : ChdMap {
    @Throws(IOException::class)
    override fun hunk(index: Long): ChdHunk {
        val entry = source.readFully(mapOffset + index * 4, 4)
        if (entry.size < 4) throw NotRead("chd: map is truncated")
        val offset = entry.u32be(0) * hunkBytes
        return if (offset == 0L) ChdHunk(ChdHunk.ZEROS, 0, 0) else ChdHunk(ChdHunk.NONE, hunkBytes.toLong(), offset)
    }
}

/**
 * Compressed CHD v5 map (`chd_file::decompress_v5_map`): a 16-byte header (compressed length
 * u32, first data offset u48, map CRC u16, bits per length, per self reference, per parent
 * reference), then a bit stream read most significant bit first: a Huffman tree (16 symbols, up
 * to 8 bits, stored run-length coded in 4-bit fields), the type of every hunk (with run-length
 * symbols 7 and 8), then per hunk its length and CRC, or its reference.
 *
 * Finding hunk n needs the types of all hunks (the second part starts after them) but only the
 * lengths of hunks 0..n, so the stream is fetched in 4 KB pieces and decoded only that far. The
 * map CRC covers every hunk and is not checked: a damaged map shows up as a hunk that fails to
 * decompress, which becomes a note.
 */
internal class CompressedChdMap private constructor(
    private val bits: BitReader,
    private val types: ByteArray,
    private val header: ByteArray,
    private val hunkBytes: Int,
    private val unitBytes: Int,
) : ChdMap {
    private val lengthBits = header.u8(12)
    private val selfBits = header.u8(13)
    private val parentBits = header.u8(14)
    private var position = (header.u16be(4).toLong() shl 32) or header.u32be(6)
    private var lastSelf = 0L
    private var lastParent = 0L
    private val decoded = ArrayList<ChdHunk>()

    @Throws(IOException::class)
    override fun hunk(index: Long): ChdHunk {
        if (index < 0 || index >= types.size) throw NotRead("chd: hunk $index is past the first ${types.size} hunks of the map")
        while (decoded.size <= index) decoded += next(decoded.size)
        return decoded[index.toInt()]
    }

    @Throws(IOException::class)
    private fun next(index: Int): ChdHunk {
        val type = types[index].toInt()
        return when (type) {
            in 0..ChdHunk.CODEC_3 -> {
                val length = bits.read(lengthBits)
                bits.read(CRC_BITS)
                ChdHunk(type, length, position).also { position += length }
            }
            ChdHunk.NONE -> {
                bits.read(CRC_BITS)
                ChdHunk(type, hunkBytes.toLong(), position).also { position += hunkBytes }
            }
            ChdHunk.SELF -> ChdHunk(ChdHunk.SELF, 0, bits.read(selfBits).also { lastSelf = it })
            ChdHunk.PARENT -> ChdHunk(ChdHunk.PARENT, 0, bits.read(parentBits).also { lastParent = it })
            SELF_1 -> ChdHunk(ChdHunk.SELF, 0, ++lastSelf)
            SELF_0 -> ChdHunk(ChdHunk.SELF, 0, lastSelf)
            PARENT_SELF -> {
                lastParent = index.toLong() * hunkBytes / unitBytes
                ChdHunk(ChdHunk.PARENT, 0, lastParent)
            }
            PARENT_1 -> {
                lastParent += hunkBytes / unitBytes
                ChdHunk(ChdHunk.PARENT, 0, lastParent)
            }
            PARENT_0 -> ChdHunk(ChdHunk.PARENT, 0, lastParent)
            else -> throw NotRead("chd: map has hunk type $type")
        }
    }

    companion object {
        private const val CRC_BITS = 16
        private const val RLE_SMALL = 7
        private const val RLE_LARGE = 8
        private const val SELF_0 = 9
        private const val SELF_1 = 10
        private const val PARENT_SELF = 11
        private const val PARENT_0 = 12
        private const val PARENT_1 = 13
        private const val MAX_HUNKS = 16L shl 20

        /** Most hunks one bit of map can describe: a run symbol and its two counts cover 274 hunks in 3 bits or more. */
        private const val MAX_HUNKS_PER_BIT = 92L

        /** Types kept for the first hunks only: identification reads the start of a disc. */
        private const val TRACKED_HUNKS = 1 shl 18

        /**
         * Reads the map header and the type of every hunk, keeping the types of the first
         * [TRACKED_HUNKS]. Stops with [NotRead] as soon as the bit stream runs out, and refuses a
         * hunk count the map's bytes (at most the 64 KB a disc read may take) cannot describe.
         */
        @Throws(IOException::class)
        fun open(source: RandomAccessSource, mapOffset: Long, hunkCount: Long, hunkBytes: Int, unitBytes: Int): CompressedChdMap {
            if (hunkCount > MAX_HUNKS) throw NotRead("chd: $hunkCount hunks is more than the map reader takes")
            val header = source.readFully(mapOffset, 16)
            if (header.size < 16) throw NotRead("chd: map header is truncated")
            val mapBytes = header.u32be(0)
            val readable = minOf(mapBytes, DiscReader.DEFAULT_CAP)
            if (hunkCount > readable * 8 * MAX_HUNKS_PER_BIT) {
                throw NotRead("chd: a $mapBytes-byte map cannot describe $hunkCount hunks")
            }
            val bits = BitReader(source, mapOffset + 16, mapBytes)
            val huffman = Huffman.readTree(bits)
            val types = ByteArray(minOf(hunkCount, TRACKED_HUNKS.toLong()).toInt())
            var last = 0
            var repeat = 0
            for (i in 0 until hunkCount) {
                if (repeat > 0) {
                    repeat--
                } else {
                    // A run symbol gives this hunk the last type and repeats it for `repeat` more.
                    when (val symbol = huffman.decode(bits)) {
                        RLE_SMALL -> repeat = 2 + huffman.decode(bits)
                        RLE_LARGE -> repeat = 2 + 16 + (huffman.decode(bits) shl 4) + huffman.decode(bits)
                        else -> last = symbol
                    }
                    if (bits.overflowed) throw NotRead("chd: map ends before the type of hunk $i")
                }
                if (i < types.size) types[i.toInt()] = last.toByte()
            }
            return CompressedChdMap(bits, types, header, hunkBytes, unitBytes)
        }
    }
}

/**
 * MAME's canonical Huffman decoder (`huffman.cpp`) for 16 symbols of at most 8 bits: code lengths
 * stored as 4-bit fields, 1 is an escape (1 1 is a literal 1; 1 n r is n repeated r + 3 times).
 */
internal class Huffman private constructor(private val lookup: IntArray) {
    fun decode(bits: BitReader): Int {
        val entry = lookup[bits.peek(MAX_BITS).toInt()]
        if (entry < 0) throw NotRead("chd: map has a Huffman code that is not in its tree")
        bits.skip(entry and 0xF)
        return entry ushr 4
    }

    companion object {
        private const val SYMBOLS = 16
        private const val MAX_BITS = 8

        fun readTree(bits: BitReader): Huffman {
            val lengths = IntArray(SYMBOLS)
            var node = 0
            while (node < SYMBOLS) {
                val value = bits.read(4).toInt()
                if (value != 1) {
                    lengths[node++] = value
                    continue
                }
                val next = bits.read(4).toInt()
                if (next == 1) {
                    lengths[node++] = 1
                    continue
                }
                val count = bits.read(4).toInt() + 3
                if (node + count > SYMBOLS) throw NotRead("chd: map Huffman tree is damaged")
                repeat(count) { lengths[node++] = next }
            }
            if (lengths.any { it > MAX_BITS }) throw NotRead("chd: map Huffman tree is damaged")
            // Kraft: the codes must fit in the 8-bit lookup table; an over-subscribed tree would write past it.
            if (lengths.filter { it > 0 }.sumOf { 1 shl (MAX_BITS - it) } > 1 shl MAX_BITS) {
                throw NotRead("chd: map Huffman tree is over-subscribed")
            }
            val codes = canonicalCodes(lengths)
            for (symbol in 0 until SYMBOLS) {
                if (lengths[symbol] > 0 && codes[symbol] >= 1 shl lengths[symbol]) throw NotRead("chd: map Huffman tree is damaged")
            }
            return Huffman(lookupTable(codes, lengths))
        }

        private fun canonicalCodes(lengths: IntArray): IntArray {
            val start = IntArray(MAX_BITS + 1)
            for (length in lengths) start[length]++
            var current = 0
            for (length in MAX_BITS downTo 1) {
                val next = (current + start[length]) shr 1
                if (length != 1 && next * 2 != current + start[length]) throw NotRead("chd: map Huffman tree is damaged")
                start[length] = current
                current = next
            }
            return IntArray(SYMBOLS) { symbol -> if (lengths[symbol] > 0) start[lengths[symbol]]++ else 0 }
        }

        private fun lookupTable(codes: IntArray, lengths: IntArray): IntArray {
            val lookup = IntArray(1 shl MAX_BITS) { -1 }
            for (symbol in 0 until SYMBOLS) {
                val length = lengths[symbol]
                if (length == 0) continue
                val shift = MAX_BITS - length
                val first = codes[symbol] shl shift
                for (i in first until first + (1 shl shift)) lookup[i] = (symbol shl 4) or length
            }
            return lookup
        }
    }
}

/**
 * Bits of [length] bytes at [start] in [source], most significant first, fetched 4 KB at a time
 * as they are needed. Past the end it reads zeros and sets [overflowed], as MAME's reader does.
 */
internal class BitReader(private val source: RandomAccessSource, private val start: Long, private val length: Long) {
    private var data = ByteArray(0)
    private var bit = 0L

    var overflowed = false
        private set

    @Throws(IOException::class)
    fun read(count: Int): Long = peek(count).also { skip(count) }

    @Throws(IOException::class)
    fun peek(count: Int): Long {
        var value = 0L
        for (i in 0 until count) value = (value shl 1) or bitAt(bit + i).toLong()
        return value
    }

    fun skip(count: Int) {
        bit += count
        if (bit > length * 8) overflowed = true
    }

    @Throws(IOException::class)
    private fun bitAt(index: Long): Int {
        val byte = index ushr 3
        if (byte >= length) return 0
        while (byte >= data.size) {
            val more = source.readFully(start + data.size, minOf(CHUNK.toLong(), length - data.size).toInt())
            if (more.isEmpty()) throw NotRead("chd: map is truncated")
            data += more
        }
        return (data[byte.toInt()].toInt() ushr (7 - (index and 7).toInt())) and 1
    }

    private companion object {
        const val CHUNK = 4096
    }
}
