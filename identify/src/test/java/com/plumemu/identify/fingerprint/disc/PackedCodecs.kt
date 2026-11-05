package com.plumemu.identify.fingerprint.disc

import java.io.ByteArrayOutputStream

/** Test-only encoders for the codecs the disc readers decode: never shipped. */
internal object PackedCodecs {

    /**
     * One zstd frame of [data] made of raw blocks, and RLE blocks for runs of one byte: valid zstd
     * (RFC 8878) without compressing. The frame says its content size (4 bytes, single segment).
     * The real compressed blocks are tested with the `zstd` command line's frames in
     * `containers/make_fixtures.py`.
     */
    fun zstdFrame(data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte(), 0xA0.toByte()))
        repeat(4) { out.write((data.size ushr (8 * it)) and 0xFF) }
        var at = 0
        do {
            val size = minOf(MAX_BLOCK, data.size - at)
            val last = at + size == data.size
            val run = size > 0 && (1 until size).all { data[at + it] == data[at] }
            val type = if (run) 1 else 0
            val header = (if (last) 1 else 0) or (type shl 1) or (size shl 3)
            repeat(3) { out.write((header ushr (8 * it)) and 0xFF) }
            if (run) out.write(data[at].toInt()) else out.write(data, at, size)
            at += size
        } while (!last)
        return out.toByteArray()
    }

    private const val MAX_BLOCK = 128 * 1024

    /**
     * A small greedy LZ4 block encoder following the format's end rules (the last 5 bytes are
     * literals, no match starts in the last 12 bytes).
     */
    fun lz4Compress(src: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val n = src.size
        val table = IntArray(1 shl 12) { -1 }
        fun key(i: Int): Int {
            val v = (src[i].toInt() and 0xFF) or ((src[i + 1].toInt() and 0xFF) shl 8) or
                ((src[i + 2].toInt() and 0xFF) shl 16) or ((src[i + 3].toInt() and 0xFF) shl 24)
            return (v * -1640531535) ushr 20
        }
        fun length(extra: Int) {
            var left = extra
            while (left >= 255) {
                out.write(255)
                left -= 255
            }
            out.write(left)
        }
        fun sequence(anchor: Int, literalEnd: Int, offset: Int, matchLen: Int) {
            val literals = literalEnd - anchor
            val ml = if (matchLen > 0) matchLen - 4 else 0
            out.write((minOf(literals, 15) shl 4) or (if (matchLen > 0) minOf(ml, 15) else 0))
            if (literals >= 15) length(literals - 15)
            out.write(src, anchor, literals)
            if (matchLen > 0) {
                out.write(offset and 0xFF)
                out.write(offset ushr 8)
                if (ml >= 15) length(ml - 15)
            }
        }
        var anchor = 0
        var i = 0
        while (i + 12 < n) {
            val k = key(i)
            val candidate = table[k]
            table[k] = i
            if (candidate >= 0 && i - candidate < 65536 && src[candidate] == src[i] && src[candidate + 1] == src[i + 1] &&
                src[candidate + 2] == src[i + 2] && src[candidate + 3] == src[i + 3]
            ) {
                var len = 4
                while (i + len < n - 5 && src[candidate + len] == src[i + len]) len++
                sequence(anchor, i, i - candidate, len)
                i += len
                anchor = i
            } else {
                i++
            }
        }
        sequence(anchor, n, 0, 0)
        return out.toByteArray()
    }
}
