package com.plumemu.identify.codec.zstd

/** The sequences section (RFC 8878 section 3.1.1.3.2): decode each (literal length, offset, match length) and run it at once. */
internal object ZstdSequences {
    private const val MAX_LL = 35
    private const val MAX_ML = 52
    private const val MAX_OF = 31
    private const val MAX_OFFSET_CODE = 30
    private const val LL_LOG = 9
    private const val ML_LOG = 9
    private const val OF_LOG = 8

    private val LL_BASE = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 18, 20, 22, 24, 28, 32, 40, 48, 64, 128, 256, 512, 1024, 2048, 4096,
        8192, 16384, 32768, 65536,
    )
    private val LL_BITS = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
    )
    private val ML_BASE = IntArray(MAX_ML + 1) { if (it < 32) it + 3 else 0 }.also {
        val tail = intArrayOf(35, 37, 39, 41, 43, 47, 51, 59, 67, 83, 99, 131, 259, 515, 1027, 2051, 4099, 8195, 16387, 32771, 65539)
        tail.copyInto(it, 32)
    }
    private val ML_BITS = IntArray(MAX_ML + 1).also {
        intArrayOf(1, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16).copyInto(it, 32)
    }

    private val LL_DEFAULT = intArrayOf(
        4, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 3, 2, 1, 1, 1, 1, 1, -1, -1, -1, -1,
    )
    private val ML_DEFAULT = intArrayOf(1, 4, 3, 2, 2, 2, 2, 2, 2) + IntArray(37) { 1 } + IntArray(7) { -1 }
    private val OF_DEFAULT = intArrayOf(1, 1, 1, 1, 1, 1, 2, 2, 2) + IntArray(15) { 1 } + IntArray(5) { -1 }

    // Built on first use: a Plumemu that never reads a zstd frame pays nothing.
    private val PREDEFINED_LL by lazy(LazyThreadSafetyMode.PUBLICATION) { FseTable.build(LL_DEFAULT, LL_DEFAULT.size, 6) }
    private val PREDEFINED_ML by lazy(LazyThreadSafetyMode.PUBLICATION) { FseTable.build(ML_DEFAULT, ML_DEFAULT.size, 6) }
    private val PREDEFINED_OF by lazy(LazyThreadSafetyMode.PUBLICATION) { FseTable.build(OF_DEFAULT, OF_DEFAULT.size, 5) }

    private const val MODE_PREDEFINED = 0
    private const val MODE_RLE = 1
    private const val MODE_FSE = 2

    private fun byteAt(src: ByteArray, at: Int, end: Int): Int {
        if (at >= end) throw ZstdException("the sequences section is cut short")
        return src[at].toInt() and 0xFF
    }

    fun run(st: ZstdState, src: ByteArray, off: Int, end: Int, out: ZstdOutput, frameStart: Int, window: Long) {
        var p = off
        val b0 = byteAt(src, p++, end)
        val count = when {
            b0 < 128 -> b0
            b0 < 255 -> ((b0 - 128) shl 8) + byteAt(src, p++, end)
            else -> {
                val lo = byteAt(src, p++, end)
                lo + (byteAt(src, p++, end) shl 8) + 0x7F00
            }
        }
        if (count == 0) {
            if (p != end) throw ZstdException("a block with no sequences has bytes left over")
            copyLiterals(st, 0, st.literalCount, out)
            return
        }
        val modes = byteAt(src, p++, end)
        if (modes and 3 != 0) throw ZstdException("the sequences section sets a reserved bit")
        val ll = chooseTable(modes shr 6, src, p, end, MAX_LL, LL_LOG, PREDEFINED_LL, st.literalLengths)
        p += tableBytes(modes shr 6, ll)
        val of = chooseTable((modes shr 4) and 3, src, p, end, MAX_OF, OF_LOG, PREDEFINED_OF, st.offsets)
        p += tableBytes((modes shr 4) and 3, of)
        val ml = chooseTable((modes shr 2) and 3, src, p, end, MAX_ML, ML_LOG, PREDEFINED_ML, st.matchLengths)
        p += tableBytes((modes shr 2) and 3, ml)
        st.literalLengths = ll
        st.offsets = of
        st.matchLengths = ml
        execute(st, ll, of, ml, BackwardBits(src, p, end), count, out, frameStart, window)
    }

    /** Bytes a table took in the section: a read FSE table its description, an RLE table its one byte, the others none. */
    private fun tableBytes(mode: Int, table: FseTable): Int = when (mode) {
        MODE_RLE -> 1
        MODE_FSE -> table.consumed
        else -> 0
    }

    private fun chooseTable(
        mode: Int, src: ByteArray, at: Int, end: Int, maxSymbol: Int, maxLog: Int, predefined: FseTable, previous: FseTable?,
    ): FseTable = when (mode) {
        MODE_PREDEFINED -> predefined
        MODE_RLE -> {
            val symbol = byteAt(src, at, end)
            if (symbol > maxSymbol) throw ZstdException("an RLE sequence code $symbol is out of range")
            FseTable.single(symbol)
        }
        MODE_FSE -> FseTable.parse(src, at, end, maxSymbol, maxLog)
        else -> previous ?: throw ZstdException("a block repeats a sequence table that was never sent")
    }

    private fun execute(
        st: ZstdState, ll: FseTable, of: FseTable, ml: FseTable, bits: BackwardBits, count: Int,
        out: ZstdOutput, frameStart: Int, window: Long,
    ) {
        var sLl = bits.read(ll.accuracyLog).toInt()
        var sOf = bits.read(of.accuracyLog).toInt()
        var sMl = bits.read(ml.accuracyLog).toInt()
        var literalPos = 0
        for (i in 0 until count) {
            val ofCode = of.symbol[sOf]
            val mlCode = ml.symbol[sMl]
            val llCode = ll.symbol[sLl]
            if (ofCode > MAX_OFFSET_CODE) throw ZstdException("an offset code $ofCode is beyond any window")
            val offsetValue = (1 shl ofCode) + bits.read(ofCode).toInt()
            val matchLength = ML_BASE[mlCode] + bits.read(ML_BITS[mlCode]).toInt()
            val literalLength = LL_BASE[llCode] + bits.read(LL_BITS[llCode]).toInt()
            if (bits.overflow) throw ZstdException("the sequences are cut short")
            if (i < count - 1) {
                sLl = ll.base[sLl] + bits.read(ll.nbBits[sLl]).toInt()
                sMl = ml.base[sMl] + bits.read(ml.nbBits[sMl]).toInt()
                sOf = of.base[sOf] + bits.read(of.nbBits[sOf]).toInt()
            }
            val offset = resolveOffset(st, offsetValue, literalLength)
            if (literalLength > st.literalCount - literalPos) throw ZstdException("a sequence uses more literals than the block has")
            copyLiterals(st, literalPos, literalLength, out)
            literalPos += literalLength
            copyMatch(out, offset, matchLength, frameStart, window)
        }
        if (!bits.consumed) throw ZstdException("the sequence stream is not used up exactly")
        copyLiterals(st, literalPos, st.literalCount - literalPos, out)
    }

    /** Offset values 1-3 are repeat offsets (shifted by one when there are no literals); larger ones are new and minus three. */
    private fun resolveOffset(st: ZstdState, value: Int, literalLength: Int): Int {
        if (value > 3) {
            st.rep3 = st.rep2
            st.rep2 = st.rep1
            st.rep1 = value - 3
            return st.rep1
        }
        val index = if (literalLength == 0) value + 1 else value
        val offset = when (index) {
            1 -> return st.rep1
            2 -> st.rep2
            3 -> st.rep3
            else -> st.rep1 - 1
        }
        if (offset <= 0) throw ZstdException("a repeat offset is zero")
        if (index != 2) st.rep3 = st.rep2
        st.rep2 = st.rep1
        st.rep1 = offset
        return offset
    }

    private fun copyLiterals(st: ZstdState, from: Int, length: Int, out: ZstdOutput) {
        out.ensure(length)
        System.arraycopy(st.literals, from, out.buf, out.pos, length)
        out.pos += length
    }

    private fun copyMatch(out: ZstdOutput, offset: Int, length: Int, frameStart: Int, window: Long) {
        if (offset > out.pos - frameStart || offset > window) throw ZstdException("an offset of $offset reaches outside the window")
        out.ensure(length)
        val buf = out.buf
        val to = out.pos
        val from = to - offset
        if (offset >= length) {
            System.arraycopy(buf, from, buf, to, length)
        } else {
            for (k in 0 until length) buf[to + k] = buf[from + k]
        }
        out.pos = to + length
    }
}
