package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.ascii
import com.plumemu.identify.fingerprint.asciiText
import com.plumemu.identify.fingerprint.cart.MegaDriveReader
import com.plumemu.identify.fingerprint.startsWith
import java.util.Locale

/**
 * Sega disc system areas, in the user data of the data track's sector 0:
 * - Saturn (Sega "Disc Format Standards", system ID): "SEGA SEGASATURN " at 0, product number
 *   at 0x20 (10 bytes), title at 0x60 (112 bytes, upper case, padded).
 * - Sega CD / Mega-CD: "SEGADISCSYSTEM  " at 0, then a Mega Drive style header at 0x100:
 *   domestic title at 0x120, overseas title at 0x150 (48 bytes each), serial at 0x180 (14),
 *   whose product code is kept as for a cartridge ([MegaDriveReader.productCode]).
 * - Dreamcast `IP.BIN` (first sector of the GD-ROM high-density area, track 3): "SEGA
 *   SEGAKATANA " at 0, product number at 0x40 (10 bytes), title at 0x80 (128 bytes).
 *
 * Header titles are upper case or cut short, so none is canonical. A raw dump whose sector has
 * the system ID at 0x10 is the raw layout; [SectorImage] already skips the 16-byte sync header.
 */
internal object SegaDiscs {
    /** Bytes of sector 0 that hold every field read here. */
    const val SYSTEM_AREA = 0x200

    private val SATURN = ascii("SEGA SEGASATURN ")
    private val SEGA_CD = ascii("SEGADISCSYSTEM  ")
    private val DREAMCAST = ascii("SEGA SEGAKATANA ")

    /** The disc's id from [area] (sector 0's first [SYSTEM_AREA] bytes), or null when it is not a Sega disc. */
    fun fromSystemArea(area: ByteArray): DiscId? = when {
        area.size < SYSTEM_AREA -> null
        area.startsWith(SATURN) -> id(CodeKind.SATURN, area.asciiText(0x20, 10), area.asciiText(0x60, 112))
        area.startsWith(SEGA_CD) ->
            id(
                CodeKind.SEGACD,
                MegaDriveReader.productCode(area, 0x180),
                area.asciiText(0x150, 48) ?: area.asciiText(0x120, 48),
            )
        area.startsWith(DREAMCAST) -> id(CodeKind.DREAMCAST, area.asciiText(0x40, 10), area.asciiText(0x80, 128))
        else -> null
    }

    private fun id(kind: CodeKind, code: String?, title: String?): DiscId {
        val serial = code?.uppercase(Locale.ROOT)
        return DiscId(kind.takeIf { serial != null }, serial, title)
    }
}
