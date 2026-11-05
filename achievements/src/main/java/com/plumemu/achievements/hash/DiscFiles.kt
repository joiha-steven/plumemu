// Disc containers read as one flat byte range. rcheevos: MIT licence; `.cso` is not in rcheevos.
package com.plumemu.achievements.hash

import com.plumemu.identify.codec.lz4.Lz4
import com.plumemu.identify.codec.lz4.Lz4Exception
import java.io.IOException
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * The file called [name] next to this one, or null. A name that leaves the folder (an absolute path,
 * a drive letter, a `..` segment) is refused: a cue sheet or playlist from a download must not make
 * the app open files elsewhere. A backslash counts as a slash.
 */
internal fun RaRomSource.safeSibling(name: String): RaRomSource? {
    val path = name.replace('\\', '/')
    if (path.isEmpty() || path.startsWith("/") || path.getOrNull(1) == ':' || path.split('/').any { it == ".." }) return null
    return sibling(path)
}

/** A disc image the hasher cannot use; [reason] and the message go to the result. */
internal class DiscFormatException(val reason: RaCannotHashReason, message: String) : Exception(message)

/** A disc image's bytes as one flat range, whatever the container. Streaming: nothing is read whole. */
internal interface DiscFile : AutoCloseable {
    val size: Long

    /** Reads up to [len] bytes at [offset]; returns how many (fewer only at the end of the image). */
    @Throws(IOException::class)
    fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int
}

/** The file as it is, read by seeking (reopening only when the target is behind the position). */
internal class RawDiscFile(source: RaRomSource, check: () -> Unit) : DiscFile {
    private val reader = RangeReader(source, check)
    override val size: Long = source.sizeBytes

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int = reader.readAt(offset, buffer, off, len)

    override fun close() = reader.close()
}

/**
 * A `.cso` (CISO v1, v2) or `.zso`: a 24-byte header, an index of 4-byte block offsets (shifted left by
 * `align`), then the blocks; what index bit 31 means depends on [Kind]. The block read last stays decoded,
 * because a sector read touches one block. Largest buffers: one block (at most 1 MiB), one stored block
 * (a block plus padding) and the index (an int array of at most 32 MiB, read in 64 KiB chunks).
 */
internal class CsoDiscFile private constructor(
    private val reader: RangeReader,
    private val fileSize: Long,
    private val blockSize: Int,
    private val align: Int,
    private val index: IntArray,
    override val size: Long,
    private val kind: Kind,
) : DiscFile {
    /** v1: deflate, bit 31 = raw. v2: bit 31 = LZ4, else deflate, or raw when the block did not shrink. ZSO: LZ4, bit 31 = raw. */
    enum class Kind { V1, V2, ZSO }

    private var cachedBlock = -1
    private val block = ByteArray(blockSize)
    private var blockLength = 0

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        var done = 0
        var position = offset
        while (done < len && position < size) {
            val n = (position / blockSize).toInt()
            loadBlock(n)
            val inBlock = (position % blockSize).toInt()
            val take = minOf(len - done, blockLength - inBlock)
            if (take <= 0) break
            System.arraycopy(block, inBlock, buffer, off + done, take)
            done += take
            position += take
        }
        return done
    }

    private fun loadBlock(n: Int) {
        if (n == cachedBlock) return
        cachedBlock = -1
        val start = (index[n] and Int.MAX_VALUE).toLong() shl align
        val end = (index[n + 1] and Int.MAX_VALUE).toLong() shl align
        val stored = end - start
        // A stored block is at most a block plus the alignment padding; anything larger is a bad index, not data to allocate.
        if (stored <= 0 || end > fileSize || stored > blockSize + (1L shl align)) throw corrupt("block $n points outside the file")
        val expected = minOf(blockSize.toLong(), size - n.toLong() * blockSize).toInt()
        val flagged = index[n] < 0
        // v2: a block stored at full size is raw whatever bit 31 says; below that, bit 31 means LZ4 (maxcso, PPSSPP). Others: bit 31 = raw (ZSO: LZ4 otherwise).
        val v2Raw = stored >= blockSize
        val raw = if (kind == Kind.V2) v2Raw else flagged
        val lz4 = (kind == Kind.V2 && !v2Raw && flagged) || (kind == Kind.ZSO && !flagged)
        val data = ByteArray(stored.toInt())
        if (reader.readAt(start, data) < data.size) throw corrupt("block $n is cut short")
        if (raw) {
            if (data.size < expected) throw corrupt("block $n is shorter than a block")
            System.arraycopy(data, 0, block, 0, expected)
        } else if (lz4) {
            try {
                Lz4.decode(data, 0, data.size, block, expected)
            } catch (e: Lz4Exception) {
                throw corrupt("block $n cannot be decompressed (${e.message})")
            }
        } else {
            inflate(n, data, expected)
        }
        blockLength = expected
        cachedBlock = n
    }

    private fun inflate(n: Int, data: ByteArray, expected: Int) {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            var got = 0
            while (got < expected) {
                val read = inflater.inflate(block, got, expected - got)
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                got += read
            }
            if (got < expected) throw corrupt("block $n holds $got of $expected bytes")
        } catch (e: DataFormatException) {
            throw corrupt("block $n cannot be decompressed (${e.message})")
        } finally {
            inflater.end()
        }
    }

    private fun corrupt(why: String) = DiscFormatException(RaCannotHashReason.BadImage, "Damaged .cso: $why")

    override fun close() = reader.close()

    companion object {
        private const val HEADER_BYTES = 24
        private const val MAX_BLOCK_BYTES = 1 shl 20
        private const val MIN_BLOCK_BYTES = 2048
        private const val MAX_INDEX_BYTES = 32L shl 20
        private const val INDEX_CHUNK_BYTES = 64 shl 10

        @Throws(IOException::class, DiscFormatException::class)
        fun open(source: RaRomSource, check: () -> Unit): CsoDiscFile {
            val reader = RangeReader(source, check)
            try {
                return parse(reader, source.sizeBytes)
            } catch (e: Throwable) {
                reader.close()
                throw e
            }
        }

        private fun parse(reader: RangeReader, fileSize: Long): CsoDiscFile {
            val header = ByteArray(HEADER_BYTES)
            if (reader.readAt(0, header) < HEADER_BYTES) throw DiscFormatException(RaCannotHashReason.BadImage, "The .cso header is cut short")
            val magic = String(header, 0, 4, Charsets.ISO_8859_1)
            if (magic != "CISO" && magic != "ZISO") throw DiscFormatException(RaCannotHashReason.BadImage, "Not a .cso file (no CISO header)")
            val version = header[20].toInt() and 0xFF
            if (magic == "CISO" && version > 2) throw DiscFormatException(RaCannotHashReason.NeedsDecoder, "A .cso v$version file is newer than this reader")
            val kind = if (magic == "ZISO") Kind.ZSO else if (version == 2) Kind.V2 else Kind.V1
            val total = le64(header, 8)
            val blockSize = le32(header, 16)
            val align = header[21].toInt() and 0xFF
            if (blockSize < MIN_BLOCK_BYTES || blockSize > MAX_BLOCK_BYTES || (blockSize and (blockSize - 1)) != 0 || align > 8 || total <= 0) {
                throw DiscFormatException(RaCannotHashReason.BadImage, "The .cso header is not valid (block $blockSize, size $total)")
            }
            val blocks = (total + blockSize - 1) / blockSize
            if ((blocks + 1) * 4 > MAX_INDEX_BYTES || (blocks + 1) * 4 + HEADER_BYTES > fileSize) throw DiscFormatException(RaCannotHashReason.BadImage, "The .cso index is cut short")
            // Read in chunks straight into the int array, so the peak is the index once, not twice.
            val index = IntArray((blocks + 1).toInt())
            val chunk = ByteArray(INDEX_CHUNK_BYTES)
            var done = 0
            while (done < index.size) {
                val take = minOf(index.size - done, INDEX_CHUNK_BYTES / 4)
                if (reader.readAt(HEADER_BYTES.toLong() + done * 4L, chunk, 0, take * 4) < take * 4) throw DiscFormatException(RaCannotHashReason.BadImage, "The .cso index is cut short")
                for (i in 0 until take) index[done + i] = le32(chunk, i * 4)
                done += take
            }
            return CsoDiscFile(reader, fileSize, blockSize, align, index, total, kind)
        }

        private fun le32(b: ByteArray, at: Int): Int =
            (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
                ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)

        private fun le64(b: ByteArray, at: Int): Long = le32(b, at).toUInt().toLong() or (le32(b, at + 4).toLong() shl 32)
    }
}

/**
 * One CD track of a `.chd` as a flat byte range: each 2448-byte frame contributes the bytes that hold the track's data
 * ([ChdCdTrack.dataBytes]), so a Mode 2 raw track reads like the same track in a `.bin`. Closing it closes the image.
 */
internal class ChdTrackFile(private val image: ChdImage, private val track: ChdCdTrack) : DiscFile {
    private val dataBytes = track.dataBytes
    private val framesPerHunk = image.hunkBytes / ChdImage.CD_FRAME_BYTES
    override val size: Long = track.frames.toLong() * dataBytes

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        var done = 0
        var position = offset
        while (done < len && position < size) {
            val frame = track.firstFrame + (position / dataBytes).toInt()
            val inFrame = (position % dataBytes).toInt()
            val hunk = image.hunk(frame / framesPerHunk)
            val at = (frame % framesPerHunk) * ChdImage.CD_FRAME_BYTES + inFrame
            val take = minOf(len - done, dataBytes - inFrame)
            if (track.isAudio) {
                // A CHD keeps audio samples byte-swapped (big-endian); a .bin has them as they are on the disc.
                for (i in 0 until take) buffer[off + done + i] = hunk[at - inFrame + ((inFrame + i) xor 1)]
            } else {
                System.arraycopy(hunk, at, buffer, off + done, take)
            }
            done += take
            position += take
        }
        return done
    }

    override fun close() = image.close()
}

/** A `.chd` that is not a CD (a DVD, or a plain disk image) as a flat byte range. Closing it closes the image. */
internal class ChdFlatFile(private val image: ChdImage) : DiscFile {
    override val size: Long = image.logicalBytes

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        var done = 0
        var position = offset
        while (done < len && position < size) {
            val hunk = image.hunk((position / image.hunkBytes).toInt())
            val inHunk = (position % image.hunkBytes).toInt()
            val take = minOf(len - done, image.hunkBytes - inHunk, (size - position).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            System.arraycopy(hunk, inHunk, buffer, off + done, take)
            done += take
            position += take
        }
        return done
    }

    override fun close() = image.close()
}
