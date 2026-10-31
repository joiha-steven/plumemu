package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.codec.lzma.CompressedInput
import com.plumemu.identify.codec.lzma.LzmaException
import com.plumemu.identify.codec.lzma.LzmaProperties
import com.plumemu.identify.codec.lzma.lzmaDecode
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * The compressed bytes of one CHD hunk, fetched from [file] in [STEP]-byte pieces only as far as
 * a decoder asks, and kept: decoding a longer prefix of the same hunk later reads nothing twice.
 * The caller has checked that the [length] bytes at [start] lie inside the file.
 */
internal class HunkBytes(private val file: RandomAccessSource, private val start: Long, val length: Int) {
    private var fetched = ByteArray(minOf(length, STEP))
    private var count = 0

    /** Bytes taken from the file so far. */
    val fetchedBytes: Int get() = count

    /** The byte at [index] (0 until [length]), 0..255. */
    @Throws(IOException::class, LzmaException::class)
    fun byteAt(index: Int): Int {
        if (index !in 0 until length) throw LzmaException("hunk data ends after $length bytes")
        while (index >= count) fetch()
        return fetched[index].toInt() and 0xFF
    }

    /** The [size] bytes from [from] as decoder input; its [CompressedInput.length] is [size]. */
    fun input(from: Int, size: Int): CompressedInput = object : CompressedInput {
        private var position = 0
        override val consumed: Long get() = position.toLong()
        override val length: Long get() = size.toLong()

        override fun readByte(): Int {
            if (position >= size) throw LzmaException("compressed data ends after $size bytes")
            return byteAt(from + position++)
        }
    }

    private fun fetch() {
        val wanted = minOf(STEP, length - count)
        if (count + wanted > fetched.size) fetched = fetched.copyOf(minOf(length, maxOf(fetched.size * 2, count + wanted)))
        val got = file.readFully(start + count, wanted)
        if (got.isEmpty()) throw LzmaException("file ends inside the hunk, after $count bytes")
        got.copyInto(fetched, count)
        count += got.size
    }

    private companion object {
        const val STEP = 4096
    }
}

/**
 * The CHD codecs that use LZMA (MAME `src/lib/util/chdcodec.cpp`), decoded with
 * [lzmaDecode]. MAME never stores the LZMA properties: they are lc=3, lp=0, pb=2 and a
 * dictionary worked out from the input size (`chd_lzma_compressor::configure_properties`, level
 * 6, `reduceSize` = the size); passing the input size itself is enough here, since a match can
 * never reach further back than the bytes produced. The stream has no end marker and fills its
 * compressed length exactly.
 *
 * Both decoders can stop early: [prefix] bytes (a whole hunk when it is the hunk size) are
 * decoded and only the compressed bytes they need are fetched. A whole hunk is decoded with every
 * MAME check ([lzmaDecode] `exactInput`); a prefix only has to be produced.
 */
internal object ChdLzma {
    private const val SECTOR = 2352
    private const val SECTOR_LZMA = 1 shl 16

    /**
     * `lzma`: the whole hunk is one raw LZMA stream. Returns the first [prefix] bytes.
     *
     * @throws LzmaException when the data is damaged.
     */
    @Throws(IOException::class, LzmaException::class)
    fun lzmaHunk(bytes: HunkBytes, hunkBytes: Int, prefix: Int): ByteArray =
        decode(bytes.input(0, bytes.length), hunkBytes, prefix)

    /**
     * `cdlz` (`chd_cd_decompressor<chd_lzma_decompressor, chd_zlib_decompressor>`): a bitmap
     * with one bit per frame (set: the sync header and ECC were removed), the length of the
     * sector part (2 bytes, 3 when the hunk is 64 KB or more), the sectors (2352 bytes per frame)
     * as raw LZMA, then the subcode deflated. Returns the first [frames] frames of 2448 bytes, the
     * sync header put back as `cdzl` does; the subcode is left as zeros and the ECC is not
     * rebuilt (the user data needs neither).
     *
     * @throws LzmaException when the data is damaged.
     * @throws NotRead when the header of the hunk is out of range.
     */
    @Throws(IOException::class, LzmaException::class)
    fun cdlzHunk(bytes: HunkBytes, hunkBytes: Int, frames: Int): ByteArray {
        val allFrames = hunkBytes / ChdHeader.CD_FRAME
        val eccBytes = (allFrames + 7) / 8
        val lengthBytes = if (hunkBytes < SECTOR_LZMA) 2 else 3
        if (bytes.length < eccBytes + lengthBytes) throw NotRead("chd cdlz: hunk is truncated")
        var baseLength = 0
        for (i in 0 until lengthBytes) baseLength = (baseLength shl 8) or bytes.byteAt(eccBytes + i)
        val start = eccBytes + lengthBytes
        if (baseLength.toLong() + start > bytes.length) throw NotRead("chd cdlz: sector data length is out of range")
        val sectors = decode(bytes.input(start, baseLength), allFrames * SECTOR, frames * SECTOR)
        val out = ByteArray(if (frames == allFrames) hunkBytes else frames * ChdHeader.CD_FRAME)
        for (frame in 0 until frames) {
            System.arraycopy(sectors, frame * SECTOR, out, frame * ChdHeader.CD_FRAME, SECTOR)
            if (bytes.byteAt(frame / 8) and (1 shl (frame % 8)) != 0) {
                System.arraycopy(CD_SYNC, 0, out, frame * ChdHeader.CD_FRAME, CD_SYNC.size)
            }
        }
        return out
    }

    /** The first [prefix] of the [total] bytes of a raw CHD LZMA stream; all checks when whole. */
    private fun decode(input: CompressedInput, total: Int, prefix: Int): ByteArray {
        val props = LzmaProperties(lc = 3, lp = 0, pb = 2, dictionarySize = total.toLong())
        if (prefix >= total) return lzmaDecode(input, props, total, knownSize = true, exactInput = true)
        val out = lzmaDecode(input, props, prefix, knownSize = false)
        if (out.size != prefix) throw LzmaException("end marker after ${out.size} of $total bytes")
        return out
    }

    /** The 12-byte sync header of a raw CD sector. */
    val CD_SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)
}
