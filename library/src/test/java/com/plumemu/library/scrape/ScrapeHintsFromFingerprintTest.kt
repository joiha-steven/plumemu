package com.plumemu.library.scrape

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.library.naming.FingerprintCodec
import com.plumemu.scraper.RomHints
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The hints come from the stored fingerprint, never from reading the file. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScrapeHintsFromFingerprintTest {
    private fun hints(fingerprints: Fingerprints?, fileSize: Long = 4_000) =
        ScrapeHintsFromFingerprint.hints(fingerprints?.let(FingerprintCodec::encode), fileSize)

    @Test
    fun aZipGivesItsEntrysCrcAndTheEntrysSize() {
        val zip = Fingerprints(size = 600, container = Container.ZIP, innerName = "zq.sfc", entryCount = 1, contentSize = 1_048_576, crc32 = 0x0BADF00DL, crcSource = CrcSource.ZIP_DIRECTORY, archiveCrc32 = 0x0BADF00DL)
        assertEquals(RomHints(crc32 = "0BADF00D", serial = null, sizeBytes = 1_048_576), hints(zip))
    }

    @Test
    fun aZippedRomReadInFullGivesItsHeaderlessCrcAndSize() {
        val zip = Fingerprints(size = 600, container = Container.ZIP, contentSize = 40_960, crc32 = 0x1234L, crcSource = CrcSource.ZIP_ENTRY_CONTENT, headerBytesSkipped = 16, archiveCrc32 = 0x9999L)
        assertEquals(RomHints(crc32 = "00001234", serial = null, sizeBytes = 40_960), hints(zip))
    }

    @Test
    fun aDiscGivesItsSerialAndTheFileSize() {
        val chd = Fingerprints(size = 300_000_000, container = Container.CHD, productCode = "SLUS-99999", codeKind = CodeKind.PS1, chdSha1 = "ab".repeat(20))
        assertEquals(RomHints(crc32 = null, serial = "SLUS-99999", sizeBytes = 4_000, serialSystemId = "psx"), hints(chd))
    }

    @Test
    fun aCartridgeGivesItsCrcAndItsRealCodeButNotADummyCode() {
        val gba = Fingerprints(size = 65_536, container = Container.NONE, contentSize = 65_536, crc32 = 0xFFL, crcSource = CrcSource.WHOLE_FILE, productCode = "ZZZE", codeKind = CodeKind.GBA)
        assertEquals(RomHints(crc32 = "000000FF", serial = "ZZZE", sizeBytes = 65_536, serialSystemId = "gba"), hints(gba))
        assertEquals(RomHints(crc32 = "000000FF", serial = null, sizeBytes = 65_536), hints(gba.copy(productCode = "0000")))
        // A Game Boy Color file in a gba/ folder: the format's system goes with the code, not the folder's.
        val gbc = gba.copy(productCode = "AZQE", codeKind = CodeKind.GB)
        assertEquals("gb", hints(gbc).serialSystemId)
    }

    @Test
    fun withoutACrcOnlyTheNameAndTheFileSizeAreSent() {
        val loose = Fingerprints(size = 4_000, container = Container.NONE, internalTitle = "ZORK")
        assertEquals(RomHints(crc32 = null, serial = null, sizeBytes = 4_000), hints(loose))
        assertEquals("nothing read", RomHints(sizeBytes = 4_000), hints(null))
        assertEquals("unreadable", RomHints(sizeBytes = 9), ScrapeHintsFromFingerprint.hints("{not json", 9))
    }
}
