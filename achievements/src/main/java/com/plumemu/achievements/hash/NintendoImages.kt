// GameCube and Wii image containers read as one flat byte range. Not in rcheevos, which reads the plain image only.
package com.plumemu.achievements.hash

import java.io.IOException
import java.util.zip.Adler32
import java.util.zip.DataFormatException
import java.util.zip.Inflater

private fun damaged(format: String, why: String) = DiscFormatException(RaCannotHashReason.BadImage, "Damaged $format: $why")

private fun le32(b: ByteArray, at: Int): Long =
    (b[at].toLong() and 0xFF) or ((b[at + 1].toLong() and 0xFF) shl 8) or ((b[at + 2].toLong() and 0xFF) shl 16) or ((b[at + 3].toLong() and 0xFF) shl 24)

private fun le64(b: ByteArray, at: Int): Long = le32(b, at) or (le32(b, at + 4) shl 32)

/** Opens a GameCube or Wii image by its extension: `.gcz`, `.wbfs`, `.rvz`, `.wia`, or the plain image. */
internal object NintendoImages {
    @Throws(IOException::class, DiscFormatException::class)
    fun open(rom: RaRomSource, extension: String, check: () -> Unit): DiscFile = when (extension) {
        "gcz" -> GczDiscFile.open(rom, check)
        "wbfs" -> WbfsDiscFile.open(rom, check)
        "rvz", "wia" -> WiaDiscFile.open(rom, check)
        else -> RawDiscFile(rom, check)
    }
}

/**
 * A Dolphin `.gcz`: a 32-byte header (magic, type, packed size, image size, block size, block count), a
 * table of 8-byte block offsets (bit 63 set = stored raw), a table of Adler-32 sums of the stored bytes,
 * then zlib blocks. The block read last stays decoded, because a read touches one block at a time.
 */
internal class GczDiscFile private constructor(
    private val reader: RangeReader,
    private val fileSize: Long,
    private val blockSize: Int,
    private val offsets: LongArray,
    private val sums: IntArray,
    private val dataStart: Long,
    private val packedSize: Long,
    override val size: Long,
) : DiscFile {
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
        val stored = (offsets[n] and STORED_BIT) != 0L
        val start = offsets[n] and STORED_BIT.inv()
        val end = if (n + 1 < offsets.size) offsets[n + 1] and STORED_BIT.inv() else packedSize
        val length = end - start
        if (start < 0 || length <= 0 || length > blockSize + SLACK_BYTES || dataStart + end > fileSize) throw damaged(".gcz", "block $n points outside the file")
        val expected = minOf(blockSize.toLong(), size - n.toLong() * blockSize).toInt()
        val data = ByteArray(length.toInt())
        if (reader.readAt(dataStart + start, data) < data.size) throw damaged(".gcz", "block $n is cut short")
        val sum = Adler32().also { it.update(data) }.value.toInt()
        if (sum != sums[n]) throw damaged(".gcz", "block $n does not match its checksum")
        if (stored) {
            if (data.size < expected) throw damaged(".gcz", "block $n is shorter than a block")
            System.arraycopy(data, 0, block, 0, expected)
        } else {
            inflate(n, data, expected)
        }
        blockLength = expected
        cachedBlock = n
    }

    private fun inflate(n: Int, data: ByteArray, expected: Int) {
        val inflater = Inflater()
        try {
            inflater.setInput(data)
            var got = 0
            while (got < expected) {
                val read = inflater.inflate(block, got, expected - got)
                if (read == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) break
                got += read
            }
            if (got < expected) throw damaged(".gcz", "block $n holds $got of $expected bytes")
        } catch (e: DataFormatException) {
            throw damaged(".gcz", "block $n cannot be decompressed (${e.message})")
        } finally {
            inflater.end()
        }
    }

    override fun close() = reader.close()

    companion object {
        private const val HEADER_BYTES = 32
        private val MAGIC = byteArrayOf(0x01, 0xC0.toByte(), 0x0B, 0xB1.toByte())
        private const val MIN_BLOCK_BYTES = 512
        private const val MAX_BLOCK_BYTES = 1 shl 20
        private const val MAX_TABLE_BYTES = 32L shl 20
        private const val SLACK_BYTES = 1024
        private const val STORED_BIT = Long.MIN_VALUE

        @Throws(IOException::class, DiscFormatException::class)
        fun open(source: RaRomSource, check: () -> Unit): GczDiscFile {
            val reader = RangeReader(source, check)
            try {
                return parse(reader, source.sizeBytes)
            } catch (e: Throwable) {
                reader.close()
                throw e
            }
        }

        private fun parse(reader: RangeReader, fileSize: Long): GczDiscFile {
            val header = ByteArray(HEADER_BYTES)
            if (reader.readAt(0, header) < HEADER_BYTES) throw damaged(".gcz", "the header is cut short")
            if (!header.copyOfRange(0, 4).contentEquals(MAGIC)) throw damaged(".gcz", "not a .gcz file (no magic word)")
            val packedSize = le64(header, 8)
            val dataSize = le64(header, 16)
            val blockSize = le32(header, 24)
            val blocks = le32(header, 28)
            val tableBytes = blocks * 12
            if (blockSize < MIN_BLOCK_BYTES || blockSize > MAX_BLOCK_BYTES || dataSize <= 0 || blocks <= 0 || tableBytes > MAX_TABLE_BYTES) {
                throw damaged(".gcz", "the header is not valid (block $blockSize, blocks $blocks, size $dataSize)")
            }
            if ((dataSize + blockSize - 1) / blockSize != blocks) throw damaged(".gcz", "the block count does not fit the image size")
            val dataStart = HEADER_BYTES + tableBytes
            if (packedSize <= 0 || dataStart + packedSize > fileSize) throw damaged(".gcz", "the packed size does not fit the file")
            val raw = ByteArray(tableBytes.toInt())
            if (reader.readAt(HEADER_BYTES.toLong(), raw) < raw.size) throw damaged(".gcz", "the block table is cut short")
            val n = blocks.toInt()
            val offsets = LongArray(n) { le64(raw, it * 8) }
            val sums = IntArray(n) { le32(raw, n * 8 + it * 4).toInt() }
            return GczDiscFile(reader, fileSize, blockSize.toInt(), offsets, sums, dataStart, packedSize, dataSize)
        }
    }
}

/**
 * A `.wbfs` of one Wii disc: a header sector (magic, sector sizes as powers of two), then at the next
 * hard-disc sector the disc's header copy and a table of 2-byte numbers saying which sector of the file
 * holds each sector of the disc; 0 means the sector was left out and reads as zeros.
 */
internal class WbfsDiscFile private constructor(
    private val reader: RangeReader,
    private val fileSize: Long,
    private val sectorShift: Int,
    private val table: IntArray,
) : DiscFile {
    override val size: Long = table.size.toLong() shl sectorShift

    override fun readAt(offset: Long, buffer: ByteArray, off: Int, len: Int): Int {
        var done = 0
        var position = offset
        val sectorSize = 1L shl sectorShift
        while (done < len && position < size) {
            val index = (position shr sectorShift).toInt()
            val inSector = (position and (sectorSize - 1)).toInt()
            val take = minOf((len - done).toLong(), sectorSize - inSector, size - position).toInt()
            val where = table[index]
            if (where == 0) {
                buffer.fill(0, off + done, off + done + take)
            } else {
                val start = (where.toLong() shl sectorShift) + inSector
                if (start + take > fileSize) throw damaged(".wbfs", "sector $index points outside the file")
                if (reader.readAt(start, buffer, off + done, take) < take) throw damaged(".wbfs", "sector $index is cut short")
            }
            done += take
            position += take
        }
        return done
    }

    override fun close() = reader.close()

    companion object {
        private const val MIN_DISC_SECTOR_SHIFT = 9
        private const val MAX_DISC_SECTOR_SHIFT = 16
        private const val MIN_SECTOR_SHIFT = 15
        private const val MAX_SECTOR_SHIFT = 25
        private const val DISC_HEADER_COPY_BYTES = 0x100

        /** libwbfs sizes the table for a dual-layer disc: 2 * 143,432 Wii sectors of 32 KiB. */
        private const val WII_SECTORS_PER_DISC = 143_432 * 2

        @Throws(IOException::class, DiscFormatException::class)
        fun open(source: RaRomSource, check: () -> Unit): WbfsDiscFile {
            val reader = RangeReader(source, check)
            try {
                val file = parse(reader, source.sizeBytes)
                val stem = source.fileName.substringBeforeLast('.')
                if (source.safeSibling("$stem.wbf1") != null) {
                    throw DiscFormatException(
                        RaCannotHashReason.NotYetSupported,
                        "${source.fileName} is split into parts (.wbf1, ...); reading split .wbfs files is not supported",
                    )
                }
                return file
            } catch (e: Throwable) {
                reader.close()
                throw e
            }
        }

        private fun parse(reader: RangeReader, fileSize: Long): WbfsDiscFile {
            val header = ByteArray(16)
            if (reader.readAt(0, header) < header.size) throw damaged(".wbfs", "the header is cut short")
            if (String(header, 0, 4, Charsets.ISO_8859_1) != "WBFS") throw damaged(".wbfs", "not a .wbfs file (no WBFS header)")
            val discShift = header[8].toInt() and 0xFF
            val sectorShift = header[9].toInt() and 0xFF
            if (discShift !in MIN_DISC_SECTOR_SHIFT..MAX_DISC_SECTOR_SHIFT || sectorShift !in MIN_SECTOR_SHIFT..MAX_SECTOR_SHIFT) {
                throw damaged(".wbfs", "the sector sizes are not valid ($discShift, $sectorShift)")
            }
            if (header[12].toInt() == 0) throw damaged(".wbfs", "the file holds no disc")
            val entries = WII_SECTORS_PER_DISC shr (sectorShift - MIN_SECTOR_SHIFT)
            val infoAt = 1L shl discShift
            val raw = ByteArray(entries * 2)
            if (reader.readAt(infoAt + DISC_HEADER_COPY_BYTES, raw) < raw.size) throw damaged(".wbfs", "the sector table is cut short")
            val table = IntArray(entries) { ((raw[it * 2].toInt() and 0xFF) shl 8) or (raw[it * 2 + 1].toInt() and 0xFF) }
            return WbfsDiscFile(reader, fileSize, sectorShift, table)
        }
    }
}
