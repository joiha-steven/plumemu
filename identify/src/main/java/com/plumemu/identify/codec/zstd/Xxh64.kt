package com.plumemu.identify.codec.zstd

/** XXH64 with seed 0 (Yann Collet's xxHash, BSD-2-Clause algorithm); a zstd frame keeps its low 32 bits as the content checksum. */
internal object Xxh64 {
    private const val P1 = -7046029288634856825L
    private const val P2 = -4417276706812531889L
    private const val P3 = 1609587929392839161L
    private const val P4 = -8796714831421723037L
    private const val P5 = 2870177450012600261L

    private fun le64(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 7 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun le32(b: ByteArray, at: Int): Long {
        var v = 0L
        for (i in 3 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun round(acc: Long, input: Long): Long = java.lang.Long.rotateLeft(acc + input * P2, 31) * P1

    private fun merge(acc: Long, value: Long): Long = (acc xor round(0, value)) * P1 + P4

    fun hash(b: ByteArray, off: Int, len: Int): Long {
        var p = off
        val end = off + len
        var h: Long
        if (len >= 32) {
            var v1 = P1 + P2
            var v2 = P2
            var v3 = 0L
            var v4 = -P1
            while (p <= end - 32) {
                v1 = round(v1, le64(b, p))
                v2 = round(v2, le64(b, p + 8))
                v3 = round(v3, le64(b, p + 16))
                v4 = round(v4, le64(b, p + 24))
                p += 32
            }
            h = java.lang.Long.rotateLeft(v1, 1) + java.lang.Long.rotateLeft(v2, 7) +
                java.lang.Long.rotateLeft(v3, 12) + java.lang.Long.rotateLeft(v4, 18)
            h = merge(h, v1)
            h = merge(h, v2)
            h = merge(h, v3)
            h = merge(h, v4)
        } else {
            h = P5
        }
        h += len.toLong()
        while (p <= end - 8) {
            h = java.lang.Long.rotateLeft(h xor round(0, le64(b, p)), 27) * P1 + P4
            p += 8
        }
        if (p <= end - 4) {
            h = java.lang.Long.rotateLeft(h xor (le32(b, p) * P1), 23) * P2 + P3
            p += 4
        }
        while (p < end) {
            h = java.lang.Long.rotateLeft(h xor ((b[p].toLong() and 0xFF) * P5), 11) * P1
            p++
        }
        h = h xor (h ushr 33)
        h *= P2
        h = h xor (h ushr 29)
        h *= P3
        h = h xor (h ushr 32)
        return h
    }
}
