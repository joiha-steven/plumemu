package com.plumemu.identify.codec.lzma

import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readInto
import java.io.IOException

/**
 * Compressed bytes pulled one at a time by the decoder, with a hard end: asking for a byte past
 * it throws [LzmaException] (never an endless stream of zeros). [consumed] says how many bytes
 * the decoder has taken, for the read-volume measurements and the LZMA2 chunk-size checks; [length]
 * where the input ends, for the check that a CHD hunk uses all of its compressed bytes.
 */
internal interface CompressedInput {
    /** Bytes handed to the decoder so far. */
    val consumed: Long

    /** Bytes this input holds in all; reading past them throws. */
    val length: Long

    /**
     * The next byte, 0..255.
     *
     * @throws LzmaException at the end of the input.
     * @throws IOException when the underlying storage fails.
     */
    @Throws(LzmaException::class, IOException::class)
    fun readByte(): Int
}

/** Compressed bytes already in memory: [count] bytes of [bytes] from [offset]. */
internal class ByteArrayInput(
    private val bytes: ByteArray,
    private val offset: Int = 0,
    private val count: Int = bytes.size - offset,
) : CompressedInput {

    init {
        require(offset >= 0 && count >= 0 && offset + count <= bytes.size) { "range outside the array" }
    }

    private var position = 0

    override val consumed: Long get() = position.toLong()

    override val length: Long get() = count.toLong()

    override fun readByte(): Int {
        if (position >= count) throw LzmaException("compressed data ends after $count bytes")
        return bytes[offset + position++].toInt() and 0xFF
    }
}

/**
 * Compressed bytes read on demand from [length] bytes of [source] at [start]: a CHD hunk, a 7z
 * packed stream. [length] is the hard cap; reads go through a buffer of at most [bufferSize]
 * bytes, so a decode that stops early leaves the rest of the range unread. A source shorter
 * than the range (a truncated file) ends the input like the cap does.
 */
internal class SourceInput(
    private val source: RandomAccessSource,
    private val start: Long,
    override val length: Long,
    bufferSize: Int = DEFAULT_BUFFER,
) : CompressedInput {

    init {
        require(start >= 0 && length >= 0 && bufferSize > 0) { "negative range or empty buffer" }
    }

    private val buffer = ByteArray(minOf(bufferSize.toLong(), length).toInt())
    private var filled = 0
    private var index = 0
    private var bufferStart = 0L

    override val consumed: Long get() = bufferStart + index

    override fun readByte(): Int {
        if (index == filled) refill()
        return buffer[index++].toInt() and 0xFF
    }

    private fun refill() {
        val next = bufferStart + filled
        val wanted = minOf(buffer.size.toLong(), length - next).toInt()
        if (wanted <= 0) throw LzmaException("compressed data ends after $length bytes")
        val got = source.readInto(start + next, buffer, wanted)
        if (got <= 0) throw LzmaException("file ends inside the compressed data, after $next bytes")
        bufferStart = next
        filled = got
        index = 0
    }

    private companion object {
        const val DEFAULT_BUFFER = 4096
    }
}

/**
 * The [length] bytes of an LZMA2 chunk inside [parent]; reading past them is an error, so a
 * chunk can never eat the next chunk's header.
 */
internal class ChunkInput(private val parent: CompressedInput, private val size: Int) : CompressedInput {

    private var position = 0

    override val consumed: Long get() = position.toLong()

    override val length: Long get() = size.toLong()

    override fun readByte(): Int {
        if (position >= size) throw LzmaException("LZMA2 chunk reads past its $size compressed bytes")
        position++
        return parent.readByte()
    }
}
