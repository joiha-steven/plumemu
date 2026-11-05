package com.plumemu.identify.codec.zstd

/** What a frame carries from one block to the next: tables to repeat, repeat offsets, the literals of the current block. */
internal class ZstdState {
    var huffman: HuffmanTable? = null
    var literalLengths: FseTable? = null
    var offsets: FseTable? = null
    var matchLengths: FseTable? = null
    var rep1 = 1
    var rep2 = 4
    var rep3 = 8
    var literals = ByteArray(0)
    var literalCount = 0

    fun ensureLiterals(size: Int) {
        if (literals.size < size) literals = ByteArray(size)
    }
}

/** The output of a whole call: grows up to [limit] and never past it. */
internal class ZstdOutput(initial: Int, val limit: Int) {
    var buf = ByteArray(initial)
        private set
    var pos = 0

    fun ensure(extra: Int) {
        if (extra < 0 || extra > limit - pos) throw ZstdException("the data is longer than the $limit bytes allowed")
        val need = pos + extra
        if (need > buf.size) buf = buf.copyOf(minOf(limit.toLong(), maxOf(need.toLong(), buf.size * 2L)).toInt())
    }

    /** Make room for a size the frame header announced. */
    fun reserve(total: Int) {
        if (total > limit) throw ZstdException("the data is $total bytes, more than the $limit allowed")
        if (total > buf.size) buf = buf.copyOf(total)
    }
}

/** One compressed block (RFC 8878 section 3.1.1.3): the literals section, then the sequences section. */
internal object ZstdBlock {
    const val MAX_BLOCK = 128 * 1024

    fun decode(st: ZstdState, src: ByteArray, off: Int, end: Int, out: ZstdOutput, frameStart: Int, window: Long) {
        val afterLiterals = ZstdLiterals.read(st, src, off, end)
        ZstdSequences.run(st, src, afterLiterals, end, out, frameStart, window)
    }
}
