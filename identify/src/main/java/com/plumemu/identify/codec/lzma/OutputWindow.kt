package com.plumemu.identify.codec.lzma

/**
 * The decoded bytes, which are also the dictionary matches copy from: the decoder only ever
 * produces up to [limit] bytes and a match can never reach further back than what was produced,
 * so no separate dictionary buffer is needed whatever size the stream declares.
 *
 * The array starts at [INITIAL_CAPACITY] (or [limit] if smaller) and doubles up to [limit], so a
 * caller that allows 1 MB but gets a 2 KB header allocates a few KB.
 *
 * [dictionaryStart] is where the last LZMA2 dictionary reset happened: positions for the
 * lp / pb contexts count from there and matches may not reach in front of it.
 */
internal class OutputWindow(val limit: Int) {

    init {
        require(limit >= 0) { "negative output limit" }
    }

    private var buffer = ByteArray(minOf(limit, INITIAL_CAPACITY))

    /** Bytes produced so far. */
    var size = 0
        private set

    var dictionaryStart = 0
        private set

    /** Bytes allocated for output so far; never above [limit]. */
    val capacity: Int get() = buffer.size

    val isFull: Boolean get() = size >= limit

    /** Bytes produced since the last dictionary reset; matches may reach back this far. */
    val sinceReset: Int get() = size - dictionaryStart

    fun resetDictionary() {
        dictionaryStart = size
    }

    /** The byte [distance] + 1 positions back (0 is the last byte), or 0 at a dictionary start. */
    fun byteBack(distance: Int): Int =
        if (distance < sinceReset) buffer[size - distance - 1].toInt() and 0xFF else 0

    fun put(value: Int) {
        ensure(size + 1)
        buffer[size++] = value.toByte()
    }

    /**
     * Copies a match of [length] bytes from [distance] + 1 back, overlapping as LZ77 does, but
     * stops at [stopAt] (at most [limit]). Returns how many bytes were copied. The caller has
     * already checked that [distance] is inside the dictionary.
     */
    fun copyMatch(distance: Int, length: Int, stopAt: Int): Int {
        val count = minOf(length, stopAt - size)
        if (count <= 0) return 0
        ensure(size + count)
        var from = size - distance - 1
        val end = size + count
        while (size < end) buffer[size++] = buffer[from++]
        return count
    }

    /** Appends [count] bytes pulled from [input] (an LZMA2 uncompressed chunk), up to [limit]. */
    fun copyFrom(input: CompressedInput, count: Int): Int {
        val n = minOf(count, limit - size)
        ensure(size + n)
        repeat(n) { buffer[size++] = input.readByte().toByte() }
        return n
    }

    /** The produced bytes; the buffer itself when it is exactly full. */
    fun toByteArray(): ByteArray = if (size == buffer.size) buffer else buffer.copyOf(size)

    private fun ensure(needed: Int) {
        check(needed <= limit) { "output past its limit" }
        if (needed <= buffer.size) return
        var next = buffer.size.toLong().coerceAtLeast(1)
        while (next < needed) next *= 2
        buffer = buffer.copyOf(minOf(next, limit.toLong()).toInt())
    }

    companion object {
        const val INITIAL_CAPACITY = 64 * 1024
    }
}
