package com.plumemu.identify.codec.zstd

import java.io.IOException

/** Every malformed zstd input ends here: a clear message, never a crash or an endless loop. */
class ZstdException(message: String) : IOException(message)

/** Forward little-endian bit reader over `src[start, end)`; used for FSE table descriptions. Bits past the end read as zero. */
internal class ForwardBits(private val src: ByteArray, private val start: Int, private val end: Int) {
    private var bitPos = 0

    /** The next [n] bits (at most 24) without consuming them. */
    fun peek(n: Int): Int {
        val index = start + (bitPos shr 3)
        var v = 0L
        for (i in 0 until 4) {
            val p = index + i
            if (p < end) v = v or ((src[p].toLong() and 0xFF) shl (8 * i))
        }
        return ((v ushr (bitPos and 7)) and ((1L shl n) - 1)).toInt()
    }

    fun skip(n: Int) {
        bitPos += n
        if (bitPos > (end - start) * 8) throw ZstdException("a table description is cut short")
    }

    val bytesUsed: Int get() = (bitPos + 7) shr 3
}

/**
 * Backward bit reader: the stream is written forwards and read from its last bit; the highest set bit of the last byte
 * marks the end. [pos] is the number of unread bits; reading past the start leaves it negative (an overflow the caller checks).
 */
internal class BackwardBits(private val src: ByteArray, private val start: Int, private val limit: Int) {
    var pos: Int
        private set

    init {
        if (limit <= start) throw ZstdException("an empty bit stream")
        val last = src[limit - 1].toInt() and 0xFF
        if (last == 0) throw ZstdException("a bit stream has no end mark")
        pos = (limit - start - 1) * 8 + (31 - Integer.numberOfLeadingZeros(last))
    }

    /** [n] (0..32) bits below the current position, the highest unread bit first; bits below the start are zero. */
    fun peek(n: Int): Long {
        if (n == 0) return 0
        val lo = pos - n
        val from = if (lo < 0) 0 else lo
        val take = n - (from - lo)
        if (take <= 0) return 0
        var v = 0L
        val index = start + (from shr 3)
        for (i in 0 until 5) {
            val p = index + i
            if (p < limit) v = v or ((src[p].toLong() and 0xFF) shl (8 * i))
        }
        v = (v ushr (from and 7)) and ((1L shl take) - 1)
        return if (lo < 0) v shl -lo else v
    }

    fun skip(n: Int) {
        pos -= n
        if (pos < -64) throw ZstdException("a bit stream is read past its start")
    }

    fun read(n: Int): Long {
        val v = peek(n)
        skip(n)
        return v
    }

    /** True when every bit was read, none more. */
    val consumed: Boolean get() = pos == 0
    val overflow: Boolean get() = pos < 0
}
