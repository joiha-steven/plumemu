package com.plumemu.achievements.hash

import java.io.ByteArrayOutputStream

/**
 * A small greedy LZ4 block encoder for tests, following the format's end rules (the last 5 bytes are
 * literals, no match starts in the last 12 bytes). Its output is checked against the decoder and against
 * the `lz4` command in `Lz4Test`; it is not shipped.
 */
internal fun lz4Compress(src: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    val n = src.size
    val table = IntArray(1 shl 12) { -1 }
    fun key(i: Int): Int {
        val v = (src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8) or
            ((src[i + 2].toInt() and 0xFF) shl 16) or ((src[i + 3].toInt() and 0xFF) shl 24)
        return (v * -1640531535) ushr 20
    }
    fun length(out: ByteArrayOutputStream, extra: Int) {
        var left = extra
        while (left >= 255) { out.write(255); left -= 255 }
        out.write(left)
    }
    fun sequence(anchor: Int, literalEnd: Int, offset: Int, matchLen: Int) {
        val literals = literalEnd - anchor
        val ml = if (matchLen > 0) matchLen - 4 else 0
        out.write((minOf(literals, 15) shl 4) or (if (matchLen > 0) minOf(ml, 15) else 0))
        if (literals >= 15) length(out, literals - 15)
        out.write(src, anchor, literals)
        if (matchLen > 0) {
            out.write(offset and 0xFF); out.write(offset ushr 8)
            if (ml >= 15) length(out, ml - 15)
        }
    }
    var anchor = 0
    var i = 0
    while (i + 12 < n) {
        val k = key(i)
        val cand = table[k]
        table[k] = i
        if (cand >= 0 && i - cand < 65536 && src[cand] == src[i] && src[cand + 1] == src[i + 1] &&
            src[cand + 2] == src[i + 2] && src[cand + 3] == src[i + 3]
        ) {
            var len = 4
            while (i + len < n - 5 && src[cand + len] == src[i + len]) len++
            sequence(anchor, i, i - cand, len)
            i += len
            anchor = i
        } else {
            i++
        }
    }
    sequence(anchor, n, 0, 0)
    return out.toByteArray()
}
