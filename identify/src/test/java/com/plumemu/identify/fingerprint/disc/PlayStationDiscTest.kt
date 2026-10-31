package com.plumemu.identify.fingerprint.disc

import com.plumemu.identify.fingerprint.CodeKind
import com.plumemu.identify.fingerprint.Container
import com.plumemu.identify.fingerprint.FingerprintReader
import com.plumemu.identify.fingerprint.FingerprintReader.Depth
import com.plumemu.identify.fingerprint.Fingerprints
import com.plumemu.identify.fingerprint.disc.DiscImages.dir
import com.plumemu.identify.fingerprint.disc.DiscImages.file
import com.plumemu.identify.fingerprint.disc.DiscImages.iso
import com.plumemu.identify.fingerprint.disc.DiscImages.paramSfo
import com.plumemu.identify.fingerprint.disc.DiscImages.putU32le
import com.plumemu.identify.source.ByteArraySource
import com.plumemu.identify.source.CountingSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayStationDiscTest {

    private fun read(bytes: ByteArray, name: String, system: String? = null, depth: Depth = Depth.QUICK): Pair<Fingerprints, Long> {
        val counting = CountingSource(ByteArraySource(bytes))
        val result = FingerprintReader().read(counting, name, system, depth)
        println("$name (${bytes.size} bytes): read ${counting.bytesRead} bytes; ${result.notes}")
        return result to counting.bytesRead
    }

    @Test
    fun ps1IsoGivesTheBootSerial() {
        val (f, bytes) = read(DiscImages.ps1Iso(), "game.iso", "psx")
        assertEquals(Container.ISO, f.container)
        assertEquals(CodeKind.PS1, f.codeKind)
        assertEquals("SLUS-99999", f.productCode)
        assertNull(f.internalTitle)
        assertEquals(listOf("code:PS1:SLUS-99999"), f.fixKeys())
        assertTrue("read $bytes", bytes < 10 * 1024)
    }

    @Test
    fun ps2IsoIsTellByBoot2() {
        val (f, _) = read(DiscImages.ps2Iso(), "game.iso", "ps2")
        assertEquals(CodeKind.PS2, f.codeKind)
        assertEquals("SLES-99998", f.productCode)
    }

    @Test
    fun rawMode1AndMode2BinsGiveTheSameSerial() {
        for (mode in listOf(1, 2)) {
            val (f, bytes) = read(DiscImages.toRaw(DiscImages.ps1Iso(), mode), "game.bin", "psx")
            assertEquals("mode $mode", Container.BIN_RAW, f.container)
            assertEquals("mode $mode", "SLUS-99999", f.productCode)
            assertTrue("mode $mode read $bytes", bytes < 12 * 1024)
        }
    }

    @Test
    fun fullReadsNoMoreThanQuickForADisc() {
        val (quick, quickBytes) = read(DiscImages.ps1Iso(padTo = 8 shl 20), "game.iso", "psx", Depth.QUICK)
        val (full, fullBytes) = read(DiscImages.ps1Iso(padTo = 8 shl 20), "game.iso", "psx", Depth.FULL)
        assertEquals(quick, full)
        assertEquals(quickBytes, fullBytes)
        assertNull(full.crc32)
    }

    @Test
    fun bootLinesInManySpellings() {
        val cases = mapOf(
            "BOOT=cdrom:SLPS_999.98;1" to "SLPS-99998",
            "boot = cdrom:\\GAME\\scus_999.97;1" to "SCUS-99997",
            "BOOT2 = cdrom0:\\SLUS_999.96;1" to "SLUS-99996",
        )
        for ((line, serial) in cases) {
            val notes = ArrayList<String>()
            assertEquals(line, serial, PlayStationDiscs.fromSystemCnf("$line\r\nVER = 1.00", notes)?.productCode)
        }
        assertEquals("SLUS-99999", PlayStationDiscs.normalise("SLUS99999"))
        assertEquals("SLUS-99999", PlayStationDiscs.normalise("slus_999.99"))
        assertNull(PlayStationDiscs.normalise("PSX.EXE"))
    }

    @Test
    fun aBootFileThatIsNotASerialIsANote() {
        val (f, _) = read(DiscImages.ps1Iso(boot = "BOOT = cdrom:\\PSX.EXE;1"), "homebrew.iso", "psx")
        assertEquals(Container.ISO, f.container)
        assertNull(f.productCode)
        assertTrue(f.notes.toString(), f.notes.any { it.contains("not a serial") })
    }

    @Test
    fun anIsoWithoutPlayStationFilesIsANote() {
        val (f, _) = read(iso(file("README.TXT", "hello")), "other.iso", null)
        assertEquals(Container.ISO, f.container)
        assertNull(f.productCode)
        assertTrue(f.notes.toString(), f.notes.any { it.contains("no SYSTEM.CNF") })
    }

    @Test
    fun pspIsoGivesSerialAndCanonicalTitle() {
        val (f, bytes) = read(DiscImages.pspIso(title = "TEST GAME: Caf\u00e9 \u2013 Portable"), "game.iso", "psp")
        assertEquals(CodeKind.PSP, f.codeKind)
        assertEquals("ULUS-99999", f.productCode)
        assertEquals("TEST GAME: Caf\u00e9 \u2013 Portable", f.internalTitle)
        assertTrue(f.titleIsCanonical)
        assertTrue("read $bytes", bytes < 16 * 1024)
    }

    @Test
    fun pspIsoWithOnlyParamSfoUsesDiscId() {
        val sfo = paramSfo(mapOf("DISC_ID" to "ULES99998", "TITLE" to "TEST GAME"))
        val (f, _) = read(iso(dir("PSP_GAME", file("PARAM.SFO", sfo))), "game.iso", "psp")
        assertEquals("ULES-99998", f.productCode)
        assertEquals("TEST GAME", f.internalTitle)
    }

    @Test
    fun pbpGivesDiscIdAndTitle() {
        val psp = DiscImages.pbp(paramSfo(mapOf("CATEGORY" to "EG", "DISC_ID" to "NPUZ99999", "TITLE" to "TEST GAME")))
        val (f, bytes) = read(psp, "EBOOT.PBP", "psp")
        assertEquals(Container.PBP, f.container)
        assertEquals(CodeKind.PSP, f.codeKind)
        assertEquals("NPUZ-99999", f.productCode)
        assertTrue(f.titleIsCanonical)
        assertTrue("read $bytes", bytes < 1024)
        val classic = DiscImages.pbp(paramSfo(mapOf("CATEGORY" to "ME", "DISC_ID" to "SLUS99999", "TITLE" to "TEST GAME")))
        val (c, _) = read(classic, "EBOOT.PBP", "psx")
        assertEquals(CodeKind.PS1, c.codeKind)
        assertEquals("SLUS-99999", c.productCode)
    }

    @Test
    fun damagedParamSfoIsANote() {
        val sfo = paramSfo(mapOf("DISC_ID" to "NPUZ99999", "TITLE" to "TEST GAME"))
        sfo.putU32le(16, 100_000) // entry count past the block
        val (f, _) = read(DiscImages.pbp(sfo), "EBOOT.PBP", "psp")
        assertNull(f.productCode)
        assertTrue(f.notes.toString(), f.notes.any { it.startsWith("pbp:") })
        // Offsets of one entry past the block: that entry is skipped, the others are kept.
        val partly = paramSfo(mapOf("DISC_ID" to "NPUZ99999", "TITLE" to "TEST GAME"))
        partly.putU32le(20 + 16 + 12, 1_000_000)
        val parsed = ParamSfo.parse(partly)
        assertEquals("NPUZ99999", parsed?.get("DISC_ID"))
        assertFalse(parsed!!.containsKey("TITLE"))
    }

    @Test
    fun serialPrefixesAndDigitSplits() {
        val cases = mapOf(
            "SCES_000.01" to "SCES-00001",
            "SLPM_123.45" to "SLPM-12345",
            "ABC_123.45" to "ABC-12345",
            "SCED_123.4" to "SCED-1234",
            "ABCDE_12345" to "ABCDE-12345",
        )
        for ((file, serial) in cases) assertEquals(file, serial, PlayStationDiscs.normalise(file))
        assertNull(PlayStationDiscs.normalise("AB_123.45"))
        assertNull(PlayStationDiscs.normalise("SLUS_1.2"))
    }

    @Test
    fun aRootOfManyFilesIsReadUpToSystemCnf() {
        val many = (1..250).map { file("F%04d.DAT".format(it), "x") }
        val image = iso(*many.toTypedArray(), file("SYSTEM.CNF", "BOOT = cdrom:\\SLUS_999.99;1\n"), padTo = 2 shl 20)
        val (f, bytes) = read(image, "game.iso", "psx")
        assertEquals("SLUS-99999", f.productCode)
        assertTrue("read $bytes", bytes < 20 * 1024)
    }

    @Test
    fun rawSectorsWithSubchannelAreTold() {
        val raw = DiscImages.toRaw(DiscImages.ps1Iso())
        val withSubchannel = PackedImages.toFrames(raw)
        val (f, _) = read(withSubchannel, "game.img", "psx")
        assertEquals(Container.BIN_RAW, f.container)
        assertEquals("SLUS-99999", f.productCode)
    }
}
