// Zstandard decompressor (RFC 8878), frame format, decompression only, written for Plumemu from the RFC.
package com.plumemu.identify.codec.zstd

/**
 * `Zstd.decompress` reads one or more zstd frames from memory. It is bounded: the output never exceeds `maxOutput`
 * (the one big allocation, grown only as far as the data really goes, or at once to a content size the header announces),
 * a frame asking for a window over `maxWindow` is refused, a dictionary is refused, and every malformed byte gives a
 * [ZstdException] (an `IOException`) rather than a crash or a loop: each loop below is bounded by the input or the output.
 */
object Zstd {
    private const val MAGIC = 4247762216L
    private const val SKIPPABLE_FIRST = 407710288L
    private const val SKIPPABLE_LAST = 407710303L
    private const val MAX_WINDOW_LOG = 41
    private const val DEFAULT_MAX_WINDOW = 8 shl 20

    @Throws(java.io.IOException::class)
    fun decompress(
        src: ByteArray,
        off: Int = 0,
        len: Int = src.size - off,
        maxOutput: Int,
        maxWindow: Int = DEFAULT_MAX_WINDOW,
    ): ByteArray {
        if (off < 0 || len < 0 || off > src.size - len) throw ZstdException("the input range is not valid")
        if (maxOutput < 0) throw ZstdException("the output limit is not valid")
        val end = off + len
        val out = ZstdOutput(0, maxOutput)
        var p = off
        var frames = 0
        while (p < end) {
            if (end - p < 4) throw ZstdException("the data ends inside a frame header")
            val magic = le(src, p, 4)
            p = when {
                magic == MAGIC -> decodeFrame(src, p + 4, end, out, maxWindow)
                magic in SKIPPABLE_FIRST..SKIPPABLE_LAST -> skip(src, p, end)
                else -> throw ZstdException("not a zstd frame (bad magic number)")
            }
            frames++
        }
        if (frames == 0) throw ZstdException("no zstd frame")
        return if (out.buf.size == out.pos) out.buf else out.buf.copyOf(out.pos)
    }

    private fun le(b: ByteArray, at: Int, bytes: Int): Long {
        var v = 0L
        for (i in bytes - 1 downTo 0) v = (v shl 8) or (b[at + i].toLong() and 0xFF)
        return v
    }

    private fun skip(src: ByteArray, p: Int, end: Int): Int {
        if (end - p < 8) throw ZstdException("a skippable frame is cut short")
        val size = le(src, p + 4, 4)
        if (size > end - p - 8) throw ZstdException("a skippable frame is cut short")
        return p + 8 + size.toInt()
    }

    private fun decodeFrame(src: ByteArray, start: Int, end: Int, out: ZstdOutput, maxWindow: Int): Int {
        var p = start
        if (p >= end) throw ZstdException("the frame header is cut short")
        val descriptor = src[p++].toInt() and 0xFF
        if (descriptor and 0x08 != 0) throw ZstdException("a frame header sets a reserved bit")
        val single = descriptor and 0x20 != 0
        val checksum = descriptor and 0x04 != 0
        val dictBytes = intArrayOf(0, 1, 2, 4)[descriptor and 3]
        val sizeBytes = when (descriptor shr 6) { 0 -> if (single) 1 else 0; 1 -> 2; 2 -> 4; else -> 8 }
        var windowByte = 0
        if (!single) {
            if (p >= end) throw ZstdException("the frame header is cut short")
            windowByte = src[p++].toInt() and 0xFF
        }
        if (end - p < dictBytes + sizeBytes) throw ZstdException("the frame header is cut short")
        if (dictBytes > 0 && le(src, p, dictBytes) != 0L) throw ZstdException("the frame needs a dictionary, which is not supported")
        p += dictBytes
        var contentSize = -1L
        if (sizeBytes > 0) {
            contentSize = le(src, p, sizeBytes)
            if (sizeBytes == 2) contentSize += 256
            p += sizeBytes
            if (contentSize < 0 || contentSize > out.limit - out.pos) throw ZstdException("the frame holds more than the ${out.limit} bytes allowed")
        }
        val window: Long
        if (single) {
            window = contentSize
        } else {
            val log = 10 + (windowByte shr 3)
            if (log > MAX_WINDOW_LOG) throw ZstdException("the frame's window is too large")
            val base = 1L shl log
            window = base + (base shr 3) * (windowByte and 7)
        }
        if (window > maxWindow) throw ZstdException("the frame needs a window of $window bytes, more than the $maxWindow allowed")
        val frameStart = out.pos
        if (contentSize >= 0) out.reserve(out.pos + contentSize.toInt())
        p = decodeBlocks(src, p, end, out, frameStart, window)
        if (contentSize >= 0 && out.pos - frameStart != contentSize.toInt()) throw ZstdException("the frame is not the size its header says")
        if (checksum) {
            if (end - p < 4) throw ZstdException("the content checksum is cut short")
            val expected = le(src, p, 4)
            val actual = Xxh64.hash(out.buf, frameStart, out.pos - frameStart).toInt().toUInt().toLong()
            if (expected != actual) throw ZstdException("the content checksum does not match")
            p += 4
        }
        return p
    }

    private fun decodeBlocks(src: ByteArray, start: Int, end: Int, out: ZstdOutput, frameStart: Int, window: Long): Int {
        val state = ZstdState()
        val blockLimit = minOf(window, ZstdBlock.MAX_BLOCK.toLong()).toInt()
        var p = start
        while (true) {
            if (end - p < 3) throw ZstdException("a block header is cut short")
            val header = le(src, p, 3).toInt()
            p += 3
            val last = header and 1 != 0
            val size = header ushr 3
            // RFC 8878 3.1.1.2.4: a block holds at most min(window size, 128 KiB).
            if (size > blockLimit) throw ZstdException("a block of $size bytes is larger than the $blockLimit its frame allows")
            when ((header shr 1) and 3) {
                0 -> {
                    if (size > end - p) throw ZstdException("a raw block is cut short")
                    out.ensure(size)
                    System.arraycopy(src, p, out.buf, out.pos, size)
                    out.pos += size
                    p += size
                }
                1 -> {
                    if (p >= end) throw ZstdException("an RLE block is cut short")
                    out.ensure(size)
                    java.util.Arrays.fill(out.buf, out.pos, out.pos + size, src[p])
                    out.pos += size
                    p += 1
                }
                2 -> {
                    if (size > end - p) throw ZstdException("a compressed block is cut short")
                    val before = out.pos
                    ZstdBlock.decode(state, src, p, p + size, out, frameStart, window)
                    if (out.pos - before > blockLimit) throw ZstdException("a block expands to more than the $blockLimit its frame allows")
                    p += size
                }
                else -> throw ZstdException("a block has the reserved type")
            }
            if (last) return p
        }
    }
}
