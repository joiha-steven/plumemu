package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.codec.zstd.Zstd
import com.plumemu.identify.codec.zstd.ZstdException
import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/** The serial and title one disc carries itself. */
internal data class DiscId(
    val codeKind: CodeKind?,
    val productCode: String?,
    val title: String?,
    val titleIsCanonical: Boolean = false,
)

/**
 * Something in a disc image this module does not or cannot read (an unsupported codec, a
 * damaged structure, the read cap). The message is a plain-English note; the disc reader catches
 * it, keeps what it already knows and adds the note. Never used for I/O failures: those stay
 * IOException and reach the caller.
 */
internal class NotRead(message: String) : RuntimeException(message)

/**
 * Bytes a disc read may still take from storage. Shared by the image and the
 * sibling files a cue sheet or playlist opens, so the whole read stays under one cap.
 */
internal class ReadCap(val limit: Long) {
    var used: Long = 0
        private set

    fun charge(bytes: Long) {
        if (used + bytes > limit) throw NotRead("disc: stopped at the $limit-byte read cap after $used bytes")
        used += bytes
    }
}

/**
 * A source that charges every read to [cap] before it happens. Does not close [delegate]:
 * whoever opened it closes it. Used from one thread only (one disc read).
 */
internal class CappedSource(private val delegate: RandomAccessSource, private val cap: ReadCap) : RandomAccessSource {
    override val size: Long get() = delegate.size

    @Throws(IOException::class)
    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        cap.charge(minOf(length.toLong(), size - position))
        return delegate.readAt(position, dst, offset, length)
    }

    override fun close() = Unit
}

/**
 * Inflates [input] into at most [outputLength] bytes. [nowrap] is raw deflate (CSO, CHD) as
 * opposed to a zlib stream (GCZ). Stops as soon as [outputLength] bytes are out, so a caller
 * that needs only a block's first bytes can pass a prefix of the compressed data.
 *
 * @return the bytes produced, possibly fewer than [outputLength] when the input ends first.
 * @throws NotRead when the data is not valid deflate.
 */
internal fun inflate(input: ByteArray, outputLength: Int, nowrap: Boolean, what: String): ByteArray {
    val inflater = Inflater(nowrap)
    try {
        // Raw deflate may need one byte past the end of the stream (java.util.zip.Inflater docs).
        inflater.setInput(if (nowrap) input + 0 else input)
        val out = ByteArray(outputLength)
        var done = 0
        while (done < outputLength) {
            val n = inflater.inflate(out, done, outputLength - done)
            if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
            done += n
        }
        return if (done == outputLength) out else out.copyOf(done)
    } catch (e: DataFormatException) {
        throw NotRead("$what: compressed data is damaged (${e.message})")
    } finally {
        inflater.end()
    }
}

/**
 * Decodes zstd [input] (one or more frames) to exactly [expected] bytes. A frame may declare a
 * window up to 128 MB (level 19 declares 8 MB even for a small hunk); only the output is
 * allocated, and it never passes [expected], so memory stays bounded.
 *
 * @throws NotRead when the data is damaged or is not [expected] bytes long: a decoder fault is
 * a note, never an IOException (that is the storage failing).
 */
internal fun zstd(input: ByteArray, expected: Int, what: String): ByteArray {
    val out = try {
        Zstd.decompress(input, maxOutput = expected, maxWindow = ZSTD_MAX_WINDOW)
    } catch (e: ZstdException) {
        throw NotRead("$what: compressed data is damaged (${e.message})")
    }
    if (out.size != expected) throw NotRead("$what: zstd data is ${out.size} bytes, not $expected")
    return out
}

private const val ZSTD_MAX_WINDOW = 1 shl 27

/**
 * A small text file (cue sheet, track list, playlist, `SYSTEM.CNF`): UTF-8 when it decodes
 * cleanly, else ISO-8859-1, so a name in an old code page still opens the file it names.
 */
internal fun decodeText(bytes: ByteArray): String {
    val utf8 = bytes.decodeToString()
    val text = if ('\ufffd' in utf8) String(bytes, Charsets.ISO_8859_1) else utf8
    return text.removePrefix("\ufeff")
}

/** Reads a whole small text file, refusing one longer than [limit] bytes. */
@Throws(IOException::class)
internal fun readSmallText(source: RandomAccessSource, limit: Int, what: String): String {
    if (source.size > limit) throw NotRead("$what: ${source.size} bytes is too long for a list of files")
    return decodeText(source.readFully(0, source.size.toInt()))
}

/** Lower-case hex of [bytes]. */
internal fun hex(bytes: ByteArray): String {
    val out = StringBuilder(bytes.size * 2)
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        out.append(HEX_DIGITS[v ushr 4]).append(HEX_DIGITS[v and 0xF])
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
