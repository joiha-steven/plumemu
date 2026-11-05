package com.plumemu.identify.codec.zstd

/** The literals section of a compressed block: raw, RLE, Huffman (one or four streams) or Huffman with the previous table. */
internal object ZstdLiterals {
    /** Reads the section at `src[off, end)` into [st]; returns where the sequences section starts. */
    fun read(st: ZstdState, src: ByteArray, off: Int, end: Int): Int {
        if (off >= end) throw ZstdException("a block has no literals section")
        val b0 = src[off].toInt() and 0xFF
        val type = b0 and 3
        val format = (b0 shr 2) and 3
        if (type <= 1) return readSimple(st, src, off, end, type, format)
        val headerBytes = when (format) { 0, 1 -> 3; 2 -> 4; else -> 5 }
        if (off + headerBytes > end) throw ZstdException("a literals header is cut short")
        var bits = 0L
        for (i in 0 until headerBytes) bits = bits or ((src[off + i].toLong() and 0xFF) shl (8 * i))
        bits = bits ushr 4
        val sizeBits = when (format) { 0, 1 -> 10; 2 -> 14; else -> 18 }
        val regen = (bits and ((1L shl sizeBits) - 1)).toInt()
        val packed = ((bits ushr sizeBits) and ((1L shl sizeBits) - 1)).toInt()
        if (regen > ZstdBlock.MAX_BLOCK) throw ZstdException("a block has $regen literals, more than ${ZstdBlock.MAX_BLOCK}")
        val streamsStart = off + headerBytes
        val streamsEnd = streamsStart + packed
        if (packed < 1 || streamsEnd > end) throw ZstdException("the literals are cut short")
        var at = streamsStart
        if (type == 2) {
            val table = HuffmanTable.parse(src, at, streamsEnd)
            st.huffman = table
            at += table.consumed
        }
        val table = st.huffman ?: throw ZstdException("a block reuses a Huffman table that was never sent")
        st.ensureLiterals(regen)
        st.literalCount = regen
        if (format == 0) {
            if (at >= streamsEnd) throw ZstdException("the literals are cut short")
            table.decode(src, at, streamsEnd, st.literals, 0, regen)
        } else {
            decodeFour(table, src, at, streamsEnd, st.literals, regen)
        }
        return streamsEnd
    }

    private fun readSimple(st: ZstdState, src: ByteArray, off: Int, end: Int, type: Int, format: Int): Int {
        val b0 = src[off].toInt() and 0xFF
        val headerBytes: Int
        val size: Int
        when (format) {
            0, 2 -> { headerBytes = 1; size = b0 shr 3 }
            1 -> {
                if (off + 2 > end) throw ZstdException("a literals header is cut short")
                headerBytes = 2
                size = (b0 shr 4) or ((src[off + 1].toInt() and 0xFF) shl 4)
            }
            else -> {
                if (off + 3 > end) throw ZstdException("a literals header is cut short")
                headerBytes = 3
                size = (b0 shr 4) or ((src[off + 1].toInt() and 0xFF) shl 4) or ((src[off + 2].toInt() and 0xFF) shl 12)
            }
        }
        if (size > ZstdBlock.MAX_BLOCK) throw ZstdException("a block has $size literals, more than ${ZstdBlock.MAX_BLOCK}")
        val body = off + headerBytes
        st.ensureLiterals(size)
        st.literalCount = size
        if (type == 0) {
            if (size > end - body) throw ZstdException("the literals are cut short")
            System.arraycopy(src, body, st.literals, 0, size)
            return body + size
        }
        if (body >= end) throw ZstdException("the literals are cut short")
        java.util.Arrays.fill(st.literals, 0, size, src[body])
        return body + 1
    }

    /** Six bytes of three 16-bit stream sizes, then the four streams; each of the first three yields a quarter, rounded up. */
    private fun decodeFour(table: HuffmanTable, src: ByteArray, at: Int, end: Int, out: ByteArray, regen: Int) {
        if (at + 6 > end) throw ZstdException("the literals are cut short")
        val s1 = (src[at].toInt() and 0xFF) or ((src[at + 1].toInt() and 0xFF) shl 8)
        val s2 = (src[at + 2].toInt() and 0xFF) or ((src[at + 3].toInt() and 0xFF) shl 8)
        val s3 = (src[at + 4].toInt() and 0xFF) or ((src[at + 5].toInt() and 0xFF) shl 8)
        val first = at + 6
        val s4 = end - first - s1 - s2 - s3
        if (s4 < 1) throw ZstdException("the four literal streams do not fit")
        val part = (regen + 3) / 4
        val last = regen - 3 * part
        if (last < 0) throw ZstdException("the literals are too few for four streams")
        var p = first
        table.decode(src, p, p + s1, out, 0, part); p += s1
        table.decode(src, p, p + s2, out, part, part); p += s2
        table.decode(src, p, p + s3, out, 2 * part, part); p += s3
        table.decode(src, p, end, out, 3 * part, last)
    }
}
