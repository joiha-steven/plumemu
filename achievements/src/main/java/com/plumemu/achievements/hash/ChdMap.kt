// The compressed map of a CHD version 5 (Huffman-coded hunk kinds, then each hunk's length, offset and CRC).
package com.plumemu.achievements.hash

import com.plumemu.achievements.hash.RaCannotHashReason.BadImage

internal object ChdMap {
    // The compressed map's symbols.
    private const val SYMBOL_NONE = 4
    private const val SYMBOL_SELF = 5
    private const val SYMBOL_PARENT = 6
    private const val SYMBOL_RLE_SMALL = 7
    private const val SYMBOL_RLE_LARGE = 8
    private const val SYMBOL_SELF_0 = 9
    private const val SYMBOL_SELF_1 = 10
    private const val SYMBOL_PARENT_SELF = 11
    private const val SYMBOL_PARENT_0 = 12
    private const val SYMBOL_PARENT_1 = 13

    fun read(
        reader: RangeReader, fileSize: Long, at: Long, hunkBytes: Int, unitBytes: Int,
        types: ByteArray, offsets: LongArray, lengths: IntArray, crcs: IntArray,
    ) {
        val head = ByteArray(16)
        if (reader.readAt(at, head) < 16) throw ChdImage.bad("the map is cut short")
        val dataLength = ChdImage.be(head, 0, 4)
        val firstOffset = ChdImage.be(head, 4, 6)
        val mapCrc = ChdImage.be(head, 10, 2).toInt()
        val lengthBits = head[12].toInt() and 0xFF
        val selfBits = head[13].toInt() and 0xFF
        val parentBits = head[14].toInt() and 0xFF
        if (dataLength <= 0 || at + 16 + dataLength > fileSize || dataLength > types.size * 4L + 4096) throw ChdImage.bad("the map length is not valid")
        if (lengthBits > 24 || selfBits > 32 || parentBits > 32) throw ChdImage.bad("the map's field widths are not valid")
        val data = ByteArray(dataLength.toInt())
        if (reader.readAt(at + 16, data) < data.size) throw ChdImage.bad("the map is cut short")

        val bits = MapBits(data)
        val huffman = MapHuffman.read(bits)
        val hunks = types.size
        // First every hunk's kind (runs of equal kinds are packed), then each hunk's length/offset and CRC.
        val kinds = IntArray(hunks)
        var last = 0
        var repeat = 0
        for (n in 0 until hunks) {
            if (repeat > 0) {
                kinds[n] = last
                repeat--
                continue
            }
            when (val symbol = huffman.decode(bits)) {
                SYMBOL_RLE_SMALL -> { kinds[n] = last; repeat = 2 + huffman.decode(bits) }
                SYMBOL_RLE_LARGE -> {
                    kinds[n] = last
                    repeat = 2 + 16 + (huffman.decode(bits) shl 4)
                    repeat += huffman.decode(bits)
                }
                else -> { kinds[n] = symbol; last = symbol }
            }
        }
        var offset = firstOffset
        var lastSelf = 0L
        var lastParent = 0L
        val crcInput = ByteArray(hunks * 12)
        for (n in 0 until hunks) {
            var length = 0
            var entryOffset = offset
            var crc = 0
            var type: Int
            when (val kind = kinds[n]) {
                in 0..3 -> {
                    type = kind
                    length = bits.read(lengthBits).toInt()
                    offset += length
                    crc = bits.read(16).toInt()
                }
                SYMBOL_NONE -> {
                    type = ChdImage.TYPE_NONE
                    length = hunkBytes
                    offset += hunkBytes
                    crc = bits.read(16).toInt()
                }
                SYMBOL_SELF -> { type = ChdImage.TYPE_SELF; entryOffset = bits.read(selfBits); lastSelf = entryOffset }
                SYMBOL_PARENT -> { type = ChdImage.TYPE_PARENT; entryOffset = bits.read(parentBits); lastParent = entryOffset }
                SYMBOL_SELF_0 -> { type = ChdImage.TYPE_SELF; entryOffset = lastSelf }
                SYMBOL_SELF_1 -> { type = ChdImage.TYPE_SELF; lastSelf++; entryOffset = lastSelf }
                SYMBOL_PARENT_SELF -> { type = ChdImage.TYPE_PARENT; lastParent = n.toLong() * hunkBytes / unitBytes; entryOffset = lastParent }
                SYMBOL_PARENT_0 -> { type = ChdImage.TYPE_PARENT; entryOffset = lastParent }
                SYMBOL_PARENT_1 -> { type = ChdImage.TYPE_PARENT; lastParent += hunkBytes / unitBytes; entryOffset = lastParent }
                else -> throw ChdImage.bad("the map has an entry kind $kind that no tool writes")
            }
            types[n] = type.toByte()
            offsets[n] = entryOffset
            lengths[n] = length
            if (type <= ChdImage.TYPE_NONE) crcs[n] = crc
            if (type <= ChdImage.TYPE_NONE && (length < 0 || length > 2L * hunkBytes + 4096 || entryOffset < ChdImage.HEADER_BYTES || entryOffset + length > fileSize)) {
                throw ChdImage.bad("hunk $n points outside the file")
            }
            val e = n * 12
            crcInput[e] = type.toByte()
            for (i in 0 until 3) crcInput[e + 1 + i] = (length shr (16 - 8 * i)).toByte()
            for (i in 0 until 6) crcInput[e + 4 + i] = (entryOffset shr (40 - 8 * i)).toByte()
            crcInput[e + 10] = (crc shr 8).toByte()
            crcInput[e + 11] = crc.toByte()
        }
        if (ChdImage.crc16(crcInput) != mapCrc) throw ChdImage.bad("the map fails its CRC")
    }

}

/** Reads bits most significant first; reading past the end is a damaged map. */
private class MapBits(private val data: ByteArray) {
    private var position = 0L

    fun read(count: Int): Long {
        var v = 0L
        repeat(count) {
            val byte = (position shr 3).toInt()
            if (byte >= data.size) throw DiscFormatException(BadImage, "Damaged .chd: the map is cut short")
            v = (v shl 1) or ((data[byte].toInt() shr (7 - (position and 7).toInt())) and 1).toLong()
            position++
        }
        return v
    }
}

/** The canonical Huffman code the compressed map uses for its 16 symbols (at most 8 bits each). */
private class MapHuffman private constructor(private val symbols: Map<Int, Int>) {
    fun decode(bits: MapBits): Int {
        var code = 0
        for (length in 1..MAX_BITS) {
            code = (code shl 1) or bits.read(1).toInt()
            symbols[(length shl 16) or code]?.let { return it }
        }
        throw DiscFormatException(BadImage, "Damaged .chd: a map code matches no symbol")
    }

    companion object {
        private const val SYMBOLS = 16
        private const val MAX_BITS = 8
        private const val LENGTH_FIELD_BITS = 4

        /** The code lengths come run-length packed: a 1 is an escape (a second 1 is a literal 1, otherwise a length repeated 3 or more times). */
        fun read(bits: MapBits): MapHuffman {
            val lengths = IntArray(SYMBOLS)
            var node = 0
            while (node < SYMBOLS) {
                var value = bits.read(LENGTH_FIELD_BITS).toInt()
                if (value != 1) {
                    lengths[node++] = value
                    continue
                }
                value = bits.read(LENGTH_FIELD_BITS).toInt()
                if (value == 1) {
                    lengths[node++] = 1
                } else {
                    val count = bits.read(LENGTH_FIELD_BITS).toInt() + 3
                    if (node + count > SYMBOLS) throw DiscFormatException(BadImage, "Damaged .chd: the map's code table is too long")
                    repeat(count) { lengths[node++] = value }
                }
            }
            if (lengths.any { it > MAX_BITS }) throw DiscFormatException(BadImage, "Damaged .chd: the map's code table is not valid")
            // Canonical codes: the longest codes take the lowest values.
            val perLength = IntArray(MAX_BITS + 2)
            for (l in lengths) if (l > 0) perLength[l]++
            val first = IntArray(MAX_BITS + 2)
            var start = 0
            for (length in MAX_BITS downTo 1) {
                val sum = start + perLength[length]
                if (length != 1 && sum % 2 != 0) throw DiscFormatException(BadImage, "Damaged .chd: the map's code table is not valid")
                first[length] = start
                start = sum shr 1
            }
            val symbols = HashMap<Int, Int>()
            for (symbol in 0 until SYMBOLS) {
                val l = lengths[symbol]
                if (l > 0) symbols[(l shl 16) or first[l]++] = symbol
            }
            return MapHuffman(symbols)
        }
    }
}
