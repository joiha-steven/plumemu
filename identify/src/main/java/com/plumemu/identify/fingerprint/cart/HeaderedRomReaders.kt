package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind

import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * NES iNES and NES 2.0 files (NESdev wiki, "iNES" and "NES 2.0"): "NES" 0x1A at 0, a 16-byte
 * header, then a 512-byte trainer when bit 2 of byte 6 is set, then PRG and CHR ROM. Reads 16
 * bytes. Only the 16-byte header is left out of the CRC: No-Intro's headerless NES CRCs, and the
 * whole-file CRCs RetroArch computes after stripping the header, cover everything after it, so a
 * trainer stays in (a note says it is there; trainers come from old copier dumps and hacks).
 * There is no product code or title in the header.
 */
internal object NesReader : CartridgeReader {
    override val name = "nes"
    override val codeKind: CodeKind? = null
    override val extensions = setOf("nes")
    override val storedFormMayDiffer = true
    private val MAGIC = byteArrayOf('N'.code.toByte(), 'E'.code.toByte(), 'S'.code.toByte(), 0x1A)

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 16)
        if (header.size < 16 || !header.startsWith(MAGIC)) return null
        val trainer = header.u8(6) and 0x04 != 0
        return CartridgeHeader(
            headerBytesSkipped = 16,
            notes = if (trainer) listOf("nes: 512-byte trainer kept in the CRC") else emptyList(),
        )
    }

    /** PRG is 16 KB units and CHR 8 KB units, so a headered file is 16 (or 528) over a multiple of 8 KB. */
    override fun likelyHeaderSize(length: Long): Int = when (length % 8192) {
        16L, 528L -> 16
        else -> 0
    }
}

/**
 * Famicom Disk System images with the fwNES header (NESdev wiki, "FDS file format"): "FDS" 0x1A
 * at 0 and a 16-byte header, left out of the CRC. Reads 16 bytes. Disk sides are 65,500 bytes.
 */
internal object FdsReader : CartridgeReader {
    override val name = "fds"
    override val codeKind: CodeKind? = null
    override val extensions = setOf("fds")
    override val storedFormMayDiffer = true
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'D'.code.toByte(), 'S'.code.toByte(), 0x1A)
    private const val SIDE = 65_500L

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 16)
        if (header.size < 16 || !header.startsWith(MAGIC)) return null
        return CartridgeHeader(headerBytesSkipped = 16)
    }

    override fun likelyHeaderSize(length: Long): Int = if (length > 16 && length % SIDE == 16L) 16 else 0
}

/**
 * Atari 7800 `.a78` files (the A78 header as documented by the a78 format notes on AtariAge and
 * 7800 emulators): "ATARI7800" at offset 1, title at 0x11 (32 bytes), header 128 bytes, left
 * out of the CRC. Reads 128 bytes.
 */
internal object Atari7800Reader : CartridgeReader {
    override val name = "atari7800"
    override val codeKind: CodeKind? = null
    override val extensions = setOf("a78")
    override val storedFormMayDiffer = true
    private val MAGIC = ascii("ATARI7800")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 128)
        if (header.size < 128 || !header.startsWith(MAGIC, 1)) return null
        return CartridgeHeader(title = header.asciiText(0x11, 32), headerBytesSkipped = 128)
    }

    override fun likelyHeaderSize(length: Long): Int = if (length % 1024 == 128L) 128 else 0
}

/**
 * Atari Lynx `.lnx` files (the LNX header used by Handy and its successors): "LYNX" at 0,
 * cartridge name at 0x0A (32 bytes), header 64 bytes, left out of the CRC. Reads 64 bytes.
 */
internal object LynxReader : CartridgeReader {
    override val name = "lynx"
    override val codeKind: CodeKind? = null
    override val extensions = setOf("lnx")
    override val storedFormMayDiffer = true
    private val MAGIC = ascii("LYNX")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 64)
        if (header.size < 64 || !header.startsWith(MAGIC)) return null
        return CartridgeHeader(title = header.asciiText(0x0A, 32), headerBytesSkipped = 64)
    }

    override fun likelyHeaderSize(length: Long): Int = if (length % 1024 == 64L) 64 else 0
}
