package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.codec.lz4.Lz4
import com.plumemu.identify.codec.lz4.Lz4Exception
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.fingerprint.u64le
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * The uncompressed image inside a CSO (v1 or v2) or ZSO file, decompressed one block at a time
 * on demand.
 *
 * Layout (PSP homebrew `ciso.h`, PCSX2 `CsoFileReader`), little-endian: "CISO" at 0, header size
 * at 4, uncompressed size u64 at 8, block size at 0x10, version at 0x14, index shift at 0x15,
 * then from 0x18 one u32 per block plus one: bits 0-30 are the block's offset shifted right by
 * the index shift. A compressed block ends where the next one starts. In v1 bit 31 set means the
 * block is stored as is, else it is raw deflate. In v2 (maxcso, PPSSPP) a block stored at the full
 * block size or more is raw whatever bit 31 says; a smaller one is LZ4 when bit 31 is set, else
 * raw deflate. ZSO ("ZISO", same header, version 1): bit 31 set is raw, else LZ4. The magic
 * decides which. A block that does not decode is a [NotRead].
 *
 * Used by one disc read on one thread; keeps the last block decoded.
 */
internal class CsoSource private constructor(
    private val file: RandomAccessSource,
    override val size: Long,
    private val blockSize: Int,
    private val indexShift: Int,
    private val kind: Kind,
) : RandomAccessSource {

    private enum class Kind { V1, V2, ZSO }

    private enum class How { STORED, DEFLATE, LZ4 }

    private var cachedBlock = -1L
    private var cached = ByteArray(0)

    @Throws(IOException::class)
    override fun readAt(position: Long, dst: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val block = position / blockSize
        val data = block(block)
        val inBlock = (position - block * blockSize).toInt()
        val n = minOf(length, data.size - inBlock)
        if (n <= 0) throw NotRead("cso: block $block is shorter than the block size")
        System.arraycopy(data, inBlock, dst, offset, n)
        return n
    }

    @Throws(IOException::class)
    private fun block(block: Long): ByteArray {
        if (block == cachedBlock) return cached
        val index = file.readFully(INDEX + block * 4, 8)
        if (index.size < 8) throw NotRead("cso: block index is truncated")
        val entry = index.u32le(0)
        val start = (entry and OFFSET_MASK) shl indexShift
        val end = (index.u32le(4) and OFFSET_MASK) shl indexShift
        val stored = entry and STORED_FLAG != 0L
        val length = end - start
        // Blocks start on 2^shift boundaries, so a block may carry up to that much padding.
        if (start < INDEX || length < 0 || end > file.size || length > blockSize + maxOf(MAX_BLOCK_OVERHEAD, 1 shl indexShift)) {
            throw NotRead("cso: block $block at $start-$end is out of range")
        }
        val raw = file.readFully(start, length.toInt())
        val expected = minOf(blockSize.toLong(), size - block * blockSize).toInt()
        val data = when (how(stored, length)) {
            How.STORED -> raw.copyOf(minOf(raw.size, blockSize))
            How.DEFLATE -> inflate(raw, blockSize, nowrap = true, what = "cso")
            How.LZ4 -> lz4(raw, expected, block)
        }
        cachedBlock = block
        cached = data
        return data
    }

    private fun how(flagged: Boolean, length: Long): How = when (kind) {
        Kind.V1 -> if (flagged) How.STORED else How.DEFLATE
        Kind.V2 -> if (length >= blockSize) How.STORED else if (flagged) How.LZ4 else How.DEFLATE
        Kind.ZSO -> if (flagged) How.STORED else How.LZ4
    }

    /**
     * LZ4 data that must give the block's [expected] bytes. An encoder may have been given the
     * last block padded to a whole block, so the buffer is a block long and what follows the
     * wanted bytes is not read.
     */
    private fun lz4(raw: ByteArray, expected: Int, block: Long): ByteArray {
        val out = ByteArray(blockSize)
        try {
            Lz4.decode(raw, 0, raw.size, out, minOut = expected)
        } catch (e: Lz4Exception) {
            throw NotRead("cso: block $block: LZ4 data damaged (${e.message})")
        }
        return out.copyOf(expected)
    }

    override fun close() = Unit

    companion object {
        private val CISO = ascii("CISO")
        private val ZISO = ascii("ZISO")
        private const val HEADER = 0x18
        private const val INDEX = 0x18L
        private const val OFFSET_MASK = (1L shl 31) - 1
        private const val STORED_FLAG = 1L shl 31
        private const val MAX_BLOCK_OVERHEAD = 64
        private const val MAX_BLOCK_SIZE = 1 shl 20

        /** [Container.CSO] or [Container.ZSO] from a file's first bytes, or null. */
        fun containerOf(start: ByteArray): Container? = when {
            start.startsWith(CISO) -> Container.CSO
            start.startsWith(ZISO) -> Container.ZSO
            else -> null
        }

        /** The image inside a CSO v1 / v2 or ZSO [file]; [NotRead] for a later version or a damaged header. */
        @Throws(IOException::class)
        fun open(file: RandomAccessSource): CsoSource {
            val header = file.readFully(0, HEADER)
            if (header.size < HEADER) throw NotRead("cso: header is truncated")
            val zso = header.startsWith(ZISO)
            if (!zso && !header.startsWith(CISO)) throw NotRead("cso: no CISO or ZISO magic")
            val version = header.u8(0x14)
            // ZSO has only version 1; CSO is read up to version 2.
            if (version > (if (zso) 1 else 2)) throw NotRead("cso: version $version not read")
            val size = header.u64le(8)
            val blockSize = header.u32le(0x10)
            val shift = header.u8(0x15)
            if (size <= 0 || blockSize < SectorImage.SECTOR || blockSize > MAX_BLOCK_SIZE || shift > 16) {
                throw NotRead("cso: header values are out of range")
            }
            val kind = if (zso) Kind.ZSO else if (version == 2) Kind.V2 else Kind.V1
            return CsoSource(file, size, blockSize.toInt(), shift, kind)
        }
    }
}
