package com.plumemu.identify.fingerprint.cart

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.isGameCode
import com.plumemu.identify.fingerprint.cleanTitle
import com.plumemu.identify.fingerprint.u16le
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.fingerprint.u8
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException

/**
 * Game Boy Advance cartridge header (GBATEK, "GBA Cartridge Header"): title at 0xA0 (12 bytes),
 * game code at 0xAC (4), fixed value 0x96 at 0xB2, complement check at 0xBD over 0xA0-0xBC.
 * Reads 0xC0 bytes. Trusted only when the complement check matches (the BIOS refuses to boot a
 * cartridge without it, so every good dump has it) and the code is 4 letters or digits.
 */
internal object GbaReader : CartridgeReader {
    override val name = "gba"
    override val codeKind: CodeKind? = CodeKind.GBA
    override val extensions = setOf("gba", "agb")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 0xC0)
        if (header.size < 0xC0 || header.u8(0xB2) != 0x96) return null
        var check = 0
        for (i in 0xA0..0xBC) check -= header.u8(i)
        if ((check - 0x19) and 0xFF != header.u8(0xBD)) return null
        val code = header.asciiText(0xAC, 4)
        return CartridgeHeader(
            codeKind = CodeKind.GBA.takeIf { isGameCode(code, 4) },
            productCode = code.takeIf { isGameCode(it, 4) },
            title = header.asciiText(0xA0, 12),
        )
    }
}

/**
 * Nintendo DS cartridge header (GBATEK, "DS Cartridge Header"): title at 0x000 (12 bytes), game
 * code at 0x00C (4), banner offset at 0x068 (u32), header CRC-16 at 0x15E over 0x000-0x15D
 * (CRC-16/MODBUS: initial 0xFFFF, reflected polynomial 0xA001). Reads 0x160 bytes; trusted only
 * when the CRC matches.
 *
 * The banner (GBATEK, "DS Cartridge Icon/Title"): version u16 at 0 (1, 2, 3 or 0x103), CRC-16 at
 * 2 over 0x020-0x83F (same CRC), then titles of 0x100 bytes each in UTF-16LE: Japanese at 0x240,
 * English at 0x340, French, German, Italian, Spanish. A title has one to three lines: title,
 * optional subtitle, publisher. With two lines the first is the full title and is canonical;
 * with three the official title is "title - subtitle" or just the title and the banner does not
 * say which, so the two are joined for name matching and not canonical; one line is taken as
 * is, not canonical (nothing says it is not a publisher). It replaces the 12-character header
 * title. Reads 0x840 bytes more; a banner whose CRC fails is ignored with a note.
 */
internal object NdsReader : CartridgeReader {
    override val name = "nds"
    override val codeKind: CodeKind? = CodeKind.NDS
    override val extensions = setOf("nds", "dsi", "ids")

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 0x160)
        if (header.size < 0x160 || crc16Modbus(header, 0, 0x15E) != header.u16le(0x15E)) return null
        val code = header.asciiText(0x0C, 4)
        val notes = ArrayList<String>(1)
        val banner = bannerTitle(source, header.u32le(0x68), notes)
        return CartridgeHeader(
            codeKind = CodeKind.NDS.takeIf { isGameCode(code, 4) },
            productCode = code.takeIf { isGameCode(it, 4) },
            title = banner?.title ?: header.asciiText(0x00, 12),
            titleIsCanonical = banner?.canonical == true,
            notes = notes,
        )
    }

    @Throws(IOException::class)
    private fun bannerTitle(source: RandomAccessSource, offset: Long, notes: MutableList<String>): BannerTitle? {
        if (offset == 0L) return null
        if (offset < 0x160 || offset + BANNER > source.size) {
            notes += "nds: banner offset $offset is out of range"
            return null
        }
        val banner = source.readFully(offset, BANNER)
        if (banner.size < BANNER || banner.u16le(0) !in BANNER_VERSIONS) {
            notes += "nds: banner version is unknown"
            return null
        }
        if (crc16Modbus(banner, 0x20, BANNER - 0x20) != banner.u16le(2)) {
            notes += "nds: banner CRC does not match; header title used"
            return null
        }
        val english = String(banner, ENGLISH_TITLE, 0x100, Charsets.UTF_16LE).substringBefore('\u0000')
        return titleOfBannerLines(english.split('\n').mapNotNull { cleanTitle(it) })
    }

    private class BannerTitle(val title: String, val canonical: Boolean)

    /** Banner lines are title[, subtitle], publisher; see the class KDoc for what each count means. */
    private fun titleOfBannerLines(lines: List<String>): BannerTitle? = when (lines.size) {
        0 -> null
        1 -> BannerTitle(lines[0], canonical = false)
        2 -> BannerTitle(lines[0], canonical = true)
        else -> BannerTitle("${lines[0]} - ${lines[1]}", canonical = false)
    }

    private const val BANNER = 0x840
    private const val ENGLISH_TITLE = 0x340
    private val BANNER_VERSIONS = setOf(0x0001, 0x0002, 0x0003, 0x0103)

    fun crc16Modbus(bytes: ByteArray, from: Int, length: Int): Int {
        var crc = 0xFFFF
        for (i in from until from + length) {
            crc = crc xor bytes.u8(i)
            repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1 }
        }
        return crc
    }
}

/**
 * Game Boy and Game Boy Color cartridge header (Pan Docs, "The Cartridge Header"): the first
 * 8 bytes of the boot logo at 0x104, title at 0x134 (16 bytes, 15 when the CGB flag at 0x143 is
 * 0x80 or 0xC0), header checksum at 0x14D over 0x134-0x14C. Reads 0x150 bytes; trusted only when
 * both the logo start and the checksum match, as the boot ROM requires.
 *
 * Late Color cartridges put a 4-letter manufacturer code at 0x13F-0x142; older ones used those
 * bytes for the title, and nothing in the header tells which. It is taken as a code only when
 * the CGB flag is set and the title ends with a NUL before 0x13F; otherwise there is no product
 * code and the CRC does the identifying.
 */
internal object GameBoyReader : CartridgeReader {
    override val name = "gb"
    override val codeKind: CodeKind? = CodeKind.GB
    override val extensions = setOf("gb", "gbc", "cgb", "dmg", "sgb")

    private val LOGO_START = byteArrayOf(0xCE.toByte(), 0xED.toByte(), 0x66, 0x66, 0xCC.toByte(), 0x0D, 0x00, 0x0B)

    @Throws(IOException::class)
    override fun read(source: RandomAccessSource): CartridgeHeader? {
        val header = source.readFully(0, 0x150)
        if (header.size < 0x150) return null
        for (i in LOGO_START.indices) if (header[0x104 + i] != LOGO_START[i]) return null
        var check = 0
        for (i in 0x134..0x14C) check = check - header.u8(i) - 1
        if (check and 0xFF != header.u8(0x14D)) return null
        val color = header.u8(0x143) == 0x80 || header.u8(0x143) == 0xC0
        val titleEndsEarly = (0x134 until 0x13F).any { header[it].toInt() == 0 }
        val code = header.asciiText(0x13F, 4).takeIf { color && titleEndsEarly && isGameCode(it, 4) }
        return CartridgeHeader(
            codeKind = if (code != null) CodeKind.GB else null,
            productCode = code,
            title = header.asciiText(0x134, if (code != null) 11 else if (color) 15 else 16),
        )
    }
}
