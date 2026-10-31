package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.cleanTitle
import com.plumemu.identify.fingerprint.startsWith
import com.plumemu.identify.fingerprint.u32le
import com.plumemu.identify.source.RandomAccessSource
import com.plumemu.identify.source.readFully
import java.io.IOException
import java.util.Locale

/**
 * PlayStation, PlayStation 2 and PSP serials and titles:
 * - PS1 / PS2: `SYSTEM.CNF` in the root, `BOOT = cdrom:\SLUS_999.99;1` (PS1) or
 *   `BOOT2 = cdrom0:\SLUS_999.99;1` (PS2); the boot file's name is the serial.
 * - PSP disc: `UMD_DATA.BIN` in the root ("ULUS-99999|..."), `PSP_GAME/PARAM.SFO` for `TITLE`
 *   and `DISC_ID`.
 * - PBP (PSP `EBOOT.PBP`, PlayStation classics): "\0PBP" at 0, version at 4, eight u32
 *   little-endian offsets from 8; the first section is `PARAM.SFO`, ending where the second
 *   starts. `CATEGORY` "ME" marks a PlayStation classic. Nothing is decrypted.
 *
 * Serials are normalised to "PREFIX-DIGITS" (upper case, the underscore and dot of a boot file
 * name dropped): "slus_999.99" and "SLUS99999" both become "SLUS-99999".
 */
internal object PlayStationDiscs {
    private const val SYSTEM_CNF_LIMIT = 2048
    private const val UMD_DATA_LIMIT = 64
    private const val SFO_LIMIT = 16 * 1024
    private const val PBP_HEADER = 0x28
    private val PBP_MAGIC = byteArrayOf(0, 'P'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
    private val SERIAL = Regex("([A-Z]{3,5})[_ -]?([0-9]{1,7})(?:\\.([0-9]{1,7}))?")
    private const val MIN_DIGITS = 3
    private const val MAX_DIGITS = 7
    private val BOOT_LINE = Regex("^\\s*(BOOT2?)\\s*=\\s*(.*)$", RegexOption.IGNORE_CASE)

    /**
     * "slus_999.99" or "SLUS99999" to "SLUS-99999": a prefix of 3 to 5 letters, then the digits
     * with the boot file's dot dropped ("SCED_123.4" keeps its four digits: "SCED-1234"); null
     * when it is not a serial (no digits, fewer than 3 or more than 7).
     */
    fun normalise(text: String?): String? {
        val match = SERIAL.matchEntire(text?.trim()?.uppercase(Locale.ROOT) ?: return null) ?: return null
        val (letters, first, last) = match.destructured
        val digits = first + last
        return if (digits.length in MIN_DIGITS..MAX_DIGITS) "$letters-$digits" else null
    }

    /** PSP or PlayStation files in [iso]; null with a note when there are none. */
    @Throws(IOException::class)
    fun fromIso(iso: Iso9660, notes: MutableList<String>): DiscId? {
        val umd = iso.find("UMD_DATA.BIN")
        val sfoEntry = iso.find("PSP_GAME/PARAM.SFO")
        if (umd != null || sfoEntry != null) return psp(iso, umd, sfoEntry, notes)
        val cnf = iso.find("SYSTEM.CNF")
        if (cnf == null) {
            notes += "iso: no SYSTEM.CNF, UMD_DATA.BIN or PSP_GAME/PARAM.SFO in the root"
            return null
        }
        return fromSystemCnf(decodeText(iso.readHead(cnf, SYSTEM_CNF_LIMIT)), notes)
    }

    /** The serial a `SYSTEM.CNF` boots; PS2 when it has a `BOOT2` line. */
    fun fromSystemCnf(text: String, notes: MutableList<String>): DiscId? {
        val boot = text.lineSequence().mapNotNull { BOOT_LINE.find(it.trim()) }.firstOrNull()
        if (boot == null) {
            notes += "SYSTEM.CNF: no BOOT or BOOT2 line"
            return null
        }
        val kind = if (boot.groupValues[1].uppercase(Locale.ROOT) == "BOOT2") CodeKind.PS2 else CodeKind.PS1
        val file = boot.groupValues[2].trim().substringAfterLast('\\').substringAfterLast(':')
            .substringAfterLast('/').substringBefore(';').trim()
        val serial = normalise(file)
        if (serial == null) {
            notes += "SYSTEM.CNF: boot file \"${file.take(32)}\" is not a serial"
            return null
        }
        return DiscId(kind, serial, title = null)
    }

    @Throws(IOException::class)
    private fun psp(iso: Iso9660, umd: Iso9660.Entry?, sfoEntry: Iso9660.Entry?, notes: MutableList<String>): DiscId? {
        val umdSerial = umd?.let { normalise(decodeText(iso.readHead(it, UMD_DATA_LIMIT)).substringBefore('|')) }
        val sfo = sfoEntry?.let { ParamSfo.parse(iso.readHead(it, SFO_LIMIT)) }
        if (sfoEntry != null && sfo == null) notes += "psp: PSP_GAME/PARAM.SFO is damaged"
        val serial = umdSerial ?: normalise(sfo?.get("DISC_ID"))
        val title = sfo?.get("TITLE")?.let { cleanTitle(it) }
        if (serial == null && title == null) {
            notes += "psp: no serial in UMD_DATA.BIN or PARAM.SFO"
            return null
        }
        return DiscId(CodeKind.PSP.takeIf { serial != null }, serial, title, titleIsCanonical = title != null)
    }

    /** True when [start] begins with the PBP magic. */
    fun isPbp(start: ByteArray): Boolean = start.startsWith(PBP_MAGIC)

    /** `DISC_ID` and `TITLE` from a PBP's `PARAM.SFO`. */
    @Throws(IOException::class)
    fun fromPbp(source: RandomAccessSource, notes: MutableList<String>): DiscId? {
        val header = source.readFully(0, PBP_HEADER)
        if (header.size < PBP_HEADER || !isPbp(header)) throw NotRead("pbp: header is truncated")
        val start = header.u32le(8)
        val end = header.u32le(12)
        if (start < PBP_HEADER || end <= start || end > source.size || end - start > SFO_LIMIT) {
            throw NotRead("pbp: PARAM.SFO offsets $start-$end are out of range")
        }
        val sfo = ParamSfo.parse(source.readFully(start, (end - start).toInt()))
            ?: throw NotRead("pbp: PARAM.SFO is damaged")
        val serial = normalise(sfo["DISC_ID"])
        val title = sfo["TITLE"]?.let { cleanTitle(it) }
        if (serial == null) notes += "pbp: DISC_ID \"${sfo["DISC_ID"]?.take(16)}\" is not a serial"
        val kind = if (sfo["CATEGORY"] == "ME") CodeKind.PS1 else CodeKind.PSP
        return DiscId(kind.takeIf { serial != null }, serial, title, titleIsCanonical = title != null)
    }
}
