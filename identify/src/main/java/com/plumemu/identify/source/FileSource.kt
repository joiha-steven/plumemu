package com.plumemu.identify.source

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * A file read with positional [FileChannel] reads, which keep no cursor and so are safe from
 * several threads.
 *
 * The [FileChannel] constructor is what `library` uses for a Storage Access Framework document:
 * `FileInputStream(parcelFileDescriptor.fileDescriptor).channel`. Closing this source closes the
 * channel (and with it the stream it came from); the caller still closes the descriptor.
 *
 * The source owns the channel from construction: if reading its size fails, the channel is
 * closed before the exception reaches the caller.
 *
 * A [FileChannel] is interruptible: interrupting a thread blocked in [readAt] closes the channel
 * for every thread using this source, and their reads then fail with
 * `ClosedByInterruptException` / `ClosedChannelException` (both `IOException`s). Cancel work by
 * other means, or open one source per job.
 *
 * `java.nio.file` is not used: it needs API 26 and Plumemu's floor is 23.
 */
class FileSource @Throws(IOException::class) constructor(
    private val channel: FileChannel,
) : RandomAccessSource {

    /** Opens [file] for reading. */
    @Throws(IOException::class)
    constructor(file: File) : this(FileInputStream(file).channel)

    // A failed size() must not leak the channel (and, for the File constructor, the stream).
    override val size: Long = try {
        channel.size()
    } catch (e: IOException) {
        try {
            channel.close()
        } catch (closeFailure: IOException) {
            e.addSuppressed(closeFailure)
        }
        throw e
    }

    @Throws(IOException::class)
    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        if (length == 0) return 0
        return channel.read(ByteBuffer.wrap(dst, offset, length), position)
    }

    @Throws(IOException::class)
    override fun close() {
        channel.close()
    }
}
