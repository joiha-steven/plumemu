package com.plumemu.identify.source

/** Bytes held in memory: generated test data. Tests only; the app reads files. */
class ByteArraySource(private val bytes: ByteArray) : RandomAccessSource {

    override val size: Long get() = bytes.size.toLong()

    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        val n = minOf(length.toLong(), bytes.size - position).toInt()
        System.arraycopy(bytes, position.toInt(), dst, offset, n)
        return n
    }

    override fun close() = Unit
}
