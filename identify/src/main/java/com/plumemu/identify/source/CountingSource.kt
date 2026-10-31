package com.plumemu.identify.source

import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Wraps another source and counts what was read through it, to prove how little a fingerprint
 * costs. Safe from several threads. Closing it closes [delegate].
 */
class CountingSource(private val delegate: RandomAccessSource) : RandomAccessSource {

    private val bytes = AtomicLong()
    private val calls = AtomicLong()

    /** Bytes returned by [readAt] so far. */
    val bytesRead: Long get() = bytes.get()

    /** Number of [readAt] calls so far, including ones that returned -1. */
    val readCalls: Long get() = calls.get()

    override val size: Long get() = delegate.size

    @Throws(IOException::class)
    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        calls.incrementAndGet()
        val n = delegate.readAt(position, dst, offset, length)
        if (n > 0) bytes.addAndGet(n.toLong())
        return n
    }

    @Throws(IOException::class)
    override fun close() {
        delegate.close()
    }
}
