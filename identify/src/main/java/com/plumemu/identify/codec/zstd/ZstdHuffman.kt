package com.plumemu.identify.codec.zstd

/** A Huffman decoding table for literals (RFC 8878 section 4.2): index by the next [maxBits] bits. */
internal class HuffmanTable(val maxBits: Int, private val symbol: ByteArray, private val bits: ByteArray, val consumed: Int) {

    /** Decode [count] symbols of one backward stream `src[start, end)` into `out` at [outOff]; the stream must be used up exactly. */
    fun decode(src: ByteArray, start: Int, end: Int, out: ByteArray, outOff: Int, count: Int) {
        val stream = BackwardBits(src, start, end)
        for (i in 0 until count) {
            val index = stream.peek(maxBits).toInt()
            out[outOff + i] = symbol[index]
            stream.skip(bits[index].toInt())
        }
        if (!stream.consumed) throw ZstdException("a Huffman stream is not used up exactly")
    }

    companion object {
        private const val MAX_BITS = 11
        private const val MAX_WEIGHT_LOG = 6
        private const val MAX_WEIGHT_SYMBOL = 12
        private const val MAX_WEIGHTS = 255

        /** Read the table description at `src[off, end)`: direct 4-bit weights or FSE-compressed weights. */
        fun parse(src: ByteArray, off: Int, end: Int): HuffmanTable {
            if (off >= end) throw ZstdException("a Huffman table is missing")
            val header = src[off].toInt() and 0xFF
            val weights = IntArray(MAX_WEIGHTS + 3)
            val count: Int
            val consumed: Int
            if (header >= 128) {
                count = header - 127
                val bytes = (count + 1) / 2
                if (off + 1 + bytes > end) throw ZstdException("a Huffman table is cut short")
                for (i in 0 until count) {
                    val b = src[off + 1 + i / 2].toInt() and 0xFF
                    weights[i] = if (i % 2 == 0) b shr 4 else b and 15
                }
                consumed = 1 + bytes
            } else {
                if (header == 0 || off + 1 + header > end) throw ZstdException("a Huffman table is cut short")
                val streamEnd = off + 1 + header
                val table = FseTable.parse(src, off + 1, streamEnd, MAX_WEIGHT_SYMBOL, MAX_WEIGHT_LOG)
                count = decodeWeights(src, off + 1 + table.consumed, streamEnd, table, weights)
                consumed = 1 + header
            }
            return build(weights, count, consumed)
        }

        /** Two FSE states take turns, as the reference decoder does, until the stream runs dry. */
        private fun decodeWeights(src: ByteArray, start: Int, end: Int, t: FseTable, out: IntArray): Int {
            if (start >= end) throw ZstdException("Huffman weights are missing")
            val stream = BackwardBits(src, start, end)
            var s1 = stream.read(t.accuracyLog).toInt()
            var s2 = stream.read(t.accuracyLog).toInt()
            var n = 0
            while (true) {
                if (n >= MAX_WEIGHTS) throw ZstdException("a Huffman table names too many weights")
                out[n++] = t.symbol[s1]
                s1 = t.base[s1] + stream.read(t.nbBits[s1]).toInt()
                if (stream.overflow) {
                    out[n++] = t.symbol[s2]
                    break
                }
                if (n >= MAX_WEIGHTS) throw ZstdException("a Huffman table names too many weights")
                out[n++] = t.symbol[s2]
                s2 = t.base[s2] + stream.read(t.nbBits[s2]).toInt()
                if (stream.overflow) {
                    out[n++] = t.symbol[s1]
                    break
                }
            }
            if (n > MAX_WEIGHTS) throw ZstdException("a Huffman table names too many weights")
            return n
        }

        /** [count] weights are known; the last symbol's weight is what makes the total a power of two. */
        private fun build(weights: IntArray, count: Int, consumed: Int): HuffmanTable {
            var sum = 0L
            for (i in 0 until count) {
                if (weights[i] > MAX_BITS) throw ZstdException("a Huffman weight is too large")
                if (weights[i] > 0) sum += 1L shl (weights[i] - 1)
            }
            if (sum == 0L) throw ZstdException("a Huffman table has no symbols")
            val maxBits = 64 - java.lang.Long.numberOfLeadingZeros(sum)
            if (maxBits > MAX_BITS) throw ZstdException("a Huffman code is longer than $MAX_BITS bits")
            val rest = (1L shl maxBits) - sum
            if (rest and (rest - 1) != 0L) throw ZstdException("Huffman weights do not complete a code")
            weights[count] = 64 - java.lang.Long.numberOfLeadingZeros(rest)
            val symbols = count + 1
            val size = 1 shl maxBits
            val symbol = ByteArray(size)
            val bits = ByteArray(size)
            val next = IntArray(MAX_BITS + 2)
            // Entries of weight w start after those of every lower weight.
            var at = 0
            for (w in 1..maxBits) {
                next[w] = at
                for (s in 0 until symbols) if (weights[s] == w) at += 1 shl (w - 1)
            }
            for (s in 0 until symbols) {
                val w = weights[s]
                if (w == 0) continue
                val len = 1 shl (w - 1)
                java.util.Arrays.fill(symbol, next[w], next[w] + len, s.toByte())
                java.util.Arrays.fill(bits, next[w], next[w] + len, (maxBits + 1 - w).toByte())
                next[w] += len
            }
            return HuffmanTable(maxBits, symbol, bits, consumed)
        }
    }
}
