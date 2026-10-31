package com.plumemu.library.naming

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.CrcSource
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.TitleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FingerprintCodecTest {
    @Test
    fun everyFieldButTheNotesSurvivesARoundTrip() {
        val full = Fingerprints(
            size = 5_000_000_000L,
            container = Container.ZIP,
            innerName = "Zork \u00e9 (USA).sfc",
            entryCount = 2,
            contentSize = 4_194_816L,
            crc32 = (1L shl 32) - 1,
            crcSource = CrcSource.ZIP_ENTRY_CONTENT,
            headerBytesSkipped = 512,
            productCode = "A Z",
            codeKind = CodeKind.SNES,
            internalTitle = "ZORK \"QUEST\"\n",
            notes = listOf("dropped"),
            titleIsCanonical = true,
            chdSha1 = "0123456789abcdef0123456789abcdef01234567",
            archiveCrc32 = 7,
            titleId = "0100ABCD00010800",
            titleKind = TitleKind.UPDATE,
        )
        assertEquals(full.copy(notes = emptyList()), FingerprintCodec.decode(FingerprintCodec.encode(full)))
        val nsp = Fingerprints(size = 9, container = Container.PFS0, productCode = "0100ABCD00010000", codeKind = CodeKind.SWITCH)
        assertEquals(nsp, FingerprintCodec.decode(FingerprintCodec.encode(nsp)))
        val bare = Fingerprints(size = 4, container = Container.NONE)
        assertEquals(bare, FingerprintCodec.decode(FingerprintCodec.encode(bare)))
    }

    @Test
    fun anUnreadableValueMeansReadTheFileAgain() {
        assertNull(FingerprintCodec.decode(null))
        assertNull(FingerprintCodec.decode(""))
        assertNull(FingerprintCodec.decode("{not json"))
        assertNull(FingerprintCodec.decode("""{"v":2,"size":4,"container":"NONE"}"""))
        assertNull(FingerprintCodec.decode("""{"v":1,"size":4,"container":"FLOPPY"}"""))
        assertNull(FingerprintCodec.decode("""{"v":1,"container":"NONE"}"""))
        assertNull(FingerprintCodec.decode("""{"v":1,"size":4,"container":"NONE","titleKind":"PATCH"}"""))
    }

    @Test
    fun onlySystemsWhoseReaderChangedReadOldValuesAgain() {
        val fingerprints = Fingerprints(size = 4, container = Container.NONE)
        val old = FingerprintCodec.encode(fingerprints)
        assertFalse(FingerprintCodec.isCurrent(old, "switch"))
        assertFalse(FingerprintCodec.isCurrent(old, "n3ds"))
        assertTrue("other systems keep their cache", FingerprintCodec.isCurrent(old, "gba"))
        val now = FingerprintCodec.encode(fingerprints, "switch")
        assertTrue(FingerprintCodec.isCurrent(now, "switch"))
        assertEquals(fingerprints, FingerprintCodec.decode(now))
        assertEquals("rv is left out at version 1", old, FingerprintCodec.encode(fingerprints, "gba"))
    }

    @Test
    fun aValueCachedBeforeTitleIdsStillDecodes() {
        // Scrape hints decode cached values too: adding the Switch fields must not drop old rows.
        val old = """{"v":1,"size":4,"container":"NONE","crc":7,"contentSize":4}"""
        assertEquals(Fingerprints(size = 4, container = Container.NONE, crc32 = 7, contentSize = 4), FingerprintCodec.decode(old))
    }
}
