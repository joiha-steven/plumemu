package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.codec.cd.CdEcc

/** What every CD codec of a CHD (`cdzl`, `cdlz`, `cdzs`) does after its parts are unpacked, and the hunk CRC-16. */
internal object ChdCd {
    const val SECTOR = 2352
    const val SUBCODE = 96

    /**
     * The hunk of [frames] frames of 2448 bytes from its unpacked [sectors] (2352 per frame) and
     * [subcode] (96 per frame). A set bit in [ecc] (one per frame) means the sync header and ECC
     * were cut when the CHD was made: [CdEcc.rebuild] puts them back, so the hunk is the bytes
     * chdman took its CRC from.
     *
     * @throws NotRead when a part is not the size its frames say.
     */
    fun assemble(frames: Int, ecc: ByteArray, sectors: ByteArray, subcode: ByteArray, what: String): ByteArray {
        if (sectors.size != frames * SECTOR) throw NotRead("$what: sector data is short")
        if (subcode.size != frames * SUBCODE) throw NotRead("$what: subcode is short")
        val out = ByteArray(frames * ChdHeader.CD_FRAME)
        for (frame in 0 until frames) {
            val at = frame * ChdHeader.CD_FRAME
            System.arraycopy(sectors, frame * SECTOR, out, at, SECTOR)
            System.arraycopy(subcode, frame * SUBCODE, out, at + SECTOR, SUBCODE)
            if (ecc[frame / 8].toInt() and (1 shl (frame % 8)) != 0) CdEcc.rebuild(out, at)
        }
        return out
    }

    private val CRC_TABLE = IntArray(256) { n ->
        var crc = n shl 8
        repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1 }
        crc and 0xFFFF
    }

    /** CRC-16/CCITT-FALSE (poly 0x1021, start 0xFFFF): the checksum a CHD map keeps for each hunk. */
    fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (b in data) crc = ((crc shl 8) and 0xFFFF) xor CRC_TABLE[((crc shr 8) xor b.toInt()) and 0xFF]
        return crc
    }
}
