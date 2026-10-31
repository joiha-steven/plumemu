package com.plumemu.library.scrape

import com.plumemu.coredata.db.FixKeys
import com.plumemu.library.naming.FingerprintCodec
import com.plumemu.scraper.RomHints
import java.util.Locale

/**
 * What the identifier already read, as the scraper's [RomHints] (the file is never
 * read again). Always gives hints, so the scraper never opens the file: a game without a CRC is
 * looked up by its serial (if any), name and size.
 */
internal object ScrapeHintsFromFingerprint {
    /**
     * @param fingerprint `game.fingerprint` ([FingerprintCodec]); null or unreadable gives size only.
     * @param fileSize `game.size_bytes`.
     */
    fun hints(fingerprint: String?, fileSize: Long): RomHints {
        val read = FingerprintCodec.decode(fingerprint) ?: return RomHints(sizeBytes = fileSize)
        val crc = read.crc32
        // Only a code the identifier keys fixes under: a dummy header code ("0000") is no serial.
        val hasRealCode = read.fixKeys().any { it.startsWith(FixKeys.CODE_PREFIX) }
        val serial = read.productCode?.takeIf { hasRealCode }
        return RomHints(
            // The CRC as the identifier stores it: a zip entry's, header left out once read in full.
            crc32 = crc?.let { String.format(Locale.ROOT, "%08X", it) },
            serial = serial,
            // The size the CRC covers (the zip entry's, without a copier header); else the file's.
            sizeBytes = if (crc != null) read.contentSize ?: fileSize else fileSize,
            // The format the code was read from: its system's answer counts too (a .gbc in gba/).
            serialSystemId = read.codeKind?.nativeSystemId?.takeIf { serial != null },
        )
    }
}
