package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.isGameCode
import com.plumemu.identify.fingerprint.u16le
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * Super Nintendo ROM (SNESdev wiki, "ROM header"; the copier header from the same wiki).
 *
 * A copier header (SMC, SWC, FIG) is 512 bytes in front of a ROM whose own length is a multiple
 * of 1 KB, so a length that is 512 more than a multiple of 1024 means one is there; it is left
 * out of the CRC, as name databases list the ROM without it.
 *
 * The internal header sits at 0x7FC0 (LoROM) or 0xFFC0 (HiROM) after any copier header: title
 * (21 bytes), map mode at +0x15, developer id at +0x1A, checksum complement at +0x1C and
 * checksum at +0x1E (they XOR to 0xFFFF). With developer id 0x33 an extended header carries the
 * game code at +0x02 - 0x10 (4 characters, or 2 followed by spaces on older cartridges). Reads
 * 0x50 bytes from 0x10 before each candidate: 160 bytes in all. ExHiROM (0x40FFC0) is not tried.
 *
 * [read] returns null when neither candidate is a header; [skipWithoutHeader] then still gives
 * the copier header's length, so the CRC of such a file stays right.
 */
internal object SnesReader : CartridgeReader {
    override val name = "snes"
    override val codeKind: CodeKind? = CodeKind.SNES
    override val extensions = setOf("sfc", "smc", "fig", "swc")
    override val storedFormMayDiffer = true

    const val COPIER_HEADER = 512
    private const val LOROM = 0x7FC0L
    private const val HIROM = 0xFFC0L
    private const val BEFORE = 0x10

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val skip = likelyHeaderSize(source.size)
        val lo = source.readFully(skip + LOROM - BEFORE, 0x50)
        val hi = source.readFully(skip + HIROM - BEFORE, 0x50)
        val loScore = score(lo, hiRom = false)
        val hiScore = score(hi, hiRom = true)
        val best = when {
            loScore == 0 && hiScore == 0 -> null
            hiScore > loScore -> hi
            else -> lo
        }
        if (best == null) return null
        val title = best.asciiText(BEFORE, 21)
        val code = if (best.u8(BEFORE + 0x1A) == 0x33) best.asciiText(BEFORE - 0x0E, 4) else null
        val goodCode = code.takeIf { isGameCode(it, 2, 4) }
        return CartridgeHeader(
            codeKind = if (goodCode != null) CodeKind.SNES else null,
            productCode = goodCode,
            title = title,
            headerBytesSkipped = skip,
        )
    }

    override fun likelyHeaderSize(length: Long): Int = if (length % 1024 == COPIER_HEADER.toLong()) COPIER_HEADER else 0

    /** The copier header is told by length, so it is skipped even when no internal header is valid. */
    override fun skipWithoutHeader(length: Long): Int = likelyHeaderSize(length)

    /**
     * 0 when this is not a header; otherwise higher is likelier: the checksum pair is worth 2,
     * a map mode that fits the position 1, a readable title 1. A header with neither a good
     * checksum pair nor a fitting map mode and title scores 0 (homebrew often has no checksum).
     */
    private fun score(block: ByteArray, hiRom: Boolean): Int {
        if (block.size < 0x50) return 0
        val checksumPair = block.u16le(BEFORE + 0x1C) xor block.u16le(BEFORE + 0x1E) == 0xFFFF
        val mode = block.u8(BEFORE + 0x15)
        val sa1 = mode == 0x23
        val fitsPosition = if (hiRom) (mode and 1) == 1 && !sa1 else (mode and 1) == 0 || sa1
        val modeFits = (mode and 0xE0) == 0x20 && fitsPosition
        val titled = block.asciiText(BEFORE, 21) != null
        if (!checksumPair && !(modeFits && titled)) return 0
        return (if (checksumPair) 2 else 0) + (if (modeFits) 1 else 0) + (if (titled) 1 else 0)
    }
}
