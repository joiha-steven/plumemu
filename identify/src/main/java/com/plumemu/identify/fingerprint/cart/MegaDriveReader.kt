package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * Mega Drive / Genesis and 32X ROM header (Plutiedev, "ROM header"): "SEGA" at 0x100 (a few
 * cartridges have it at 0x101), domestic title at 0x120 (48 bytes), overseas title at 0x150 (48),
 * serial at 0x180 (14 bytes, e.g. "GM 00001009-00"). Reads 0x200 bytes.
 *
 * The serial is laid out "TT PPPPPPPP-VV": a 2-letter type ("GM" game, "AI" education, "OS",
 * "BR"), a space, an 8-character product code, a hyphen, a 2-digit version. MAME's software lists
 * give the product code alone, so that is the code kept ([productCode]): "GM MK-1401 -00" gives
 * "MK-1401", "GM T-50016 -00" gives "T-50016", "GM 00001009-00" gives "00001009".
 *
 * `.smd` dumps from Super Magic Drive copiers have a 512-byte header, then 16 KB blocks with the
 * odd bytes in the first 8 KB and the even bytes in the second. When the plain header is not
 * there and the length fits that layout, the header is rebuilt from the two 128-byte runs of the
 * first block that hold it (256 bytes more); the whole-file CRC then skips 512 bytes and
 * de-interleaves every block.
 */
internal object MegaDriveReader : CartridgeReader {
    override val name = "megadrive"
    override val codeKind: CodeKind? = CodeKind.MEGADRIVE
    override val extensions = setOf("md", "gen", "smd", "32x", "mdx")
    override val storedFormMayDiffer = true

    const val SMD_HEADER = 512
    const val SMD_BLOCK = 16 * 1024
    private val SEGA = ascii("SEGA")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val plain = source.readFully(0, 0x200)
        fromHeader(plain)?.let { return it }
        val size = source.size
        if (size < SMD_HEADER + SMD_BLOCK || (size - SMD_HEADER) % SMD_BLOCK != 0L) return null
        // Header bytes 0x100-0x1FF are, in the first block, odd bytes at 0x80-0xFF and even
        // bytes at 0x2080-0x20FF: 256 bytes rebuild the header, the rest of the block is not read.
        val odd = source.readFully(SMD_HEADER + 0x80L, 0x80)
        val even = source.readFully(SMD_HEADER + SMD_BLOCK / 2 + 0x80L, 0x80)
        if (odd.size < 0x80 || even.size < 0x80) return null
        val header = ByteArray(0x200)
        for (i in 0 until 0x80) {
            header[0x100 + 2 * i] = even[i]
            header[0x100 + 2 * i + 1] = odd[i]
        }
        val found = fromHeader(header) ?: return null
        return found.copy(headerBytesSkipped = SMD_HEADER, layout = ContentLayout.SMD_INTERLEAVED)
    }

    override fun likelyHeaderSize(length: Long): Int =
        if (length > SMD_HEADER && (length - SMD_HEADER) % SMD_BLOCK == 0L) SMD_HEADER else 0

    private fun fromHeader(header: ByteArray): CartridgeHeader? {
        if (header.size < 0x200) return null
        if (!header.startsWith(SEGA, 0x100) && !header.startsWith(SEGA, 0x101)) return null
        val serial = productCode(header, 0x180)
        return CartridgeHeader(
            codeKind = if (serial != null) CodeKind.MEGADRIVE else null,
            productCode = serial,
            title = header.asciiText(0x150, 48) ?: header.asciiText(0x120, 48),
            notes = if (serial == null) listOf("megadrive: serial at 0x180 is empty or not ASCII") else emptyList(),
        )
    }

    /**
     * The product code in the 14-byte serial field at [at] of [bytes] (a cartridge header, or
     * the Sega CD system area: same layout), upper case, spaces removed; null when the field is
     * empty or not printable ASCII. A field laid out as specified (two letters, a space, and a
     * hyphen or space at byte 11) is cut by position: bytes 3-10, so "GM MK-1401  00", whose
     * version has lost its hyphen, still gives "MK-1401". Any other layout goes through
     * [productCode] of its text.
     */
    fun productCode(bytes: ByteArray, at: Int): String? {
        val text = bytes.asciiText(at, SERIAL) ?: return null
        val raw = String(bytes, at, SERIAL, Charsets.ISO_8859_1).uppercase(Locale.ROOT)
        val standard = raw[0].isLetter() && raw[1].isLetter() && raw[2] == ' ' && (raw[11] == '-' || raw[11] == ' ')
        if (standard) raw.substring(3, 11).filterNot { it.isWhitespace() }.takeIf { it.isNotEmpty() }?.let { return it }
        return productCode(text)
    }

    /**
     * The product code part of a Mega Drive style [serial] text whose spaces may have moved:
     * upper case, spaces removed, the leading type and its space dropped ("GM "), then a
     * trailing version ("-00", " -00") when what stays still has 3 letters or digits (so a short
     * "T-01" without a version is not cut to "T"). Null when nothing is left. "00000000" is
     * kept: `Fingerprints.fixKeys` treats it as a dummy.
     */
    fun productCode(serial: String): String? {
        var code = serial.trim().uppercase(Locale.ROOT)
        TYPE.find(code)?.let { code = code.substring(it.range.last + 1) }
        VERSION.find(code)?.let { version ->
            val rest = code.substring(0, version.range.first)
            if (rest.count { it.isLetterOrDigit() } >= 3) code = rest
        }
        return code.filterNot { it.isWhitespace() }.ifEmpty { null }
    }

    private const val SERIAL = 14
    private val TYPE = Regex("^[A-Z]{2} +")
    private val VERSION = Regex(" *- *[0-9]{1,2}$")

    /** One 16 KB SMD block back in cartridge order (Genesis Plus GX and Plutiedev describe it). */
    fun deinterleave(block: ByteArray): ByteArray {
        val half = block.size / 2
        val out = ByteArray(block.size)
        for (i in 0 until half) {
            out[i * 2] = block[half + i]
            out[i * 2 + 1] = block[i]
        }
        return out
    }
}
