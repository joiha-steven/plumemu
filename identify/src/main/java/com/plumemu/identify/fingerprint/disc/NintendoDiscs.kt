package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.isGameCode
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.fingerprint.u64le
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * GameCube and Wii disc headers (wiibrew, "Wii disc" header; YAGCD "Disc header"): game id at
 * 0x0 (6 characters), Wii magic `5D 1C 9E A3` at 0x18, GameCube magic `C2 33 9F 3D` at 0x1C,
 * title at 0x20 (a 64-byte field; ASCII only, so a Shift-JIS title gives none). The header
 * title is the full title as published, so it is canonical, but only when a NUL ends it inside
 * the field: a field filled to the last byte may have been cut, so its title is not canonical.
 * Only the first 0x80 bytes are read,
 * wherever a container keeps them:
 * - plain `.iso` / `.gcm` (NKit images too): at 0.
 * - WBFS (libwbfs): "WBFS" at 0, sector size shifts at 8 (hard-disk sector) and 9 (WBFS
 *   sector), disc table at 12; the first used slot's disc info starts with a copy of the header.
 * - RVZ / WIA (Dolphin `WIAFileFormat`): file header 1 is 0x48 bytes; file header 2 follows
 *   with disc type, compression, level, chunk size and then a copy of the header (0x58).
 * - GCZ (Dolphin `CompressedBlob`, little-endian): magic `B1 0B C0 01`, sizes, block size and
 *   count in 32 bytes, then u64 block pointers (top bit: stored uncompressed), u32 hashes and the
 *   data; block 0 holds the header, zlib-compressed unless flagged.
 */
internal object NintendoDiscs {
    const val HEADER = 0x80
    private const val TITLE_FIELD = 64
    private val WII_MAGIC = byteArrayOf(0x5D, 0x1C, 0x9E.toByte(), 0xA3.toByte())
    private val GAMECUBE_MAGIC = byteArrayOf(0xC2.toByte(), 0x33, 0x9F.toByte(), 0x3D)
    private val WBFS = ascii("WBFS")
    private val RVZ = byteArrayOf('R'.code.toByte(), 'V'.code.toByte(), 'Z'.code.toByte(), 1)
    private val WIA = byteArrayOf('W'.code.toByte(), 'I'.code.toByte(), 'A'.code.toByte(), 1)
    private val GCZ = byteArrayOf(0x01, 0xC0.toByte(), 0x0B, 0xB1.toByte())
    private const val WIA_DISC_HEADER = 0x58
    private const val GCZ_HEADER = 32
    private const val GCZ_RAW_FLAG = Long.MIN_VALUE
    private const val GCZ_FIRST_INPUT = 1024L
    private const val GCZ_MAX_INPUT = 16 * 1024L
    private const val WII_SECTOR_SHIFT = 15
    private const val WII_SECTORS_PER_DISC = 143432L * 2
    private const val WBFS_MAX_SLOTS = 500

    /** The container a file's first bytes announce, or null. */
    fun containerOf(start: ByteArray): Container? = when {
        start.startsWith(WBFS) -> Container.WBFS
        start.startsWith(RVZ) -> Container.RVZ
        start.startsWith(WIA) -> Container.WIA
        start.startsWith(GCZ) -> Container.GCZ
        else -> null
    }

    /** The disc id in a disc header [header], or null when neither magic is there. */
    fun fromHeader(header: ByteArray): DiscId? {
        if (header.size < HEADER) return null
        val kind = when {
            header.startsWith(WII_MAGIC, 0x18) -> CodeKind.WII
            header.startsWith(GAMECUBE_MAGIC, 0x1C) -> CodeKind.GAMECUBE
            else -> return null
        }
        val id = header.asciiText(0, 6).takeIf { isGameCode(it, 6) }
        val title = header.asciiText(0x20, TITLE_FIELD)
        val terminated = (0x20 until 0x20 + TITLE_FIELD).any { header[it].toInt() == 0 }
        return DiscId(kind.takeIf { id != null }, id, title, titleIsCanonical = title != null && terminated)
    }

    /** The disc id inside [container]; a damaged container throws [NotRead]. */
    @Throws(IOException::class)
    fun fromContainer(source: RandomAccessSource, container: Container): DiscId {
        val header = when (container) {
            Container.WBFS -> wbfsHeader(source)
            Container.RVZ, Container.WIA -> source.readFully(WIA_DISC_HEADER.toLong(), HEADER)
            Container.GCZ -> gczHeader(source)
            else -> source.readFully(0, HEADER)
        }
        val name = container.name.lowercase()
        return fromHeader(header) ?: throw NotRead("$name: no GameCube or Wii disc header inside")
    }

    @Throws(IOException::class)
    private fun wbfsHeader(source: RandomAccessSource): ByteArray {
        val head = source.readFully(0, 12 + WBFS_MAX_SLOTS)
        if (head.size < 13) throw NotRead("wbfs: header is truncated")
        val hdShift = head.u8(8)
        val wbfsShift = head.u8(9)
        if (hdShift !in 9..12 || wbfsShift !in WII_SECTOR_SHIFT..30) throw NotRead("wbfs: sector sizes are out of range")
        val hdSector = 1L shl hdShift
        val slots = minOf(head.size - 12L, hdSector - 12).toInt()
        val slot = (0 until slots).firstOrNull { head[12 + it].toInt() != 0 } ?: throw NotRead("wbfs: no disc in the table")
        val wbfsSectorsPerDisc = WII_SECTORS_PER_DISC shr (wbfsShift - WII_SECTOR_SHIFT)
        val infoSize = (0x100 + wbfsSectorsPerDisc * 2 + hdSector - 1) / hdSector * hdSector
        return source.readFully(hdSector + slot * infoSize, HEADER)
    }

    @Throws(IOException::class)
    private fun gczHeader(source: RandomAccessSource): ByteArray {
        val head = source.readFully(0, GCZ_HEADER + 16)
        if (head.size < GCZ_HEADER + 8) throw NotRead("gcz: header is truncated")
        val compressedSize = head.u64le(8)
        val blockSize = head.u32le(24)
        val blocks = head.u32le(28)
        if (blocks < 1 || blockSize < HEADER || blocks > source.size / 8) throw NotRead("gcz: block table is out of range")
        val dataStart = GCZ_HEADER + blocks * 12
        val first = head.u64le(GCZ_HEADER)
        val start = first and GCZ_RAW_FLAG.inv()
        val next = if (blocks > 1 && head.size >= GCZ_HEADER + 16) head.u64le(GCZ_HEADER + 8) and GCZ_RAW_FLAG.inv() else compressedSize
        if (start < 0 || next <= start || dataStart + start >= source.size) throw NotRead("gcz: block 0 is out of range")
        if (first and GCZ_RAW_FLAG != 0L) return source.readFully(dataStart + start, HEADER)
        // The header is the first 0x80 bytes of block 0: the first KB of zlib input nearly always
        // holds them; only a block with a large code table needs more.
        for (limit in longArrayOf(GCZ_FIRST_INPUT, GCZ_MAX_INPUT)) {
            val input = source.readFully(dataStart + start, minOf(next - start, limit).toInt())
            val out = inflate(input, HEADER, nowrap = false, what = "gcz")
            if (out.size == HEADER || input.size < limit) return out
        }
        throw NotRead("gcz: block 0 does not inflate to a disc header")
    }
}
