package com.plumemu.achievements.hash

import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** rcheevos' `MAX_BUFFER_SIZE`: nothing past the first 64 MiB of a file is hashed. */
internal const val MAX_HASH_BYTES: Long = 64L * 1024 * 1024
internal const val CHUNK_BYTES: Int = 64 * 1024

internal fun newMd5(): MessageDigest = MessageDigest.getInstance("MD5")

internal fun MessageDigest.hex(): String {
    val digits = "0123456789abcdef"
    val bytes = digest()
    val out = StringBuilder(bytes.size * 2)
    for (byte in bytes) {
        val value = byte.toInt() and 0xFF
        out.append(digits[value ushr 4]).append(digits[value and 0x0F])
    }
    return out.toString()
}

/** Reads until [len] bytes or the end of the stream; returns how many it got. */
internal fun InputStream.readUpTo(buffer: ByteArray, off: Int, len: Int): Int {
    var total = 0
    while (total < len) {
        val read = read(buffer, off + total, len - total)
        if (read < 0) break
        total += read
    }
    return total
}

/** Skips [count] bytes; returns how many it skipped (fewer only at the end of the stream). */
internal fun InputStream.skipUpTo(count: Long): Long {
    var remaining = count
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else {
            // skip() may return 0 without being at the end; one real read tells which.
            if (read() < 0) break
            remaining -= 1
        }
    }
    return count - remaining
}

/**
 * Feeds up to [count] bytes of [input] into [md]; returns how many it fed. [check] runs between
 * chunks so a cancelled scan stops reading a large file. A [transform] edits each chunk first.
 */
internal fun feed(
    input: InputStream,
    md: MessageDigest,
    count: Long,
    check: () -> Unit,
    transform: ((ByteArray, Int) -> Unit)? = null,
): Long {
    val buffer = ByteArray(CHUNK_BYTES)
    var remaining = count
    while (remaining > 0) {
        check()
        val want = minOf(remaining, CHUNK_BYTES.toLong()).toInt()
        val got = input.readUpTo(buffer, 0, want)
        if (got <= 0) break
        transform?.invoke(buffer, got)
        md.update(buffer, 0, got)
        remaining -= got
        if (got < want) break
    }
    return count - remaining
}

/**
 * Reads ranges of a file in any order, reopening only when a range starts before the current
 * position. Bytes past the end of the file read as nothing; the caller pads.
 */
internal class RangeReader(private val source: RaRomSource, private val check: () -> Unit) : AutoCloseable {
    private var input: InputStream? = null
    private var position = 0L

    @Throws(IOException::class)
    private fun seek(target: Long) {
        if (input == null || target < position) {
            input?.close()
            input = source.open()
            position = 0
        }
        position += input!!.skipUpTo(target - position)
    }

    /** Reads up to [len] bytes at [offset] into [buffer] from [off]; returns the count read. */
    @Throws(IOException::class)
    fun readAt(offset: Long, buffer: ByteArray, off: Int = 0, len: Int = buffer.size - off): Int {
        seek(offset)
        if (position != offset) return 0
        val got = input!!.readUpTo(buffer, off, len)
        position += got
        return got
    }

    /** Feeds [length] bytes at [offset] into [md]; a range cut short by the end of file is padded with zeros. */
    @Throws(IOException::class)
    fun digestAt(md: MessageDigest, offset: Long, length: Long) {
        seek(offset)
        val fed = if (position == offset) feed(input!!, md, length, check) else 0L
        position += fed
        var missing = length - fed
        val zeros = ByteArray(CHUNK_BYTES)
        while (missing > 0) {
            check()
            val n = minOf(missing, zeros.size.toLong()).toInt()
            md.update(zeros, 0, n)
            missing -= n
        }
    }

    override fun close() {
        input?.close()
        input = null
    }
}
