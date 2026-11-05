// LZ4 block decoder, written from the public LZ4 block format description (lz4_Block_format.md).
package com.plumemu.identify.codec.lz4

/** An LZ4 block that cannot be decoded. */
class Lz4Exception(message: String) : Exception(message)

/**
 * Decoder for one LZ4 block (no frame). Bounded: output never passes the caller's buffer, every read is
 * checked against the input, and each round moves the input forward, so a bad block fails at once
 * instead of looping or allocating.
 */
object Lz4 {
    private const val MIN_MATCH = 4

    /**
     * Decodes `src[srcOff, srcOff + srcLen)` into the start of [dst] and returns how many bytes it wrote.
     * Decoding ends when the input is used up, or as soon as [minOut] bytes exist after a whole sequence
     * (container blocks are padded after the data, and the padding is not read). Throws [Lz4Exception]
     * when the block is malformed, writes past [dst], or gives fewer than [minOut] bytes.
     */
    @Throws(Lz4Exception::class)
    fun decode(src: ByteArray, srcOff: Int, srcLen: Int, dst: ByteArray, minOut: Int = dst.size): Int {
        require(srcOff >= 0 && srcLen >= 0 && srcOff + srcLen <= src.size && minOut in 0..dst.size)
        val end = srcOff + srcLen
        var ip = srcOff
        var op = 0
        while (ip < end) {
            val token = src[ip++].toInt() and 0xFF
            var literals = token ushr 4
            if (literals == 15) {
                while (true) {
                    if (ip >= end) throw Lz4Exception("Literal length runs past the input")
                    val b = src[ip++].toInt() and 0xFF
                    literals += b
                    if (literals > dst.size) throw Lz4Exception("Literal length is larger than the output")
                    if (b != 255) break
                }
            }
            if (literals > end - ip) throw Lz4Exception("Literals run past the input")
            if (literals > dst.size - op) throw Lz4Exception("Literals run past the output")
            System.arraycopy(src, ip, dst, op, literals)
            ip += literals
            op += literals
            // The last sequence holds literals only; after it a container may pad the block.
            if (ip >= end || op >= minOut) break
            if (end - ip < 2) throw Lz4Exception("Match offset is cut short")
            val offset = (src[ip].toInt() and 0xFF) or ((src[ip + 1].toInt() and 0xFF) shl 8)
            ip += 2
            if (offset == 0 || offset > op) throw Lz4Exception("Match offset $offset is outside the output")
            var length = token and 15
            if (length == 15) {
                while (true) {
                    if (ip >= end) throw Lz4Exception("Match length runs past the input")
                    val b = src[ip++].toInt() and 0xFF
                    length += b
                    if (length > dst.size) throw Lz4Exception("Match length is larger than the output")
                    if (b != 255) break
                }
            }
            length += MIN_MATCH
            if (length > dst.size - op) throw Lz4Exception("Match runs past the output")
            // Byte by byte on purpose: an offset smaller than the length repeats what was just written.
            var from = op - offset
            repeat(length) { dst[op++] = dst[from++] }
            if (op >= minOut) break
        }
        if (op < minOut) throw Lz4Exception("Block gives $op of $minOut bytes")
        return op
    }
}
