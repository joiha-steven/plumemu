package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FakeRoms
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GameCube / Wii and their containers, Sega discs, the NDS banner, and what is skipped. */
class NintendoSegaDiscTest {

    private fun read(bytes: ByteArray, name: String, system: String? = null, depth: Depth = Depth.QUICK): Pair<Fingerprints, Long> {
        val counting = CountingSource(ByteArraySource(bytes))
        val result = FingerprintReader().read(counting, name, system, depth)
        println("$name (${bytes.size} bytes): read ${counting.bytesRead} bytes; ${result.notes}")
        return result to counting.bytesRead
    }

    private fun padded(header: ByteArray, size: Int = 1 shl 20) = header.copyOf(size)

    @Test
    fun gameCubeAndWiiIsoHeaders() {
        val (gc, gcBytes) = read(padded(DiscImages.nintendoHeader()), "game.iso", "gc")
        assertEquals(Container.ISO, gc.container)
        assertEquals(CodeKind.GAMECUBE, gc.codeKind)
        assertEquals("GZZE01", gc.productCode)
        assertEquals("Test Game: Cube Edition", gc.internalTitle)
        assertTrue(gc.titleIsCanonical)
        assertTrue("read $gcBytes", gcBytes <= 8 + 16 + 0x80)
        val (wii, _) = read(padded(DiscImages.nintendoHeader("RZZP01", "Test Game Wii", wii = true)), "game.gcm", null)
        assertEquals(CodeKind.WII, wii.codeKind)
        assertEquals("RZZP01", wii.productCode)
    }

    @Test
    fun aShiftJisTitleIsLeftOutNotGarbled() {
        val header = DiscImages.nintendoHeader(title = "")
        header[0x20] = 0x83.toByte()
        header[0x21] = 0x65
        val (f, _) = read(padded(header), "game.iso", "gc")
        assertEquals("GZZE01", f.productCode)
        assertNull(f.internalTitle)
        assertFalse(f.titleIsCanonical)
    }

    @Test
    fun wbfsRvzWiaAndGczCarryTheHeader() {
        val wiiHeader = DiscImages.nintendoHeader("RZZE01", "Test Game Wii", wii = true)
        val gcImage = padded(DiscImages.nintendoHeader(), 0x10000)
        val cases = listOf(
            Triple(PackedImages.wbfs(wiiHeader), "game.wbfs", Container.WBFS),
            Triple(PackedImages.wbfs(wiiHeader, slot = 2), "slot2.wbfs", Container.WBFS),
            Triple(PackedImages.rvz(wiiHeader), "game.rvz", Container.RVZ),
            Triple(PackedImages.rvz(wiiHeader, magic = "WIA"), "game.wia", Container.WIA),
            Triple(PackedImages.gcz(gcImage), "game.gcz", Container.GCZ),
            Triple(PackedImages.gcz(gcImage, storeFirst = true), "stored.gcz", Container.GCZ),
        )
        for ((bytes, name, container) in cases) {
            val (f, read) = read(bytes, name, "wii")
            assertEquals(name, container, f.container)
            assertTrue("$name ${f.notes}", f.productCode == "RZZE01" || f.productCode == "GZZE01")
            assertTrue("$name read $read", read < 2048)
        }
    }

    @Test
    fun saturnAndSegaCdSystemAreas() {
        val (saturn, bytes) = read(DiscImages.toRaw(DiscImages.iso(systemArea = DiscImages.saturnArea(), padTo = 64 * 1024)), "game.bin", "saturn")
        assertEquals(Container.BIN_RAW, saturn.container)
        assertEquals(CodeKind.SATURN, saturn.codeKind)
        assertEquals("T-99999H", saturn.productCode)
        assertEquals("TEST GAME", saturn.internalTitle)
        assertFalse(saturn.titleIsCanonical)
        assertTrue("read $bytes", bytes < 1024)
        val (segaCd, _) = read(DiscImages.iso(systemArea = DiscImages.segaCdArea(), padTo = 64 * 1024), "game.iso", "megacd")
        assertEquals(CodeKind.SEGACD, segaCd.codeKind)
        assertEquals("T-99999", segaCd.productCode)
        assertEquals("TEST GAME CD", segaCd.internalTitle)
    }

    @Test
    fun binOfACartridgeSystemStaysACartridge() {
        val (md, _) = read(FakeRoms.megaDrive(), "game.bin", "megadrive")
        assertEquals(Container.NONE, md.container)
        assertEquals(CodeKind.MEGADRIVE, md.codeKind)
        // A .bin that is no disc falls back to a loose file; the disc note only when the system expected a disc.
        val (junk, _) = read(FakeRoms.filler(1 shl 16), "mystery.bin", "psx", Depth.FULL)
        assertEquals(Container.NONE, junk.container)
        assertTrue(junk.crc32 != null)
        assertTrue(junk.notes.toString(), junk.notes.any { it.startsWith("disc:") })
        val (unknown, _) = read(FakeRoms.filler(1 shl 16), "mystery.bin", null, Depth.FULL)
        assertTrue(unknown.crc32 != null)
        assertTrue(unknown.notes.toString(), unknown.notes.isEmpty())
    }

    @Test
    fun systemsAndFormatsNotReadGetANote() {
        val (pce, pceBytes) = read(DiscImages.ps1Iso(), "game.cue", "pcenginecd")
        assertNull(pce.productCode)
        assertTrue(pce.notes.toString(), pce.notes.single().startsWith("pcenginecd:"))
        assertEquals(8L, pceBytes)
        val (cdi, _) = read(ByteArray(4096), "game.cdi", "dreamcast", Depth.FULL)
        assertNull(cdi.crc32)
        assertTrue(cdi.notes.toString(), cdi.notes.single().startsWith("cdi:"))
    }

    @Test
    fun ndsBannerGivesTheFullEnglishTitle() {
        val rom = DiscImages.ndsWithBanner("TEST GAME: Long Title\nPublisher")
        val counting = CountingSource(ByteArraySource(rom))
        val f = FingerprintReader().read(counting, "game.nds", "nds", Depth.QUICK)
        assertEquals("ZZZE", f.productCode)
        assertEquals("TEST GAME: Long Title", f.internalTitle)
        assertTrue(f.titleIsCanonical)
        println("nds with banner: read ${counting.bytesRead} bytes")
        assertTrue(counting.bytesRead < 3 * 1024)
        val broken = DiscImages.ndsWithBanner("TEST GAME").also { it[0x8000 + 0x340] = 'X'.code.toByte() }
        val b = FingerprintReader().read(ByteArraySource(broken), "game.nds", "nds", Depth.QUICK)
        assertEquals("TESTDS", b.internalTitle)
        assertFalse(b.titleIsCanonical)
        assertTrue(b.notes.toString(), b.notes.any { it.contains("banner CRC") })
    }

    @Test
    fun ndsBannerWithASubtitleIsNotCanonicalAndKeepsBothLines() {
        val three = DiscImages.ndsWithBanner("Zork Quest\nThe Lost Isle\nTestsoft")
        val f = FingerprintReader().read(ByteArraySource(three), "game.nds", "nds", Depth.QUICK)
        assertEquals("Zork Quest - The Lost Isle", f.internalTitle)
        assertFalse("the banner does not say whether the subtitle is part of the title", f.titleIsCanonical)
        val one = FingerprintReader().read(ByteArraySource(DiscImages.ndsWithBanner("Zork Quest")), "game.nds", "nds", Depth.QUICK)
        assertEquals("Zork Quest", one.internalTitle)
        assertFalse(one.titleIsCanonical)
        val two = FingerprintReader().read(ByteArraySource(DiscImages.ndsWithBanner("Zork Quest\nTestsoft")), "game.nds", "nds", Depth.QUICK)
        assertEquals("Zork Quest", two.internalTitle)
        assertTrue(two.titleIsCanonical)
    }

    @Test
    fun gameCubeTitleFillingItsFieldIsNotCanonical() {
        val long = "A".repeat(80)
        val f = FingerprintReader().read(ByteArraySource(padded(DiscImages.nintendoHeader(title = long))), "game.iso", "gc", Depth.QUICK)
        assertEquals("only the 64-byte field is read", "A".repeat(64), f.internalTitle)
        assertFalse(f.titleIsCanonical)
        val fits = FingerprintReader().read(ByteArraySource(padded(DiscImages.nintendoHeader(title = "A".repeat(63)))), "game.iso", "gc", Depth.QUICK)
        assertEquals("A".repeat(63), fits.internalTitle)
        assertTrue("a 63-character title ends in a NUL inside the field", fits.titleIsCanonical)
    }
}
