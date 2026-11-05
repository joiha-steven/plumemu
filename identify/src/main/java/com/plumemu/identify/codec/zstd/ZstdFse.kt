package com.plumemu.identify.codec.zstd

/** One FSE decoding table (RFC 8878 section 4.1): per state a symbol, the bits to read and the base of the next state. */
internal class FseTable(
    val accuracyLog: Int,
    val symbol: IntArray,
    val nbBits: IntArray,
    val base: IntArray,
    /** Bytes the table description took in the input (0 for a table that was not read). */
    val consumed: Int = 0,
) {
    companion object {
        /** A table with one symbol (compression mode RLE): zero bits per step. */
        fun single(symbol: Int) = FseTable(0, intArrayOf(symbol), intArrayOf(0), intArrayOf(0))

        /** Read a table description from `src[off, end)`. */
        fun parse(src: ByteArray, off: Int, end: Int, maxSymbol: Int, maxLog: Int): FseTable {
            val bits = ForwardBits(src, off, end)
            val log = bits.peek(4) + 5
            bits.skip(4)
            if (log > maxLog) throw ZstdException("an FSE table has accuracy $log, more than $maxLog")
            val norm = IntArray(maxSymbol + 1)
            var remaining = (1 shl log) + 1
            var threshold = 1 shl log
            var nbBits = log + 1
            var symbol = 0
            var previousZero = false
            while (remaining > 1 && symbol <= maxSymbol) {
                if (previousZero) {
                    // A run of symbols with probability zero: 2-bit counts, 3 meaning "and read another".
                    var run = symbol
                    while (true) {
                        val r = bits.peek(2)
                        bits.skip(2)
                        run += r
                        if (run > maxSymbol) throw ZstdException("an FSE table names too many symbols")
                        if (r != 3) break
                    }
                    symbol = run
                }
                val max = 2 * threshold - 1 - remaining
                val low = bits.peek(nbBits - 1) and (threshold - 1)
                var count: Int
                if (low < max) {
                    count = low
                    bits.skip(nbBits - 1)
                } else {
                    count = bits.peek(nbBits) and (2 * threshold - 1)
                    if (count >= threshold) count -= max
                    bits.skip(nbBits)
                }
                count--
                remaining -= if (count < 0) -count else count
                if (remaining < 1) throw ZstdException("an FSE table's probabilities add up to too much")
                norm[symbol++] = count
                previousZero = count == 0
                while (remaining < threshold) {
                    nbBits--
                    threshold = threshold shr 1
                }
            }
            if (remaining != 1) throw ZstdException("an FSE table's probabilities do not add up")
            return build(norm, symbol, log, bits.bytesUsed)
        }

        /** Build the decoding table from normalised counts (-1 is "less than one"). */
        fun build(norm: IntArray, symbols: Int, log: Int, consumed: Int = 0): FseTable {
            val size = 1 shl log
            val mask = size - 1
            val symbolOf = IntArray(size)
            val next = IntArray(symbols)
            var high = size - 1
            for (s in 0 until symbols) {
                if (norm[s] == -1) {
                    symbolOf[high--] = s
                    next[s] = 1
                } else {
                    next[s] = norm[s]
                }
            }
            val step = (size shr 1) + (size shr 3) + 3
            var pos = 0
            for (s in 0 until symbols) {
                repeat(maxOf(norm[s], 0)) {
                    symbolOf[pos] = s
                    do pos = (pos + step) and mask while (pos > high)
                }
            }
            if (pos != 0) throw ZstdException("an FSE table's probabilities do not fill it")
            val nb = IntArray(size)
            val base = IntArray(size)
            for (u in 0 until size) {
                val state = next[symbolOf[u]]++
                val bitsOfState = log - (31 - Integer.numberOfLeadingZeros(state))
                nb[u] = bitsOfState
                base[u] = (state shl bitsOfState) - size
            }
            return FseTable(log, symbolOf, nb, base, consumed)
        }
    }
}
