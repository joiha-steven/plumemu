package com.plumemu.identify.fingerprint

import com.plumemu.identify.fingerprint.cart.ContentLayout
import com.plumemu.identify.fingerprint.cart.MegaDriveReader
import com.plumemu.identify.fingerprint.cart.Nintendo64Reader
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readInto
import java.io.IOException
import java.util.zip.CRC32

/**
 * CRC-32 of a loose file's content as name databases list it: the first [skip] bytes (a copier
 * or emulator header) left out, N64 byte orders put back in z64 order, SMD blocks
 * de-interleaved ([ContentCrc]). Streams 64 KB at a time; one buffer per call, nothing shared
 * between threads.
 */
@Throws(IOException::class)
internal fun wholeFileCrc(source: RandomAccessSource, skip: Int, layout: ContentLayout): Long {
    // The reads start after the header, so nothing is left for ContentCrc to drop.
    val crc = ContentCrc(skip = 0, layout)
    val buffer = ByteArray(CONTENT_CRC_BUFFER)
    var position = skip.toLong()
    while (position < source.size) {
        val wanted = minOf(CONTENT_CRC_BUFFER.toLong(), source.size - position).toInt()
        val got = source.readInto(position, buffer, wanted)
        if (got <= 0) throw IOException("source ended at $position, before its size ${source.size}")
        crc.update(buffer, 0, got)
        position += got
    }
    return crc.value()
}

/**
 * The content CRC of bytes fed in pieces of any length, from the start of the stored file: the
 * first [skip] bytes are dropped, the rest is normalised by [layout] in 64 KB stages counted
 * from the end of the header (a multiple of the 4-byte N64 word and the 16 KB SMD block, so no
 * unit straddles two stages), then summed: the same stages a loose file's 64 KB reads give, so
 * a zipped copy gets the same CRC, odd tails included. Used for loose files ([wholeFileCrc],
 * whose reads start after the header) and for zip entries inflated as a stream
 * (`ZipEntryContent`).
 */
internal class ContentCrc(skip: Long, private val layout: ContentLayout) {
    private var toSkip = skip
    private val crc = CRC32()
    private val stage = if (layout == ContentLayout.PLAIN) null else ByteArray(CONTENT_CRC_BUFFER)
    private var staged = 0

    /** Bytes after the header fed so far: the length the CRC covers. */
    var contentLength = 0L
        private set

    fun update(bytes: ByteArray, offset: Int, length: Int) {
        var at = offset
        var left = length
        if (toSkip > 0) {
            val dropped = minOf(toSkip, left.toLong()).toInt()
            at += dropped
            left -= dropped
            toSkip -= dropped
        }
        contentLength += left
        val stage = stage
        if (stage == null) {
            crc.update(bytes, at, left)
            return
        }
        while (left > 0) {
            val n = minOf(left, stage.size - staged)
            System.arraycopy(bytes, at, stage, staged, n)
            staged += n
            at += n
            left -= n
            if (staged == stage.size) flush(stage)
        }
    }

    /** The CRC of everything fed; a last partial stage is normalised as far as whole units go. */
    fun value(): Long {
        stage?.let { if (staged > 0) flush(it) }
        return crc.value
    }

    private fun flush(stage: ByteArray) {
        when (layout) {
            ContentLayout.PLAIN -> crc.update(stage, 0, staged)
            ContentLayout.N64_BYTE_SWAPPED, ContentLayout.N64_WORD_SWAPPED -> {
                Nintendo64Reader.normalise(stage, staged, layout)
                crc.update(stage, 0, staged)
            }
            ContentLayout.SMD_INTERLEAVED -> updateDeinterleaved(crc, stage, staged)
        }
        staged = 0
    }
}

private fun updateDeinterleaved(crc: CRC32, buffer: ByteArray, length: Int) {
    val block = MegaDriveReader.SMD_BLOCK
    var at = 0
    while (at + block <= length) {
        crc.update(MegaDriveReader.deinterleave(buffer.copyOfRange(at, at + block)))
        at += block
    }
    // The reader only picks this layout when the content is whole blocks; anything left over
    // (a file that changed since) is taken as it is rather than dropped.
    if (at < length) crc.update(buffer, at, length - at)
}

/** 64 KB: a multiple of the 4-byte N64 word and the 16 KB SMD block. */
internal const val CONTENT_CRC_BUFFER = 64 * 1024
